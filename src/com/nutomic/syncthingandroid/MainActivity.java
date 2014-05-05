package com.nutomic.syncthingandroid;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";

	private static final File SYNCTHING_FOLDER =
			new File(Environment.getExternalStorageDirectory(), "syncthing");

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SYNCTHING_FOLDER.isDirectory() && !SYNCTHING_FOLDER.mkdir()) {
			Log.w(TAG, "Failed to create syncthing folder on sdcard, exiting");
			finish();
			return;
        }
		System.loadLibrary("syncthing");
	}
	
}
