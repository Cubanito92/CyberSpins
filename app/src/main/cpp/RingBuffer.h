#ifndef RING_BUFFER_H
#define RING_BUFFER_H

#include <atomic>
#include <vector>
#include <cstring>
#include <algorithm>

// Simple lock-free single-producer / single-consumer ring buffer for
// interleaved float PCM. Used to decouple the microphone capture callback
// (its own hardware clock) from the output/render callback (a different
// hardware clock). Reading/writing raw pointers directly between two
// independently-clocked audio callbacks is what causes the slow "robotic"
// warble users hear after a while: the two clocks drift apart over time,
// and this buffer absorbs that drift smoothly instead of producing hard
// under/overrun glitches.
class RingBuffer {
public:
    explicit RingBuffer(size_t capacityInSamples)
            : mCapacity(capacityInSamples), mData(capacityInSamples, 0.0f) {}

    size_t capacity() const { return mCapacity; }

    size_t availableToRead() const {
        size_t w = mWriteIndex.load(std::memory_order_acquire);
        size_t r = mReadIndex.load(std::memory_order_relaxed);
        return w - r;
    }

    // Writes as many samples as fit; drops the oldest data instead of
    // blocking or growing unbounded if the consumer falls behind.
    void write(const float* src, size_t numSamples) {
        size_t w = mWriteIndex.load(std::memory_order_relaxed);
        size_t r = mReadIndex.load(std::memory_order_acquire);
        size_t used = w - r;

        // If this write would overflow the buffer, advance the read index
        // (drop oldest samples) so we never block the realtime audio thread.
        if (used + numSamples > mCapacity) {
            size_t drop = (used + numSamples) - mCapacity;
            mReadIndex.store(r + drop, std::memory_order_release);
        }

        for (size_t i = 0; i < numSamples; ++i) {
            mData[(w + i) % mCapacity] = src[i];
        }
        mWriteIndex.store(w + numSamples, std::memory_order_release);
    }

    // Reads numSamples into dst. If fewer are available, the remainder is
    // filled with the last known sample decaying towards zero (instead of a
    // hard drop to silence) to avoid audible clicks, and returns the number
    // of real samples that were available.
    size_t read(float* dst, size_t numSamples) {
        size_t w = mWriteIndex.load(std::memory_order_acquire);
        size_t r = mReadIndex.load(std::memory_order_relaxed);
        size_t available = w - r;
        size_t toRead = std::min(available, numSamples);

        for (size_t i = 0; i < toRead; ++i) {
            dst[i] = mData[(r + i) % mCapacity];
        }
        mReadIndex.store(r + toRead, std::memory_order_release);

        if (toRead < numSamples) {
            float last = toRead > 0 ? dst[toRead - 1] : 0.0f;
            for (size_t i = toRead; i < numSamples; ++i) {
                last *= 0.6f; // quick fade instead of an abrupt click
                dst[i] = last;
            }
        }
        return toRead;
    }

    // Drift compensation helper: tells the consumer how full the buffer is
    // relative to a target level (0.5 = half full), so the output callback
    // can insert or drop a single frame occasionally to keep the two clocks
    // in sync instead of accumulating drift.
    float fillRatio() const {
        size_t used = availableToRead();
        return mCapacity > 0 ? static_cast<float>(used) / static_cast<float>(mCapacity) : 0.0f;
    }

private:
    size_t mCapacity;
    std::vector<float> mData;
    std::atomic<size_t> mWriteIndex{0};
    std::atomic<size_t> mReadIndex{0};
};

#endif // RING_BUFFER_H
