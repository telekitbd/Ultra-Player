#include <jni.h>
#include <string>
#include <vector>
#include "whisper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_ultraplayer_app_LocalWhisper_nativeInit(JNIEnv* env, jobject, jstring path) {
    const char* model_path = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    whisper_context* context = whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(path, model_path);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_ultraplayer_app_LocalWhisper_nativeTranscribe(
    JNIEnv* env, jobject, jlong handle, jfloatArray audio, jstring language) {
    auto* context = reinterpret_cast<whisper_context*>(handle);
    if (context == nullptr) return nullptr;
    const jsize count = env->GetArrayLength(audio);
    std::vector<float> samples(static_cast<size_t>(count));
    env->GetFloatArrayRegion(audio, 0, count, samples.data());
    const char* code = env->GetStringUTFChars(language, nullptr);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4;
    params.no_context = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.language = std::string(code) == "auto" ? nullptr : code;
    const int result = whisper_full(context, params, samples.data(), samples.size());
    env->ReleaseStringUTFChars(language, code);
    if (result != 0) return nullptr;
    const int segments = whisper_full_n_segments(context);
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray output = env->NewObjectArray(segments, string_class, nullptr);
    for (int i = 0; i < segments; ++i) {
        const jlong start = whisper_full_get_segment_t0(context, i) * 10;
        const jlong end = whisper_full_get_segment_t1(context, i) * 10;
        const std::string line = std::to_string(start) + "\t" + std::to_string(end) +
            "\t" + whisper_full_get_segment_text(context, i);
        jstring value = env->NewStringUTF(line.c_str());
        env->SetObjectArrayElement(output, i, value);
        env->DeleteLocalRef(value);
    }
    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ultraplayer_app_LocalWhisper_nativeFree(JNIEnv*, jobject, jlong handle) {
    auto* context = reinterpret_cast<whisper_context*>(handle);
    if (context != nullptr) whisper_free(context);
}
