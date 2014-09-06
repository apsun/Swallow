package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NetworkScannerActivity extends Activity {
    private static final String TAG = "SWAL";

    private Timer _scanTimer;
    private ScanTask _scanTask;
    private WifiManager _wifiManager;
    private ScanResultsReceiver _scanReceiver;

    private ArrayList<Bssid> _foundBssids;
    private NetworkProfile _profile;

    private class ScanResultsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) return;
            NetworkScannerActivity.this.onWifiScanCompleted();
        }
    }

    private class ScanTask extends TimerTask {
        @Override
        public void run() {
            NetworkScannerActivity.this.startWifiScan();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_scanner);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Get the profile we're editing from the intent
        _profile = getIntent().getParcelableExtra("profile");
        _foundBssids = new ArrayList<Bssid>();

        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        _scanTimer = new Timer();

        ListView bssidListView = (ListView)findViewById(R.id.bssid_listview);
        Button addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        Button addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.network_scanner_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void addBssidToProfile(Bssid bssid) {
        _profile.add(bssid);
    }

    private void removeBssidFromProfile(Bssid bssid) {
        _profile.remove(bssid);
    }

    private void startWifiScan() {
        _wifiManager.startScan();
    }

    private void onWifiScanCompleted() {
        _foundBssids.clear();
        List<ScanResult> results = _wifiManager.getScanResults();
        ListView bssidListView = (ListView)findViewById(R.id.bssid_listview);
        for (ScanResult result : results) {
            _foundBssids.add(new Bssid(result.BSSID));
            Log.d(TAG, "Found BSSID: " + result.BSSID);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (_scanReceiver == null) {
            _scanReceiver = new ScanResultsReceiver();
        }
        registerReceiver(_scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        _scanTask = new ScanTask();
        _scanTimer.scheduleAtFixedRate(_scanTask, 0, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(_scanReceiver);
        _scanTask.cancel();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
