// TODO: Fix potential race condition in *Task where preferences changes mid-way and notification won't get cancelled
// TODO: Add IBinder interface to allow login from UI without showing notification
// TODO: Unify the two notification IDs
// TODO: Remove EXTRA_ACTION_CHECK, make actions private to this class
// TODO: Disable login status auto-check when screen is off

package com.oxycode.swallow;

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
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.io.IOException;
import java.util.HashSet;

public class LoginService extends Service {
    private class CheckLoginStatusTask extends AsyncTask<Void, Object, LoginClient.QueryResult> {
        @Override
        protected LoginClient.QueryResult doInBackground(Void... params) {
            return LoginClient.getLoginStatus(_retryCount + 1, new LoginClient.Handler() {
                @Override
                public boolean onException(IOException e, int remainingTrialCount) {
                    publishProgress(e, remainingTrialCount);
                    return !isCancelled();
                }
            });
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Checking login status...");
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            IOException e = (IOException)values[0];
            int remainingTrialCount = (Integer)values[1];
            Log.d(TAG, "Exception occured while getting login status", e);
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
                    startRetryCheckTimer();
                    break;
                case UNKNOWN:
                    displayUnknownLoginPageError();
                    break;
            }
        }
    }

    private class PerformLoginTask extends AsyncTask<Void, Object, LoginClient.LoginResult> {
        private NotificationCompat.Builder _notification;

        protected LoginClient.LoginResult doInBackground(Void... params) {
            return LoginClient.login(_username, _password, _retryCount + 1, new LoginClient.Handler() {
                @Override
                public boolean onException(IOException e, int remainingTrialCount) {
                    publishProgress(e, remainingTrialCount);
                    return !isCancelled();
                }
            });
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Logging in to WiFi...");

            _notification = new NotificationCompat.Builder(LoginService.this)
                .setSmallIcon(R.drawable.icon);

            if (_showProgressNotification) {
                _notification
                    .setContentTitle(getString(R.string.noti_logging_in))
                    .setContentText(String.format(getString(R.string.noti_logging_in_using_account), _username))
                    .setOngoing(true)
                    .setProgress(0, 0, true);
                _notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, _notification.build());
                _notificationManager.cancel(NOTI_LOGIN_ACTION_ID);
            }
        }

        @Override
        protected void onCancelled(LoginClient.LoginResult result) {
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
            }
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            IOException e = (IOException)values[0];
            int remainingTrialCount = (Integer)values[1];

            // Update notification with retry state
            if (_showProgressNotification && remainingTrialCount > 0) {
                int messageId;
                if (remainingTrialCount == 1) {
                    messageId = R.string.noti_logging_in_retrying_single;
                } else {
                    messageId = R.string.noti_logging_in_retrying_plural;
                }
                _notification.setContentText(String.format(getString(messageId), remainingTrialCount));
                _notificationManager.notify(NOTI_LOGIN_PROGRESS_ID, _notification.build());
            }
        }

        @Override
        protected void onPostExecute(LoginClient.LoginResult result) {
            Log.d(TAG, "Login result: " + result);

            // Remove progress notification
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_LOGIN_PROGRESS_ID);
            }

            // If login succeeded or no error notification is needed, remove notification and exit
            if (result == LoginClient.LoginResult.SUCCESS ||
                result == LoginClient.LoginResult.CANCELLED ||
                !_showErrorNotification) {
                return;
            }

            // Something went wrong, display an error notification
            _notification
                .setOngoing(false)
                .setContentTitle(getString(R.string.noti_login_failed))
                .setAutoCancel(true)
                .setProgress(0, 0, false);

            // Set intent to run on notification touch
            PendingIntent pendingIntent = null;
            switch (result) {
                case EMPTY_CREDENTIALS:
                case INCORRECT_CREDENTIALS:
                case ACCOUNT_BANNED:
                    Intent configIntent = new Intent(LoginService.this, MainActivity.class);
                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(LoginService.this);
                    stackBuilder.addParentStack(MainActivity.class);
                    stackBuilder.addNextIntent(configIntent);
                    pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                    break;
                case EXCEEDED_MAX_RETRIES:
                case UNKNOWN:
                    Intent retryIntent = new Intent(LoginService.this, LoginService.class);
                    retryIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
                    pendingIntent = PendingIntent.getService(LoginService.this, 0, retryIntent, 0);
                    break;
            }
            _notification.setContentIntent(pendingIntent);

            // Set body of notification
            int messageId = 0;
            switch (result) {
                case EMPTY_CREDENTIALS:
                    messageId = R.string.noti_login_failed_unset_credentials;
                    break;
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
            _notification.setContentText(getString(messageId));

            // Display the error notification
            _notificationManager.notify(NOTI_LOGIN_ACTION_ID, _notification.build());
        }
    }

    private static final String TAG = LoginService.class.getSimpleName();

    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_SHOW_LOGIN_PROMPT_KEY = "pref_show_login_prompt";
    private static final String PREF_SHOW_PROGRESS_NOTIFICATION_KEY = "pref_show_progress_notification";
    private static final String PREF_SHOW_ERROR_NOTIFICATION_KEY = "pref_show_error_notification";

    private static final int NOTIFICATION_ID = 0x000222;
    private static final int NOTI_LOGIN_PROGRESS_ID = 0xdcaeee;
    private static final int NOTI_LOGIN_ACTION_ID = 0x9f37ff;

    public static final String PREF_LOGIN_CREDENTIALS = "com.oxycode.swallow.credentials";
    public static final String PREF_USERNAME_KEY = "username";
    public static final String PREF_PASSWORD_KEY = "password";

    public static final String EXTRA_ACTION = "action";
    public static final int EXTRA_ACTION_DEFAULT = 0;
    public static final int EXTRA_ACTION_CHECK = 1;
    public static final int EXTRA_ACTION_LOG_IN = 2;

    private SharedPreferences _preferences;
    private SharedPreferences _credentials;
    private SharedPreferences.OnSharedPreferenceChangeListener _prefChangeListener;
    private NetworkProfileDBAdapter _profileDatabase;
    private HashSet<String> _whitelistedBssids;

    private WifiManager _wifiManager;
    private NotificationManager _notificationManager;
    private Handler _timerHandler;
    private Runnable _checkLoginStatusAction;
    private BroadcastReceiver _broadcastReceiver;
    private boolean _whitelistCacheDirty;

    private int _retryCount;
    private boolean _showPromptNotification;
    private boolean _showProgressNotification;
    private boolean _showErrorNotification;
    private String _username;
    private String _password;

    private AsyncTask<?, ?, ?> _runningTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // Cache system services
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

        // Broadcast receiver for WiFi disconnected and profile modified events
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    // SupplicantState.CONNECTED is handled by our external receiver,
                    // only disconnect events should be handled here.
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

        // Load preferences
        _preferences = PreferenceManager.getDefaultSharedPreferences(this);
        _credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);
        _retryCount = Integer.parseInt(_preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, "0"));
        _showPromptNotification = _preferences.getBoolean(PREF_SHOW_LOGIN_PROMPT_KEY, false);
        _showProgressNotification = _preferences.getBoolean(PREF_SHOW_PROGRESS_NOTIFICATION_KEY, false);
        _showErrorNotification = _preferences.getBoolean(PREF_SHOW_ERROR_NOTIFICATION_KEY, false);
        _username = _credentials.getString(PREF_USERNAME_KEY, null);
        _password = _credentials.getString(PREF_PASSWORD_KEY, null);
        _prefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(PREF_USERNAME_KEY)) {
                    _username = sharedPreferences.getString(key, null);
                } else if (key.equals(PREF_PASSWORD_KEY)) {
                    _password = sharedPreferences.getString(key, null);
                } else if (key.equals(PREF_LOGIN_RETRY_COUNT_KEY)) {
                    _retryCount = Integer.parseInt(sharedPreferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, "0"));
                } else if (key.equals(PREF_SHOW_LOGIN_PROMPT_KEY)) {
                    _showPromptNotification = sharedPreferences.getBoolean(PREF_SHOW_LOGIN_PROMPT_KEY, false);
                } else if (key.equals(PREF_SHOW_PROGRESS_NOTIFICATION_KEY)) {
                    _showProgressNotification = sharedPreferences.getBoolean(PREF_SHOW_PROGRESS_NOTIFICATION_KEY, false);
                } else if (key.equals(PREF_SHOW_ERROR_NOTIFICATION_KEY)) {
                    _showErrorNotification = sharedPreferences.getBoolean(PREF_SHOW_ERROR_NOTIFICATION_KEY, false);
                }
            }
        };
        _preferences.registerOnSharedPreferenceChangeListener(_prefChangeListener);
        _credentials.registerOnSharedPreferenceChangeListener(_prefChangeListener);

        // Load profiles from database
        _profileDatabase = new NetworkProfileDBAdapter(this);
        _whitelistedBssids = new HashSet<String>();
        _whitelistCacheDirty = true;

        Log.d(TAG, "Started login service");
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

        Log.d(TAG, String.format("Loaded %d BSSIDs from database whitelist", bssidCount));
    }

    private void onLoginRequired() {
        if (!_showPromptNotification) {
            performLogin();
        } else {
            // Show login confirmation notification
            Intent loginIntent = new Intent(LoginService.this, LoginService.class);
            loginIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
            PendingIntent pendingIntent = PendingIntent.getService(LoginService.this, 0, loginIntent, 0);

            NotificationCompat.Builder notification = new NotificationCompat.Builder(LoginService.this)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.noti_touch_to_log_in_title))
                .setContentText(getString(R.string.noti_touch_to_log_in_content))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

            _notificationManager.notify(NOTI_LOGIN_ACTION_ID, notification.build());
        }
    }

    private void onShsNetwork() {
        checkLoginStatus();
    }

    private void onNotShsNetwork() {
        cancelRetryCheckTimer();
        stopRunningTask();
    }

    private void stopRunningTask() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
        }
    }

    private void displayUnknownLoginPageError() {
        // TODO: ???
    }

    private void cancelRetryCheckTimer() {
        _timerHandler.removeCallbacks(_checkLoginStatusAction);
    }

    private void startRetryCheckTimer() {
        _timerHandler.postDelayed(_checkLoginStatusAction, 5000 /* TODO */);
    }

    private boolean isBssidWhitelisted(String bssid) {
        if (_whitelistCacheDirty) {
            loadWhitelistedBssids();
            _whitelistCacheDirty = false;
        }

        boolean whitelisted = _whitelistedBssids.contains(bssid);
        Log.d(TAG, "Checking BSSID: " + bssid + " -> " + whitelisted);
        return whitelisted;
    }

    private void checkConnectivity() {
        Log.d(TAG, "Checking connectivity");
        WifiInfo wifiInfo = _wifiManager.getConnectionInfo();
        String bssid = wifiInfo.getBSSID();
        if (bssid != null && isBssidWhitelisted(bssid)) {
            onShsNetwork();
        } else {
            onNotShsNetwork();
        }
    }

    private void removeNotification() {
        _notificationManager.cancel(NOTIFICATION_ID);
    }

    private void checkLoginStatus() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
        }
        _runningTask = new CheckLoginStatusTask().execute();
    }

    private void performLogin() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
        }
        _runningTask = new PerformLoginTask().execute();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action;

        // Intent can be null if the service was restarted by the system
        if (intent != null) {
            action = intent.getIntExtra(EXTRA_ACTION, EXTRA_ACTION_DEFAULT);
        } else {
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

        // Make sure the service remains running in the background
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping login service");
        stopRunningTask();
        removeNotification();
        unregisterReceiver(_broadcastReceiver);
    }
}
