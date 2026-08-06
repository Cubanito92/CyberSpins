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

// Handshake dialect used to negotiate the source connection with the streaming
// server. Icecast uses an HTTP-style "SOURCE" request with Basic auth; Shoutcast
// (legacy v1/v2 source protocol) uses a plain password line followed by ICY headers.
enum StreamProtocol {
    PROTOCOL_ICECAST = 0,
    PROTOCOL_SHOUTCAST = 1
};

class IcecastStreamer {
public:
    IcecastStreamer();
    ~IcecastStreamer();

    bool connectStream(const std::string& host, int port, const std::string& mount,
                       const std::string& pass, int bitrateKbps, int protocol,
                       const std::string& stationName);
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
    int mProtocol{PROTOCOL_ICECAST};
    std::string mStationName;
    std::string mLastError;

    int mSocketFd{-1};
    int mSampleRateOut{48000};
    int mChannelsOut{2};

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
    bool performIcecastHandshake();
    bool performShoutcastHandshake();
    void encodeAndSend(const int16_t* pcmBuf, size_t numSamples);
    std::string base64Encode(const std::string& in);

    // Builds a streaming-mode RIFF/WAVE header (data size fields set to "unknown length")
    // so the raw PCM payload that follows is valid, immediately-playable audio.
    // NOTE: This sends uncompressed 16-bit PCM, not a compressed codec (MP3/AAC/Opus).
    // It is correct and fully playable, but uses far more bandwidth than a compressed
    // stream. Swapping in a real encoder (e.g. via Android's MediaCodec AAC encoder, or
    // a vendored MP3/Opus library) is the recommended next step — see README.md.
    std::vector<uint8_t> buildWavStreamHeader(int sampleRate, int channels, int bitsPerSample);
};

#endif // ICECAST_STREAMER_H
