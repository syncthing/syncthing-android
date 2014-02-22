package com.nutomic.syncthingandroid;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		System.loadLibrary("syncthing");
	}
	
}
