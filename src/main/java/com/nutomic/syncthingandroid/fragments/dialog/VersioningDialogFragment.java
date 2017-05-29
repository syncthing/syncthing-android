package com.nutomic.syncthingandroid.fragments.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import com.nutomic.syncthingandroid.R;

import java.util.Arrays;
import java.util.List;

/**
 * Allows the user to switch between file versioning types and displays the appropriate configuration options for each type.
 */

public class VersioningDialogFragment extends android.support.v4.app.DialogFragment {

    View mView;
    Spinner mVersioningTypeSpinner;

    Fragment mCurrentFragment;

    VersioningDialogInterface mVersioningDialogInterface;

    List<String> mTypes = Arrays.asList( "none", "simple", "trashcan", "staggered", "external");

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_versioning_dialog, container);
        mVersioningDialogInterface = (VersioningDialogInterface) getActivity();

        initiateFinishBtn();
        initiateSpinner();

        return mView;
    }

    private void initiateFinishBtn() {
        Button finishBtn = (Button) mView.findViewById(R.id.finish_btn);
        finishBtn.setOnClickListener(v -> getDialog().dismiss());
    }

    private void initiateSpinner() {
        mVersioningTypeSpinner = (Spinner) mView.findViewById(R.id.versioningTypeSpinner);
        mVersioningTypeSpinner.setSelection(mTypes.indexOf(getArguments().getString("type")));
        mVersioningTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateVersioningType(position);
                updateFragmentView(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void updateVersioningType(int position) {
        getArguments().putString("type", mTypes.get(position).toLowerCase());
    }

    private void updateFragmentView(int selection) {
        mCurrentFragment = getFragment(selection);
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        //This Fragment (VersioningDialogFragment) contains all the file versioning parameters that have been passed from the FolderActivity, so we simply
        // pass that to the currentfragment.
        mCurrentFragment.setArguments(getArguments());
        transaction.replace(R.id.versioningFragmentContainer, mCurrentFragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }
    
    private Fragment getFragment(int selection) {
        Fragment fragment = null;
        switch (selection) {
            case 0:
                fragment = new NoVersioningFragment();
                break;
            case 1:
                fragment = new SimpleVersioningFragment();
                break;
            case 2:
                fragment = new TrashCanVersioningFragment();
                break;
            case 3:
                fragment = new StaggeredVersioningFragment();
                break;
            case 4:
                fragment = new ExternalVersioningFragment();
                break;
        }
        
        return fragment;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        //The current fragment contains the file versioning parameters that have been set by the user in its arguments, so we pass that
        // to the FolderActivity through the VersioningDialogInterface.
        mVersioningDialogInterface.onDialogClosed(mCurrentFragment.getArguments());
        super.onDismiss(dialog);
    }

    public interface VersioningDialogInterface {
         void onDialogClosed(Bundle arguments);
    }
}
