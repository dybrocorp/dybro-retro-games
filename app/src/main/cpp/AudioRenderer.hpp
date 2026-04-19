#ifndef AUDIO_RENDERER_HPP
#define AUDIO_RENDERER_HPP

#include <aaudio/AAudio.h>
#include <mutex>

class AudioRenderer {
public:
    static AudioRenderer& getInstance() {
        static AudioRenderer instance;
        return instance;
    }

    bool init(int sampleRate = 44100, int channels = 2);
    void write(const int16_t* data, size_t frames);
    void pause();
    void resume();
    void close();

private:
    AudioRenderer() : stream(nullptr) {}
    ~AudioRenderer() { close(); }

    AAudioStream* stream;
    std::mutex audioMutex;
};

#endif
