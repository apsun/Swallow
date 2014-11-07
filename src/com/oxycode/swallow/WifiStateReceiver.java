package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        // First perform some filtering of actions here, to save the overhead
        // of starting a service that will just exit immediately upon starting.
        // Then pass the intent to LoginService using startService().
        // We want to start the service when WiFi connection is established,
        // but destroying it when WiFi is disconnected is not really necessary.
        String action = intent.getAction();
        Intent loginIntent = new Intent(context, LoginService.class);

        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION");
            // Toast.makeText(context, "Network state changed", Toast.LENGTH_SHORT).show();
        } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
            Log.d(TAG, "SUPPLICANT_CONNECTION_CHANGE_ACTION");
            boolean connected = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
            Log.d(TAG, "[+] EXTRA_SUPPLICANT_CONNECTED = " + connected);
            Toast.makeText(context, "Supplicant connection changed -> " + connected, Toast.LENGTH_SHORT).show();
        } else if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
            Log.d(TAG, "SUPPLICANT_STATE_CHANGED_ACTION");
            SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
            Log.d(TAG, "[+] EXTRA_NEW_STATE = " + state);
            Toast.makeText(context, "Supplicant state changed -> " + state, Toast.LENGTH_SHORT).show();

            if (state == SupplicantState.COMPLETED) {
                // Get the SSID/BSSID of the newly-connected network
                WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo == null) return;
                String ssid = wifiInfo.getSSID();
                String bssid = wifiInfo.getBSSID();
                Log.d(TAG, String.format("Connected to network with SSID: %s, BSSID: %s", ssid, bssid));
                context.startService(loginIntent);
            } else if (state == SupplicantState.DISCONNECTED) {
                Log.d(TAG, "Disconnected from WiFi network");
                context.stopService(loginIntent);
            }
        }
    }
}
