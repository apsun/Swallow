package com.oxycode.swallow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
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

        // TODO: Check that BSSID is whitelisted

        Intent loginIntent = new Intent(context, LoginService.class);
        context.startService(loginIntent);
    }
}
