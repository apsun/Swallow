package com.oxycode.swallow;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;
import android.util.Log;
import com.oxycode.swallow.provider.NetworkProfileContract;
import com.oxycode.swallow.utils.PreferenceUtils;

import java.io.IOException;
import java.util.HashSet;

public class LoginService extends Service {
    private class CheckLoginStatusTask extends AsyncTask<Void, Void, LoginClient.QueryResult> {
        private final int _retryCount;
        private int _tries;

        public CheckLoginStatusTask() {
            _retryCount = PreferenceUtils.getInt(_preferences, PREF_KEY_LOGIN_RETRY_COUNT, 2);
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
                    Log.w(TAG, "Login status check returned result: " + result);
                    break;
            }
        }
    }

    private class PerformLoginTask extends AsyncTask<Void, Void, LoginClient.LoginResult> {
        private final String _username;
        private final String _password;
        private final int _retryCount;
        private final boolean _showProgressNotification;
        private final NotificationCompat.Builder _notification;
        private int _tries;

        public PerformLoginTask() {
            _username = _credentials.getString(PREF_KEY_USERNAME, "");
            _password = _credentials.getString(PREF_KEY_PASSWORD, "");
            _retryCount = PreferenceUtils.getInt(_preferences, PREF_KEY_LOGIN_RETRY_COUNT, 2);
            _showProgressNotification = _preferences.getBoolean(PREF_KEY_SHOW_PROGRESS_NOTIFICATION, true);
            if (_showProgressNotification) {
                _notification = new NotificationCompat.Builder(LoginService.this)
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
                _notificationManager.notify(NOTI_ID_LOGIN_PROGRESS, _notification.build());
            }
        }

        @Override
        protected void onCancelled(LoginClient.LoginResult result) {
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_ID_LOGIN_PROGRESS);
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
                    _notificationManager.notify(NOTI_ID_LOGIN_PROGRESS, _notification.build());
                }
            }
        }

        @Override
        protected void onPostExecute(LoginClient.LoginResult result) {
            if (_showProgressNotification) {
                _notificationManager.cancel(NOTI_ID_LOGIN_PROGRESS);
            }

            switch (result) {
                case SUCCESS:
                    break;
                case INCORRECT_CREDENTIALS:
                case ACCOUNT_BANNED:
                    showLoginErrorNotification(result);
                case EXCEEDED_MAX_RETRIES:
                case UNKNOWN:
                    Log.w(TAG, "Login returned result: " + result);
                    onLoginFailedTimeout(result);
                    break;
            }
        }
    }

    private static final String TAG = LoginService.class.getSimpleName();

    public static final String PREF_LOGIN_CREDENTIALS = "credentials";
    public static final String PREF_KEY_USERNAME = "username";
    public static final String PREF_KEY_PASSWORD = "password";

    private static final String PREF_KEY_LOGIN_RETRY_COUNT = "pref_login_retry_count";
    private static final String PREF_KEY_SHOW_LOGIN_PROMPT = "pref_show_login_prompt";
    private static final String PREF_KEY_SHOW_PROGRESS_NOTIFICATION = "pref_show_progress_notification";
    private static final String PREF_KEY_SHOW_ERROR_NOTIFICATION = "pref_show_error_notification";
    private static final String PREF_KEY_LOGIN_STATUS_CHECK_INTERVAL = "pref_login_status_check_interval";

    private static final int NOTI_ID_SETUP = 0;
    private static final int NOTI_ID_LOGIN_PROMPT = 1;
    private static final int NOTI_ID_LOGIN_PROGRESS = 2;
    private static final int NOTI_ID_LOGIN_ERROR = 3;

    private static final String EXTRA_ACTION = "action";
    private static final int EXTRA_ACTION_DEFAULT = 0;
    private static final int EXTRA_ACTION_CHECK = 1;
    private static final int EXTRA_ACTION_LOG_IN = 2;

    private SharedPreferences _preferences;
    private SharedPreferences _credentials;
    private WifiManager _wifiManager;
    private NotificationManager _notificationManager;
    private Handler _handler;
    private Runnable _checkLoginStatusAction;
    private BroadcastReceiver _broadcastReceiver;
    private ContentObserver _contentObserver;
    private HashSet<String> _whitelistedBssids;
    private boolean _whitelistCacheDirty;
    private AsyncTask<?, ?, ?> _runningTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // Load preferences and credentials
        _preferences = PreferenceManager.getDefaultSharedPreferences(this);
        _credentials = getSharedPreferences(PREF_LOGIN_CREDENTIALS, MODE_PRIVATE);

        // Get system services
        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        _notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        // Set up timers
        _handler = new Handler();
        _checkLoginStatusAction = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Timer -> checking login status");
                startCheckLoginStatusTask();
                enqueueDelayedLoginStatusCheck();
            }
        };

        // Create broadcast receiver for WiFi disconnected event
        // This is not in the extrnal WifiStateReceiver class because
        // it should not start nor stop the service.
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    if (state == SupplicantState.DISCONNECTED) {
                        onNotShsNetwork();
                    }
                }
            }
        };

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(_broadcastReceiver, filter);

        // Create content observer for database modified event
        _contentObserver = new ContentObserver(_handler) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "Database modified, marking whitelist as dirty");
                _whitelistCacheDirty = true;
            }
        };

        // Register content observer
        ContentResolver contentResolver = getContentResolver();
        contentResolver.registerContentObserver(NetworkProfileContract.Bssids.CONTENT_URI, true, _contentObserver);

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
                checkIfOnShsNetwork();
                break;
            case EXTRA_ACTION_CHECK:
                startCheckLoginStatusTask();
                break;
            case EXTRA_ACTION_LOG_IN:
                startLoginTask();
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping login service");
        cancelDelayedLoginStatusCheck();
        stopRunningTask();
        removeNotifications();
        unregisterReceiver(_broadcastReceiver);
        ContentResolver contentResolver = getContentResolver();
        contentResolver.unregisterContentObserver(_contentObserver);
    }

    private void ensureCleanBssidCache() {
        if (!_whitelistCacheDirty) {
            return;
        }

        _whitelistedBssids.clear();

        Uri uri = NetworkProfileContract.ProfileBssids.CONTENT_URI;
        String[] projection = {NetworkProfileContract.ProfileBssids.BSSID};
        String selection = NetworkProfileContract.ProfileBssids.PROFILE_ENABLED + "=1";
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(uri, projection, selection, null, null);

        // This should be 0, since we specified only 1 item in projection
        // Anyways, we use .Bssids.BSSID instead of .ProfileBssids.BSSID because
        // the result of the query should not have table qualifiers.
        // (Actually, .ProfileBssids.BSSID works too, but it shows an ugly error
        // in logcat, and we're trying to avoid that :p)
        int bssidColumn = cursor.getColumnIndex(NetworkProfileContract.Bssids.BSSID);

        int bssidCount;
        for (bssidCount = 0; cursor.moveToNext(); ++bssidCount) {
            String bssid = cursor.getString(bssidColumn);
            Log.d(TAG, "Loaded whitelisted BSSID: " + bssid);
            _whitelistedBssids.add(bssid);
        }

        cursor.close();

        _whitelistCacheDirty = false;

        Log.d(TAG, String.format("Loaded %d BSSIDs from database whitelist", bssidCount));
    }

    private boolean isBssidWhitelisted(String bssid) {
        ensureCleanBssidCache();
        boolean whitelisted = _whitelistedBssids.contains(bssid);
        Log.d(TAG, "Checking BSSID: " + bssid + " -> " + whitelisted);
        return whitelisted;
    }

    private void checkIfOnShsNetwork() {
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

    private boolean requiresSetup() {
        String username = _credentials.getString(PREF_KEY_USERNAME, "");
        String password = _credentials.getString(PREF_KEY_PASSWORD, "");
        return TextUtils.isEmpty(username) || TextUtils.isEmpty(password);
    }

    private void startCheckLoginStatusTask() {
        if (requiresSetup()) {
            showSetupRequiredNotification();
        } else {
            stopRunningTask();
            _runningTask = new CheckLoginStatusTask().execute();
        }
    }

    private void startLoginTask() {
        if (requiresSetup()) {
            showSetupRequiredNotification();
        } else {
            stopRunningTask();
            _runningTask = new PerformLoginTask().execute();
        }
    }

    private void stopRunningTask() {
        if (_runningTask != null) {
            _runningTask.cancel(true);
            _runningTask = null;
        }
    }

    private void onShsNetwork() {
        startCheckLoginStatusTask();
        enqueueDelayedLoginStatusCheck();
    }

    private void onNotShsNetwork() {
        cancelDelayedLoginStatusCheck();
        stopRunningTask();
        _notificationManager.cancel(NOTI_ID_LOGIN_PROMPT);
    }

    private void onLoginRequired() {
        if (_preferences.getBoolean(PREF_KEY_SHOW_LOGIN_PROMPT, true)) {
            showLoginPromptNotification();
        } else {
            startLoginTask();
        }
    }

    private void onLoginFailedTimeout(LoginClient.LoginResult result) {
        if (_preferences.getBoolean(PREF_KEY_SHOW_ERROR_NOTIFICATION, true)) {
            showLoginErrorNotification(result);
        }
    }

    private void showSetupRequiredNotification() {
        Intent configIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(configIntent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.noti_setup_required_title))
            .setContentText(getString(R.string.noti_setup_required_message))
            .setContentIntent(pendingIntent);

        _notificationManager.notify(NOTI_ID_SETUP, notification.build());
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

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.noti_login_failed))
            .setContentText(getString(messageId))
            .setContentIntent(pendingIntent);

        _notificationManager.notify(NOTI_ID_LOGIN_ERROR, notification.build());
    }

    private void showLoginPromptNotification() {
        Intent loginIntent = new Intent(this, LoginService.class);
        loginIntent.putExtra(EXTRA_ACTION, EXTRA_ACTION_LOG_IN);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, loginIntent, 0);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon)
            .setAutoCancel(true)
            .setContentTitle(getString(R.string.noti_touch_to_log_in_title))
            .setContentText(getString(R.string.noti_touch_to_log_in_content))
            .setContentIntent(pendingIntent);

        _notificationManager.notify(NOTI_ID_LOGIN_PROMPT, notification.build());
    }

    private void removeNotifications() {
        _notificationManager.cancel(NOTI_ID_SETUP);
        _notificationManager.cancel(NOTI_ID_LOGIN_PROMPT);
        _notificationManager.cancel(NOTI_ID_LOGIN_ERROR);
        _notificationManager.cancel(NOTI_ID_LOGIN_PROGRESS);
    }

    private void enqueueDelayedLoginStatusCheck() {
        // We don't need to worry about running this while the screen is off:
        // Handler#postDelayed() will not run tasks while the device is in deep sleep
        int delay = PreferenceUtils.getInt(_preferences, PREF_KEY_LOGIN_STATUS_CHECK_INTERVAL, 60);
        if (delay > 0) {
            Log.d(TAG, "Enqueued login status check with delay " + delay + " seconds");
            _handler.postDelayed(_checkLoginStatusAction, delay * 1000);
        }
    }

    private void cancelDelayedLoginStatusCheck() {
        _handler.removeCallbacks(_checkLoginStatusAction);
    }
}
