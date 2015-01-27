package com.oxycode.swallow;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.database.Cursor;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;

public class LoginService extends Service {
    private class CheckLoginStatusTask extends AsyncTask<Void, Void, LoginClient.QueryResult> {
        private final int _retryCount;
        private int _tries;

        public CheckLoginStatusTask() {
            _retryCount = Integer.parseInt(_preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, "3"));
            _tries = 0;
        }

        @Override
        protected LoginClient.QueryResult doInBackground(Void... params) {
            return LoginClient.getLoginStatus(new LoginClient.Handler() {
                @Override
                public boolean onException(IOException e) {
                    boolean cont = !isCancelled() && _tries++ < _retryCount;
                    publishProgress();
                    return cont;
                }
            });
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Checking login status...");
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            int remainingTrialCount = _retryCount - _tries + 1;
            if (remainingTrialCount > 0) {
                Log.d(TAG, "Retrying login status fetch " + remainingTrialCount + " more time(s)");
            }
        }

        @Override
        protected void onPostExecute(LoginClient.QueryResult result) {
            switch (result) {
                case LOGGED_IN:
                    break;
                case LOGGED_OUT:
                    onLoginRequired();
                    break;
                case EXCEEDED_MAX_RETRIES:
                case UNKNOWN:
                    startRetryCheckTimer();
                    break;
            }
        }
    }

    private class PerformLoginTask extends AsyncTask<Void, Void, LoginClient.LoginResult> {
        private final String _username;
        private final String _password;
        private final int _retryCount;
        private final boolean _showProgressNotification;
        private final Notification.Builder _notification;
        private int _tries;

        public PerformLoginTask() {
            _username = _credentials.getString(PREF_USERNAME_KEY, "");
            _password = _credentials.getString(PREF_PASSWORD_KEY, "");
            _retryCount = Integer.parseInt(_preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, "3"));
            _showProgressNotification = _preferences.getBoolean(PREF_SHOW_PROGRESS_NOTIFICATION_KEY, true);
            if (_showProgressNotification) {
                _notification = new Notification.Builder(LoginService.this)
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true)
                    .setContentTitle(getString(R.string.noti_logging_in))
                    .setContentText(String.format(getString(R.string.noti_logging_in_using_account), _username))
                    .setProgress(0, 0, true);
            } else {
                _notification = null;
            }
            _tries = 0;
        }

        protected LoginClient.LoginResult doInBackground(Void... params) {
            return LoginClient.login(_username, _password, new LoginClient.Handler() {
                @Override
                public boolean onException(IOException e) {
                    boolean cont = !isCancelled() && _tries++ < _retryCount;
                    publishProgress();
                    return cont;
                }
            });
        }

        @Override
        protected void onPreExecute() {
            if (_showProgressNotification) {
                _notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, _notification.getNotification());
            }
        }

        @Override
        protected void onCancelled(LoginClient.LoginResult result) {
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
            }
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            int remainingTrialCount = _retryCount - _tries + 1;
            if (remainingTrialCount > 0) {
                Log.d(TAG, "Retrying login " + remainingTrialCount + " more time(s)");
                if (_showProgressNotification) {
                    int messageId;
                    if (remainingTrialCount == 1) {
                        messageId = R.string.noti_logging_in_retrying_single;
                    } else {
                        messageId = R.string.noti_logging_in_retrying_plural;
                    }
                    _notification.setContentText(String.format(getString(messageId), remainingTrialCount));
                    _notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, _notification.getNotification());
                }
            }
        }

        @Override
        protected void onPostExecute(LoginClient.LoginResult result) {
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
            }

            switch (result) {
                case SUCCESS:
                    break;
                case INCORRECT_CREDENTIALS:
                case ACCOUNT_BANNED:
                    showLoginErrorNotification(result);
                case EXCEEDED_MAX_RETRIES:
                case UNKNOWN:
                    onLoginFailedTimeout(result);
                    break;
            }
        }
    }

    private static final String TAG = LoginService.class.getSimpleName();

    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_SHOW_LOGIN_PROMPT_KEY = "pref_show_login_prompt";
    private static final String PREF_SHOW_PROGRESS_NOTIFICATION_KEY = "pref_show_progress_notification";
    private static final String PREF_SHOW_ERROR_NOTIFICATION_KEY = "pref_show_error_notification";
    private static final String PREF_LOGIN_STATUS_CHECK_INTERVAL_KEY = "pref_login_status_check_interval";

    private static final int NOTI_LOGIN_PROMPT_ID = 0xdcaeee;
    private static final int NOTI_LOGIN_PROGRESS_ID = 0x222546;
    private static final int NOTI_LOGIN_ERROR_ID = 0x233666;

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    private static final String EXTRA_ACTION = "action";
    private static final int EXTRA_ACTION_DEFAULT = 0;
    private static final int EXTRA_ACTION_CHECK = 1;
    private static final int EXTRA_ACTION_LOG_IN = 2;

    private SharedPreferences _preferences;
    private SharedPreferences _credentials;
    private NetworkProfileDBAdapter _profileDatabase;
    private HashSet<String> _whitelistedBssids;

    private WifiManager _wifiManager;
    private NotificationManager _notificationManager;
    private Handler _timerHandler;
    private Runnable _checkLoginStatusAction;
    private BroadcastReceiver _broadcastReceiver;
    private boolean _whitelistCacheDirty;

    private AsyncTask<?, ?, ?> _runningTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // Get system services
        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        _notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        // Set up timers
        _timerHandler = new Handler();
        _checkLoginStatusAction = new Runnable() {
            @Override
            public void run() {
                checkLoginStatus();
            }
        };

        // Create broadcast receiver for WiFi disconnected and profile modified events
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    if (state == SupplicantState.DISCONNECTED) {
                        onNotShsNetwork();
                    }
                } else if (action.equals(NetworkProfileDBAdapter.DATABASE_CHANGED_ACTION)) {
                    _whitelistCacheDirty = true;
                }
            }
        };

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(NetworkProfileDBAdapter.DATABASE_CHANGED_ACTION);
        registerReceiver(_broadcastReceiver, filter);

        // Load preferences and credentials
        _preferences = PreferenceManager.getDefaultSharedPreferences(this);
        _credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);

        // Load profiles from database
        _profileDatabase = new NetworkProfileDBAdapter(this);
        _whitelistedBssids = new HashSet<String>();
        _whitelistCacheDirty = true;

        Log.d(TAG, "Started login service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action;
        if (intent != null) {
            action = intent.getIntExtra(EXTRA_ACTION, EXTRA_ACTION_DEFAULT);
        } else {
            // Intent can be null if the service was restarted by the system
            action = EXTRA_ACTION_DEFAULT;
        }

        switch (action) {
            case EXTRA_ACTION_DEFAULT:
                checkConnectivity();
                break;
            case EXTRA_ACTION_CHECK:
                checkLoginStatus();
                break;
            case EXTRA_ACTION_LOG_IN:
                performLogin();
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping login service");
        stopRunningTask();
        removeNotifications();
        unregisterReceiver(_broadcastReceiver);
    }

    private void loadWhitelistedBssids() {
        _whitelistedBssids.clear();
        _profileDatabase.open();
        int bssidCount = 0;
        try {
            Cursor bssidCursor = _profileDatabase.getAllBssids(true);
            while (bssidCursor.moveToNext()) {
                _whitelistedBssids.add(bssidCursor.getString(2));
                ++bssidCount;
            }
        } finally {
            _profileDatabase.close();
        }
        _whitelistCacheDirty = false;
        Log.d(TAG, String.format("Loaded %d BSSIDs from database whitelist", bssidCount));
    }

    private boolean isBssidWhitelisted(String bssid) {
        if (_whitelistCacheDirty) {
            loadWhitelistedBssids();
        }

        boolean whitelisted = _whitelistedBssids.contains(bssid);
        Log.d(TAG, "Checking BSSID: " + bssid + " -> " + whitelisted);
        return whitelisted;
    }

    private void checkConnectivity() {
        Log.d(TAG, "Checking connectivity");
        WifiInfo wifiInfo = _wifiManager.getConnectionInfo();
        String ssid = wifiInfo.getSSID();
        Log.d(TAG, "Current SSID: " + ssid);
        String bssid = wifiInfo.getBSSID();
        if (bssid != null && isBssidWhitelisted(bssid)) {
            onShsNetwork();
        } else {
            onNotShsNetwork();
        }
    }

    private void onShsNetwork() {
        checkLoginStatus();
    }

    private void onNotShsNetwork() {
        stopRetryCheckTimer();
        stopRunningTask();
    }

    private void checkLoginStatus() {
        stopRunningTask();
        _runningTask = new CheckLoginStatusTask().execute();
    }

    private void performLogin() {
        stopRunningTask();
        _runningTask = new PerformLoginTask().execute();
    }

    private void stopRunningTask() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
            _runningTask = null;
        }
    }

    private void onLoginRequired() {
        if (_preferences.getBoolean(PREF_SHOW_LOGIN_PROMPT_KEY, false)) {
            showLoginPromptNotification();
        } else {
            performLogin();
        }
    }

    private void onLoginFailedTimeout(LoginClient.LoginResult result) {
        if (_preferences.getBoolean(PREF_SHOW_ERROR_NOTIFICATION_KEY, true)) {
            showLoginErrorNotification(result);
        } else {
            startRetryCheckTimer();
        }
    }

    private void showLoginErrorNotification(LoginClient.LoginResult result) {
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
                retryIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
                pendingIntent = PendingIntent.getService(this, 0, retryIntent, 0);
                break;
        }

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

        Notification.Builder notification = new Notification.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.noti_login_failed))
            .setContentText(getString(messageId))
            .setContentIntent(pendingIntent);

        _notificationManager.notify(NOTI_LOGIN_ERROR_ID, notification.getNotification());
    }

    private void showLoginPromptNotification() {
        Intent loginIntent = new Intent(this, LoginService.class);
        loginIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, loginIntent, 0);

        Notification.Builder notification = new Notification.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.noti_touch_to_log_in_title))
            .setContentText(getString(R.string.noti_touch_to_log_in_content))
            .setContentIntent(pendingIntent);

        _notificationManager.notify(NOTI_LOGIN_PROMPT_ID, notification.getNotification());
    }

    private void removeNotifications() {
        _notificationManager.cancel(NOTI_LOGIN_PROMPT_ID);
        _notificationManager.cancel(NOTI_LOGIN_ERROR_ID);
        _notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
    }

    private void startRetryCheckTimer() {
        int delay = Integer.parseInt(_preferences.getString(PREF_LOGIN_STATUS_CHECK_INTERVAL_KEY, "30"));
        Log.d(TAG, "Starting WiFi status retry check timer with interval: " + delay + "s");
        _timerHandler.postDelayed(_checkLoginStatusAction, delay * 1000);
    }

    private void stopRetryCheckTimer() {
        Log.d(TAG, "Stopping WiFi status retry check timer");
        _timerHandler.removeCallbacks(_checkLoginStatusAction);
    }
}
