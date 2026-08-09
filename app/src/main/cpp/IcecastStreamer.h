#ifndef ICECAST_STREAMER_H
#define ICECAST_STREAMER_H

#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <condition_variable>

enum StreamState {
    STREAM_DISCONNECTED = 0,
    STREAM_CONNECTING = 1,
    STREAM_STREAMING = 2,
    STREAM_ERROR = 3
};

class IcecastStreamer {
public:
    IcecastStreamer();
    ~IcecastStreamer();

    bool connectStream(const std::string& host, int port, const std::string& mount, 
                       const std::string& pass, int bitrateKbps);
    void disconnectStream();

    void pushAudio(const float* pcmInterleaved, int32_t numFrames, int32_t channels, int32_t sampleRate);

    int getStreamStatus() const { return mState.load(); }
    const char* getLastError() const { return mLastError.c_str(); }

private:
    std::atomic<int> mState{STREAM_DISCONNECTED};
    std::string mHost;
    int mPort{8000};
    std::string mMount;
    std::string mPassword;
    int mBitrateKbps{128};
    std::string mLastError;

    int mSocketFd{-1};

    std::thread mWorkerThread;
    std::atomic<bool> mRunning{false};

    // Ring buffer for raw PCM samples
    std::mutex mBufferMutex;
    std::condition_variable mBufferCv;
    std::vector<int16_t> mPcmRingBuffer;
    size_t mBufferHead{0};
    size_t mBufferTail{0};
    static constexpr size_t RING_BUFFER_CAPACITY = 48000 * 2 * 4; // ~4 seconds buffer

    void workerLoop();
    bool performHandshake();
    void encodeAndSend(const int16_t* pcmBuf, size_t numSamples);
    std::string base64Encode(const std::string& in);

    // MP3 Bitstream Header helper
    std::vector<uint8_t> generateMp3Frame(const int16_t* pcmBuf, size_t numSamples, int sampleRate, int channels);
};

#endif // ICECAST_STREAMER_H
