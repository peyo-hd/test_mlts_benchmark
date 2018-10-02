/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.nn.benchmark.evaluators;

import android.util.Log;

import com.android.nn.benchmark.core.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Inference evaluator for the TTS model.
 *
 * This validates that the Mel-cep distortion and log F0 error are within the limits.
 */
public class MelCepLogF0 implements EvaluatorInterface {

    static private final float MEL_CEP_DISTORTION_LIMIT = 4f;
    static private final float LOG_F0_ERROR_LIMIT = 0.01f;

    // The TTS model predicts 4 frames per inference.
    // For each frame, there are 40 amplitude values, 7 aperiodicity values,
    // 1 log F0 value and 1 voicing value.
    static private final int FRAMES_PER_INFERENCE = 4;
    static private final int AMPLITUDE_DIMENSION = 40;
    static private final int APERIODICITY_DIMENSION = 7;
    static private final int LOG_F0_DIMENSION = 1;
    static private final int VOICING_DIMENSION = 1;
    static private final int FRAME_OUTPUT_DIMENSION = AMPLITUDE_DIMENSION + APERIODICITY_DIMENSION +
            LOG_F0_DIMENSION + VOICING_DIMENSION;
    // The threshold to classify if a frame is voiced (above threshold) or unvoiced (below).
    static private final float VOICED_THRESHOLD = 0f;

    private OutputMeanStdDev mOutputMeanStdDev;

    @Override
    public void setOutputMeanStdDev(OutputMeanStdDev outputMeanStdDev) {
        mOutputMeanStdDev = outputMeanStdDev;
    }

    @Override
    public void EvaluateAccuracy(
            List<InferenceInOutSequence> inferenceInOuts, List<InferenceResult> inferenceResults,
            List<String> keys, List<Float> values) throws ValidationException {
        if (inferenceInOuts.isEmpty()) {
            throw new IllegalArgumentException("Empty inputs/outputs");
        }

        float maxMelCepDistortion = 0f;
        float maxLogF0Error = 0f;
        int dataSize = inferenceInOuts.get(0).mDatasize;
        int outputSize = inferenceInOuts.get(0).get(0).mExpectedOutput.length / dataSize;
        int sequenceIndex = 0;
        int inferenceIndex = 0;
        while (inferenceIndex < inferenceResults.size()) {
            int sequenceLength = inferenceInOuts.get(sequenceIndex % inferenceInOuts.size()).size();
            float[][] outputs = new float[sequenceLength][outputSize];
            float[][] expectedOutputs = new float[sequenceLength][outputSize];
            for (int i = 0; i < sequenceLength; ++i, ++inferenceIndex) {
                InferenceResult result = inferenceResults.get(inferenceIndex);
                System.arraycopy(
                        mOutputMeanStdDev.denormalize(readBytes(result.mInferenceOutput, dataSize)),
                        0, outputs[i], 0, outputSize);

                InferenceInOut inOut = inferenceInOuts.get(result.mInputOutputSequenceIndex)
                        .get(result.mInputOutputIndex);
                System.arraycopy(
                        mOutputMeanStdDev.denormalize(readBytes(inOut.mExpectedOutput, dataSize)),
                        0, expectedOutputs[i], 0, outputSize);
            }

            float melCepDistortion = calculateMelCepDistortion(outputs, expectedOutputs);
            if (melCepDistortion > MEL_CEP_DISTORTION_LIMIT) {
                throw new ValidationException("Mel-cep distortion exceeded the limit: " +
                        melCepDistortion);
            }
            maxMelCepDistortion = Math.max(maxMelCepDistortion, melCepDistortion);

            float logF0Error = calculateLogF0Error(outputs, expectedOutputs);
            if (logF0Error > LOG_F0_ERROR_LIMIT) {
                throw new ValidationException("Log F0 error exceeded the limit: " + logF0Error);
            }
            maxLogF0Error = Math.max(maxLogF0Error, logF0Error);

            ++sequenceIndex;
        }
        keys.add("max_mel_cep_distortion");
        values.add(maxMelCepDistortion);
        keys.add("max_log_f0_error");
        values.add(maxLogF0Error);
    }

    private static float[] readBytes(byte[] bytes, int dataSize) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int size = bytes.length / dataSize;
        float[] result = new float[size];
        for (int i = 0; i < size; ++i) {
            if (dataSize == 4) {
                result[i] = buffer.getFloat();
            }
            // TODO: Handle dataSize == 1 when adding the quantized TTS model.
        }
        return result;
    }

    private static float calculateMelCepDistortion(float[][] outputs, float[][] expectedOutputs) {
        int inferenceCount = outputs.length;
        float squared_error = 0;
        for (int inferenceIndex = 0; inferenceIndex < inferenceCount; ++inferenceIndex) {
            for (int frameIndex = 0; frameIndex < FRAMES_PER_INFERENCE; ++frameIndex) {
                // Mel-Cep distortion skips the first amplitude element.
                for (int amplitudeIndex = 1; amplitudeIndex < AMPLITUDE_DIMENSION;
                     ++amplitudeIndex) {
                    int i = frameIndex * FRAME_OUTPUT_DIMENSION + amplitudeIndex;
                    squared_error += Math.pow(
                            outputs[inferenceIndex][i] - expectedOutputs[inferenceIndex][i], 2);
                }
            }
        }
        return (float)Math.sqrt(squared_error /
                (inferenceCount * FRAMES_PER_INFERENCE * (AMPLITUDE_DIMENSION - 1)));
    }

    private static float calculateLogF0Error(float[][] outputs, float[][] expectedOutputs) {
        int inferenceCount = outputs.length;
        float squared_error = 0;
        int count = 0;
        for (int inferenceIndex = 0; inferenceIndex < inferenceCount; ++inferenceIndex) {
            for (int frameIndex = 0; frameIndex < FRAMES_PER_INFERENCE; ++frameIndex) {
                int f0Index = frameIndex * FRAME_OUTPUT_DIMENSION + AMPLITUDE_DIMENSION +
                        APERIODICITY_DIMENSION;
                int voicedIndex = f0Index + LOG_F0_DIMENSION;
                if (outputs[inferenceIndex][voicedIndex] > VOICED_THRESHOLD &&
                        expectedOutputs[inferenceIndex][voicedIndex] > VOICED_THRESHOLD) {
                    squared_error += Math.pow(outputs[inferenceIndex][f0Index] -
                            expectedOutputs[inferenceIndex][f0Index], 2);
                    ++count;
                }
            }
        }
        float logF0Error = 0f;
        if (count > 0) {
            logF0Error = (float)Math.sqrt(squared_error / count);
        }
        return logF0Error;
    }
}
