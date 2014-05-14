package com.nutomic.syncthingandroid.service;

import android.os.Binder;

import com.nutomic.syncthingandroid.service.SyncthingService;

public class SyncthingServiceBinder extends Binder {

	SyncthingService mService;

	public SyncthingServiceBinder(SyncthingService service) {
		mService = service;
	}

	public SyncthingService getService() {
		return mService;
	}

}