package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        // Only activate the login service when network state changes to connected
        String action = intent.getAction();
        if (!action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) return;
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        NetworkInfo.State state = networkInfo.getState();
        if (state != NetworkInfo.State.CONNECTED) return;

        // Get the BSSID of the newly-connected network from the intent parameters
        String bssidStr = intent.getStringExtra(WifiManager.EXTRA_BSSID);
        Bssid bssid = new Bssid(bssidStr);

        // Start the login service, which will also perform the other state checks
        Intent loginIntent = new Intent(context, LoginService.class);
        loginIntent.putExtra(LoginService.NETWORK_BSSID_EXTRA, bssid);
        context.startService(loginIntent);
    }
}
