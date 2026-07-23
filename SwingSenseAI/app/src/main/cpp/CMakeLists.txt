# Edge Impulse (TFLite Micro) + JNI — mirrors example-standalone-inferencing-linux micro build.
cmake_minimum_required(VERSION 3.22.1)

project(swingsense_ei)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(EI_ROOT ${CMAKE_SOURCE_DIR}/edge_impulse)
set(EI_SDK ${EI_ROOT}/edge-impulse-sdk)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ei_jni_bridge.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        NDEBUG
        EI_CLASSIFIER_ENABLE_DETECTION_POSTPROCESS_OP=1
        TF_LITE_DISABLE_X86_NEON=1
        SILENCE_EI_CLASSFIER_OBJECT_DETECTION_COUNT_WARNING=1
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${EI_ROOT}
        ${EI_SDK}
)

# --- CMSIS-DSP (.c) — same globs as Edge Impulse Makefile ---
file(GLOB EI_CMSIS_C
        "${EI_SDK}/CMSIS/DSP/Source/TransformFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/CommonTables/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/BasicMathFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/ComplexMathFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/FastMathFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/SupportFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/MatrixFunctions/*.c"
        "${EI_SDK}/CMSIS/DSP/Source/StatisticsFunctions/*.c"
)

# --- TensorFlow Lite Micro ---
set(TFLITE_C ${EI_SDK}/tensorflow/lite/c/common.c)
file(GLOB TFLITE_CC
        "${EI_SDK}/tensorflow/lite/kernels/*.cc"
        "${EI_SDK}/tensorflow/lite/kernels/internal/*.cc"
        "${EI_SDK}/tensorflow/lite/micro/kernels/*.cc"
        "${EI_SDK}/tensorflow/lite/micro/*.cc"
        "${EI_SDK}/tensorflow/lite/micro/memory_planner/*.cc"
        "${EI_SDK}/tensorflow/lite/core/api/*.cc"
)

# --- Edge Impulse model + DSP + Android porting ---
set(EI_CXX
        ${EI_ROOT}/tflite-model/tflite_learn_909575_8.cpp
        ${EI_SDK}/dsp/kissfft/kiss_fft.cpp
        ${EI_SDK}/dsp/kissfft/kiss_fftr.cpp
        ${EI_SDK}/dsp/dct/fast-dct-fft.cpp
        ${EI_SDK}/dsp/memory.cpp
        ${EI_SDK}/porting/android/ei_classifier_porting.cpp
)

target_sources(${CMAKE_PROJECT_NAME} PRIVATE
        ${EI_CMSIS_C}
        ${TFLITE_C}
        ${TFLITE_CC}
        ${EI_CXX}
)

set_source_files_properties(${EI_CMSIS_C} ${TFLITE_C} PROPERTIES LANGUAGE C)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wno-unknown-attributes
        -fmacro-prefix-map=${EI_ROOT}/=
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        m
)
