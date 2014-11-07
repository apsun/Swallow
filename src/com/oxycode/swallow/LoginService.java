package com.oxycode.swallow;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LoginService extends Service {
    private static final String TAG = LoginService.class.getSimpleName();

    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_SHOW_LOGIN_PROMPT_KEY = "pref_show_login_prompt";
    private static final String PREF_SHOW_PROGRESS_NOTIFICATION_KEY = "pref_show_progress_notification";
    private static final String PREF_SHOW_ERROR_NOTIFICATION_KEY = "pref_show_error_notification";

    private static final int NOTI_LOGIN_PROGRESS_ID = 0xdcaeee;
    private static final int NOTI_LOGIN_ACTION_ID = 0x9f37ff;

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    public static final String EXTRA_ACTION = "action";
    public static final int EXTRA_ACTION_DEFAULT = 0;
    // public static final int EXTRA_ACTION_CHECK = 1;
    public static final int EXTRA_ACTION_LOG_IN = 2;

    public static final String EXTRA_SSID = "ssid";
    public static final String EXTRA_BSSID = "bssid";

    private static final Set<String> XMB_PROFILE;

    private SharedPreferences.OnSharedPreferenceChangeListener _prefChangeListener;

    private SharedPreferences _preferences;
    private SharedPreferences _credentials;
    private WifiManager _wifiManager;
    private NotificationManager _notificationManager;

    // Preferences
    private int _retryCount;
    private boolean _showPromptNotification;
    private boolean _showProgressNotification;
    private boolean _showErrorNotification;
    private String _username;
    private String _password;

    static {
        XMB_PROFILE = new HashSet<String>(Arrays.asList(
            "00:1f:41:27:62:69",
            "00:22:7f:18:2c:79",
            "00:22:7f:18:33:39",
            "00:22:7f:18:2f:e9",
            "00:22:7f:18:33:19",
            "00:22:7f:18:21:c9",
            "58:93:96:1b:8c:d9",
            "58:93:96:1b:91:e9",
            "58:93:96:1b:92:19",
            "58:93:96:1b:91:99",
            "58:93:96:1b:8e:99",
            "58:93:96:1b:91:49"

            // TODO: TESTING BSSIDS
            // "c4:01:7c:39:4e:e9",
            // "74:91:1a:2c:b4:79",
            // "c4:01:7c:39:97:29"
        ));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        _notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        _preferences = PreferenceManager.getDefaultSharedPreferences(this);
        _credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);
        // Load database here
    }

    private void performLogin() {
        final String username = _username;
        final String password = _password;
        final int retryCount = _retryCount;
        final boolean showProgressNotification = _showProgressNotification;
        final boolean showErrorNotification = _showErrorNotification;
        final NotificationManager notificationManager = _notificationManager;

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            // TODO: Do we want to display a notification here?
            return;
        }

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
                retryIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
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
        _notificationManager.notify(NOTI_LOGIN_ACTION_ID, notification.build());
    }

    private void checkLoginStatus(String ssid, String bssid) {
        LoginClient.QueryResult result = LoginClient.getLoginStatus(_retryCount + 1, null);
        if (result == LoginClient.QueryResult.LOGGED_OUT) {
            if (_showPromptNotification) {
                Intent loginIntent = new Intent(this, LoginService.class);
                loginIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
                PendingIntent pendingIntent = PendingIntent.getService(this, 0, loginIntent, 0);

                NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(getString(R.string.noti_touch_to_log_in))
                    .setContentText(String.format("%s - %s", ssid, bssid))
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

                _notificationManager.notify(NOTI_LOGIN_ACTION_ID, notification.build());
            } else {
                performLogin();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action = intent.getIntExtra(EXTRA_ACTION, EXTRA_ACTION_DEFAULT);
        if (action == EXTRA_ACTION_DEFAULT) {
            String ssid = intent.getStringExtra(EXTRA_SSID);
            String bssid = intent.getStringExtra(EXTRA_BSSID);

            if (!XMB_PROFILE.contains(bssid)) {
                _notificationManager.cancel(NOTI_LOGIN_ACTION_ID);
            }

            checkLoginStatus(ssid, bssid);
        }

        if (action == EXTRA_ACTION_LOG_IN) {
            // Perform login
            performLogin();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        _notificationManager.cancelAll();
    }
}
