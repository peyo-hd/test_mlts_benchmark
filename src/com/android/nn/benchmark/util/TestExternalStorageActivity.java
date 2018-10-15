package com.android.nn.benchmark.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Helper class for testing and requesting WRITE_EXTERNAL_STORAGE permission
 *
 * If run sucessfuly, it will create /sdcard/mlts_write_external_storage file.
 */
public class TestExternalStorageActivity extends Activity {
    private static final String TAG = TestExternalStorageActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (testWriteExternalStorage(this)) {
            try {
                new File(Environment.getExternalStorageDirectory(),
                        "mlts_write_external_storage").createNewFile();
            } catch (IOException e) {
                Log.e(TAG, "Failed to create a file", e);
            }
        }
        finish();
    }

    public static boolean testWriteExternalStorage(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                activity.requestPermissions(
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        return true;
    }
}
