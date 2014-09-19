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

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean("pref_auto_login_enabled", false)) return;

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
        }

        Intent loginIntent = new Intent(context, LoginService.class);
        loginIntent.putExtra("username", username);
        loginIntent.putExtra("password", password);
        context.startService(loginIntent);*/
    }
}
