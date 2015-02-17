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

    public static final String EXTRA_ACTION = "action";
    public static final int EXTRA_ACTION_DEFAULT = 0;
    public static final int EXTRA_ACTION_CHECK = 1;
    public static final int EXTRA_ACTION_LOG_IN = 2;
    public static final int EXTRA_ACTION_NONE = 3;

    public static final String EXTRA_SHOW_SETUP = "setup";
    public static final int EXTRA_SHOW_SETUP_DEFAULT = 0;
    public static final int EXTRA_SHOW_SETUP_TRUE = 1;
    public static final int EXTRA_SHOW_SETUP_FALSE = 2;

    private SharedPreferences _preferences;
    private SharedPreferences _credentials;
    private WifiManager _wifiManager;
    private NotificationManager _notificationManager;
    private Handler _handler;
    private Runnable _checkNetworkStatusAction;
    private BroadcastReceiver _broadcastReceiver;
    private ContentObserver _contentObserver;
    private HashSet<String> _whitelistedBssids;
    private boolean _shouldShowSetupNotification;
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

        _handler = new Handler();
        _checkNetworkStatusAction = new Runnable() {
            @Override
            public void run() {
                checkNetworkStatus();
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
                        onNetworkStatusChanged(false);
                    }
                } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    onScreenStatusChanged(true);
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    onScreenStatusChanged(false);
                }
            }
        };

        // Create content observer for database modified event
        _contentObserver = new ContentObserver(_handler) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "Database modified, marking whitelist as dirty");
                _whitelistedBssids = null;
            }
        };

        _whitelistedBssids = null;
        _shouldShowSetupNotification = true;
        _runningTask = null;

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(_broadcastReceiver, filter);

        // Register content observer
        Uri bssidsUri = NetworkProfileContract.Bssids.CONTENT_URI;
        getContentResolver().registerContentObserver(bssidsUri, true, _contentObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action;
        int showSetup;
        if (intent != null) {
            action = intent.getIntExtra(EXTRA_ACTION, EXTRA_ACTION_DEFAULT);
            showSetup = intent.getIntExtra(EXTRA_SHOW_SETUP, EXTRA_SHOW_SETUP_DEFAULT);
        } else {
            // Intent can be null if the service was restarted by the system
            action = EXTRA_ACTION_DEFAULT;
            showSetup = EXTRA_SHOW_SETUP_DEFAULT;
        }

        switch (showSetup) {
            case EXTRA_SHOW_SETUP_DEFAULT:
                break;
            case EXTRA_SHOW_SETUP_TRUE:
                _shouldShowSetupNotification = true;
                if (isBssidWhitelisted(getNetworkBssid())) {
                    showSetupNotification();
                }
                break;
            case EXTRA_SHOW_SETUP_FALSE:
                _shouldShowSetupNotification = false;
                _notificationManager.cancel(NOTI_ID_SETUP);
                break;
        }

        switch (action) {
            case EXTRA_ACTION_DEFAULT:
                checkNetworkStatus();
                break;
            case EXTRA_ACTION_CHECK:
                startCheckLoginStatusTask();
                break;
            case EXTRA_ACTION_LOG_IN:
                startLoginTask();
                break;
            case EXTRA_ACTION_NONE:
                break;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelDelayedLoginStatusCheck();
        stopRunningTask();
        cancelAllNotifications();
        unregisterReceiver(_broadcastReceiver);
        getContentResolver().unregisterContentObserver(_contentObserver);
    }

    private HashSet<String> getBssidWhitelist() {
        HashSet<String> whitelist = _whitelistedBssids;
        if (whitelist != null) {
            return whitelist;
        }

        whitelist = new HashSet<String>();

        Uri uri = NetworkProfileContract.ProfileBssids.CONTENT_URI;
        String[] projection = {NetworkProfileContract.ProfileBssids.BSSID};
        String selection = NetworkProfileContract.ProfileBssids.PROFILE_ENABLED + "=1";
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(uri, projection, selection, null, null);

        // Use Bssids.BSSID instead of ProfileBssids.BSSID because
        // the result of the query should not have table qualifiers
        int bssidColumn = cursor.getColumnIndexOrThrow(NetworkProfileContract.Bssids.BSSID);
        int bssidCount;
        for (bssidCount = 0; cursor.moveToNext(); ++bssidCount) {
            String bssid = cursor.getString(bssidColumn);
            Log.d(TAG, "Loaded whitelisted BSSID: " + bssid);
            whitelist.add(bssid);
        }

        cursor.close();
        _whitelistedBssids = whitelist;
        Log.d(TAG, "Loaded " + bssidCount + " BSSID(s) from database whitelist");
        return whitelist;
    }

    private String getNetworkBssid() {
        WifiInfo wifiInfo = _wifiManager.getConnectionInfo();
        return wifiInfo.getBSSID();
    }

    private boolean isBssidWhitelisted(String bssid) {
        HashSet<String> whitelist = getBssidWhitelist();
        boolean whitelisted = whitelist.contains(bssid);
        Log.d(TAG, "Checking BSSID: " + bssid + " -> " + whitelisted);
        return whitelisted;
    }

    private void checkNetworkStatus() {
        String bssid = getNetworkBssid();
        boolean networkWhitelisted = isBssidWhitelisted(bssid);
        onNetworkStatusChanged(networkWhitelisted);
    }

    private boolean requiresSetup() {
        String username = _credentials.getString(PREF_KEY_USERNAME, "");
        String password = _credentials.getString(PREF_KEY_PASSWORD, "");
        return TextUtils.isEmpty(username) || TextUtils.isEmpty(password);
    }

    private void startCheckLoginStatusTask() {
        if (requiresSetup()) {
            showSetupNotification();
        } else {
            stopRunningTask();
            _runningTask = new CheckLoginStatusTask().execute();
        }
    }

    private void startLoginTask() {
        if (requiresSetup()) {
            showSetupNotification();
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

    private void onNetworkStatusChanged(boolean whitelisted) {
        Log.d(TAG, "Network whitelisted status changed -> " + whitelisted);

        if (whitelisted) {
            startCheckLoginStatusTask();
            enqueueDelayedLoginStatusCheck();
        } else {
            cancelDelayedLoginStatusCheck();
            stopRunningTask();
            _notificationManager.cancel(NOTI_ID_LOGIN_PROMPT);
        }
    }

    private void onScreenStatusChanged(boolean screenOn) {
        Log.d(TAG, "Screen status changed -> " + screenOn);

        if (screenOn) {
            checkNetworkStatus();
        } else {
            cancelDelayedLoginStatusCheck();
        }
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

    private void showSetupNotification() {
        if (!_shouldShowSetupNotification) {
            // This should happen when the main activity is visible
            // in the foreground; there's no point in telling the user
            // to set up their account when they're probably doing it
            // anyways
            return;
        }

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

    private void cancelAllNotifications() {
        _notificationManager.cancel(NOTI_ID_SETUP);
        _notificationManager.cancel(NOTI_ID_LOGIN_PROMPT);
        _notificationManager.cancel(NOTI_ID_LOGIN_ERROR);
        _notificationManager.cancel(NOTI_ID_LOGIN_PROGRESS);
    }

    private void enqueueDelayedLoginStatusCheck() {
        // Make sure not to check multiple times
        cancelDelayedLoginStatusCheck();

        int delay = PreferenceUtils.getInt(_preferences, PREF_KEY_LOGIN_STATUS_CHECK_INTERVAL, 60);
        if (delay > 0) {
            _handler.postDelayed(_checkNetworkStatusAction, delay * 1000);
        }
    }

    private void cancelDelayedLoginStatusCheck() {
        _handler.removeCallbacks(_checkNetworkStatusAction);
    }
}
