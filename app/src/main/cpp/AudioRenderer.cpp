#include "AudioRenderer.hpp"
#include <android/log.h>

#define LOG_TAG "AudioRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

bool AudioRenderer::init(int sampleRate, int channels) {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (stream) return true;

    AAudioStreamBuilder* builder;
    AAudio_createStreamBuilder(&builder);

    // Normalización de Sample Rate para máxima compatibilidad y nitidez
    int finalRate = sampleRate;
    if (finalRate <= 0 || finalRate > 192000) finalRate = 44100;
    
    LOGI("Audio init requested: %d, final: %d", sampleRate, finalRate);
    AAudioStreamBuilder_setSampleRate(builder, finalRate);
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    
    // Optimizamos para evitar underruns/overruns
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, 4096); 

    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK) {
        LOGE("Failed to open AAudio stream");
        AAudioStreamBuilder_delete(builder);
        return false;
    }

    if (AAudioStream_requestStart(stream) != AAUDIO_OK) {
        LOGE("Failed to start AAudio stream");
        AAudioStream_close(stream);
        stream = nullptr;
        AAudioStreamBuilder_delete(builder);
        return false;
    }

    AAudioStreamBuilder_delete(builder);
    return true;
}

void AudioRenderer::write(const int16_t* data, size_t frames) {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (!stream) return;

    // Bloqueamos hasta 100ms si el buffer está lleno para sincronizar con el hardware.
    int64_t timeoutNanoseconds = 100 * 1000 * 1000;
    AAudioStream_write(stream, data, frames, timeoutNanoseconds);
}

void AudioRenderer::close() {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (stream) {
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
        stream = nullptr;
    }
}

void AudioRenderer::pause() {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (stream) AAudioStream_requestPause(stream);
}

void AudioRenderer::resume() {
    std::lock_guard<std::mutex> lock(audioMutex);
    if (stream) AAudioStream_requestStart(stream);
}
