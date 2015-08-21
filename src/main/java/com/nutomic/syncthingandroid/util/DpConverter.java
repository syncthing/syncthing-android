package com.nutomic.syncthingandroid.util;

import android.content.Context;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.applyDimension;

public abstract class DpConverter {

    /**
     * Converts dips to pixels.
     *
     * @param dp Number of dps
     * @param c  The context to convert in.
     * @return Number of pixels that equal dp in context.
     */
    public static int dp(int dp, Context c) {
        return (int) applyDimension(COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }
}