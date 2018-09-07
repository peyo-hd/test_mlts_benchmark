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
    public final float mTotalTimeSec;
    public final float mSumOfMSEs;
    public final float mMaxSingleError;
    public final int mIterations;
    public final float mTimeStdDeviation;
    public final String mTestInfo;
    public final int mNumberOfEvaluatorResults;
    public final String[] mEvaluatorKeys;
    public final float[] mEvaluatorResults;

    public BenchmarkResult(float totalTimeSec, int iterations, float timeVarianceSec,
            float sumOfMSEs, float maxSingleError, String testInfo,
            String[] evaluatorKeys, float[] evaluatorResults) {
        mTotalTimeSec = totalTimeSec;
        mSumOfMSEs = sumOfMSEs;
        mMaxSingleError = maxSingleError;
        mIterations = iterations;
        mTimeStdDeviation = timeVarianceSec;
        mTestInfo = testInfo;
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
                ", mTimeStdDeviation=" + mTimeStdDeviation;
        for (int i = 0; i < mEvaluatorKeys.length; i++) {
            result += ", " + mEvaluatorKeys[i] + "=" + mEvaluatorResults[i];
        }
        result = result + '}';
        return result;
    }

    public static BenchmarkResult fromInferenceResults(
            String testInfo,
            List<InferenceInOutSequence> inferenceInOuts,
            List<InferenceResult> inferenceResults,
            String evaluatorName) {
        float totalTime = 0;
        int iterations = 0;
        float sumOfMSEs = 0;
        float maxSingleError = 0;

        for (InferenceResult iresult : inferenceResults) {
            iterations++;
            totalTime += iresult.mComputeTimeSec;
            sumOfMSEs += iresult.mMeanSquaredError;
            if (iresult.mMaxSingleError > maxSingleError) {
              maxSingleError = iresult.mMaxSingleError;
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
        if (evaluatorName != null) {
            EvaluatorInterface evaluator = null;
            try {
                Class<?> clazz = Class.forName(
                        "com.android.nn.benchmark.evaluators." + evaluatorName);
                evaluator = (EvaluatorInterface) clazz.getConstructor().newInstance();
            } catch(Exception e) {
                throw new IllegalArgumentException(
                        "Can not create evaluator named '" + evaluatorName + "'",
                        e);
            }
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

        return new BenchmarkResult(totalTime, iterations, (float) Math.sqrt(variance),
                sumOfMSEs, maxSingleError, testInfo, evaluatorKeys, evaluatorResults);
    }
}

