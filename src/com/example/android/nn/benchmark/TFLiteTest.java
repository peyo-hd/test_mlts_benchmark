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

package com.example.android.nn.benchmark;

import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.nn.benchmark.core.TestModels.TestModelEntry;
import org.junit.Test;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;

@RunWith(Parameterized.class)
public class TFLiteTest extends NNTest {

    public TFLiteTest(TestModelEntry model) {
        super(model);
    }

    @Override
    protected void prepareTest() {
        super.prepareTest();
        setUseNNApi(false);
    }

    @Test
    @LargeTest
    public void testTFLite10Seconds() {
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                RUNTIME_REPEATABLE_SECONDS);
        runTest(ta, mModel.getTestName());
    }

    @Test
    @MediumTest
    public void testTFLite() {
        TestAction ta = new TestAction(mModel, WARMUP_SHORT_SECONDS,
                RUNTIME_SHORT_SECONDS);
        runTest(ta, mModel.getTestName());
    }

    @Test
    @LargeTest
    public void testTFLiteAllData() {
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
                RUNTIME_ONCE);
        runTest(ta, mModel.getTestName());
    }
}
