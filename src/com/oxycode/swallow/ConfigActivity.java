package com.oxycode.swallow;

import android.app.Activity;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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

        final EditText usernameTextBox = (EditText)findViewById(R.id.usernameTextBox);
        final EditText passwordTextBox = (EditText)findViewById(R.id.passwordTextBox);
        // final Button saveCredentialsButton = (Button)findViewById(R.id.saveCredentialsButton);
        final ToggleButton autoLoginToggle = (ToggleButton)findViewById(R.id.autoLoginToggle);

        usernameTextBox.setText(preferences.getString("username", null));
        passwordTextBox.setText(preferences.getString("password", null));
        autoLoginToggle.setChecked(getReceiverEnabled());

        /*saveCredentialsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = usernameTextBox.getText().toString();
                String password = passwordTextBox.getText().toString();

                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("username", username);
                editor.putString("password", password);
                editor.commit();
            }
        });*/

        autoLoginToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean b) {
                // TODO: Check if username and password are set correctly
                setReceiverEnabled(b);
            }
        });
    }

    private void setReceiverEnabled(boolean enabled) {
        PackageManager packageManager = ConfigActivity.this.getPackageManager();
        ComponentName componentName = new ComponentName(ConfigActivity.this, WifiStateReceiver.class);
        int status = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, status, PackageManager.DONT_KILL_APP);
    }

    private boolean getReceiverEnabled() {
        PackageManager packageManager = ConfigActivity.this.getPackageManager();
        ComponentName componentName = new ComponentName(ConfigActivity.this, WifiStateReceiver.class);
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
