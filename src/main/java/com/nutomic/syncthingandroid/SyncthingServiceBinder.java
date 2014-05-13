package com.nutomic.syncthingandroid;

import android.os.Binder;

public class SyncthingServiceBinder extends Binder {

	SyncthingService mService;

	public SyncthingServiceBinder(SyncthingService service) {
		mService = service;
	}

	public SyncthingService getService() {
		return mService;
	}

}