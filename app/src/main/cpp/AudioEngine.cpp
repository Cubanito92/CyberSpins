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
              ->setDataCallback(this)
              ->setErrorCallback(this);

    oboe::Result result = outBuilder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Output Stream: %s", oboe::convertToText(result));
        return false;
    }

    mSampleRate = mOutputStream->getSampleRate();
    mChannelCount = mOutputStream->getChannelCount();

    // Configure Input Stream (Microphone)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Exclusive)
             ->setFormat(oboe::AudioFormat::Float)
             ->setChannelCount(mChannelCount)
             ->setSampleRate(mSampleRate);

    result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGI("Could not open input stream exclusively, falling back to Shared mode");
        inBuilder.setSharingMode(oboe::SharingMode::Shared);
        result = inBuilder.openStream(mInputStream);
    }

    if (mInputStream) {
        mInputStream->requestStart();
    }
    if (mOutputStream) {
        mOutputStream->requestStart();
    }

    mIsLive.store(true);
    LOGI("AudioEngine started successfully with Oboe! SampleRate: %d", mSampleRate);
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
    
    float *output = static_cast<float*>(audioData);
    int32_t numSamples = numFrames * mChannelCount;

    std::vector<float> inputBuffer(numSamples, 0.0f);

    if (mInputStream && mInputStream->getState() == oboe::StreamState::Started) {
        mInputStream->read(inputBuffer.data(), numFrames, 0);
    }

    processAudioFrame(inputBuffer.data(), output, numFrames);

    // Push audio frame to Icecast streamer
    mStreamer.pushAudio(output, numFrames, mChannelCount, mSampleRate);

    // Calculate VU meter peak for UI meter telemetry
    float rms = AudioMixer::calculateRMS(output, numSamples);
    mPeakVuMeter.store(rms);

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processAudioFrame(float* input, float* output, int32_t numFrames) {
    float masterVol = mMasterVolume.load();
    float micGain = mMicGain.load();
    int32_t totalSamples = numFrames * mChannelCount;

    for (int i = 0; i < totalSamples; ++i) {
        float micSample = input[i] * micGain;
        float mixedSample = micSample * masterVol;
        output[i] = AudioMixer::softClip(mixedSample);
    }
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
}
