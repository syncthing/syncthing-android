package com.nutomic.syncthingandroid.fragments.dialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.FrameLayout.LayoutParams;
import android.widget.NumberPicker;

import com.nutomic.syncthingandroid.R;

import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class KeepVersionsDialogFragment extends DialogFragment {

    private static String KEEP_VERSION_VALUE = "KEEP_VERSION_KEY";

    private OnValueChangeListener mOnValueChangeListener;

    private NumberPicker mNumberPickerView;

    private int mValue;

    private final DialogInterface.OnClickListener mDialogButtonListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    mValue = mNumberPickerView.getValue();
                    if (mOnValueChangeListener != null)
                        mOnValueChangeListener.onValueChange(mValue);
                    break;
            }
        }
    };

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            setValue(savedInstanceState.getInt(KEEP_VERSION_VALUE));
        }

        mNumberPickerView = createNumberPicker();
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.keep_versions)
                .setView(mNumberPickerView)
                .setPositiveButton(android.R.string.ok, mDialogButtonListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEEP_VERSION_VALUE, mNumberPickerView.getValue());
    }

    public void setOnValueChangeListener(OnValueChangeListener onValueChangeListener) {
        mOnValueChangeListener = onValueChangeListener;
    }

    public void setValue(int value) {
        this.mValue = value;

        if (mNumberPickerView != null) {
            mNumberPickerView.setValue(value);
        }
    }

    private NumberPicker createNumberPicker() {
        NumberPicker picker = new NumberPicker(getActivity());
        picker.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, CENTER));
        picker.setMinValue(0);
        picker.setMaxValue(5);
        picker.setValue(mValue);
        picker.setWrapSelectorWheel(false);
        return picker;
    }

    public interface OnValueChangeListener {
        void onValueChange(int value);
    }
}
