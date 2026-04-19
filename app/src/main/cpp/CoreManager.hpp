#ifndef CORE_MANAGER_HPP
#define CORE_MANAGER_HPP

#include "libretro.h"
#include <string>
#include <dlfcn.h>
#include <android/log.h>

#include <thread>
#include <chrono>

class CoreManager {
public:
    static CoreManager& getInstance() {
        static CoreManager instance;
        return instance;
    }

    bool loadCore(const std::string& path);
    bool loadGame(const std::string& path);
    void run();
    void unload();
    
    // Partidas Guardadas
    bool saveState(const std::string& path);
    bool loadState(const std::string& path);
    bool saveSRAM(const std::string& path);
    bool loadSRAM(const std::string& path);

    void startLoop();
    void stopLoop();
    void setPaused(bool paused);

private:
    CoreManager() : handle(nullptr), gameLoaded(false), running(false), paused(false) {}
    ~CoreManager() { stopLoop(); unload(); }

    void* handle;
    bool gameLoaded;
    std::atomic<bool> running;
    std::atomic<bool> paused;
    std::thread loopThread;

    std::string currentGamePath;
    void loop();

    // Core functions pointers
    void (*retro_init)(void);
    void (*retro_deinit)(void);
    unsigned (*retro_api_version)(void);
    void (*retro_get_system_info)(struct retro_system_info*);
    void (*retro_get_system_av_info)(struct retro_system_av_info*);
    void (*retro_set_environment)(retro_environment_t);
    void (*retro_set_video_refresh)(retro_video_refresh_t);
    void (*retro_set_audio_sample)(retro_audio_sample_t);
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
    void (*retro_set_input_poll)(retro_input_poll_t);
    void (*retro_set_input_state)(retro_input_state_t);
    void (*retro_set_controller_port_device)(unsigned, unsigned);
    void (*retro_reset)(void);
    void (*retro_run)(void);
    bool (*retro_load_game)(const struct retro_game_info*);
    void (*retro_unload_game)(void);

    // Serialization (Save States)
    size_t (*retro_serialize_size)(void);
    bool (*retro_serialize)(void*, size_t);
    bool (*retro_unserialize)(const void*, size_t);

    // Memory (SRAM)
    void* (*retro_get_memory_data)(unsigned);
    size_t (*retro_get_memory_size)(unsigned);
};

#endif
