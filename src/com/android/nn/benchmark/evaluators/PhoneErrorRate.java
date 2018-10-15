package com.android.nn.benchmark.evaluators;

import java.util.List;

/**
 * Inference evaluator for the ASR model.
 *
 * This validates that the Phone Error Rate (PER) is within the limit.
 */
public class PhoneErrorRate extends BaseSequenceEvaluator {
    static private final float PHONE_ERROR_RATE_LIMIT = 5f;  // 5%

    private float mMaxPER = 0f;

    @Override
    protected void EvaluateSequenceAccuracy(float[][] outputs, float[][] expectedOutputs,
            List<String> outValidationErrors) {
        float per = calculatePER(outputs, expectedOutputs);
        if (per > PHONE_ERROR_RATE_LIMIT) {
            outValidationErrors.add("Phone error rate exceeded the limit: " + per);
        }
        mMaxPER = Math.max(mMaxPER, per);
    }

    @Override
    protected void AddValidationResult(List<String> keys, List<Float> values) {
        keys.add("max_phone_error_rate");
        values.add(mMaxPER);
    }

    /** Calculates Phone Error Rate in percent. */
    private static float calculatePER(float[][] outputs, float[][] expectedOutputs) {
        int inferenceCount = outputs.length;
        float squared_error = 0;
        int errorCount = 0;
        for (int inferenceIndex = 0; inferenceIndex < inferenceCount; ++inferenceIndex) {
            if (indexOfLargest(outputs[inferenceIndex]) !=
                    indexOfLargest(expectedOutputs[inferenceIndex])) {
                ++errorCount;
            }
        }

        return (float)(errorCount * 100.0 / inferenceCount);
    }

    private static int indexOfLargest(float[] items) {
        int ret = -1;
        float largest = -Float.MAX_VALUE;
        for (int i = 0; i < items.length; ++i) {
            if (items[i] > largest) {
                ret = i;
                largest = items[i];
            }
        }
        return ret;
    }
}
