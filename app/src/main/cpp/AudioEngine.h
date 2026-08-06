#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include "IcecastStreamer.h"
#include <atomic>
#include <memory>
#include <vector>

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
    void playSoundboardEffect(int effectId);

    // Network Streaming (Icecast)
    bool connectStream(const std::string& host, int port, const std::string& mount, const std::string& pass, int bitrateKbps);
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

    // 3-band EQ parameters
    std::atomic<float> mEqLow{0.0f};
    std::atomic<float> mEqMid{0.0f};
    std::atomic<float> mEqHigh{0.0f};

    int32_t mSampleRate{48000};
    int32_t mChannelCount{2};
    int32_t mLatencyMillis{12};

    IcecastStreamer mStreamer;

    void processAudioFrame(float* input, float* output, int32_t numFrames);
};

#endif // AUDIO_ENGINE_H
