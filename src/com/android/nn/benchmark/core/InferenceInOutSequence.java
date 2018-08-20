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

import java.io.IOException;

import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Input and expected output sequence pair for inference benchmark */
public class InferenceInOutSequence {
    /** Sequence of input/output pairs */
    private List<InferenceInOut> mInputOutputs;

    public InferenceInOutSequence(int sequenceLength) {
        mInputOutputs = new ArrayList<>(sequenceLength);
    }

    public int size() {
        return mInputOutputs.size();
    }

    public InferenceInOut get(int i) {
        return mInputOutputs.get(i);
    }

    /** Helper class, generates {@link InferenceInOut} from android assets */
    public static class FromAssets {
        private String mInputAssetName;
        private String mOutputAssetName;
        private int mDataBytesSize;
        private int mInputSizeBytes;

        public FromAssets(String inputAssetName, String outputAssetName, int dataBytesSize,
                          int inputSizeBytes) {
            this.mInputAssetName = inputAssetName;
            this.mOutputAssetName = outputAssetName;
            this.mDataBytesSize = dataBytesSize;
            this.mInputSizeBytes = inputSizeBytes;
        }

        public InferenceInOutSequence readAssets(AssetManager assetManager) throws IOException {
            byte[] inputs = readAsset(assetManager, mInputAssetName, mDataBytesSize);
            byte[] outputs = readAsset(assetManager, mOutputAssetName, mDataBytesSize);
            if (inputs.length % mInputSizeBytes != 0) {
                throw new IllegalArgumentException("Input data size (in bytes): " + inputs.length +
                        " is not a multiple of input size (in bytes): " + mInputSizeBytes);
            }

            int sequenceLength = inputs.length / mInputSizeBytes;
            if (outputs.length % sequenceLength != 0) {
                throw new IllegalArgumentException("Output data size (in bytes): " +
                        outputs.length +  " is not a multiple of sequence length: " +
                        sequenceLength);
            }
            int outputSizeBytes = outputs.length / sequenceLength;

            InferenceInOutSequence sequence = new InferenceInOutSequence(sequenceLength);
            for (int i = 0; i < sequenceLength; ++i) {
                sequence.mInputOutputs.add(new InferenceInOut(
                        Arrays.copyOfRange(inputs, mInputSizeBytes * i, mInputSizeBytes * (i+1)),
                        Arrays.copyOfRange(outputs,outputSizeBytes * i, outputSizeBytes * (i+1))));
            }
            return sequence;
        }


        /** Reverse endianness on array of 4 byte elements */
        static void invertOrder4(byte[] data) {
            if (data.length % 4 != 0) {
                throw new IllegalArgumentException("Data is not 4 byte aligned");
            }
            for (int i = 0; i < data.length; i += 4) {
                byte a = data[i];
                byte b = data[i + 1];
                data[i] = data[i + 3];
                data[i + 1] = data[i + 2];
                data[i + 2] = b;
                data[i + 3] = a;
            }
        }

        /** Reverse endianness on array of 2 byte elements */
        static void invertOrder2(byte[] data) {
            if (data.length % 2 != 0) {
                throw new IllegalArgumentException("Data is not 2 byte aligned");
            }
            for (int i = 0; i < data.length; i += 2) {
                byte a = data[i];
                data[i] = data[i + 1];
                data[i + 1] = a;
            }
        }

        /** Read input/output data in native byte order */
        private static byte[] readAsset(AssetManager assetManager, String assetFilename,
                                        int dataBytesSize)
                throws IOException {
            try (InputStream in = assetManager.open(assetFilename)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = in.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }

                byte[] result = output.toByteArray();
                // Do we need to swap data endianess?
                if (dataBytesSize > 1 && ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
                    if (dataBytesSize == 4) {
                        invertOrder4(result);
                    } if (dataBytesSize == 2) {
                        invertOrder2(result);
                    } else {
                        throw new IllegalArgumentException(
                                "Byte order swapping for " + dataBytesSize
                                        + " bytes is not implmemented (yet)");
                    }
                }
                return result;
            }
        }
    }
}
