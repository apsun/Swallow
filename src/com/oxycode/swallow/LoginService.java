package com.oxycode.swallow;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String RETRY_COUNT = "retry_count";
    public static final String SHOW_NOTIFICATIONS = "show_notifications";
    public static final String USE_TOAST = "use_toast";

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
        String username = intent.getStringExtra(USERNAME);
        String password = intent.getStringExtra(PASSWORD);
        int retries = intent.getIntExtra(RETRY_COUNT, 0);
        boolean showNotification = intent.getBooleanExtra(SHOW_NOTIFICATIONS, false);
        boolean useToast = intent.getBooleanExtra(USE_TOAST, false);

        LoginResult result = loginWithRetries(username, password, retries);

        if (showNotification) {
            if (useToast) {
                notifyResultToast(username, result);
            } else {

            }
        }
    }
}
