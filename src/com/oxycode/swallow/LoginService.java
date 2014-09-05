package com.oxycode.swallow;

import android.app.IntentService;
import android.content.Intent;
import android.os.Handler;
import android.widget.Toast;

import java.io.IOException;

public class LoginService extends IntentService {
    private static final String TAG = "SWAL";
    private final Handler _handler;

    public LoginService() {
        super(TAG);
        _handler = new Handler();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String username = intent.getStringExtra("username");
        final String password = intent.getStringExtra("password");

        final LoginResult result;
        try {
            result = LoginClient.login(username, password);
        } catch (IOException e) {
            // Probably disconnected from the network
            // TODO: Should retry a few times before showing a notification
            // TODO: When user opens notification, try connecting again
            return;
        }

        // Notify result
        _handler.post(new Runnable() {
            @Override
            public void run() {
                String message;
                switch (result) {
                    case SUCCESS:
                        message = String.format(getResources().getString(R.string.login_success), username);
                    case INCORRECT_CREDENTIALS:
                        message = getResources().getString(R.string.login_fail_incorrect_credentials);
                    case ACCOUNT_BANNED:
                        message = getResources().getString(R.string.login_fail_account_banned);
                    default:
                        message = getResources().getString(R.string.login_fail_unknown_error);
                }

                Toast.makeText(LoginService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
