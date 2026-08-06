#include "AudioEngine.h"
#include "AudioMixer.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdlib>

#define LOG_TAG "OboeRadioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Biquad (RBJ Audio EQ Cookbook formulas)
// ---------------------------------------------------------------------------
void Biquad::setLowShelf(float sampleRate, float freq, float gainDb) {
    float A = powf(10.f, gainDb / 40.f);
    float w0 = 2.f * (float)M_PI * freq / sampleRate;
    float cosw0 = cosf(w0);
    float sinw0 = sinf(w0);
    float S = 1.0f; // shelf slope
    float alpha = sinw0 / 2.f * sqrtf((A + 1.f / A) * (1.f / S - 1.f) + 2.f);
    float twoSqrtAalpha = 2.f * sqrtf(A) * alpha;

    float b0 = A * ((A + 1.f) - (A - 1.f) * cosw0 + twoSqrtAalpha);
    float b1 = 2.f * A * ((A - 1.f) - (A + 1.f) * cosw0);
    float b2 = A * ((A + 1.f) - (A - 1.f) * cosw0 - twoSqrtAalpha);
    float a0 = (A + 1.f) + (A - 1.f) * cosw0 + twoSqrtAalpha;
    float a1 = -2.f * ((A - 1.f) + (A + 1.f) * cosw0);
    float a2 = (A + 1.f) + (A - 1.f) * cosw0 - twoSqrtAalpha;

    this->b0 = b0 / a0; this->b1 = b1 / a0; this->b2 = b2 / a0;
    this->a1 = a1 / a0; this->a2 = a2 / a0;
}

void Biquad::setHighShelf(float sampleRate, float freq, float gainDb) {
    float A = powf(10.f, gainDb / 40.f);
    float w0 = 2.f * (float)M_PI * freq / sampleRate;
    float cosw0 = cosf(w0);
    float sinw0 = sinf(w0);
    float S = 1.0f;
    float alpha = sinw0 / 2.f * sqrtf((A + 1.f / A) * (1.f / S - 1.f) + 2.f);
    float twoSqrtAalpha = 2.f * sqrtf(A) * alpha;

    float b0 = A * ((A + 1.f) + (A - 1.f) * cosw0 + twoSqrtAalpha);
    float b1 = -2.f * A * ((A - 1.f) + (A + 1.f) * cosw0);
    float b2 = A * ((A + 1.f) + (A - 1.f) * cosw0 - twoSqrtAalpha);
    float a0 = (A + 1.f) - (A - 1.f) * cosw0 + twoSqrtAalpha;
    float a1 = 2.f * ((A - 1.f) - (A + 1.f) * cosw0);
    float a2 = (A + 1.f) - (A - 1.f) * cosw0 - twoSqrtAalpha;

    this->b0 = b0 / a0; this->b1 = b1 / a0; this->b2 = b2 / a0;
    this->a1 = a1 / a0; this->a2 = a2 / a0;
}

void Biquad::setPeaking(float sampleRate, float freq, float gainDb, float q) {
    float A = powf(10.f, gainDb / 40.f);
    float w0 = 2.f * (float)M_PI * freq / sampleRate;
    float cosw0 = cosf(w0);
    float sinw0 = sinf(w0);
    float alpha = sinw0 / (2.f * q);

    float b0 = 1.f + alpha * A;
    float b1 = -2.f * cosw0;
    float b2 = 1.f - alpha * A;
    float a0 = 1.f + alpha / A;
    float a1 = -2.f * cosw0;
    float a2 = 1.f - alpha / A;

    this->b0 = b0 / a0; this->b1 = b1 / a0; this->b2 = b2 / a0;
    this->a1 = a1 / a0; this->a2 = a2 / a0;
}

// ---------------------------------------------------------------------------
// SimplePitchShifter — dual-tap read pointer over a circular write buffer,
// crossfaded to avoid clicks. Not true PSOLA, but gives a real, audible
// pitch effect with no external dependency.
// ---------------------------------------------------------------------------
SimplePitchShifter::SimplePitchShifter() {
    mBuffer.assign(kSize, 0.f);
}

float SimplePitchShifter::process(float input, float semitones) {
    if (fabsf(semitones) < 0.01f) {
        // Bypass: just keep the buffer warm for a click-free re-engage later.
        mBuffer[mWritePos] = input;
        mWritePos = (mWritePos + 1) % kSize;
        return input;
    }

    mBuffer[mWritePos] = input;

    float ratio = powf(2.0f, semitones / 12.0f);
    const float grain = (float) kSize / 2.f;

    mReadPos += ratio;
    while (mReadPos >= (float) kSize) mReadPos -= (float) kSize;
    while (mReadPos < 0.f) mReadPos += (float) kSize;

    auto tap = [&](float pos) -> float {
        size_t i0 = (size_t) pos % kSize;
        size_t i1 = (i0 + 1) % kSize;
        float frac = pos - floorf(pos);
        return mBuffer[i0] * (1.f - frac) + mBuffer[i1] * frac;
    };

    float posA = mReadPos;
    float posB = fmodf(mReadPos + grain, (float) kSize);

    // Distance from write head determines fade-out to avoid reading stale/garbage seams.
    float distA = fmodf((float) mWritePos - posA + (float) kSize, (float) kSize);
    float distB = fmodf((float) mWritePos - posB + (float) kSize, (float) kSize);
    float weightA = sinf((distA / grain) * (float) M_PI);
    float weightB = sinf((distB / grain) * (float) M_PI);
    weightA = weightA < 0.f ? 0.f : weightA;
    weightB = weightB < 0.f ? 0.f : weightB;
    float sumW = weightA + weightB;
    if (sumW < 0.0001f) sumW = 1.f;

    float outSample = (tap(posA) * weightA + tap(posB) * weightB) / sumW;

    mWritePos = (mWritePos + 1) % kSize;
    return outSample;
}

// ---------------------------------------------------------------------------
// SimpleReverb — classic Schroeder network (parallel combs + series allpass)
// ---------------------------------------------------------------------------
SimpleReverb::SimpleReverb() {
    const int combLengths[kNumCombs] = {1116, 1188, 1277, 1356};
    const int allpassLengths[kNumAllpass] = {556, 441};

    for (int i = 0; i < kNumCombs; ++i) {
        mCombBuffers.emplace_back(std::vector<float>(combLengths[i], 0.f));
        mCombPos.push_back(0);
    }
    for (int i = 0; i < kNumAllpass; ++i) {
        mAllpassBuffers.emplace_back(std::vector<float>(allpassLengths[i], 0.f));
        mAllpassPos.push_back(0);
    }
}

float SimpleReverb::process(float input, float mix) {
    if (mix <= 0.001f) return input;

    float combSum = 0.f;
    const float feedback = 0.78f;
    for (int i = 0; i < kNumCombs; ++i) {
        auto& buf = mCombBuffers[i];
        size_t& pos = mCombPos[i];
        float delayed = buf[pos];
        buf[pos] = input + delayed * feedback;
        pos = (pos + 1) % buf.size();
        combSum += delayed;
    }
    combSum /= (float) kNumCombs;

    float apOut = combSum;
    const float apFeedback = 0.5f;
    for (int i = 0; i < kNumAllpass; ++i) {
        auto& buf = mAllpassBuffers[i];
        size_t& pos = mAllpassPos[i];
        float bufOut = buf[pos];
        float y = -apFeedback * apOut + bufOut;
        buf[pos] = apOut + apFeedback * y;
        pos = (pos + 1) % buf.size();
        apOut = y;
    }

    return input * (1.f - mix) + apOut * mix;
}

// ---------------------------------------------------------------------------
// PcmFeedBuffer — small SPSC-ish ring buffer for the music deck / soundboard
// ---------------------------------------------------------------------------
PcmFeedBuffer::PcmFeedBuffer(size_t capacitySamples) : mCapacity(capacitySamples) {
    mBuffer.assign(capacitySamples, 0);
}

void PcmFeedBuffer::push(const int16_t* samples, size_t count) {
    std::lock_guard<std::mutex> lock(mMutex);
    for (size_t i = 0; i < count; ++i) {
        size_t next = (mHead + 1) % mCapacity;
        if (next == mTail) {
            mTail = (mTail + 1) % mCapacity; // drop oldest to avoid unbounded latency
        }
        mBuffer[mHead] = samples[i];
        mHead = next;
    }
}

bool PcmFeedBuffer::popFrame(float* left, float* right) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mTail == mHead) return false;
    // Need at least 2 samples (interleaved stereo) available.
    size_t available = (mHead >= mTail) ? (mHead - mTail) : (mCapacity - mTail + mHead);
    if (available < 2) return false;

    int16_t l = mBuffer[mTail];
    mTail = (mTail + 1) % mCapacity;
    int16_t r = mBuffer[mTail];
    mTail = (mTail + 1) % mCapacity;

    *left = l / 32768.f;
    *right = r / 32768.f;
    return true;
}

void PcmFeedBuffer::clear() {
    std::lock_guard<std::mutex> lock(mMutex);
    mHead = 0;
    mTail = 0;
}

size_t PcmFeedBuffer::available() const {
    std::lock_guard<std::mutex> lock(mMutex);
    return (mHead >= mTail) ? (mHead - mTail) : (mCapacity - mTail + mHead);
}

// ---------------------------------------------------------------------------
// AudioEngine
// ---------------------------------------------------------------------------
AudioEngine::AudioEngine() : mMusicBuffer(48000 * 2 * 8) {
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

    // Configure Input Stream (Microphone).
    // InputPreset::VoiceCommunication turns on the device's built-in AEC / noise
    // suppression / automatic gain control, which is what stops the howling/static
    // ("hace ruido") you get from a raw unprocessed full-duplex mic+speaker loop.
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Shared)
             ->setFormat(oboe::AudioFormat::Float)
             ->setChannelCount(mChannelCount)
             ->setSampleRate(mSampleRate)
             ->setInputPreset(oboe::InputPreset::VoiceCommunication);

    result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGI("VoiceCommunication input preset failed, falling back to Generic preset");
        inBuilder.setInputPreset(oboe::InputPreset::Generic);
        result = inBuilder.openStream(mInputStream);
    }

    if (result != oboe::Result::OK) {
        LOGE("Failed to open Input (mic) Stream: %s — continuing output-only", oboe::convertToText(result));
    }

    if (mInputStream) {
        auto startResult = mInputStream->requestStart();
        if (startResult != oboe::Result::OK) {
            LOGE("Failed to start input stream: %s", oboe::convertToText(startResult));
        }
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
    mMusicBuffer.clear();
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
    mEqDirty = true;
}

void AudioEngine::setVoiceEffects(float reverbMix, float pitchSemitones, float noiseGateDb) {
    mVoiceReverbMix.store(reverbMix);
    mVoicePitchSemitones.store(pitchSemitones);
    mNoiseGateDb.store(noiseGateDb);
}

void AudioEngine::playSoundboardEffect(int effectId) {
    LOGI("Triggered Soundboard Effect ID: %d", effectId);
    mFxPhase = 0.0;
    // ~900ms one-shot synthesized cue, different timbre per pad id so each pad is
    // distinguishable even without bundling external audio assets.
    mFxSamplesRemaining = (int64_t) (mSampleRate * 0.9);
    mActiveFxId.store(effectId);
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

void AudioEngine::feedMusicPcm(const int16_t* interleaved, int32_t frames, int32_t channels, int32_t sampleRate) {
    if (frames <= 0 || channels <= 0) return;

    // Resample (linear) + up/down-mix to the engine's stereo output rate so any
    // locally decoded file (whatever its native rate/channels) mixes correctly.
    if (sampleRate == mSampleRate && channels == 2) {
        mMusicBuffer.push(interleaved, (size_t) frames * 2);
        return;
    }

    double ratio = (double) mSampleRate / (double) sampleRate;
    int32_t outFrames = (int32_t) (frames * ratio);
    if (outFrames <= 0) return;

    std::vector<int16_t> out(outFrames * 2);
    for (int32_t i = 0; i < outFrames; ++i) {
        double srcPosD = i / ratio;
        int32_t srcPos = (int32_t) srcPosD;
        float frac = (float) (srcPosD - srcPos);
        if (srcPos >= frames - 1) srcPos = frames - 2 >= 0 ? frames - 2 : 0;

        int16_t l0, r0, l1, r1;
        if (channels == 1) {
            l0 = r0 = interleaved[srcPos];
            l1 = r1 = interleaved[std::min(srcPos + 1, frames - 1)];
        } else {
            l0 = interleaved[srcPos * channels];
            r0 = interleaved[srcPos * channels + 1];
            l1 = interleaved[std::min(srcPos + 1, frames - 1) * channels];
            r1 = interleaved[std::min(srcPos + 1, frames - 1) * channels + 1];
        }
        out[i * 2] = (int16_t) (l0 + (l1 - l0) * frac);
        out[i * 2 + 1] = (int16_t) (r0 + (r1 - r0) * frac);
    }
    mMusicBuffer.push(out.data(), out.size());
}

void AudioEngine::setMusicPlaying(bool playing) {
    mMusicPlaying.store(playing);
    if (!playing) mMusicBuffer.clear();
}

void AudioEngine::clearMusicBuffer() {
    mMusicBuffer.clear();
}

const char* AudioEngine::getAudioApiName() const {
    if (mOutputStream) {
        return (mOutputStream->getAudioApi() == oboe::AudioApi::AAudio) ? "AAudio (Native)" : "OpenSL ES";
    }
    return "AAudio (Default)";
}

void AudioEngine::updateEqIfNeeded() {
    float low = mEqLow.load();
    float mid = mEqMid.load();
    float high = mEqHigh.load();
    if (!mEqDirty && low == mEqLowApplied && mid == mEqMidApplied && high == mEqHighApplied) return;

    for (int ch = 0; ch < 2; ++ch) {
        mEqLowBand[ch].setLowShelf((float) mSampleRate, 100.f, low);
        mEqMidBand[ch].setPeaking((float) mSampleRate, 1000.f, mid, 0.9f);
        mEqHighBand[ch].setHighShelf((float) mSampleRate, 10000.f, high);
    }
    mEqLowApplied = low;
    mEqMidApplied = mid;
    mEqHighApplied = high;
    mEqDirty = false;
}

float AudioEngine::renderFxSample() {
    int fxId = mActiveFxId.load();
    if (fxId < 0 || mFxSamplesRemaining <= 0) {
        mActiveFxId.store(-1);
        return 0.f;
    }

    // Each pad gets a distinct synthesized cue (tone/sweep/noise-burst) purely so the
    // soundboard is audibly functional without requiring bundled mp3/wav assets.
    // Drop your own stings into res/raw and swap this for MediaCodec playback later.
    double t = mFxPhase;
    float envelope = (float) std::min(1.0, mFxSamplesRemaining / (mSampleRate * 0.9));
    float sample;
    switch (fxId % 8) {
        case 0: sample = sinf((float) (2.0 * M_PI * 1200.0 * t)); break; // AIRHORN
        case 1: sample = sinf((float) (2.0 * M_PI * (600.0 + 400.0 * sin(t * 6.0)) * t)); break; // APPLAUSE swell
        case 2: sample = sinf((float) (2.0 * M_PI * 880.0 * t)) * 0.6f + sinf((float)(2.0 * M_PI * 1320.0 * t)) * 0.4f; break; // JINGLE
        case 3: sample = ((float) rand() / RAND_MAX * 2.f - 1.f) * 0.5f; break; // CHEER (noise burst)
        case 4: sample = sinf((float) (2.0 * M_PI * (200.0 + 3000.0 * t) * t)); break; // SCRATCH sweep
        case 5: sample = ((float) rand() / RAND_MAX * 2.f - 1.f) * (fmodf((float) t, 0.15f) < 0.03f ? 1.f : 0.f); break; // DRUMS
        case 6: sample = sinf((float) (2.0 * M_PI * 90.0 * t)); break; // BASS DROP
        default: sample = sinf((float) (2.0 * M_PI * 660.0 * t)); break; // VOX ID
    }

    mFxPhase += 1.0 / mSampleRate;
    mFxSamplesRemaining--;
    return sample * envelope * 0.7f;
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

    // Push the final mixed signal to Icecast streamer
    mStreamer.pushAudio(output, numFrames, mChannelCount, mSampleRate);

    // Calculate VU meter peak for UI meter telemetry
    float rms = AudioMixer::calculateRMS(output, numSamples);
    mPeakVuMeter.store(rms);

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processAudioFrame(float* input, float* output, int32_t numFrames) {
    updateEqIfNeeded();

    float masterVol = mMasterVolume.load();
    float micGain = mMicGain.load();
    float musicVol = mMusicVolume.load();
    bool duckingOn = mDuckingEnabled.load();
    float reverbMix = mVoiceReverbMix.load();
    float pitchSemis = mVoicePitchSemitones.load();
    float gateDb = mNoiseGateDb.load();
    float gateLinear = powf(10.f, gateDb / 20.f);

    const float duckAttack = 0.35f;   // fast pull-down when voice starts
    const float duckRelease = 0.02f;  // slower recovery when voice stops
    const float duckMinMusicGain = 0.22f; // how far music ducks under voice

    for (int i = 0; i < numFrames; ++i) {
        // --- Values computed once per frame (shared across channels) ---

        // Sidechain envelope for ducking, based on the raw (pre-gate) mic level.
        float sideEnv = 0.f;
        for (int ch = 0; ch < mChannelCount && ch < 2; ++ch) {
            sideEnv = std::max(sideEnv, fabsf(input[i * mChannelCount + ch] * micGain));
        }
        if (sideEnv > mDuckEnvelope) {
            mDuckEnvelope += (sideEnv - mDuckEnvelope) * duckAttack;
        } else {
            mDuckEnvelope += (sideEnv - mDuckEnvelope) * duckRelease;
        }
        float duckAmount = duckingOn ? std::min(1.f, mDuckEnvelope * 8.f) : 0.f;
        float musicDuckGain = 1.f - duckAmount * (1.f - duckMinMusicGain);

        // Music deck: pop one stereo frame per audio frame.
        float musicL = 0.f, musicR = 0.f;
        if (mMusicPlaying.load()) {
            mMusicBuffer.popFrame(&musicL, &musicR);
        }

        // One-shot soundboard FX: same synthesized sample fed to both channels.
        float fxSample = renderFxSample();

        for (int ch = 0; ch < mChannelCount && ch < 2; ++ch) {
            int idx = i * mChannelCount + ch;

            // 1) Mic chain: gain -> noise gate -> EQ -> reverb -> pitch shift
            float micSample = input[idx] * micGain;

            float micAbs = fabsf(micSample);
            float targetEnv = micAbs > mGateEnvelope ? micAbs : mGateEnvelope * 0.995f;
            mGateEnvelope = targetEnv;
            if (mGateEnvelope < gateLinear) {
                micSample = 0.f;
            }

            micSample = mEqLowBand[ch].process(micSample);
            micSample = mEqMidBand[ch].process(micSample);
            micSample = mEqHighBand[ch].process(micSample);

            if (reverbMix > 0.001f) {
                micSample = mReverb[ch].process(micSample, reverbMix);
            }
            if (fabsf(pitchSemis) > 0.01f) {
                micSample = mPitchShifter[ch].process(micSample, pitchSemis);
            }

            // 2) Music deck sample (already ducked against the mic sidechain)
            float musicSample = (ch == 0 ? musicL : musicR) * musicVol * musicDuckGain;

            // 3) One-shot soundboard FX (identical on both channels)
            float mixedSample = (micSample + musicSample + fxSample) * masterVol;
            output[idx] = AudioMixer::softClip(mixedSample);
        }
    }
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Oboe error after close: %s", oboe::convertToText(error));
}
