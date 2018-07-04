#!/usr/bin/python3
""" Converts input/output data from CSV format for the TTS benchmark model.

Usage:
./gen_tts_data.py <model input csv file> <model output csv file> <output dir>
"""


import numpy as np
import sys

# TODO(vddang): Support multiple input/output pairs.
def gen_input_output_files(model_input_csv_file, model_output_csv_file,
                           output_dir):
  with open(output_dir + '/tts.input', 'wb') as f:
    f.write(np.genfromtxt(model_input_csv_file, delimiter=',',dtype=np.float,
                          max_rows=1).astype('float32').tobytes())
  with open(output_dir + '/tts.output', 'wb') as f:
    f.write(np.genfromtxt(model_output_csv_file, delimiter=',',dtype=np.float,
                          max_rows=1).astype('float32').tobytes())

if __name__ == '__main__':
  if len(sys.argv) < 4:
    print("Usage:\n ./gen_tts_data.py <model input csv file> " +
          "<model output csv file> <output dir>\n")
    sys.exit(1)
  gen_input_output_files(sys.argv[1], sys.argv[2], sys.argv[3]);
