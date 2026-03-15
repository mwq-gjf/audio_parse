#include <jni.h>
#include <string>
#include <android/log.h>
#include <fstream>
#include <vector>

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#include "whisper.h"

static whisper_context* g_context = nullptr;
static std::string g_model_path;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uai_audio_1parse_WhisperRecognizer_loadModel(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {
    
    const char* c_model_path = env->GetStringUTFChars(model_path, nullptr);
    if (c_model_path == nullptr) {
        LOGE("Failed to get model path string");
        return JNI_FALSE;
    }
    
    LOGI("Loading whisper model from: %s", c_model_path);
    
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
    }
    
    g_context = whisper_init_from_file_with_params(c_model_path, whisper_context_default_params());
    
    env->ReleaseStringUTFChars(model_path, c_model_path);
    
    if (g_context == nullptr) {
        LOGE("Failed to load whisper model");
        return JNI_FALSE;
    }
    
    g_model_path = std::string(c_model_path);
    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uai_audio_1parse_WhisperRecognizer_isModelLoaded(
        JNIEnv* env,
        jobject thiz) {
    return g_context != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_uai_audio_1parse_WhisperRecognizer_freeModel(
        JNIEnv* env,
        jobject thiz) {
    if (g_context != nullptr) {
        whisper_free(g_context);
        g_context = nullptr;
        LOGI("Whisper model freed");
    }
}

static std::string transcribeInternal(whisper_context* ctx, const char* audio_path, const char* language, bool withTimestamps) {
    LOGI("Transcribing audio: %s", audio_path);
    
    std::ifstream file(audio_path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open audio file");
        return "";
    }
    
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    
    std::vector<char> buffer(size);
    if (!file.read(buffer.data(), size)) {
        LOGE("Failed to read audio file");
        return "";
    }
    file.close();
    
    if (size < 44) {
        LOGE("Audio file too small");
        return "";
    }
    
    int num_channels = *reinterpret_cast<int16_t*>(&buffer[22]);
    int sample_rate = *reinterpret_cast<int32_t*>(&buffer[24]);
    int bits_per_sample = *reinterpret_cast<int16_t*>(&buffer[34]);
    
    LOGI("Audio info: channels=%d, sample_rate=%d, bits=%d", num_channels, sample_rate, bits_per_sample);
    
    int data_offset = 44;
    char* data = buffer.data() + data_offset;
    int data_size = size - data_offset;
    
    int num_samples = data_size / (bits_per_sample / 8) / num_channels;
    
    std::vector<float> samples;
    samples.reserve(num_samples);
    
    if (bits_per_sample == 16) {
        int16_t* samples_16 = reinterpret_cast<int16_t*>(data);
        for (int i = 0; i < num_samples; i++) {
            float sample = samples_16[i * num_channels] / 32768.0f;
            samples.push_back(sample);
        }
    } else if (bits_per_sample == 32) {
        int32_t* samples_32 = reinterpret_cast<int32_t*>(data);
        for (int i = 0; i < num_samples; i++) {
            float sample = samples_32[i * num_channels] / 2147483648.0f;
            samples.push_back(sample);
        }
    }
    
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    if (strcmp(language, "auto") != 0) {
        params.language = language;
    }
    params.translate = false;
    params.n_threads = 4;
    params.print_progress = false;
    params.print_timestamps = withTimestamps;
    
    int result = whisper_full(ctx, params, samples.data(), samples.size());
    
    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return "";
    }
    
    std::string transcription;
    int n_segments = whisper_full_n_segments(ctx);
    
    for (int i = 0; i < n_segments; i++) {
        if (withTimestamps) {
            int64_t t0 = whisper_full_get_segment_t0(ctx, i);
            int64_t t1 = whisper_full_get_segment_t1(ctx, i);
            const char* text = whisper_full_get_segment_text(ctx, i);
            
            if (text != nullptr) {
                int start_sec = t0 / 100;
                int end_sec = t1 / 100;
                int start_ms = (t0 % 100) * 10;
                int end_ms = (t1 % 100) * 10;
                
                char timestamp[64];
                snprintf(timestamp, sizeof(timestamp), "[%02d:%02d.%03d --> %02d:%02d.%03d] ",
                        start_sec / 60, start_sec % 60, start_ms,
                        end_sec / 60, end_sec % 60, end_ms);
                
                transcription += timestamp;
                transcription += text;
                transcription += "\n";
            }
        } else {
            const char* text = whisper_full_get_segment_text(ctx, i);
            if (text != nullptr) {
                transcription += text;
            }
        }
    }
    
    LOGI("Transcription completed");
    return transcription;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uai_audio_1parse_WhisperRecognizer_transcribe(
        JNIEnv* env,
        jobject thiz,
        jstring audio_path,
        jstring language) {
    
    if (g_context == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }
    
    const char* c_audio_path = env->GetStringUTFChars(audio_path, nullptr);
    const char* c_language = env->GetStringUTFChars(language, nullptr);
    
    if (c_audio_path == nullptr) {
        LOGE("Failed to get audio path string");
        return env->NewStringUTF("");
    }
    
    std::string result = transcribeInternal(g_context, c_audio_path, c_language, false);
    
    env->ReleaseStringUTFChars(audio_path, c_audio_path);
    env->ReleaseStringUTFChars(language, c_language);
    
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uai_audio_1parse_WhisperRecognizer_transcribeWithTimestamps(
        JNIEnv* env,
        jobject thiz,
        jstring audio_path,
        jstring language) {
    
    if (g_context == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }
    
    const char* c_audio_path = env->GetStringUTFChars(audio_path, nullptr);
    const char* c_language = env->GetStringUTFChars(language, nullptr);
    
    if (c_audio_path == nullptr) {
        LOGE("Failed to get audio path string");
        return env->NewStringUTF("");
    }
    
    std::string result = transcribeInternal(g_context, c_audio_path, c_language, true);
    
    env->ReleaseStringUTFChars(audio_path, c_audio_path);
    env->ReleaseStringUTFChars(language, c_language);
    
    return env->NewStringUTF(result.c_str());
}
