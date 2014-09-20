package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getName();
    private static final String PREF_AUTO_LOGIN_ENABLED_KEY = "pref_auto_login_enabled";
    private static final String PREF_LOGIN_RETRY_COUNT_KEY = "pref_login_retry_count";
    private static final String PREF_ENABLE_STATUS_NOTIFICATIONS_KEY = "pref_enable_status_notifications";
    private static final String PREF_USE_TOAST_MESSAGES_KEY = "pref_use_toast_messages";
    private static final String PREF_ENABLED_TOAST_MESSAGES_KEY = "pref_enabled_toast_messages";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(PREF_AUTO_LOGIN_ENABLED_KEY, false)) return;

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        if (!action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) return;

        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        NetworkInfo.State state = networkInfo.getState();
        Log.d(TAG, "Network state changed to: " + state);
        if (state != NetworkInfo.State.CONNECTED) return;

        String bssidStr = intent.getStringExtra(WifiManager.EXTRA_BSSID);
        Log.d(TAG, "BSSID: " + bssidStr);
        Bssid bssid = new Bssid(bssidStr);


        /*Set<String> activeProfileNames = connectPrefs.getStringSet("profiles", null);
        Set<NetworkProfile> activeProfiles = ProfileManager.getProfiles(activeProfileNames);

        boolean bssidInWhitelist = false;
        for (NetworkProfile profile : activeProfiles) {
            if (profile.contains(bssid)) {
                bssidInWhitelist = true;
                break;
            }
        }

        if (!bssidInWhitelist) {
            Log.d(TAG, "Active profiles does not include BSSID, skipping login");
        }

        String username = loginPrefs.getString("username", null);
        String password = loginPrefs.getString("password", null);

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            return;
        } */

        /*int retryCount = Integer.parseInt(preferences.getString(PREF_LOGIN_RETRY_COUNT_KEY, null));
        boolean showNotifications = preferences.getBoolean(PREF_ENABLE_STATUS_NOTIFICATIONS_KEY, false);
        boolean useToast = preferences.getBoolean(PREF_USE_TOAST_MESSAGES_KEY, false);
        Set<String> enabledToastMessages = preferences.getStringSet(PREF_ENABLED_TOAST_MESSAGES_KEY, null);


        Intent loginIntent = new Intent(context, LoginService.class);
        loginIntent.putExtras()
        loginIntent.putExtra(LoginService.USERNAME, username);
        loginIntent.putExtra(LoginService.PASSWORD, password);
        loginIntent.putExtra()
        context.startService(loginIntent);*/
    }
}
