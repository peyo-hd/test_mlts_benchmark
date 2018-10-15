#!/bin/bash
#
# Build benchmark app and run it, mimicking a user-initiated run
#
# Output is logged to a temporary folder and summarized in txt and JSON formats.
#
# Parameters
# - number of runs

if [[ -z "$ANDROID_BUILD_TOP" ]]; then
  echo ANDROID_BUILD_TOP not set, bailing out
  echo you must run lunch before running this script
  exit 1
fi

set -e
cd $ANDROID_BUILD_TOP

LOGDIR=$(mktemp -d)/mlts-logs
mkdir -p $LOGDIR
echo Creating logs in $LOGDIR

# Build and install benchmark app
make NeuralNetworksApiBenchmark
adb -d install -r $OUT/data/app/NeuralNetworksApiBenchmark/NeuralNetworksApiBenchmark.apk

# Should we figure out if we run on release device
if [ -z "$MLTS_RELEASE_DEVICE" ]; then
  BUILD_DESCRIPTION=`adb -d shell getprop ro.build.description`
  if [[ $BUILD_DESCRIPTION =~ .*release.* ]]
  then
    MLTS_RELEASE_DEVICE=True
  else
    MLTS_RELEASE_DEVICE=False
  fi
fi

if [[ "$MLTS_RELEASE_DEVICE" == "True" ]]; then
  TEST_EXTENRAL_STORAGE="com.android.nn.benchmark.app/com.android.nn.benchmark.util.TestExternalStorageActivity"
  while ! adb shell "am start -W $TEST_EXTENRAL_STORAGE && rm /sdcard/mlts_write_external_storage" > /dev/null 2>&1; do
     echo "************************************************************"
     echo "Grant External storage write permissions to MLTS to proceed!"
     echo "************************************************************"
     read -n 1 -r -p "Continue? (press any key)"
     echo
  done
else
  adb -d root
  # Skip setup wizard and remount (read-write)
  if ! adb -d shell test -f /data/local.prop; then
    adb -d shell 'echo ro.setupwizard.mode=DISABLED > /data/local.prop'
    adb -d shell 'chmod 644 /data/local.prop'
    adb -d shell 'settings put global device_provisioned 1*'
    adb -d shell 'settings put secure user_setup_complete 1'
    adb -d disable-verity
    adb -d reboot
    sleep 5
    adb wait-for-usb-device remount
  fi
  set +e
  # Enable menu key press through adb
  adb -d shell 'echo testing > /data/local/enable_menu_key'
  # Leave screen on (affects scheduling)
  adb -d shell settings put system screen_off_timeout 86400000
  # Stop background apps, seem to take ~10% CPU otherwise
  adb -d shell 'pm disable com.google.android.googlequicksearchbox'
  adb shell 'pm list packages -f' | sed -e 's/.*=//' | sed 's/\r//g' | grep "com.breel.wallpapers" | while read pkg; do adb -d shell "pm disable $pkg"; done;
  set -e
fi

adb -d shell setprop debug.nn.cpuonly 0
adb -d shell setprop debug.nn.vlog 0

HOST_CSV=$LOGDIR/benchmark.csv
RESULT_HTML=$LOGDIR/result.html
DEVICE_CSV=/sdcard/mlts_benchmark.csv

# Menukey - make sure screen is on
adb shell "input keyevent 82"
# Show homescreen
adb shell wm dismiss-keyguard
# Remove old benchmark csv data
adb shell rm -f ${DEVICE_CSV}
# Set the shell pid as a top-app and run tests
adb shell 'echo $$ > /dev/stune/top-app/tasks; am instrument -w -e size large -e class com.android.nn.benchmark.app.NNScoringTest com.android.nn.benchmark.app/android.support.test.runner.AndroidJUnitRunner'
adb pull $DEVICE_CSV $HOST_CSV
echo Benchmark data saved in $HOST_CSV

$ANDROID_BUILD_TOP/test/mlts/benchmark/results/generate_result.py $HOST_CSV $RESULT_HTML
echo Results stored  in $RESULT_HTML
xdg-open $RESULT_HTML
