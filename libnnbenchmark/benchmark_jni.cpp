/**
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "run_tflite.h"

#include <jni.h>
#include <string>
#include <iomanip>
#include <sstream>
#include <fcntl.h>

#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <android/sharedmem.h>
#include <sys/mman.h>


extern "C"
JNIEXPORT jlong
JNICALL
Java_com_android_nn_benchmark_core_NNTestBase_initModel(
        JNIEnv *env,
        jobject /* this */,
        jstring _modelFileName) {
    const char *modelFileName = env->GetStringUTFChars(_modelFileName, NULL);
    void* handle = new BenchmarkModel(modelFileName);
    env->ReleaseStringUTFChars(_modelFileName, modelFileName);

    return (jlong)(uintptr_t)handle;
}

extern "C"
JNIEXPORT void
JNICALL
Java_com_android_nn_benchmark_core_NNTestBase_destroyModel(
        JNIEnv *env,
        jobject /* this */,
        jlong _modelHandle) {
    BenchmarkModel* model = (BenchmarkModel *) _modelHandle;
    delete(model);
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_android_nn_benchmark_core_NNTestBase_resizeInputTensors(
        JNIEnv *env,
        jobject /* this */,
        jlong _modelHandle,
        jintArray _inputShape) {
    BenchmarkModel* model = (BenchmarkModel *) _modelHandle;
    jint* shapePtr = env->GetIntArrayElements(_inputShape, nullptr);
    jsize shapeLen = env->GetArrayLength(_inputShape);

    std::vector<int> shape(shapePtr, shapePtr + shapeLen);
    return model->resizeInputTensors(std::move(shape));
}

/** RAII container for a list of InferenceInOutSequence to handle JNI data release in destructor. */
class InferenceInOutSequenceList {
public:
    InferenceInOutSequenceList(JNIEnv *env,
                               const jobject& inOutDataList,
                               const jmethodID& list_size,
                               const jmethodID& list_get,
                               const jmethodID& inOutSeq_size,
                               const jmethodID& inOutSeq_get,
                               const jfieldID& inout_input,
                               const jfieldID& inout_expectedOutput,
                               bool expectGoldenOutputs);
    ~InferenceInOutSequenceList();

    bool isValid() const { return mValid; }

    const std::vector<InferenceInOutSequence>& data() const { return mData; }

private:
    JNIEnv *mEnv;  // not owned.
    const jobject& mInOutDataList;
    const jmethodID& mList_get;
    const jmethodID& mInOutSeq_get;
    const jfieldID& mInout_input;
    const jfieldID& mInout_expectedOutput;

    std::vector<InferenceInOutSequence> mData;
    bool mValid;
};

InferenceInOutSequenceList::InferenceInOutSequenceList(JNIEnv *env,
                                                       const jobject& inOutDataList,
                                                       const jmethodID& list_size,
                                                       const jmethodID& list_get,
                                                       const jmethodID& inOutSeq_size,
                                                       const jmethodID& inOutSeq_get,
                                                       const jfieldID& inout_input,
                                                       const jfieldID& inout_expectedOutput,
                                                       bool expectGoldenOutputs)
    : mEnv(env),
      mInOutDataList(inOutDataList),
      mList_get(list_get),
      mInOutSeq_get(inOutSeq_get),
      mInout_input(inout_input),
      mInout_expectedOutput(inout_expectedOutput),
      mValid(false) {
    // Fetch input/output arrays
    size_t data_count = mEnv->CallIntMethod(mInOutDataList, list_size);
    if (env->ExceptionCheck()) {return;}
    mData.reserve(data_count);
    for (int seq_index = 0; seq_index < data_count; ++seq_index) {
        jobject inOutSeq = mEnv->CallObjectMethod(mInOutDataList, mList_get, seq_index);
        if (mEnv->ExceptionCheck()) {return;}

        size_t seqLen = mEnv->CallIntMethod(inOutSeq, inOutSeq_size);
        if (mEnv->ExceptionCheck()) {return;}

        mData.push_back(InferenceInOutSequence{});
        auto& seq = mData.back();
        seq.reserve(seqLen);
        for (int i = 0; i < seqLen; ++i) {
            jobject inout = mEnv->CallObjectMethod(inOutSeq, mInOutSeq_get, i);
            if (mEnv->ExceptionCheck()) {return;}

            jbyteArray input = static_cast<jbyteArray>(
                    mEnv->GetObjectField(inout, mInout_input));
            uint8_t *input_data = reinterpret_cast<uint8_t*>(
                    mEnv->GetByteArrayElements(input, NULL));
            size_t input_len = mEnv->GetArrayLength(input);

            jbyteArray expectedOutput = static_cast<jbyteArray>(
                    mEnv->GetObjectField(inout, mInout_expectedOutput));
            if (expectedOutput != nullptr) {
                uint8_t *expectedOutput_data = reinterpret_cast<uint8_t*>(
                        mEnv->GetByteArrayElements(expectedOutput, NULL));
                size_t expectedOutput_len = mEnv->GetArrayLength(expectedOutput);

                seq.push_back(
                        {input_data, input_len, expectedOutput_data, expectedOutput_len});
            } else {
                seq.push_back( { input_data, input_len, nullptr, 0} );
                if (expectGoldenOutputs) {
                    jclass iaeClass = mEnv->FindClass("java/lang/IllegalArgumentException");
                    mEnv->ThrowNew(iaeClass, "Expected golden output for every input");

                    return;
                }
            }
        }
    }
    mValid = true;
}

InferenceInOutSequenceList::~InferenceInOutSequenceList() {
    for (int seq_index = 0; seq_index < mData.size(); ++seq_index) {
        jobject inOutSeq = mEnv->CallObjectMethod(mInOutDataList, mList_get, seq_index);
        if (mEnv->ExceptionCheck()) {return;}

        for (int i = 0; i < mData[seq_index].size(); ++i) {
            jobject inout = mEnv->CallObjectMethod(inOutSeq, mInOutSeq_get, i);
            if (mEnv->ExceptionCheck()) {return;}

            jbyteArray input = static_cast<jbyteArray>(mEnv->GetObjectField(inout, mInout_input));
            if (mEnv->ExceptionCheck()) {return;}
            mEnv->ReleaseByteArrayElements(
                    input, reinterpret_cast<jbyte*>(mData[seq_index][i].input), JNI_ABORT);
            jbyteArray expectedOutput = static_cast<jbyteArray>(
                    mEnv->GetObjectField(inout, mInout_expectedOutput));
            if (mEnv->ExceptionCheck()) {return;}
            if (expectedOutput != nullptr) {
                mEnv->ReleaseByteArrayElements(
                        expectedOutput, reinterpret_cast<jbyte*>(mData[seq_index][i].output),
                        JNI_ABORT);
            }
        }
    }
}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_android_nn_benchmark_core_NNTestBase_runBenchmark(
        JNIEnv *env,
        jobject /* this */,
        jlong _modelHandle,
        jobject inOutDataList,
        jobject resultList,
        jint inferencesMaxCount,
        jfloat timeoutSec,
        jint flags) {

    jclass list_class = env->FindClass("java/util/List");
    if (list_class == nullptr) {return false;}
    jmethodID list_size = env->GetMethodID(list_class, "size", "()I");
    if (list_size == nullptr) {return false;}
    jmethodID list_get = env->GetMethodID(list_class, "get", "(I)Ljava/lang/Object;");
    if (list_get == nullptr) {return false;}
    jmethodID list_add = env->GetMethodID(list_class, "add", "(Ljava/lang/Object;)Z");
    if (list_add == nullptr) {return false;}

    jclass inOutSeq_class = env->FindClass("com/android/nn/benchmark/core/InferenceInOutSequence");
    if (inOutSeq_class == nullptr) {return false;}
    jmethodID inOutSeq_size = env->GetMethodID(inOutSeq_class, "size", "()I");
    if (inOutSeq_size == nullptr) {return false;}
    jmethodID inOutSeq_get = env->GetMethodID(inOutSeq_class, "get",
                                              "(I)Lcom/android/nn/benchmark/core/InferenceInOut;");
    if (inOutSeq_get == nullptr) {return false;}

    jclass inout_class = env->FindClass("com/android/nn/benchmark/core/InferenceInOut");
    if (inout_class == nullptr) {return false;}
    jfieldID inout_input = env->GetFieldID(inout_class, "mInput", "[B");
    if (inout_input == nullptr) {return false;}
    jfieldID inout_expectedOutput = env->GetFieldID(inout_class, "mExpectedOutput", "[B");
    if (inout_expectedOutput == nullptr) {return false;}

    jclass result_class = env->FindClass("com/android/nn/benchmark/core/InferenceResult");
    if (result_class == nullptr) {return false;}
    jmethodID result_ctor = env->GetMethodID(result_class, "<init>", "(FFF[B)V");
    if (result_ctor == nullptr) {return false;}

    BenchmarkModel* model = (BenchmarkModel *) _modelHandle;

    std::vector<InferenceResult> result;

    const bool expectGoldenOutputs = (flags & FLAG_IGNORE_GOLDEN_OUTPUT) == 0;
    InferenceInOutSequenceList data(env, inOutDataList, list_size, list_get, inOutSeq_size,
                                    inOutSeq_get, inout_input, inout_expectedOutput,
                                    expectGoldenOutputs);
    if (!data.isValid()) {
        return false;
    }

    // TODO: Remove success boolean from this method and throw an exception in case of problems
    bool success = model->benchmark(data.data(), inferencesMaxCount, timeoutSec, flags, &result);

    // Generate results
    if (success) {
        for (const InferenceResult &rentry : result) {
            jbyteArray inferenceOutput = nullptr;

            if ((flags & FLAG_DISCARD_INFERENCE_OUTPUT) == 0) {
                inferenceOutput = env->NewByteArray(rentry.inferenceOutput.size());
                if (env->ExceptionCheck()) {return false;}
                jbyte *bytes = env->GetByteArrayElements(inferenceOutput, nullptr);
                memcpy(bytes, &rentry.inferenceOutput[0], rentry.inferenceOutput.size());
                env->ReleaseByteArrayElements(inferenceOutput, bytes, 0);
            }

            jobject object = env->NewObject(
                result_class, result_ctor, rentry.computeTimeSec,
                rentry.meanSquareError, rentry.maxSingleError, inferenceOutput);
            if (env->ExceptionCheck() || object == NULL) {return false;}

            env->CallBooleanMethod(resultList, list_add, object);
            if (env->ExceptionCheck()) {return false;}
        }
    }

    return success;
}
