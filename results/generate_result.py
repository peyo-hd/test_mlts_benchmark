#!/usr/bin/python3
#
# Copyright 2018, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""MLTS benchmark result generator.

Reads a CSV produced by MLTS benchmark and generates
an HTML page with results summary.

Usage:
  generate_result [csv input file] [html output file]
"""

import argparse
import collections
import csv
import os


class ScoreException(Exception):
  """Generator base exception type. """
  pass

BenchmarkResult = collections.namedtuple(
    'BenchmarkResult',
    ['name', 'backend_type', 'iterations', 'total_time_sec', 'max_single_error',
     'testset_size', 'evaluator_keys', 'evaluator_values',
     'time_freq_start_sec', 'time_freq_step_sec', 'time_freq_sec'])


def parse_csv_input(input_filename):
  """Parse input CSV file, returns: (benchmarkInfo, list of BenchmarkResult)."""
  with open(input_filename, 'r') as csvfile:
    csv_reader = csv.reader(filter(lambda row: row[0] != '#', csvfile))

    # First line contain device info
    benchmark_info = next(csv_reader)

    results = [BenchmarkResult(
        name=row[0],
        backend_type=row[1],
        iterations=int(row[2]),
        total_time_sec=float(row[3]),
        max_single_error=float(row[4]),
        testset_size=int(row[5]),
        time_freq_start_sec=float(row[7]),
        time_freq_step_sec=float(row[8]),
        evaluator_keys=row[9:9 + int(row[6])*2:2],
        evaluator_values=row[10: 9 + int(row[6])*2:2],
        time_freq_sec=[float(x) for x in row[10 + int(row[6])*2:]])
               for row in csv_reader]
    return (benchmark_info, results)


def group_results(results):
  """Group list of results by their name/backend, returns list of lists."""
  # Group by name
  groupings = collections.defaultdict(list)
  for result in results:
    groupings[result.name].append(result)

  # Sort by backend type inside groups
  for name in groupings:
    groupings[name] = sorted(groupings[name], key=lambda x: x.backend_type)

  # Turn into a list sorted by name
  groupings_list = []
  for name in sorted(groupings.keys()):
    groupings_list.append(groupings[name])
  return groupings_list


def get_frequency_graph(time_freq_start_sec, time_freq_step_sec, time_freq_sec):
  """Generate input x/y data for latency frequency graph."""
  return (['{:.2f}ms'.format(
      (time_freq_start_sec + x * time_freq_step_sec) * 1000.0)
           for x in range(len(time_freq_sec))], time_freq_sec)


def is_topk_evaluator(evaluator_keys):
  """Are these evaluator keys from TopK evaluator?"""
  return (len(evaluator_keys) == 5 and
          evaluator_keys[0] == 'top_1' and
          evaluator_keys[1] == 'top_2' and
          evaluator_keys[2] == 'top_3' and
          evaluator_keys[3] == 'top_4' and
          evaluator_keys[4] == 'top_5')


def is_melceplogf0_evaluator(evaluator_keys):
  """Are these evaluator keys from MelCepLogF0 evaluator?"""
  return (len(evaluator_keys) == 2 and
          evaluator_keys[0] == 'max_mel_cep_distortion' and
          evaluator_keys[1] == 'max_log_f0_error')


def generate_accuracy_headers(entries_group):
  """Accuracy-related headers for result table."""
  if is_topk_evaluator(entries_group[0].evaluator_keys):
    return ACCURACY_HEADERS_TOPK_TEMPLATE
  elif is_melceplogf0_evaluator(entries_group[0].evaluator_keys):
    return ACCURACY_HEADERS_MELCEPLOGF0_TEMPLATE
  elif entries_group[0].evaluator_keys:
    return ACCURACY_HEADERS_BASIC_TEMPLATE
  raise ScoreException('Unknown accuracy headers for: ' + str(entries_group[0]))


def generate_accuracy_values(result):
  """Accuracy-related data for result table."""
  if is_topk_evaluator(result.evaluator_keys):
    return ACCURACY_VALUES_TOPK_TEMPLATE.format(
        top1=float(result.evaluator_values[0]) * 100.0,
        top2=float(result.evaluator_values[1]) * 100.0,
        top3=float(result.evaluator_values[2]) * 100.0,
        top4=float(result.evaluator_values[3]) * 100.0,
        top5=float(result.evaluator_values[4]) * 100.0)
  elif is_melceplogf0_evaluator(result.evaluator_keys):
    return ACCURACY_VALUES_MELCEPLOGF0_TEMPLATE.format(
        max_log_f0=float(result.evaluator_values[0]),
        max_mel_cep_distortion=float(result.evaluator_values[1]),
        max_single_error=float(result.max_single_error),
        )
  elif result.evaluator_keys:
    return ACCURACY_VALUES_BASIC_TEMPLATE.format(
        max_single_error=result.max_single_error,
        )
  raise ScoreException('Unknown accuracy values for: ' + str(result))


def getchartjs_source():
  return open(os.path.dirname(os.path.abspath(__file__)) + "/" + CHART_JS_FILE).read()


def generate_result(benchmark_info, data):
  """Turn list of results into HTML."""
  return MAIN_TEMPLATE.format(
      jsdeps=getchartjs_source(),
      device_info=DEVICE_INFO_TEMPLATE.format(
          benchmark_time=benchmark_info[0],
          device_info=benchmark_info[1],
          ),
      results_list=''.join((
          RESULT_GROUP_TEMPLATE.format(
              accuracy_headers=generate_accuracy_headers(entries_group),
              results=''.join((
                  RESULT_ENTRY_TEMPLATE.format(
                      name=result.name,
                      backend=result.backend_type,
                      i=id(result),
                      iterations=result.iterations,
                      testset_size=result.testset_size,
                      accuracy_values=generate_accuracy_values(result),
                      avg_ms=(result.total_time_sec / result.iterations)*1000.0,
                      freq_data=get_frequency_graph(result.time_freq_start_sec,
                                                    result.time_freq_step_sec,
                                                    result.time_freq_sec)
                  ) for i, result in enumerate(entries_group))
                             )
          ) for entries_group in group_results(data))
                          ))


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument('input', help='input csv filename')
  parser.add_argument('output', help='output html filename')
  args = parser.parse_args()

  benchmark_info, data = parse_csv_input(args.input)

  with open(args.output, 'w') as htmlfile:
    htmlfile.write(generate_result(benchmark_info, data))


# -----------------
# Templates below

MAIN_TEMPLATE = """<!doctype html>
<html lang="en-US">
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
  <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
  <script>{jsdeps}</script>
  <title>MLTS results</title>
  <style>
    .results {{
      border-collapse: collapse;
      width: 100%;
    }}
    .results td, .results th {{
      border: 1px solid #ddd;
      padding: 6px;
    }}
    .results tr:nth-child(even) {{background-color: #eee;}}
    .results tr:hover {{background-color: #ddd;}}
    .results th {{
      padding: 10px;
      font-weight: bold;
      text-align: left;
      background-color: #333;
      color: white;
    }}
  </style>
</head>
<body>
{device_info}
{results_list}
</body>
</html>"""

DEVICE_INFO_TEMPLATE = """<div id="device_info">
Benchmark for {device_info}, started at {benchmark_time}
</div>"""


RESULT_GROUP_TEMPLATE = """<div>
<table class="results">
 <tr>
   <th>Name</th>
   <th>Backend</th>
   <th>Iterations</th>
   <th>Test set size</th>
   <th>Average latency ms</th>
   {accuracy_headers}
   <th>Latency frequency</th>
 </tr>
 {results}
</table>
</div>"""


RESULT_ENTRY_TEMPLATE = """
  <tr>
   <td>{name}</td>
   <td>{backend}</td>
   <td>{iterations:d}</td>
   <td>{testset_size:d}</td>
   <td>{avg_ms:.2f}ms</td>
   {accuracy_values}
   <td class="container" style="width: 500px;">
    <canvas id="latency_chart{i}" class="latency_chart"></canvas>
  </td>
 </tr>
 <script>
   $(function() {{
       var freqData = {{
         labels: {freq_data[0]},
         datasets: [{{
            label: '{name} latency frequency',
            data: {freq_data[1]},
            backgroundColor: 'rgba(255, 99, 132, 0.6)',
            borderColor:  'rgba(255, 0, 0, 0.6)',
            borderWidth: 1,
         }}]
       }};
       var ctx = $('#latency_chart{i}')[0].getContext('2d');
       window.latency_chart{i} = new Chart(ctx,
        {{
          type: 'bar',
          data: freqData,
          options: {{
           responsive: true,
           title: {{
             display: true,
             text: 'Latency frequency'
           }},
           legend: {{
             display: false
           }},
           scales: {{
            xAxes: [ {{
              barPercentage: 1.0,
              categoryPercentage: 0.9,
            }}],
            yAxes: [{{
              scaleLabel: {{
                display: true,
                labelString: 'Iterations Count'
              }}
            }}]
           }}
         }}
       }});
     }});
  </script>"""


ACCURACY_HEADERS_TOPK_TEMPLATE = """
<th>Top 1</th>
<th>Top 2</th>
<th>Top 3</th>
<th>Top 4</th>
<th>Top 5</th>
"""

ACCURACY_VALUES_TOPK_TEMPLATE = """
<td>{top1:.3f}%</td>
<td>{top2:.3f}%</td>
<td>{top3:.3f}%</td>
<td>{top4:.3f}%</td>
<td>{top5:.3f}%</td>
"""

ACCURACY_HEADERS_MELCEPLOGF0_TEMPLATE = """
<th>Max log(F0) error</th>
<th>Max Mel Cep distortion</th>
<th>Max scalar error</th>
"""

ACCURACY_VALUES_MELCEPLOGF0_TEMPLATE = """
<td>{max_log_f0:.2E}</td>
<td>{max_mel_cep_distortion:.2E}</td>
<td>{max_single_error:.2E}</td>
"""


ACCURACY_HEADERS_BASIC_TEMPLATE = """
<th>Max single scalar error</th>
"""


ACCURACY_VALUES_BASIC_TEMPLATE = """
<td>{max_single_error:.2f}</td>
"""


CHART_JS_FILE = "Chart.bundle.min.js"

if __name__ == '__main__':
  main()
