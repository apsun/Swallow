package com.oxycode.swallow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String INST_USERNAME = "username";
    private static final String INST_PASSWORD = "password";

    private Switch _enabledSwitch;
    private EditText _usernameTextBox;
    private EditText _passwordTextBox;
    private Button _saveCredentialsButton;
    private Button _profileManagerButton;
    private Button _settingsButton;
    private SharedPreferences _preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        _preferences = getSharedPreferences(LoginService.PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        _usernameTextBox = (EditText)findViewById(R.id.username_edittext);
        _passwordTextBox = (EditText)findViewById(R.id.password_edittext);
        _saveCredentialsButton = (Button)findViewById(R.id.save_credentials_button);
        _profileManagerButton = (Button)findViewById(R.id.profile_manager_button);
        _settingsButton = (Button)findViewById(R.id.settings_button);

        if (savedInstanceState != null) {
            _usernameTextBox.setText(savedInstanceState.getString(INST_USERNAME));
            _passwordTextBox.setText(savedInstanceState.getString(INST_PASSWORD));
            Log.d(TAG, "Loaded instance state from bundle");
        } else {
            _usernameTextBox.setText(_preferences.getString(LoginService.PREF_USERNAME_KEY, null));
            _passwordTextBox.setText(_preferences.getString(LoginService.PREF_PASSWORD_KEY, null));
            Log.d(TAG, "Loaded instance state from preferences");
        }

        _saveCredentialsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get credentials from textboxes
                String username = _usernameTextBox.getText().toString();
                String password = _passwordTextBox.getText().toString();

                // Save credentials to preferences
                SharedPreferences.Editor editor = _preferences.edit();
                editor.putString(LoginService.PREF_USERNAME_KEY, username);
                editor.putString(LoginService.PREF_PASSWORD_KEY, password);
                editor.apply();

                // Show toast notifying user that the save was successful
                Toast.makeText(MainActivity.this, R.string.credentials_saved, Toast.LENGTH_SHORT).show();

                Log.d(TAG, "Saved login credentials");
            }
        });

        _profileManagerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ProfileManagerActivity.class);
                startActivity(intent);
            }
        });

        _settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String username = _usernameTextBox.getText().toString();
        String password = _passwordTextBox.getText().toString();
        outState.putString(INST_USERNAME, username);
        outState.putString(INST_PASSWORD, password);
        Log.d(TAG, "Saved instance state to bundle");
    }

    @Override
    public void onBackPressed() {
        String username = _usernameTextBox.getText().toString();
        String password = _passwordTextBox.getText().toString();

        String prefUsername = _preferences.getString(LoginService.PREF_USERNAME_KEY, null);
        String prefPassword = _preferences.getString(LoginService.PREF_PASSWORD_KEY, null);

        if (!username.equals(prefUsername) || !password.equals(prefPassword)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.unsaved_credentials_title))
                .setMessage(getString(R.string.unsaved_credentials_message))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
            AlertDialog alert = builder.create();
            alert.show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options_menu, menu);

        View switchView = menu.findItem(R.id.enable_switch_view).getActionView();
        _enabledSwitch = (Switch)switchView.findViewById(R.id.enable_auto_login_switch);
        _enabledSwitch.setChecked(getReceiverEnabled());
        _enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setReceiverEnabled(isChecked);
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    private void setReceiverEnabled(boolean enabled) {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                             : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, status, PackageManager.DONT_KILL_APP);
        Log.d(TAG, "Set receiver enabled -> " + enabled);
    }

    private boolean getReceiverEnabled() {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = packageManager.getComponentEnabledSetting(componentName);
        switch (status) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                // Note: this value must be synchronized with the value defined in
                // the manifest file. For some reason, if the default state is false,
                // we cannot retrieve the receiver info, so getting this value
                // programatically is not possible as of right now.
                return true;
            default:
                Log.w(TAG, "Unknown receiver state: " + status);
                return false;
        }
    }
}
