#include "CoreManager.hpp"
#include "AudioRenderer.hpp"
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <vector>
#include <atomic>
#include <string>
#include <jni.h>

#define LOG_TAG "CoreManager"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Globals for callbacks
static std::atomic<int16_t> input_state[16];
static enum retro_pixel_format video_format = RETRO_PIXEL_FORMAT_RGB565;
static ANativeWindow* activeWindow = nullptr;
static bool needGeometryUpdate = true;

// Callbacks implementation
bool retro_environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) *(bool*)data = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format *format = (const enum retro_pixel_format *)data;
            if (video_format != *format) {
                video_format = *format;
                needGeometryUpdate = true;
                LOGI("Pixel format set to: %d (0:1555, 1:8888, 2:565)", video_format);
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data) *(const char**)data = "/data/user/0/com.dybrocorp.retrogame/files";
            return true;
    }
    return false;
}

void retro_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!activeWindow || !data) return;

    if (needGeometryUpdate) {
        int nativeFormat = (video_format == RETRO_PIXEL_FORMAT_XRGB8888) ? WINDOW_FORMAT_RGBA_8888 : WINDOW_FORMAT_RGB_565;
        ANativeWindow_setBuffersGeometry(activeWindow, width, height, nativeFormat);
        needGeometryUpdate = false;
        LOGI("Geometry updated: %dx%d, format: %d", width, height, nativeFormat);
    }
    
    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(activeWindow, &buffer, nullptr) < 0) return;
    
    auto* src = (const uint8_t*)data;
    auto* dst = (uint8_t*)buffer.bits;
    
    // Determinamos bytes por píxel según el formato detectado
    int bpp = 2;
    if (video_format == RETRO_PIXEL_FORMAT_XRGB8888) bpp = 4;
    
    int src_stride = (pitch != 0) ? pitch : width * bpp;
    int dst_stride = buffer.stride * bpp;

    // Copia de buffer con recorte de seguridad y conversión de color si es necesario
    unsigned copy_width = std::min(width, (unsigned)buffer.width);
    unsigned copy_height = std::min(height, (unsigned)buffer.height);

    if (video_format == RETRO_PIXEL_FORMAT_0RGB1555) {
        // Conversión manual optimizada de 0RGB1555 a RGB565
        for (unsigned y = 0; y < copy_height; y++) {
            auto* src_row = (const uint16_t*)(src + y * src_stride);
            auto* dst_row = (uint16_t*)(dst + y * dst_stride);
            for (unsigned x = 0; x < copy_width; x++) {
                uint16_t pixel = src_row[x];
                // 1555: 0RRRRRGGGGGBBBBB -> 565: RRRRRGGGGGGBBBBB
                dst_row[x] = ((pixel & 0x7FE0) << 1) | (pixel & 0x001F);
            }
        }
    } else {
        // Direct copy para RGB565 y XRGB8888
        for (unsigned y = 0; y < copy_height; y++) {
            memcpy(dst + y * dst_stride, src + y * src_stride, copy_width * bpp);
        }
    }
    
    ANativeWindow_unlockAndPost(activeWindow);
}

// Buffer de audio para evitar sobrecarga en llamadas individuales
static std::vector<int16_t> audio_internal_buffer;
static const size_t AUDIO_BUFFER_THRESHOLD = 512; // Frames

void retro_audio_sample_cb(int16_t left, int16_t right) {
    audio_internal_buffer.push_back(left);
    audio_internal_buffer.push_back(right);
    
    if (audio_internal_buffer.size() >= AUDIO_BUFFER_THRESHOLD * 2) {
        AudioRenderer::getInstance().write(audio_internal_buffer.data(), AUDIO_BUFFER_THRESHOLD);
        audio_internal_buffer.clear();
    }
}

size_t retro_audio_sample_batch_cb(const int16_t *data, size_t frames) {
    // Si hay algo en el buffer interno, lo enviamos primero para mantener el orden
    if (!audio_internal_buffer.empty()) {
        AudioRenderer::getInstance().write(audio_internal_buffer.data(), audio_internal_buffer.size() / 2);
        audio_internal_buffer.clear();
    }
    AudioRenderer::getInstance().write(data, frames);
    return frames;
}

void retro_input_poll_cb() {}

int16_t retro_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port == 0 && device == RETRO_DEVICE_JOYPAD) {
        return input_state[id].load();
    }
    return 0;
}

// CoreManager Implementation
bool CoreManager::loadCore(const std::string& path) {
    if (running) stopLoop();
    unload();

    LOGI("Opening core: %s", path.c_str());
    handle = dlopen(path.c_str(), RTLD_LAZY);
    if (!handle) {
        LOGE("Failed to dlopen core: %s", dlerror());
        return false;
    }

    #define BIND_SYMBOL(name) \
        this->name = (decltype(this->name))dlsym(handle, #name); \
        if (!this->name) { LOGE("Failed to bind: %s", #name); return false; }

    BIND_SYMBOL(retro_init);
    BIND_SYMBOL(retro_deinit);
    BIND_SYMBOL(retro_api_version);
    BIND_SYMBOL(retro_get_system_info);
    BIND_SYMBOL(retro_get_system_av_info);
    BIND_SYMBOL(retro_set_environment);
    BIND_SYMBOL(retro_set_video_refresh);
    BIND_SYMBOL(retro_set_audio_sample);
    BIND_SYMBOL(retro_set_audio_sample_batch);
    BIND_SYMBOL(retro_set_input_poll);
    BIND_SYMBOL(retro_set_input_state);
    BIND_SYMBOL(retro_set_controller_port_device);
    BIND_SYMBOL(retro_reset);
    BIND_SYMBOL(retro_run);
    BIND_SYMBOL(retro_load_game);
    BIND_SYMBOL(retro_unload_game);
    BIND_SYMBOL(retro_serialize_size);
    BIND_SYMBOL(retro_serialize);
    BIND_SYMBOL(retro_unserialize);
    BIND_SYMBOL(retro_get_memory_data);
    BIND_SYMBOL(retro_get_memory_size);

    LOGI("Symbols bound. Initializing callbacks...");
    retro_set_environment(retro_environment_cb);
    retro_set_video_refresh(retro_video_refresh_cb);
    retro_set_audio_sample(retro_audio_sample_cb);
    retro_set_audio_sample_batch(retro_audio_sample_batch_cb);
    retro_set_input_poll(retro_input_poll_cb);
    retro_set_input_state(retro_input_state_cb);

    LOGI("Calling retro_init()...");
    if (retro_init) retro_init();
    LOGI("retro_init() done.");
    return true;
}

bool CoreManager::loadGame(const std::string& path) {
    if (!handle) return false;
    this->currentGamePath = path;
    struct retro_game_info info = { path.c_str(), nullptr, 0, nullptr };
    gameLoaded = retro_load_game(&info);
    if (gameLoaded) {
        audio_internal_buffer.clear();
        audio_internal_buffer.reserve(AUDIO_BUFFER_THRESHOLD * 2);
        
        LOGI("retro_load_game success. Getting AV info...");
        struct retro_system_av_info av_info = {0};
        if (retro_get_system_av_info) {
            retro_get_system_av_info(&av_info);
            LOGI("AV Info: Base %dx%d, Max %dx%d, FPS %.2f, Rate %.2f", 
                 (int)av_info.geometry.base_width, (int)av_info.geometry.base_height,
                 (int)av_info.geometry.max_width, (int)av_info.geometry.max_height,
                 av_info.timing.fps, av_info.timing.sample_rate);
                 
            if (av_info.timing.sample_rate > 0) {
                AudioRenderer::getInstance().init((int)av_info.timing.sample_rate);
            }
        }
    }
    return gameLoaded;
}

void CoreManager::startLoop() {
    if (running) return;
    running = true;
    loopThread = std::thread(&CoreManager::loop, this);
}

void CoreManager::stopLoop() {
    running = false;
    if (loopThread.joinable()) loopThread.join();
}

void CoreManager::loop() {
    auto nextFrame = std::chrono::steady_clock::now();
    while (running) {
        if (!paused && gameLoaded) {
            retro_run();
        }
        nextFrame += std::chrono::microseconds(16667);
        std::this_thread::sleep_until(nextFrame);
    }
}

void CoreManager::setPaused(bool p) {
    paused = p;
    if (paused) {
        AudioRenderer::getInstance().pause();
        LOGI("Game Paused");
    } else {
        AudioRenderer::getInstance().resume();
        LOGI("Game Resumed");
    }
}

void CoreManager::unload() {
    if (gameLoaded) {
        if (!currentGamePath.empty()) {
            LOGI("Auto-saving before unload: %s", currentGamePath.c_str());
            saveSRAM(currentGamePath + ".srm");
            saveState(currentGamePath + ".state");
        }
        retro_unload_game();
        gameLoaded = false;
    }
    if (handle) {
        retro_deinit();
        dlclose(handle);
        handle = nullptr;
    }
}

bool CoreManager::saveState(const std::string& path) {
    if (!gameLoaded) return false;
    size_t size = retro_serialize_size();
    if (size == 0) { LOGE("saveState: Size is 0"); return false; }
    std::vector<uint8_t> buffer(size);
    if (!retro_serialize(buffer.data(), size)) { LOGE("saveState: retro_serialize failed"); return false; }
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) { LOGE("saveState: Failed to open file %s", path.c_str()); return false; }
    fwrite(buffer.data(), 1, size, f);
    fclose(f);
    LOGI("saveState: Saved %zu bytes to %s", size, path.c_str());
    return true;
}

bool CoreManager::loadState(const std::string& path) {
    if (!gameLoaded) return false;
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { LOGW("loadState: Failed to open %s (normal if first time)", path.c_str()); return false; }
    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<uint8_t> buffer(size);
    fread(buffer.data(), 1, size, f);
    fclose(f);
    bool success = retro_unserialize(buffer.data(), size);
    LOGI("loadState: Loaded %zu bytes from %s. Success: %d", size, path.c_str(), success);
    return success;
}

bool CoreManager::saveSRAM(const std::string& path) {
    if (!gameLoaded) return false;
    void* data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) { LOGW("saveSRAM: No save RAM data to save (size 0)"); return false; }
    FILE* f = fopen(path.c_str(), "wb");
    if (!f) { LOGE("saveSRAM: Failed to open %s", path.c_str()); return false; }
    fwrite(data, 1, size, f);
    fclose(f);
    LOGI("saveSRAM: Saved %zu bytes to %s", size, path.c_str());
    return true;
}

bool CoreManager::loadSRAM(const std::string& path) {
    if (!gameLoaded) return false;
    void* data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) { LOGW("loadSRAM: Core has no save RAM area"); return false; }
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) { LOGW("loadSRAM: No SRAM file found at %s", path.c_str()); return false; }
    fread(data, 1, size, f);
    fclose(f);
    LOGI("loadSRAM: Loaded %zu bytes from %s", size, path.c_str());
    return true;
}

// JNI EXPORTS
extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_loadCore(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().loadCore(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_loadGame(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().loadGame(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT void JNICALL Java_com_dybrocorp_retrogame_MainActivity_startLoop(JNIEnv* env, jobject obj) {
        CoreManager::getInstance().startLoop();
    }

    JNIEXPORT void JNICALL Java_com_dybrocorp_retrogame_MainActivity_stopLoop(JNIEnv* env, jobject obj) {
        CoreManager::getInstance().stopLoop();
    }

    JNIEXPORT void JNICALL Java_com_dybrocorp_retrogame_MainActivity_setSurface(JNIEnv* env, jobject obj, jobject surface) {
        LOGI("JNI: setSurface called. Surface: %p", surface);
        if (activeWindow) {
            ANativeWindow_release(activeWindow);
            activeWindow = nullptr;
        }
        if (surface) {
            activeWindow = ANativeWindow_fromSurface(env, surface);
            needGeometryUpdate = true;
            LOGI("JNI: activeWindow set to %p", activeWindow);
        }
    }

    JNIEXPORT void JNICALL Java_com_dybrocorp_retrogame_MainActivity_updateInput(JNIEnv* env, jobject obj, jint id, jboolean pressed) {
        if (id >= 0 && id < 16) {
            input_state[id].store(pressed ? 1 : 0);
        }
    }

    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_saveState(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().saveState(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_loadState(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().loadState(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_saveSRAM(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().saveSRAM(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT jboolean JNICALL Java_com_dybrocorp_retrogame_MainActivity_loadSRAM(JNIEnv* env, jobject obj, jstring path) {
        const char* nativePath = env->GetStringUTFChars(path, nullptr);
        bool success = CoreManager::getInstance().loadSRAM(nativePath);
        env->ReleaseStringUTFChars(path, nativePath);
        return success;
    }

    JNIEXPORT void JNICALL Java_com_dybrocorp_retrogame_MainActivity_setPaused(JNIEnv* env, jobject obj, jboolean paused) {
        CoreManager::getInstance().setPaused(paused);
    }
}
