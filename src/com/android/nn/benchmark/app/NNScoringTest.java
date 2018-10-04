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

package com.android.nn.benchmark.app;

import android.test.suitebuilder.annotation.LargeTest;

import com.android.nn.benchmark.core.TestModels;
import com.android.nn.benchmark.util.CSVWriter;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Tests that run all models/datasets/backend that are required for scoring the device.
 * Produces a CSV file with benchmark results.
 * Currently it runs a mobilenet network over provided ~400 image datasets, on NNAPI and CPU.
 *
 * Tu use, please run:
 * adb shell am instrument -w -e size large
 * com.android.nn.benchmark.app.NNScoringTest/android.support.test.runner.AndroidJUnitRunner
 *
 * To fetch results, please run:
 * adb pull /data/data/com.android.nn.benchmark.app/benchmark.csv
 */
// TODO(pszczepaniak): Make it an activity, so it's possible to start from UI
@RunWith(Parameterized.class)
public class NNScoringTest extends BenchmarkTestBase {
    private static final String CSV_PATH = "/data/data/com.android.nn.benchmark.app/benchmark.csv";

    private static CSVWriter csvWriter;

    public NNScoringTest(TestModels.TestModelEntry model) {
        super(model);
    }

    @Override
    protected void prepareTest() {
        super.prepareTest();
    }

    @Parameters(name = "{0}")
    public static List<TestModels.TestModelEntry> modelsList() {
        try {
            return Arrays.asList(new TestModels.TestModelEntry[]{
                    TestModels.getModelByName("mobilenet_quantized_topk"),
                    TestModels.getModelByName("mobilenet_float_topk"),
                    TestModels.getModelByName("tts_float"),

            });
        } catch (IllegalArgumentException e) {
            // No internal datasets, use AOSP ones.
            return Arrays.asList(new TestModels.TestModelEntry[]{
                    TestModels.getModelByName("mobilenet_quantized_topk_aosp"),
                    TestModels.getModelByName("mobilenet_float_topk_aosp"),
                    TestModels.getModelByName("tts_float"),
            });
        }
    }

    @Test
    @LargeTest
    public void testTFLite() throws IOException {
        setUseNNApi(false);
        setCompleteInputSet(true);
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                COMPLETE_SET_TIMEOUT_SECOND);
        runTest(ta, mModel.getTestName());

        try (CSVWriter writer = new CSVWriter(new File(CSV_PATH))) {
            writer.write(ta.getBenchmark());
        }
    }

    @Test
    @LargeTest
    public void testNNAPI() throws IOException {
        setUseNNApi(true);
        setCompleteInputSet(true);
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                COMPLETE_SET_TIMEOUT_SECOND);
        runTest(ta, mModel.getTestName());

        try (CSVWriter writer = new CSVWriter(new File(CSV_PATH))) {
            writer.write(ta.getBenchmark());
        }
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        new File(CSV_PATH).delete();
        try (CSVWriter writer = new CSVWriter(new File(CSV_PATH))) {
            writer.writeHeader();
        }
    }
}
