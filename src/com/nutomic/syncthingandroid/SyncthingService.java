package com.nutomic.syncthingandroid;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class SyncthingService extends Service {

	private static final String TAG = "SyncthingService";

	@Override
	public void onCreate() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				System.loadLibrary("syncthing");
			}
		}).start();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
