#include "AudioEngine.h"
#include "AudioMixer.h"
#include <android/log.h>
#include <cmath>

#define LOG_TAG "OboeRadioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    LOGI("AudioEngine initialized.");
}

AudioEngine::~AudioEngine() {
    stopRecordingAndPlay();
}

bool AudioEngine::startRecordingAndPlay() {
    if (mIsLive.load()) {
        LOGI("AudioEngine already running.");
        return true;
    }

    // Configure Output Stream
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
              ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
              ->setSharingMode(oboe::SharingMode::Exclusive)
              ->setFormat(oboe::AudioFormat::Float)
              ->setChannelCount(oboe::ChannelCount::Stereo)
              ->setSampleRate(mSampleRate)
              ->setUsage(oboe::Usage::Media)
              ->setDataCallback(this)
              ->setErrorCallback(this);

    oboe::Result result = outBuilder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Output Stream: %s", oboe::convertToText(result));
        return false;
    }

    mSampleRate = mOutputStream->getSampleRate();
    mChannelCount = mOutputStream->getChannelCount();

    // Mic ring buffer: ~300ms of headroom, enough to absorb clock drift
    // between the input and output hardware streams without audible delay.
    size_t ringCapacitySamples = static_cast<size_t>(mSampleRate) * mChannelCount * 3 / 10;
    mMicRingBuffer = std::make_unique<RingBuffer>(ringCapacitySamples);

    // Configure Input Stream (Microphone).
    // - VoiceCommunication preset asks Android to route the mic through its
    //   platform Acoustic Echo Canceler / Noise Suppressor / AGC chain,
    //   which is what stops the phone's own speaker output from feeding
    //   back into the mic as a whistle/beep.
    // - SessionId::Allocate gives us a valid audio session id so the Kotlin
    //   side can additionally attach AcousticEchoCanceler / NoiseSuppressor
    //   explicitly for devices that don't auto-apply them on this preset.
    // - Its own data callback (instead of a blocking read() call inside the
    //   output callback) is what fixes the "robotic" degradation: each
    //   stream now only ever runs on its own callback thread.
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Exclusive)
             ->setFormat(oboe::AudioFormat::Float)
             ->setChannelCount(mChannelCount)
             ->setSampleRate(mSampleRate)
             ->setInputPreset(oboe::InputPreset::VoiceCommunication)
             ->setSessionId(oboe::SessionId::Allocate)
             ->setDataCallback(this)
             ->setErrorCallback(this);

    result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGI("Could not open input stream exclusively, falling back to Shared mode");
        inBuilder.setSharingMode(oboe::SharingMode::Shared);
        result = inBuilder.openStream(mInputStream);
    }

    if (mInputStream) {
        mInputSessionId = mInputStream->getSessionId();
        mInputStream->requestStart();
    }
    if (mOutputStream) {
        mOutputStream->requestStart();
    }

    mGateEnvelope = 0.0f;
    mGateGain = 0.0f;
    mIsLive.store(true);
    LOGI("AudioEngine started successfully with Oboe! SampleRate: %d, InputSessionId: %d", mSampleRate, mInputSessionId);
    return true;
}

void AudioEngine::stopRecordingAndPlay() {
    if (!mIsLive.load()) return;
    mIsLive.store(false);

    if (mInputStream) {
        mInputStream->requestStop();
        mInputStream->close();
        mInputStream.reset();
    }
    if (mOutputStream) {
        mOutputStream->requestStop();
        mOutputStream->close();
        mOutputStream.reset();
    }
    mMicRingBuffer.reset();
    LOGI("AudioEngine stopped.");
}

void AudioEngine::setMasterVolume(float volume) {
    mMasterVolume.store(volume);
}

void AudioEngine::setMicGain(float gain) {
    mMicGain.store(gain);
}

void AudioEngine::setMusicVolume(float volume) {
    mMusicVolume.store(volume);
}

void AudioEngine::setDuckingEnabled(bool enabled) {
    mDuckingEnabled.store(enabled);
}

void AudioEngine::setEqGains(float lowDb, float midDb, float highDb) {
    mEqLow.store(lowDb);
    mEqMid.store(midDb);
    mEqHigh.store(highDb);
}

void AudioEngine::setNoiseGateThresholdDb(float thresholdDb) {
    mNoiseGateThresholdDb.store(thresholdDb);
}

void AudioEngine::playSoundboardEffect(int effectId) {
    LOGI("Triggered Soundboard Effect ID: %d", effectId);
}

bool AudioEngine::connectStream(const std::string& host, int port, const std::string& mount, const std::string& pass, int bitrateKbps) {
    return mStreamer.connectStream(host, port, mount, pass, bitrateKbps);
}

void AudioEngine::disconnectStream() {
    mStreamer.disconnectStream();
}

int AudioEngine::getStreamStatus() {
    return mStreamer.getStreamStatus();
}

const char* AudioEngine::getAudioApiName() const {
    if (mOutputStream) {
        return (mOutputStream->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio (Native)" : "OpenSL ES";
    }
    return "AAudio (Default)";
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

    if (oboeStream->getDirection() == oboe::Direction::Input) {
        onMicCaptureReady(audioData, numFrames);
    } else {
        onRenderReady(audioData, numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

// Runs on the INPUT stream's own callback thread. Only touches the ring
// buffer - never blocks, never talks to the output stream directly.
void AudioEngine::onMicCaptureReady(void* audioData, int32_t numFrames) {
    if (!mMicRingBuffer) return;
    float* input = static_cast<float*>(audioData);
    int32_t numSamples = numFrames * mChannelCount;
    mMicRingBuffer->write(input, numSamples);
}

// Runs on the OUTPUT stream's own callback thread. Pulls whatever mic audio
// is available from the ring buffer (with a gentle fade instead of a hard
// drop if the input is briefly behind), mixes it, and writes to the output.
void AudioEngine::onRenderReady(void* audioData, int32_t numFrames) {
    float *output = static_cast<float*>(audioData);
    int32_t numSamples = numFrames * mChannelCount;

    std::vector<float> micBuffer(numSamples, 0.0f);
    if (mMicRingBuffer) {
        mMicRingBuffer->read(micBuffer.data(), numSamples);
    }

    float masterVol = mMasterVolume.load();
    float micGain = mMicGain.load();

    for (int32_t f = 0; f < numFrames; ++f) {
        float* frame = &micBuffer[f * mChannelCount];
        float gateGain = applyNoiseGate(frame, mChannelCount);

        for (int32_t c = 0; c < mChannelCount; ++c) {
            int idx = f * mChannelCount + c;
            float micSample = micBuffer[idx] * gateGain * micGain;
            output[idx] = AudioMixer::softClip(micSample * masterVol);
        }
    }

    // Push audio frame to Icecast streamer
    mStreamer.pushAudio(output, numFrames, mChannelCount, mSampleRate);

    // Calculate VU meter peak for UI meter telemetry
    float rms = AudioMixer::calculateRMS(output, numSamples);
    mPeakVuMeter.store(rms);
}

// Smooth attack/release noise gate. Keeps the voice sounding fully clean by
// muting the mic between words (hiss, hum, faint room echo) while never
// clipping the start of a word. Operates on a per-frame envelope so it
// reacts fast enough for speech but doesn't chatter on quiet passages.
float AudioEngine::applyNoiseGate(float* frame, int32_t channelCount) {
    float peak = 0.0f;
    for (int32_t c = 0; c < channelCount; ++c) {
        peak = std::max(peak, std::fabs(frame[c]));
    }

    // Envelope follower (fast attack, slower release)
    const float attack = 0.6f;
    const float release = 0.05f;
    if (peak > mGateEnvelope) {
        mGateEnvelope += (peak - mGateEnvelope) * attack;
    } else {
        mGateEnvelope += (peak - mGateEnvelope) * release;
    }

    float thresholdDb = mNoiseGateThresholdDb.load();
    float thresholdLinear = powf(10.0f, thresholdDb / 20.0f);

    float envelopeDb = 20.0f * log10f(std::max(mGateEnvelope, 1e-6f));
    float targetGain = (envelopeDb > thresholdDb) ? 1.0f : 0.0f;

    // Smooth the gate itself so it fades instead of clicking open/closed.
    const float gateSmoothing = 0.08f;
    mGateGain += (targetGain - mGateGain) * gateSmoothing;

    (void) thresholdLinear;
    return mGateGain;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
    // If the input stream died (common cause of the mic going silent or
    // glitching mid-broadcast), tear the ring buffer state down so the next
    // start begins clean instead of compounding drift.
    if (oboeStream == mInputStream.get()) {
        mGateEnvelope = 0.0f;
        mGateGain = 0.0f;
    }
}
