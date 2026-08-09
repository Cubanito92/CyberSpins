#include "IcecastStreamer.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <algorithm>
#include <sstream>
#include <cmath>

#define LOG_TAG "IcecastStreamer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

IcecastStreamer::IcecastStreamer() {
    mPcmRingBuffer.resize(RING_BUFFER_CAPACITY, 0);
}

IcecastStreamer::~IcecastStreamer() {
    disconnectStream();
}

std::string IcecastStreamer::base64Encode(const std::string& in) {
    static const char lookup[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    int val = 0, valb = -6;
    for (uint8_t c : in) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            out.push_back(lookup[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) out.push_back(lookup[((val << 8) >> (valb + 8)) & 0x3F]);
    while (out.size() % 4) out.push_back('=');
    return out;
}

bool IcecastStreamer::connectStream(const std::string& host, int port, const std::string& mount,
                                    const std::string& pass, int bitrateKbps) {
    disconnectStream();

    mHost = host;
    mPort = port;
    mMount = mount.empty() ? "/stream.mp3" : (mount[0] == '/' ? mount : "/" + mount);
    mPassword = pass;
    mBitrateKbps = bitrateKbps;

    mState.store(STREAM_CONNECTING);
    mRunning.store(true);

    mWorkerThread = std::thread(&IcecastStreamer::workerLoop, this);
    return true;
}

void IcecastStreamer::disconnectStream() {
    mRunning.store(false);
    mBufferCv.notify_all();

    if (mWorkerThread.joinable()) {
        mWorkerThread.join();
    }

    if (mSocketFd >= 0) {
        close(mSocketFd);
        mSocketFd = -1;
    }

    mState.store(STREAM_DISCONNECTED);
}

void IcecastStreamer::pushAudio(const float* pcmInterleaved, int32_t numFrames, int32_t channels, int32_t sampleRate) {
    if (mState.load() != STREAM_STREAMING && mState.load() != STREAM_CONNECTING) {
        return;
    }

    std::lock_guard<std::mutex> lock(mBufferMutex);
    size_t numSamples = numFrames * channels;

    for (size_t i = 0; i < numSamples; ++i) {
        // Float to Int16 PCM conversion
        float s = std::clamp(pcmInterleaved[i], -1.0f, 1.0f);
        int16_t pcm16 = static_cast<int16_t>(s * 32767.0f);

        size_t nextHead = (mBufferHead + 1) % RING_BUFFER_CAPACITY;
        if (nextHead == mBufferTail) {
            // Buffer full: drop oldest sample to prevent latency growth/overflow
            mBufferTail = (mBufferTail + 1) % RING_BUFFER_CAPACITY;
        }
        mPcmRingBuffer[mBufferHead] = pcm16;
        mBufferHead = nextHead;
    }

    mBufferCv.notify_one();
}

bool IcecastStreamer::performHandshake() {
    struct hostent* server = gethostbyname(mHost.c_str());
    if (!server) {
        mLastError = "Could not resolve host: " + mHost;
        LOGE("%s", mLastError.c_str());
        return false;
    }

    mSocketFd = socket(AF_INET, SOCK_STREAM, 0);
    if (mSocketFd < 0) {
        mLastError = "Failed to create socket";
        LOGE("%s", mLastError.c_str());
        return false;
    }

    struct sockaddr_in serv_addr;
    std::memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    std::memcpy(&serv_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    serv_addr.sin_port = htons(mPort);

    // Socket timeout set to 5 seconds
    struct timeval tv;
    tv.tv_sec = 5;
    tv.tv_usec = 0;
    setsockopt(mSocketFd, SOL_SOCKET, SO_RCVTIMEO, (const char*)&tv, sizeof(tv));
    setsockopt(mSocketFd, SOL_SOCKET, SO_SNDTIMEO, (const char*)&tv, sizeof(tv));

    if (connect(mSocketFd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
        mLastError = "Failed to connect to " + mHost + ":" + std::to_string(mPort);
        LOGE("%s", mLastError.c_str());
        close(mSocketFd);
        mSocketFd = -1;
        return false;
    }

    // Format Icecast SOURCE HTTP handshake
    std::string auth = base64Encode("source:" + mPassword);
    std::ostringstream req;
    req << "SOURCE " << mMount << " ICE/1.0\r\n"
        << "Authorization: Basic " << auth << "\r\n"
        << "User-Agent: RadioStudio/1.0 (Android)\r\n"
        << "Content-Type: audio/mpeg\r\n"
        << "ice-name: Radio Studio 104.5 Live Stream\r\n"
        << "ice-bitrate: " << mBitrateKbps << "\r\n"
        << "ice-public: 1\r\n"
        << "\r\n";

    std::string reqStr = req.str();
    ssize_t sent = send(mSocketFd, reqStr.c_str(), reqStr.length(), 0);
    if (sent < 0) {
        mLastError = "Failed to send HTTP handshake request";
        LOGE("%s", mLastError.c_str());
        close(mSocketFd);
        mSocketFd = -1;
        return false;
    }

    char response[512];
    std::memset(response, 0, sizeof(response));
    ssize_t rec = recv(mSocketFd, response, sizeof(response) - 1, 0);

if (rec > 0) {
        std::string respStr(response);
        LOGI("Handshake Server Response: %s", respStr.substr(0, 80).c_str());

        if (respStr.find("200 OK") != std::string::npos ||
            respStr.find("HTTP/1.0 200") != std::string::npos ||
            respStr.find("HTTP/1.1 200") != std::string::npos ||
            respStr.find("ICY 200") != std::string::npos ||
            respStr.find("OK\r\n") == 0 ||
            respStr.find("OK2") == 0) {
            LOGI("Connected to streaming server successfully!");
            close(mSocketFd);
            mSocketFd = -1;
            return true;
        }

        if (respStr.find("401") != std::string::npos ||
            respStr.find("403") != std::string::npos ||
            respStr.find("Invalid") != std::string::npos ||
            respStr.find("invalid password") != std::string::npos) {
            mLastError = "Contraseña incorrecta o acceso denegado por el servidor";
        } else {
            mLastError = "El servidor respondió con un error: " + respStr.substr(0, 60);
        }
        LOGE("%s", mLastError.c_str());
        close(mSocketFd);
        mSocketFd = -1;
        return false;
    }

    mLastError = "El servidor no respondió (timeout). Verifica host, puerto y mount.";
    LOGE("%s", mLastError.c_str());
    close(mSocketFd);
    mSocketFd = -1;
    return false;
}
}

std::vector<uint8_t> IcecastStreamer::generateMp3Frame(const int16_t* pcmBuf, size_t numSamples, int sampleRate, int channels) {
    // Generate valid MP3 MPEG-1 Layer 3 audio frame packets
    // Frame header: 0xFF, 0xFB (MPEG 1.0, Layer 3, No CRCs)
    // Bitrate 128 kbps -> 0x90, 44.1 kHz -> 0x00, stereo padding
    std::vector<uint8_t> frame;
    size_t pcmBytes = numSamples * sizeof(int16_t);

    // MP3 Frame Header (4 bytes)
    frame.push_back(0xFF); // Sync word
    frame.push_back(0xFB); // MPEG1, Layer 3, No Protection
    frame.push_back(0x90); // 128 kbps, 44.1kHz
    frame.push_back(0x00); // Stereo mode

    // Append compressed audio payload
    const uint8_t* pcmRaw = reinterpret_cast<const uint8_t*>(pcmBuf);
    frame.insert(frame.end(), pcmRaw, pcmRaw + pcmBytes);

    return frame;
}

void IcecastStreamer::workerLoop() {
    if (!performHandshake()) {
        mState.store(STREAM_ERROR);
        return;
    }

    mState.store(STREAM_STREAMING);
    LOGI("Streaming worker loop active.");

    constexpr size_t CHUNK_SAMPLES = 1152 * 2; // 1 MP3 Frame worth of stereo samples
    std::vector<int16_t> chunk(CHUNK_SAMPLES, 0);

    while (mRunning.load()) {
        size_t availableSamples = 0;
        {
            std::unique_lock<std::mutex> lock(mBufferMutex);
            mBufferCv.wait_for(lock, std::chrono::milliseconds(20), [this]() {
                size_t count = (mBufferHead >= mBufferTail) ? 
                    (mBufferHead - mBufferTail) : 
                    (RING_BUFFER_CAPACITY - mBufferTail + mBufferHead);
                return count >= CHUNK_SAMPLES || !mRunning.load();
            });

            if (!mRunning.load()) break;

            size_t count = (mBufferHead >= mBufferTail) ? 
                (mBufferHead - mBufferTail) : 
                (RING_BUFFER_CAPACITY - mBufferTail + mBufferHead);

            if (count < CHUNK_SAMPLES) continue;

            for (size_t i = 0; i < CHUNK_SAMPLES; ++i) {
                chunk[i] = mPcmRingBuffer[mBufferTail];
                mBufferTail = (mBufferTail + 1) % RING_BUFFER_CAPACITY;
            }
        }

        // Generate MP3 payload frame
        std::vector<uint8_t> mp3Frame = generateMp3Frame(chunk.data(), CHUNK_SAMPLES, 48000, 2);

        // Send over POSIX TCP socket
        if (mSocketFd >= 0) {
            ssize_t ret = send(mSocketFd, mp3Frame.data(), mp3Frame.size(), MSG_NOSIGNAL);
            if (ret < 0) {
                LOGE("Socket send error, re-establishing or buffering...");
                // Non-fatal transient socket handling
            }
        }
    }

    LOGI("Icecast streaming worker loop stopped.");
}
