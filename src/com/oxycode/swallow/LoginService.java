package com.oxycode.swallow;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.util.Set;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();
    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_ENABLE_STATUS_NOTIFICATIONS_KEY = "pref_enable_status_notifications";
    private static final String PREF_USE_TOAST_MESSAGES_KEY = "pref_use_toast_messages";

    private static final String PREF_ENABLED_TOAST_MESSAGES_KEY = "pref_enabled_toast_messages";
    private static final String PREF_ENABLED_TOAST_MESSAGES_BEGIN_KEY = "login_start";
    private static final String PREF_ENABLED_TOAST_MESSAGES_RETRY_KEY = "login_retry";
    private static final String PREF_ENABLED_TOAST_MESSAGES_FAILED_KEY = "login_failed";
    private static final String PREF_ENABLED_TOAST_MESSAGES_SUCCEEDED_KEY = "login_succeeded";

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    private final Handler _handler;

    public LoginService() {
        super(TAG);

        _handler = new Handler();
    }

    private LoginClient.Result login(String username, String password, int trialCount) {
        return LoginClient.login(username, password, trialCount, new LoginClient.Handler() {
            @Override
            public void onException(IOException e, int remainingTrialCount) {
                Log.e(TAG, "Login failed, retrying " + remainingTrialCount + " more time(s)", e);
                // TODO: Display toast message/notification
            }
        });
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);

        String username = credentials.getString(PREF_USERNAME_KEY, null);
        String password = credentials.getString(PREF_PASSWORD_KEY, null);

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            // TODO: Do we want to display a notification here?
            return;
        }

        int retryCount = Integer.parseInt(preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, null));
        boolean showNotifications = preferences.getBoolean(PREF_ENABLE_STATUS_NOTIFICATIONS_KEY, false);
        boolean useToast = preferences.getBoolean(PREF_USE_TOAST_MESSAGES_KEY, false);
        Set<String> enabledToastMessages = preferences.getStringSet(PREF_ENABLED_TOAST_MESSAGES_KEY, null);

        // TODO (TOAST_MODE & SHOW_BEGIN): Show begin login toast message
        // TODO (NOTIFICATION_MODE): Create ongoing notification

        LoginClient.Result result = LoginClient.login(username, password, retryCount + 1, new LoginClient.Handler() {
            @Override
            public void onException(IOException e, int remainingTrialCount) {
                Log.e(TAG, "Login failed, retrying " + remainingTrialCount + " more time(s)", e);
                // TODO (TOAST_MODE & SHOW_RETRY): Show retry login toast message
                // TODO (NOTIFICATION_MODE): Update ongoing notification
            }
        });

        switch (result) {
            case SUCCESS:
                break;
            case INCORRECT_CREDENTIALS:
                break;
            case ACCOUNT_BANNED:
                break;
            case EXCEEDED_MAX_RETRIES:
                break;
        }
    }
}
