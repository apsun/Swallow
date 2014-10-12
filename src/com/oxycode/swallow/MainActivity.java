package com.oxycode.swallow;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getName();

    private Switch _enabledSwitch;
    private EditText _usernameTextBox;
    private EditText _passwordTextBox;
    private Button _saveCredentialsButton;
    private Button _profileManagerButton;
    private Button _settingsButton;

    // TODO: Add warning when leaving activity/performing login without saving credentials
    private boolean _credentialsModified;

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
        // TODO: TEMP CODE
        _profileManagerButton.setEnabled(false);
        _settingsButton = (Button)findViewById(R.id.settings_button);

        _usernameTextBox.setText(_preferences.getString(LoginService.PREF_USERNAME_KEY, null));
        _passwordTextBox.setText(_preferences.getString(LoginService.PREF_PASSWORD_KEY, null));

        TextWatcher credentialsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                _credentialsModified = true;
            }
        };

        _usernameTextBox.addTextChangedListener(credentialsWatcher);
        _passwordTextBox.addTextChangedListener(credentialsWatcher);

        _saveCredentialsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = _usernameTextBox.getText().toString();
                String password = _passwordTextBox.getText().toString();
                SharedPreferences.Editor editor = _preferences.edit();
                editor.putString(LoginService.PREF_USERNAME_KEY, username);
                editor.putString(LoginService.PREF_PASSWORD_KEY, password);
                editor.apply();
                _credentialsModified = false;
                Toast.makeText(MainActivity.this, R.string.credentials_saved, Toast.LENGTH_SHORT).show();
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
