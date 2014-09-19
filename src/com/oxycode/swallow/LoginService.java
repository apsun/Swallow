package com.oxycode.swallow;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();
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
        String username = intent.getStringExtra("username");
        String password = intent.getStringExtra("password");
        int retries = intent.getIntExtra("retries", 0);
        boolean showNotification = intent.getBooleanExtra("show_notification", false);
        boolean useToast = intent.getBooleanExtra("use_toast", false);

        LoginResult result = loginWithRetries(username, password, retries);

        if (showNotification) {
            if (useToast) {
                notifyResultToast(username, result);
            } else {

            }
        }
    }
}
