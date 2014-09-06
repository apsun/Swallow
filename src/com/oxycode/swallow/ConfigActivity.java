package com.oxycode.swallow;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ToggleButton;

public class ConfigActivity extends Activity {
    private static final String TAG = "SWAL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final SharedPreferences preferences = getSharedPreferences("LoginCredentials", MODE_PRIVATE);

        EditText usernameTextBox = (EditText)findViewById(R.id.username_edittext);
        EditText passwordTextBox = (EditText)findViewById(R.id.password_edittext);
        ToggleButton autoLoginToggleButton = (ToggleButton)findViewById(R.id.autologin_togglebutton);
        Button profileManagerButton = (Button)findViewById(R.id.profile_manager_button);

        usernameTextBox.setText(preferences.getString("username", null));
        passwordTextBox.setText(preferences.getString("password", null));
        autoLoginToggleButton.setChecked(getReceiverEnabled());

        usernameTextBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                String username = s.toString();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("username", username);
                editor.apply();
            }
        });

        passwordTextBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("password", password);
                editor.apply();
            }
        });

        autoLoginToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean b) {
                setReceiverEnabled(b);
            }
        });

        profileManagerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ConfigActivity.this, ProfileManagerActivity.class);
                startActivity(intent);
            }
        });
    }

    private void setReceiverEnabled(boolean enabled) {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, status, PackageManager.DONT_KILL_APP);
    }

    private boolean getReceiverEnabled() {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = packageManager.getComponentEnabledSetting(componentName);
        switch (status) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                return false;
        }

        Log.w(TAG, "Unknown receiver state: " + status);
        return false;
    }
}
