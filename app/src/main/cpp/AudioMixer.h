#ifndef AUDIO_MIXER_H
#define AUDIO_MIXER_H

#include <vector>
#include <cstdint>
#include <cmath>

class AudioMixer {
public:
    static inline float applyGain(float sample, float gain) {
        return sample * gain;
    }

    static inline float softClip(float sample) {
        if (sample > 1.0f) return 1.0f - expf(-sample + 1.0f) * 0.5f;
        if (sample < -1.0f) return -1.0f + expf(sample + 1.0f) * 0.5f;
        return sample;
    }

    static inline float calculateRMS(const float* buffer, int32_t numSamples) {
        if (numSamples <= 0) return 0.0f;
        float sum = 0.0f;
        for (int i = 0; i < numSamples; ++i) {
            sum += buffer[i] * buffer[i];
        }
        return sqrtf(sum / static_cast<float>(numSamples));
    }
};

#endif // AUDIO_MIXER_H
