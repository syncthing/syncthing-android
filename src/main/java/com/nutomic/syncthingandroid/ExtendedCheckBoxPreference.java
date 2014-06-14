package com.nutomic.syncthingandroid;

import android.content.Context;
import android.preference.CheckBoxPreference;

/**
 * Saves an extra object on construction, which can be retrieved later.
 */
public class ExtendedCheckBoxPreference extends CheckBoxPreference {

	private final Object mObject;

	public ExtendedCheckBoxPreference(Context context, Object object) {
		super(context);
		mObject = object;
	}

	public Object getObject() {
		return mObject;
	}

}
