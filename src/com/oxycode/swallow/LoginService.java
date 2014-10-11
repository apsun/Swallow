package com.oxycode.swallow;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.io.IOException;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();
    public static final String NETWORK_BSSID_EXTRA = "bssid";
    public static final String CHECK_BSSID_EXTRA = "check_bssid";

    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_SHOW_PROGRESS_NOTIFICATION_KEY = "pref_show_progress_notification";
    private static final String PREF_SHOW_ERROR_NOTIFICATION_KEY = "pref_show_error_notification";

    private static final int NOTIFICATION_ID = 0xdcaeee;

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    public LoginService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean checkBssid = intent.getBooleanExtra(CHECK_BSSID_EXTRA, true);

        // Check that bssid is in a currently active profile
        if (checkBssid) {
            Bssid bssid = intent.getParcelableExtra(NETWORK_BSSID_EXTRA);
            // TODO: FINISH THIS
        }

        // Get login credentials from saved preferences
        SharedPreferences credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);
        String username = credentials.getString(PREF_USERNAME_KEY, null);
        String password = credentials.getString(PREF_PASSWORD_KEY, null);

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            // TODO: Do we want to display a notification here?
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final int retryCount = Integer.parseInt(preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, null));
        final boolean showProgressNotification = preferences.getBoolean(PREF_SHOW_PROGRESS_NOTIFICATION_KEY, true);
        final boolean showErrorNotification = preferences.getBoolean(PREF_SHOW_ERROR_NOTIFICATION_KEY, true);

        final NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon);

        if (showProgressNotification) {
            notification
                .setContentTitle(getString(R.string.noti_logging_in))
                .setContentText(String.format(getString(R.string.noti_logging_in_using_account), username))
                .setOngoing(true)
                .setProgress(0, 0, true);
            notificationManager.notify(NOTIFICATION_ID, notification.build());
        }

        LoginClient.LoginResult result = LoginClient.login(username, password, retryCount + 1, new LoginClient.Handler() {
            @Override
            public void onException(IOException e, int remainingTrialCount) {
                // Update notification with retry state
                if (showProgressNotification && remainingTrialCount > 0) {
                    int messageId = (remainingTrialCount > 1) ? R.string.noti_logging_in_retrying_plural
                                                              : R.string.noti_logging_in_retrying_single;
                    notification.setContentText(String.format(getString(messageId), remainingTrialCount));
                    notificationManager.notify(NOTIFICATION_ID, notification.build());
                }
            }
        });

        // If login succeeded or no error notification is needed, remove notification and exit
        if (result == LoginClient.LoginResult.SUCCESS || !showErrorNotification) {
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        // Something went wrong, display an error notification
        notification
            .setOngoing(false)
            .setContentTitle(getString(R.string.noti_login_failed))
            .setAutoCancel(true)
            .setProgress(0, 0, false);

        // Set intent to run on notification touch
        PendingIntent pendingIntent = null;
        switch (result) {
            case INCORRECT_CREDENTIALS:
            case ACCOUNT_BANNED:
                Intent configIntent = new Intent(this, MainActivity.class);
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
                stackBuilder.addParentStack(MainActivity.class);
                stackBuilder.addNextIntent(configIntent);
                pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            case EXCEEDED_MAX_RETRIES:
            case UNKNOWN:
                Intent retryIntent = new Intent(this, LoginService.class);
                pendingIntent = PendingIntent.getService(this, 0, retryIntent, 0);
                break;
        }
        notification.setContentIntent(pendingIntent);

        // Set body of notification
        int messageId = 0;
        switch (result) {
            case INCORRECT_CREDENTIALS:
                messageId = R.string.noti_login_failed_incorrect_credentials;
                break;
            case ACCOUNT_BANNED:
                messageId = R.string.noti_login_failed_account_banned;
                break;
            case EXCEEDED_MAX_RETRIES:
                messageId = R.string.noti_login_failed_exceeded_max_retries;
                break;
            case UNKNOWN:
                messageId = R.string.noti_login_failed_unknown;
                break;
        }
        notification.setContentText(getString(messageId));

        // Display the error notification
        notificationManager.notify(NOTIFICATION_ID, notification.build());
    }
}
