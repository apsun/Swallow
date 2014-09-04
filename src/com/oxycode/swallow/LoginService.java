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
        String username = intent.getStringExtra("username");
        String password = intent.getStringExtra("password");

        LoginClient client = new LoginClient();

        final boolean result;
        try {
            result = client.login(username, password);
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
                if (result) {
                    message = "Logged in to shs network!";
                } else {
                    // TODO: Should this be a notification or a toast message?
                    message = "Login to shs network failed, check your password!";
                }
                Toast.makeText(LoginService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
