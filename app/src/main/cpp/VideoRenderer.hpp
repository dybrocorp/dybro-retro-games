#ifndef VIDEO_RENDERER_HPP
#define VIDEO_RENDERER_HPP

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <mutex>

class VideoRenderer {
public:
    static VideoRenderer& getInstance() {
        static VideoRenderer instance;
        return instance;
    }

    void setWindow(ANativeWindow* window);
    void render(const void* data, unsigned width, unsigned height, size_t pitch);
    void setPixelFormat(int format); // 0=XRGB8888, 1=RGB565, etc.

private:
    VideoRenderer() : nativeWindow(nullptr), pixelFormat(0) {}
    ~VideoRenderer() { setWindow(nullptr); }

    ANativeWindow* nativeWindow;
    std::mutex windowMutex;
    int pixelFormat;
};

#endif
