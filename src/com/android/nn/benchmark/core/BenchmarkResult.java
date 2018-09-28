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

package com.android.nn.benchmark.core;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkResult implements Parcelable {
    public final static String BACKEND_TFLITE_NNAPI = "TFLite_NNAPI";
    public final static String BACKEND_TFLITE_CPU = "TFLite_CPU";

    public final static int TIME_FREQ_ARRAY_SIZE = 32;
    public final float mTotalTimeSec;
    public final float mSumOfMSEs;
    public final float mMaxSingleError;
    public final int mIterations;
    public final float mTimeStdDeviation;
    public final String mTestInfo;
    public final int mNumberOfEvaluatorResults;
    public final String[] mEvaluatorKeys;
    public final float[] mEvaluatorResults;

    /** Type of backend used for inference */
    public final String mBackendType;

    /** Time offset for inference frequency counts */
    public final float mTimeFreqStartSec;

    /** Index time offset for inference frequency counts */
    public final float mTimeFreqStepSec;

    /**
     * Array of inference frequency counts.
     * Each entry contains inference count for time range:
     * [mTimeFreqStartSec + i*mTimeFreqStepSec, mTimeFreqStartSec + (1+i*mTimeFreqStepSec)
     */
    public final float[] mTimeFreqSec;

    /** Size of test set using for inference */
    public final int mTestSetSize;

    public BenchmarkResult(float totalTimeSec, int iterations, float timeVarianceSec,
            float sumOfMSEs, float maxSingleError, String testInfo,
            String[] evaluatorKeys, float[] evaluatorResults,
            float timeFreqStartSec, float timeFreqStepSec, float[] timeFreqSec,
            String backendType, int testSetSize) {
        mTotalTimeSec = totalTimeSec;
        mSumOfMSEs = sumOfMSEs;
        mMaxSingleError = maxSingleError;
        mIterations = iterations;
        mTimeStdDeviation = timeVarianceSec;
        mTestInfo = testInfo;
        mTimeFreqStartSec = timeFreqStartSec;
        mTimeFreqStepSec = timeFreqStepSec;
        mTimeFreqSec = timeFreqSec;
        mBackendType = backendType;
        mTestSetSize = testSetSize;

        if (evaluatorKeys == null) {
            mEvaluatorKeys = new String[0];
        } else {
            mEvaluatorKeys = evaluatorKeys;
        }
        if (evaluatorResults == null) {
            mEvaluatorResults = new float[0];
        } else {
            mEvaluatorResults = evaluatorResults;
        }
        if (mEvaluatorResults.length != mEvaluatorKeys.length) {
            throw new IllegalArgumentException("Different number of evaluator keys vs values");
        }
        mNumberOfEvaluatorResults = mEvaluatorResults.length;
    }

    protected BenchmarkResult(Parcel in) {
        mTotalTimeSec = in.readFloat();
        mSumOfMSEs = in.readFloat();
        mMaxSingleError = in.readFloat();
        mIterations = in.readInt();
        mTimeStdDeviation = in.readFloat();
        mTestInfo = in.readString();
        mNumberOfEvaluatorResults = in.readInt();
        mEvaluatorKeys = new String[mNumberOfEvaluatorResults];
        in.readStringArray(mEvaluatorKeys);
        mEvaluatorResults = new float[mNumberOfEvaluatorResults];
        in.readFloatArray(mEvaluatorResults);
        if (mEvaluatorResults.length != mEvaluatorKeys.length) {
            throw new IllegalArgumentException("Different number of evaluator keys vs values");
        }
        mTimeFreqStartSec = in.readFloat();
        mTimeFreqStepSec = in.readFloat();
        int timeFreqSecLength = in.readInt();
        mTimeFreqSec = new float[timeFreqSecLength];
        in.readFloatArray(mTimeFreqSec);
        mBackendType = in.readString();
        mTestSetSize = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mTotalTimeSec);
        dest.writeFloat(mSumOfMSEs);
        dest.writeFloat(mMaxSingleError);
        dest.writeInt(mIterations);
        dest.writeFloat(mTimeStdDeviation);
        dest.writeString(mTestInfo);
        dest.writeInt(mNumberOfEvaluatorResults);
        dest.writeStringArray(mEvaluatorKeys);
        dest.writeFloatArray(mEvaluatorResults);
        dest.writeFloat(mTimeFreqStartSec);
        dest.writeFloat(mTimeFreqStepSec);
        dest.writeInt(mTimeFreqSec.length);
        dest.writeFloatArray(mTimeFreqSec);
        dest.writeString(mBackendType);
        dest.writeInt(mTestSetSize);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<BenchmarkResult> CREATOR =
            new Parcelable.Creator<BenchmarkResult>() {
                @Override
                public BenchmarkResult createFromParcel(Parcel in) {
                    return new BenchmarkResult(in);
                }

                @Override
                public BenchmarkResult[] newArray(int size) {
                    return new BenchmarkResult[size];
                }
            };

    public float getMeanTimeSec() {
        return mTotalTimeSec / mIterations;
    }

    @Override
    public String toString() {
        String result = "BenchmarkResult{" +
                "mTestInfo='" + mTestInfo + '\'' +
                ", getMeanTimeSec()=" + getMeanTimeSec() +
                ", mTotalTimeSec=" + mTotalTimeSec +
                ", mSumOfMSEs=" + mSumOfMSEs +
                ", mMaxSingleError=" + mMaxSingleError +
                ", mIterations=" + mIterations +
                ", mTimeStdDeviation=" + mTimeStdDeviation +
                ", mTimeFreqStartSec=" + mTimeFreqStartSec +
                ", mTimeFreqStepSec=" + mTimeFreqStepSec +
                ", mBackendType=" + mBackendType +
                ", mTestSetSize=" + mTestSetSize;
        for (int i = 0; i < mEvaluatorKeys.length; i++) {
            result += ", " + mEvaluatorKeys[i] + "=" + mEvaluatorResults[i];
        }
        result = result + '}';
        return result;
    }

    public static BenchmarkResult fromInferenceResults(
            String testInfo,
            String backendType,
            List<InferenceInOutSequence> inferenceInOuts,
            List<InferenceResult> inferenceResults,
            EvaluatorInterface evaluator) {
        float totalTime = 0;
        int iterations = 0;
        float sumOfMSEs = 0;
        float maxSingleError = 0;

        float maxComputeTimeSec = 0.0f;
        float minComputeTimeSec = Float.MAX_VALUE;

        for (InferenceResult iresult : inferenceResults) {
            iterations++;
            totalTime += iresult.mComputeTimeSec;
            sumOfMSEs += iresult.mMeanSquaredError;
            if (iresult.mMaxSingleError > maxSingleError) {
                maxSingleError = iresult.mMaxSingleError;
            }
            if (maxComputeTimeSec < iresult.mComputeTimeSec) {
                maxComputeTimeSec = iresult.mComputeTimeSec;
            }
            if (minComputeTimeSec > iresult.mComputeTimeSec) {
                minComputeTimeSec = iresult.mComputeTimeSec;
            }
        }

        float inferenceMean = (totalTime / iterations);

        float variance = 0.0f;
        for (InferenceResult iresult : inferenceResults) {
            float v = (iresult.mComputeTimeSec - inferenceMean);
            variance += v * v;
        }
        variance /= iterations;
        String[] evaluatorKeys = null;
        float[] evaluatorResults = null;
        if (evaluator != null) {
            ArrayList<String> keys = new ArrayList<String>();
            ArrayList<Float> results = new ArrayList<Float>();
            evaluator.EvaluateAccuracy(inferenceInOuts, inferenceResults, keys, results);
            evaluatorKeys = new String[keys.size()];
            evaluatorKeys = keys.toArray(evaluatorKeys);
            evaluatorResults = new float[results.size()];
            for (int i = 0; i < evaluatorResults.length; i++) {
                evaluatorResults[i] = results.get(i).floatValue();
            }
        }

        // Calculate inference frequency/histogram across TIME_FREQ_ARRAY_SIZE buckets.
        float[] timeFreqSec = new float[TIME_FREQ_ARRAY_SIZE];
        float stepSize = (maxComputeTimeSec - minComputeTimeSec) / (TIME_FREQ_ARRAY_SIZE - 1);
        for (InferenceResult iresult : inferenceResults) {
            timeFreqSec[(int) ((iresult.mComputeTimeSec - minComputeTimeSec) / stepSize)] += 1;
        }

        return new BenchmarkResult(totalTime, iterations, (float) Math.sqrt(variance),
                sumOfMSEs, maxSingleError, testInfo, evaluatorKeys, evaluatorResults,
                minComputeTimeSec, stepSize, timeFreqSec, backendType, inferenceInOuts.size());
    }
}
