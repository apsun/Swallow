package com.oxycode.swallow;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();
    private static final String PREF_AUTO_LOGIN_ENABLED_KEY = "pref_auto_login_enabled";
    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_ENABLE_STATUS_NOTIFICATIONS_KEY = "pref_enable_status_notifications";
    private static final String PREF_USE_TOAST_MESSAGES_KEY = "pref_use_toast_messages";
    private static final String PREF_ENABLED_TOAST_MESSAGES_KEY = "pref_enabled_toast_messages";

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    private final Handler _handler;

    public LoginService() {
        super(TAG);

        _handler = new Handler();
    }

    private void notifyResultToast(final String username, final LoginResult result) {
        /*_handler.post(new Runnable() {
            @Override
            public void run() {
                String message;
                switch (result) {
                    case SUCCESS:
                        message = String.format(getResources().getString(R.string.login_success), username);
                        break;
                    case INCORRECT_CREDENTIALS:
                        message = getResources().getString(R.string.login_fail_incorrect_credentials);
                        break;
                    case ACCOUNT_BANNED:
                        message = getResources().getString(R.string.login_fail_account_banned);
                        break;
                    case CONNECTION_FAILED:
                        message = getResources().getString()
                    default:
                        message = getResources().getString(R.string.login_fail_unknown_error);
                        break;
                }

                Toast.makeText(LoginService.this, message, Toast.LENGTH_SHORT).show();
            }
        });*/
    }

    private LoginResult loginWithRetries(String username, String password, int retries) {
        do {
            try {
                return LoginClient.login(username, password);
            } catch (IOException e) {
                Log.e(TAG, "Login failed, retrying " + retries + " more time(s)", e);
            }
        } while (--retries >= 0);
        return LoginResult.CONNECTION_FAILED;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // make sure we have credentials (username + password)
        // get preferences (retry count, notification method, etc)
        // if use toast:
        //   show begin login toast
        // else:
        //   create ongoing notification
        // do:
        //   try:
        //     login()
        //   except IOException:
        //     if use toast:
        //        show retrying notification
        //     else:
        //        update ongoing notification
        // while retry

        /*SharedPreferences credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);

        String username = credentials.getString(PREF_USERNAME_KEY, null);
        String password = credentials.getString(PREF_PASSWORD_KEY, null);

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            return;
        }

        int retryCount = Integer.parseInt(preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, null));
        boolean showNotifications = preferences.getBoolean(PREF_ENABLE_STATUS_NOTIFICATIONS_KEY, false);
        boolean useToast = preferences.getBoolean(PREF_USE_TOAST_MESSAGES_KEY, false);
        Set<String> enabledToastMessages = preferences.getStringSet(PREF_ENABLED_TOAST_MESSAGES_KEY, null);


        LoginResult result = loginWithRetries(username, password, retryCount);

        if (showNotifications) {
            if (useToast) {
                notifyResultToast(username, result);
            } else {

            }
        }*/
    }
}
