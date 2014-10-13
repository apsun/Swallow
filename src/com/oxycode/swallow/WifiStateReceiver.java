package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only activate the login service when connected to a new BSS.
        // Use SUPPLICANT_STATE_CHANGED_ACTION instead of NETWORK_STATE_CHANGED_ACTION,
        // since NETWORK_STATE_CHANGED_ACTION does not notify us when the BSSID of
        // the connected AP changes.
        String action = intent.getAction();
        if (!action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) return;
        SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
        if (state != SupplicantState.COMPLETED) return;

        // Get the BSSID of the newly-connected network
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) return;
        String ssidStr = wifiInfo.getSSID();
        String bssidStr = wifiInfo.getBSSID();
        if (ssidStr == null || bssidStr == null) return;
        Bssid bssid = new Bssid(bssidStr);
        Log.d(TAG, String.format("Connected to network with SSID: %s, BSSID: %s", ssidStr, bssidStr));

        // Start the login service, which will also perform the other state checks
        Intent loginIntent = new Intent(context, LoginService.class);
        loginIntent.putExtra(LoginService.NETWORK_BSSID_EXTRA, bssid);
        context.startService(loginIntent);
    }
}
