package com.nutomic.syncthingandroid.util;

/**
 * Adds an interface for the back button click.
 * Useful for handling back button events in a PreferenceFragment.
 */
public interface BackButtonHandlerInterface {
    void addBackClickListener (OnBackClickListener onBackClickListener);
    void removeBackClickListener (OnBackClickListener onBackClickListener);
}
