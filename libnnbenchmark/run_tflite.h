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

#ifndef COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
#define COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H

#include "tensorflow/contrib/lite/interpreter.h"
#include "tensorflow/contrib/lite/model.h"

#include <unistd.h>
#include <vector>

// Inputs and expected outputs for inference
struct InferenceInOut {
    // Input can either be directly specified as a pointer or indirectly with
    // the createInput callback. This is needed for large datasets where
    // allocating memory for all inputs at once is not feasible.
    uint8_t *input;
    size_t input_size;

    uint8_t *output;
    size_t output_size;

    std::function<bool(uint8_t*, size_t)> createInput;
};

// Inputs and expected outputs for an inference sequence.
using InferenceInOutSequence = std::vector<InferenceInOut>;

// Result of a single inference
struct InferenceResult {
    float computeTimeSec;
    float meanSquareError;
    float maxSingleError;
    std::vector<uint8_t> inferenceOutput;
    int inputOutputSequenceIndex;
    int inputOutputIndex;
};


/** Discard inference output in inference results. */
const int FLAG_DISCARD_INFERENCE_OUTPUT = 1 << 0;
/** Do not expect golden output for inference inputs. */
const int FLAG_IGNORE_GOLDEN_OUTPUT = 1 << 1;

class BenchmarkModel {
public:
    BenchmarkModel(const char* modelfile,
                   bool use_nnapi,
                   bool enable_intermediate_tensors_dump);
    ~BenchmarkModel();

    bool resizeInputTensors(std::vector<int> shape);
    bool setInput(const uint8_t* dataPtr, size_t length);
    bool runInference();
    // Resets TFLite states (RNN/LSTM states etc).
    bool resetStates();

    bool benchmark(const std::vector<InferenceInOutSequence>& inOutData,
                   int seqInferencesMaxCount,
                   float timeout,
                   int flags,
                   std::vector<InferenceResult> *result);

    bool dumpAllLayers(const char* path,
                       const std::vector<InferenceInOutSequence>& inOutData);

private:
    void getOutputError(const uint8_t* dataPtr, size_t length, InferenceResult* result);
    void saveInferenceOutput(InferenceResult* result);

    std::unique_ptr<tflite::FlatBufferModel> mTfliteModel;
    std::unique_ptr<tflite::Interpreter> mTfliteInterpreter;
};


#endif  // COM_EXAMPLE_ANDROID_NN_BENCHMARK_RUN_TFLITE_H
