package com.oxycode.swallow;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.*;
import com.oxycode.swallow.utils.DialogUtils;

import java.io.IOException;

public class MainActivity extends Activity {
    private class CheckCredentialsTask extends AsyncTask<Void, Void, LoginClient.LoginResult> {
        private final String _username;
        private final String _password;
        private final ProgressDialog _dialog;

        public CheckCredentialsTask(String username, String password) {
            _username = username;
            _password = password;
            ProgressDialog dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage(getString(R.string.checking_credentials_message));
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            _dialog = dialog;
        }

        protected LoginClient.LoginResult doInBackground(Void... params) {
            return LoginClient.login(_username, _password, new LoginClient.Handler() {
                @Override
                public boolean onException(IOException e) {
                    return false;
                }
            });
        }

        @Override
        protected void onPreExecute() {
            _dialog.show();
        }

        @Override
        protected void onPostExecute(LoginClient.LoginResult result) {
            _dialog.dismiss();

            switch (result) {
                case SUCCESS:
                    saveCredentials(_username, _password);
                    break;
                case INCORRECT_CREDENTIALS:
                case ACCOUNT_BANNED:
                case EXCEEDED_MAX_RETRIES:
                case UNKNOWN:
                    showConfirmSaveDialog(result, _username, _password);
                    break;
            }
        }
    }

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String INST_USERNAME = "username";
    private static final String INST_PASSWORD = "password";

    private SharedPreferences _credentials;
    private EditText _usernameTextBox;
    private EditText _passwordTextBox;
    private AsyncTask<?, ?, ?> _runningTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        _credentials = getSharedPreferences(LoginService.PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);

        _usernameTextBox = (EditText)findViewById(R.id.username_edittext);
        _passwordTextBox = (EditText)findViewById(R.id.password_edittext);

        if (savedInstanceState != null) {
            _usernameTextBox.setText(savedInstanceState.getString(INST_USERNAME));
            _passwordTextBox.setText(savedInstanceState.getString(INST_PASSWORD));
            Log.d(TAG, "Loaded instance state from bundle");
        } else {
            _usernameTextBox.setText(getPreferencesUsername());
            _passwordTextBox.setText(getPreferencesPassword());
            Log.d(TAG, "Loaded instance state from preferences");
        }

        Button saveCredentialsButton = (Button)findViewById(R.id.save_credentials_button);
        saveCredentialsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = getTextboxUsername();
                String password = getTextboxPassword();

                String preferencesUsername = getPreferencesUsername();
                String preferencesPassword = getPreferencesPassword();

                if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                    Toast.makeText(MainActivity.this, R.string.enter_credentials, Toast.LENGTH_SHORT).show();
                } else if (username.equals(preferencesUsername) && password.equals(preferencesPassword)) {
                    Toast.makeText(MainActivity.this, R.string.no_changes_detected, Toast.LENGTH_SHORT).show();
                } else {
                    _runningTask = new CheckCredentialsTask(username, password).execute();
                }
            }
        });

        Button profileManagerButton = (Button)findViewById(R.id.profile_manager_button);
        profileManagerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ProfileManagerActivity.class);
                startActivity(intent);
            }
        });

        Button settingsButton = (Button)findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        // Start the login service when starting the activity
        // This is useful when the user has just installed the
        // app, in case WiFi events don't happen any time soon
        if (getReceiverEnabled()) {
            Intent loginIntent = new Intent(this, LoginService.class);
            conditionalStartLoginService(loginIntent);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String username = getTextboxUsername();
        String password = getTextboxPassword();
        outState.putString(INST_USERNAME, username);
        outState.putString(INST_PASSWORD, password);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options_menu, menu);

        View switchView = menu.findItem(R.id.enable_switch_view).getActionView();
        Switch enabledSwitch = (Switch)switchView.findViewById(R.id.enable_auto_login_switch);
        enabledSwitch.setChecked(getReceiverEnabled());
        enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setReceiverEnabled(isChecked);
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        String username = getTextboxUsername();
        String password = getTextboxPassword();

        String prefUsername = getPreferencesUsername();
        String prefPassword = getPreferencesPassword();

        if (!username.equals(prefUsername) || !password.equals(prefPassword)) {
            showConfirmExitDialog();
        } else {
            exit();
        }
    }

    private String getTextboxUsername() {
        return _usernameTextBox.getText().toString();
    }

    private String getTextboxPassword() {
        return _passwordTextBox.getText().toString();
    }

    private String getPreferencesUsername() {
        return _credentials.getString(LoginService.PREF_KEY_USERNAME, "");
    }

    private String getPreferencesPassword() {
        return _credentials.getString(LoginService.PREF_KEY_PASSWORD, "");
    }

    private void saveCredentials(String username, String password) {
        SharedPreferences.Editor editor = _credentials.edit();
        editor.putString(LoginService.PREF_KEY_USERNAME, username);
        editor.putString(LoginService.PREF_KEY_PASSWORD, password);
        editor.apply();

        Toast.makeText(this, R.string.credentials_saved, Toast.LENGTH_SHORT).show();

        Log.d(TAG, "Saved login credentials");
    }

    private void exit() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
        }
        super.onBackPressed();
    }

    private void conditionalStartLoginService(Intent loginIntent) {
        WifiManager wifiManager = (WifiManager)getSystemService(WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            startService(loginIntent);
        }
    }

    private void setReceiverEnabled(boolean enabled) {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                             : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        packageManager.setComponentEnabledSetting(componentName, status, PackageManager.DONT_KILL_APP);
        Log.d(TAG, "Set receiver enabled -> " + enabled);

        int messageId = enabled ? R.string.enabled_autologin : R.string.disabled_autologin;
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();

        // Also start/stop the service as necessary.
        // For starting the service, only do so if WiFi is enabled.
        // When disabling the receiver, it doesn't matter what the
        // current WiFi state is, since the service must be stopped
        // either way.
        Intent loginIntent = new Intent(this, LoginService.class);
        if (enabled) {
            conditionalStartLoginService(loginIntent);
        } else {
            stopService(loginIntent);
        }
    }

    private boolean getReceiverEnabled() {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, WifiStateReceiver.class);
        int status = packageManager.getComponentEnabledSetting(componentName);
        switch (status) {
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                // Note: this value must be synchronized with the
                // value defined in the manifest file.
                return true;
            default:
                Log.w(TAG, "Unknown receiver state: " + status);
                return false;
        }
    }

    private void showConfirmExitDialog() {
        DialogUtils.showConfirmationDialog(this,
            R.drawable.ic_action_warning,
            getString(R.string.unsaved_credentials_title),
            getString(R.string.unsaved_credentials_message),
            getString(R.string.exit),
            new DialogUtils.ConfirmationDialogHandler() {
                @Override
                public void onConfirm() {
                    exit();
                }
            }
        );
    }

    private void showConfirmSaveDialog(LoginClient.LoginResult result, final String username, final String password) {
        int titleId = 0;
        switch (result) {
            case ACCOUNT_BANNED:
                titleId = R.string.confirm_save_message_account_banned;
                break;
            case INCORRECT_CREDENTIALS:
                titleId = R.string.confirm_save_message_incorrect_credentials;
                break;
            case UNKNOWN:
                titleId = R.string.confirm_save_message_unknown_result;
                break;
            case EXCEEDED_MAX_RETRIES:
                titleId = R.string.confirm_save_message_exceeded_max_retries;
                break;
        }

        DialogUtils.showConfirmationDialog(this,
            R.drawable.ic_action_warning,
            getString(R.string.confirm_save_title),
            getString(titleId) + "\n\n" + getString(R.string.confirm_save_message_footer),
            getString(R.string.save),
            new DialogUtils.ConfirmationDialogHandler() {
                @Override
                public void onConfirm() {
                    saveCredentials(username, password);
                }
            }
        );
    }
}
