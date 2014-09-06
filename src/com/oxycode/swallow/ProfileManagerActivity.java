package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.*;
import android.widget.EditText;

public class ProfileManagerActivity extends Activity {
    private static final String TAG = "SWAL";

    private SharedPreferences _preferences;

    private interface OnSaveProfileNameListener {
        void onSave(String name);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_manager);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        _preferences = getSharedPreferences("NetworkProfiles", MODE_PRIVATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showNetworkScanner(NetworkProfile profile) {
        Intent intent = new Intent(this, NetworkScannerActivity.class);
        intent.putExtra("profile", profile);
        startActivity(intent);
    }

    private void showSetProfileNameDialog(final OnSaveProfileNameListener onSave) {
        LayoutInflater inflater = LayoutInflater.from(this);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        View promptView = inflater.inflate(R.layout.set_profile_name_dialog, null);

        final EditText profileNameEditText = (EditText)promptView.findViewById(R.id.add_profile_dialog_edittext);

        alertDialogBuilder
            .setView(promptView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String profileName = profileNameEditText.getText().toString();
                        onSave.onSave(profileName);
                    }
                })
            .setNegativeButton(getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        AlertDialog alertDialog = alertDialogBuilder.create();
        // alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alertDialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.add_profile_button:
                showSetProfileNameDialog(new OnSaveProfileNameListener() {
                    @Override
                    public void onSave(String name) {
                        // TODO: Check profile name for conflicts
                        NetworkProfile profile = new NetworkProfile(name);
                        showNetworkScanner(profile);
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
