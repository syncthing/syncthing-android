package com.nutomic.syncthingandroid.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.dialog.ExternalVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.NoVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.SimpleVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.StaggeredVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.TrashCanVersioningFragment;
import com.nutomic.syncthingandroid.fragments.dialog.VersioningDialogFragment;

import java.util.Arrays;
import java.util.List;

public class VersioningDialogActivity extends AppCompatActivity {

    Spinner mVersioningTypeSpinner;

    Fragment mCurrentFragment;

    VersioningDialogFragment.VersioningDialogInterface mVersioningDialogInterface;

    List<String> mTypes = Arrays.asList( "none", "simple", "trashcan", "staggered", "external");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_versioning_dialog);

        initiateFinishBtn();
        initiateSpinner();
    }


    private void initiateFinishBtn() {
        Button finishBtn = (Button) findViewById(R.id.finish_btn);
        finishBtn.setOnClickListener(v -> this.finish());
    }

    private void initiateSpinner() {
        mVersioningTypeSpinner = (Spinner) findViewById(R.id.versioningTypeSpinner);
        mVersioningTypeSpinner.setSelection(mTypes.indexOf(getIntent().getExtras().getString("type")));
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
       getIntent().getExtras().putString("type", mTypes.get(position).toLowerCase());
    }

    private void updateFragmentView(int selection) {
        mCurrentFragment = getFragment(selection);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        //This Fragment (VersioningDialogFragment) contains all the file versioning parameters that have been passed from the FolderActivity, so we simply
        // pass that to the currentfragment.
        mCurrentFragment.setArguments(getIntent().getExtras());
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
    protected void onStop() {
        mVersioningDialogInterface.onDialogClosed(mCurrentFragment.getArguments());
        super.onStop();
    }

    public interface VersioningDialogInterface {
        void onDialogClosed(Bundle arguments);
    }

}
