package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = "SWAL";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        if (!action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) return;

        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        NetworkInfo.State state = networkInfo.getState();
        Log.d(TAG, "Network state changed to: " + state);
        if (state != NetworkInfo.State.CONNECTED) return;

        String bssid = intent.getStringExtra(WifiManager.EXTRA_BSSID);
        Log.d(TAG, "BSSID: " + bssid);
        WifiInfo wifiInfo = intent.getParcelableExtra(WifiManager.EXTRA_WIFI_INFO);
        String ssid = wifiInfo.getSSID();
        Log.d(TAG, "Connected to: " + ssid);

        // TODO: Use BSSID here! Prevent auto-logging in to other APs
        if (!ssid.equals("shs")) return;

        SharedPreferences preferences = context.getSharedPreferences("LoginCredentials", Context.MODE_PRIVATE);
        String username = preferences.getString("username", null);
        String password = preferences.getString("password", null);

        if (username == null || password == null) {
            Log.d(TAG, "Null login credentials, skipping login");
            return;
        }

        Intent loginIntent = new Intent(context, LoginService.class);
        loginIntent.putExtra("username", username);
        loginIntent.putExtra("password", password);
        context.startService(loginIntent);
    }
}
