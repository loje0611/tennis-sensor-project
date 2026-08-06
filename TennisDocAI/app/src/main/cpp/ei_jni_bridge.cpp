#include <android/log.h>
#include <jni.h>

#include <cstring>
#include <cstddef>

#include "edge-impulse-sdk/classifier/ei_run_classifier.h"

#define LOG_TAG "EdgeImpulseJNI"
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_swingsenseai_inference_EdgeImpulseNative_runClassifierNative(
        JNIEnv *env,
        jobject /* thiz */,
        jfloatArray input) {
    if (input == nullptr) {
        ALOGW("runClassifier: null FloatArray");
        return env->NewStringUTF("");
    }

    const jsize len = env->GetArrayLength(input);
    if (len != static_cast<jsize>(EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE)) {
        ALOGW("runClassifier: expected %d floats, got %d",
              EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE, len);
        return env->NewStringUTF("");
    }

    float buffer[EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE];
    env->GetFloatArrayRegion(input, 0, len, buffer);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return env->NewStringUTF("");
    }

    signal_t signal{};
    signal.total_length = EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE;
    float *bufPtr = buffer;
    signal.get_data = [bufPtr](size_t offset, size_t length, float *out_ptr) -> int {
        if (offset + length > EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE) {
            return -1;
        }
        std::memcpy(out_ptr, bufPtr + offset, length * sizeof(float));
        return 0;
    };

    ei_impulse_result_t result{};
    const EI_IMPULSE_ERROR err = run_classifier(&signal, &result, false);
    if (err != EI_IMPULSE_OK) {
        ALOGW("run_classifier failed: %d", static_cast<int>(err));
        return env->NewStringUTF("");
    }

#if EI_CLASSIFIER_LABEL_COUNT <= 0
    return env->NewStringUTF("");
#else
    size_t best_ix = 0;
    float best_val = result.classification[0].value;
    for (size_t i = 1; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        if (result.classification[i].value > best_val) {
            best_val = result.classification[i].value;
            best_ix = i;
        }
    }

    const char *label = result.classification[best_ix].label;
    if (label == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(label);
#endif
}
