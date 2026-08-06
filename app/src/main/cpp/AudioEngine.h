#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include "IcecastStreamer.h"
#include <atomic>
#include <memory>
#include <vector>
#include <mutex>

// Simple RBJ biquad filter used to build the 3-band EQ (low-shelf / peaking / high-shelf).
struct Biquad {
    float b0 = 1.f, b1 = 0.f, b2 = 0.f, a1 = 0.f, a2 = 0.f;
    float z1 = 0.f, z2 = 0.f;

    inline float process(float in) {
        float out = in * b0 + z1;
        z1 = in * b1 + z2 - a1 * out;
        z2 = in * b2 - a2 * out;
        return out;
    }

    void setLowShelf(float sampleRate, float freq, float gainDb);
    void setHighShelf(float sampleRate, float freq, float gainDb);
    void setPeaking(float sampleRate, float freq, float gainDb, float q);
};

// Lightweight granular-style pitch shifter (not true PSOLA, but gives an audible,
// glitch-free-ish pitch effect without pulling in an external DSP library).
class SimplePitchShifter {
public:
    SimplePitchShifter();
    float process(float input, float semitones);
private:
    static constexpr size_t kSize = 8192;
    std::vector<float> mBuffer;
    size_t mWritePos = 0;
    float mReadPos = 0.f;
};

// Small Schroeder-style reverb (parallel combs + series allpass) for the "Reverberación
// de Estudio" voice effect.
class SimpleReverb {
public:
    SimpleReverb();
    float process(float input, float mix);
private:
    static constexpr int kNumCombs = 4;
    static constexpr int kNumAllpass = 2;
    std::vector<std::vector<float>> mCombBuffers;
    std::vector<size_t> mCombPos;
    std::vector<std::vector<float>> mAllpassBuffers;
    std::vector<size_t> mAllpassPos;
};

// Very small ring buffer for feeding decoded music PCM (from the Kotlin/MediaCodec deck)
// into the native mix, and for one-shot synthesized soundboard effects.
class PcmFeedBuffer {
public:
    explicit PcmFeedBuffer(size_t capacitySamples);
    void push(const int16_t* samples, size_t count);
    // Pops one interleaved stereo frame (2 samples) as floats in [-1,1]; returns false if empty.
    bool popFrame(float* left, float* right);
    void clear();
    size_t available() const;
private:
    mutable std::mutex mMutex;
    std::vector<int16_t> mBuffer;
    size_t mHead = 0;
    size_t mTail = 0;
    size_t mCapacity;
};

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool startRecordingAndPlay();
    void stopRecordingAndPlay();

    // Sound controls & mixing parameters
    void setMasterVolume(float volume);
    void setMicGain(float gain);
    void setMusicVolume(float volume);
    void setDuckingEnabled(bool enabled);
    void setEqGains(float lowDb, float midDb, float highDb);
    void setVoiceEffects(float reverbMix, float pitchSemitones, float noiseGateDb);
    void playSoundboardEffect(int effectId);

    // Music deck: fed from Kotlin (decoded from local files via MediaCodec)
    void feedMusicPcm(const int16_t* interleaved, int32_t frames, int32_t channels, int32_t sampleRate);
    void setMusicPlaying(bool playing);
    void clearMusicBuffer();

    // Network Streaming (Icecast)
    bool connectStream(const std::string& host, int port, const std::string& mount, const std::string& pass,
                       int bitrateKbps, int protocol, const std::string& stationName);
    void disconnectStream();
    int getStreamStatus();

    // Engine telemetry & state
    bool isLive() const { return mIsLive.load(); }
    float getPeakVuMeter() const { return mPeakVuMeter.load(); }
    int getSampleRate() const { return mSampleRate; }
    int getLatencyMillis() const { return mLatencyMillis; }
    const char* getAudioApiName() const;

    // Oboe Callbacks
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mIsLive{false};
    std::atomic<float> mMasterVolume{1.0f};
    std::atomic<float> mMicGain{1.0f};
    std::atomic<float> mMusicVolume{0.8f};
    std::atomic<bool> mDuckingEnabled{true};
    std::atomic<float> mPeakVuMeter{0.0f};
    std::atomic<bool> mMusicPlaying{false};

    // 3-band EQ parameters (dB)
    std::atomic<float> mEqLow{0.0f};
    std::atomic<float> mEqMid{0.0f};
    std::atomic<float> mEqHigh{0.0f};
    bool mEqDirty{true};
    float mEqLowApplied{0.f}, mEqMidApplied{0.f}, mEqHighApplied{0.f};
    Biquad mEqLowBand[2];
    Biquad mEqMidBand[2];
    Biquad mEqHighBand[2];

    // Voice FX
    std::atomic<float> mVoiceReverbMix{0.0f};
    std::atomic<float> mVoicePitchSemitones{0.0f};
    std::atomic<float> mNoiseGateDb{-45.0f};
    SimplePitchShifter mPitchShifter[2];
    SimpleReverb mReverb[2];
    float mGateEnvelope{0.f};

    // Ducking (sidechain envelope follower on the mic signal)
    float mDuckEnvelope{0.f};

    // Soundboard one-shot synth effect state
    std::atomic<int> mActiveFxId{-1};
    double mFxPhase{0.0};
    int64_t mFxSamplesRemaining{0};

    // Music deck feed buffer (stereo interleaved int16 @ engine sample rate)
    PcmFeedBuffer mMusicBuffer;

    int32_t mSampleRate{48000};
    int32_t mChannelCount{2};
    int32_t mLatencyMillis{12};

    IcecastStreamer mStreamer;

    void processAudioFrame(float* input, float* output, int32_t numFrames);
    void updateEqIfNeeded();
    float renderFxSample();
};

#endif // AUDIO_ENGINE_H
