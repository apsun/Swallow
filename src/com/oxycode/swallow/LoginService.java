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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LoginService extends IntentService {
    private static final String TAG = LoginService.class.getName();

    public static final String FORCE_LOGIN_EXTRA = "force_login";
    public static final String IS_CONNECTED_EXTRA = "is_connected";
    public static final String NETWORK_SSID_EXTRA = "ssid";
    public static final String NETWORK_BSSID_EXTRA = "bssid";

    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_SHOW_LOGIN_PROMPT_KEY = "pref_show_login_prompt";
    private static final String PREF_SHOW_PROGRESS_NOTIFICATION_KEY = "pref_show_progress_notification";
    private static final String PREF_SHOW_ERROR_NOTIFICATION_KEY = "pref_show_error_notification";

    private static final int NOTI_LOGIN_PROGRESS_ID = 0xdcaeee;
    private static final int NOTI_LOGIN_ACTION_ID = 0x9f37ff;

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    private static final Set<Bssid> XMB_PROFILE;

    static {
        XMB_PROFILE = new HashSet<Bssid>(Arrays.asList(
            new Bssid("00:1f:41:27:62:69"),
            new Bssid("00:22:7f:18:2c:79"),
            new Bssid("00:22:7f:18:33:39"),
            new Bssid("00:22:7f:18:2f:e9"),
            new Bssid("00:22:7f:18:33:19"),
            new Bssid("00:22:7f:18:21:c9"),
            new Bssid("58:93:96:1b:8c:d9"),
            new Bssid("58:93:96:1b:91:e9"),
            new Bssid("58:93:96:1b:92:19"),
            new Bssid("58:93:96:1b:91:99"),
            new Bssid("58:93:96:1b:8e:99"),
            new Bssid("58:93:96:1b:91:49"),

            // TODO: TESTING BSSIDS
            new Bssid("c4:01:7c:39:4e:e9"),
            new Bssid("74:91:1a:2c:b4:79"),
            new Bssid("c4:01:7c:39:97:29")
        ));
    }

    public LoginService() {
        super(TAG);
    }

    private void performLogin() {
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
            notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, notification.build());
        }

        LoginClient.LoginResult result = LoginClient.login(username, password, retryCount + 1, new LoginClient.Handler() {
            @Override
            public void onException(IOException e, int remainingTrialCount) {
                // Update notification with retry state
                if (showProgressNotification && remainingTrialCount > 0) {
                    int messageId = (remainingTrialCount > 1) ? R.string.noti_logging_in_retrying_plural
                        : R.string.noti_logging_in_retrying_single;
                    notification.setContentText(String.format(getString(messageId), remainingTrialCount));
                    notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, notification.build());
                }
            }
        });

        // Remove progress notification
        if (showProgressNotification) {
            notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
        }

        // If login succeeded or no error notification is needed, remove notification and exit
        if (result == LoginClient.LoginResult.SUCCESS || !showErrorNotification) {
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
            case ACCOUNT_BANNED: // TODO: Why do we show main activity for banned account?
                Intent configIntent = new Intent(this, MainActivity.class);
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
                stackBuilder.addParentStack(MainActivity.class);
                stackBuilder.addNextIntent(configIntent);
                pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                break;
            case EXCEEDED_MAX_RETRIES:
            case UNKNOWN:
                Intent retryIntent = new Intent(this, LoginService.class);
                retryIntent.putExtra(LoginService.FORCE_LOGIN_EXTRA, true);
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
        notificationManager.notify(NOTI_LOGIN_ACTION_ID, notification.build());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean forceLogin = intent.getBooleanExtra(FORCE_LOGIN_EXTRA, false);
        if (forceLogin) {
            Log.d(TAG, "Performing login");
            performLogin();
            return;
        }

        boolean isConnected = intent.getBooleanExtra(IS_CONNECTED_EXTRA, true);
        if (!isConnected) {
            // TODO: What about the in-progress notifications?
            Log.d(TAG, "Disconnected from network, removing non-progress notifications");
            NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTI_LOGIN_ACTION_ID);
            return;
        }

        Bssid bssid = intent.getParcelableExtra(NETWORK_BSSID_EXTRA);
        if (!XMB_PROFILE.contains(bssid)) {
            Log.d(TAG, "BSSID not in profile, skipping login");
            NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTI_LOGIN_ACTION_ID);
            return;
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showLoginPrompt = preferences.getBoolean(PREF_SHOW_LOGIN_PROMPT_KEY, true);
        if (!showLoginPrompt) {
            Log.d(TAG, "Login prompt is disabled, directly logging in");
            performLogin();
            return;
        }

        Log.d(TAG, "Setting up delayed login notification");

        Intent loginIntent = new Intent(this, LoginService.class);
        loginIntent.putExtra(LoginService.FORCE_LOGIN_EXTRA, true);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, loginIntent, 0);

        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("LOGIN RAY READY")
            .setContentText("Tap to log into WiFi")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);
        notificationManager.notify(NOTI_LOGIN_ACTION_ID, notification.build());
    }
}
