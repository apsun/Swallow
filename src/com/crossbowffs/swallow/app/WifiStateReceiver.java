package com.crossbowffs.swallow.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiStateReceiver extends BroadcastReceiver {
    private static final String TAG = WifiStateReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Intent loginIntent = new Intent(context, LoginService.class);

        if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)) {
            SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
            Log.d(TAG, "Supplicant state -> " + state);
            if (state == SupplicantState.COMPLETED) {
                context.startService(loginIntent);
            }
        } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
            Log.d(TAG, "WiFi state -> " + state);
            if (state == WifiManager.WIFI_STATE_DISABLED) {
                context.stopService(loginIntent);
            }
        }
    }
}
