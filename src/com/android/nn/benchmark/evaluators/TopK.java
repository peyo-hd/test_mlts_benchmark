/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.util.Pair;

import com.android.nn.benchmark.core.EvaluatorInterface;
import com.android.nn.benchmark.core.InferenceInOut;
import com.android.nn.benchmark.core.InferenceInOutSequence;
import com.android.nn.benchmark.core.InferenceResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Accuracy evaluator for classifiers - top-k accuracy (with k=5).
 */

public class TopK implements EvaluatorInterface {

    public static final int K_TOP = 5;

    public void EvaluateAccuracy(
            List<InferenceInOutSequence> inferenceInOuts,
            List<InferenceResult> inferenceResults,
            List<String> keys,
            List<Float> values) {

        int total = 0;
        int[] topk = new int[K_TOP];
        for (int i = 0; i < inferenceResults.size(); i++) {
            InferenceResult result = inferenceResults.get(i);
            if (result.mInferenceOutput == null) {
                throw new IllegalArgumentException("Needs mInferenceOutput for TopK");
            }
            InferenceInOutSequence sequence = inferenceInOuts.get(result.mInputOutputSequenceIndex);
            if (sequence.size() != 1) {
                throw new IllegalArgumentException("Only one item in InferenceInOutSequenece " +
                        "supported by TopK evaluator");
            }
            if (result.mInputOutputIndex != 0) {
                throw new IllegalArgumentException("Unexpected non-zero InputOutputIndex");
            }
            InferenceInOut io = sequence.get(0);
            final int expectedClass = io.mExpectedClass;
            if (expectedClass < 0) {
                throw new IllegalArgumentException("expected class not set");
            }
            PriorityQueue<Pair<Integer, Float>> sorted = new PriorityQueue<Pair<Integer, Float>>(
                    new Comparator<Pair<Integer, Float>>() {
                        @Override
                        public int compare(Pair<Integer, Float> o1, Pair<Integer, Float> o2) {
                            // Note reverse order to get highest probability first
                            return o2.second.compareTo(o1.second);
                        }
                    });
            ByteBuffer buf = ByteBuffer.wrap(result.mInferenceOutput);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            int count = result.mInferenceOutput.length / sequence.mDatasize;
            for (int index = 0; index < count; index++) {
                float probability;
                if (sequence.mDatasize == 4) {
                    probability = buf.getFloat();
                } else {
                    probability = (float)(buf.get() & 0xff);
                }
                sorted.add(new Pair<Integer, Float>(new Integer(index), new Float(probability)));
            }
            total++;
            boolean seen = false;
            for (int k = 0; k < K_TOP; k++) {
                Pair<Integer, Float> top = sorted.remove();
                if (top.first.intValue() == expectedClass) {
                    seen = true;
                }
                if (seen) {
                    topk[k]++;
                }
            }
        }
        for (int i = 0; i < K_TOP; i++) {
            keys.add("top_" + (i + 1));
            values.add(new Float((float)topk[i] / (float)total));
        }
    }

}
