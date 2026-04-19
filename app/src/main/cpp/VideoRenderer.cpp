#include "VideoRenderer.hpp"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "VideoRenderer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void VideoRenderer::setWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(windowMutex);
    if (nativeWindow) {
        ANativeWindow_release(nativeWindow);
    }
    nativeWindow = window;
}

void VideoRenderer::setPixelFormat(int format) {
    pixelFormat = format;
}

void VideoRenderer::render(const void* data, unsigned width, unsigned height, size_t pitch) {
    std::lock_guard<std::mutex> lock(windowMutex);
    if (!nativeWindow || !data) return;

    // Adjust window buffer size if needed
    if (ANativeWindow_getWidth(nativeWindow) != width || ANativeWindow_getHeight(nativeWindow) != height) {
        ANativeWindow_setBuffersGeometry(nativeWindow, width, height, WINDOW_FORMAT_RGBX_8888);
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(nativeWindow, &buffer, nullptr) < 0) {
        LOGE("Failed to lock native window");
        return;
    }

    // Copy buffer
    uint32_t* dst = (uint32_t*)buffer.bits;
    const uint32_t* src = (const uint32_t*)data;

    // Simple copy, assumes XRGB8888 for now.
    // In a real emulator, we'd handle RGB565 and other formats here.
    for (unsigned y = 0; y < height; y++) {
        std::memcpy(dst + y * buffer.stride, src + y * (pitch / 4), width * 4);
    }

    ANativeWindow_unlockAndPost(nativeWindow);
}
