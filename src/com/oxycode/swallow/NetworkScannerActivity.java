package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.util.*;

public class NetworkScannerActivity extends Activity implements TextEntryDialog.Listener {
    private static final String TAG = "SWAL";

    private Timer _scanTimer;
    private ScanTask _scanTask;
    private WifiManager _wifiManager;
    private ScanResultsReceiver _scanReceiver;

    private HashSet<Bssid> _foundBssids;
    private NetworkProfile _profile;

    private ListView _bssidListView;
    private Button _addManuallyButton;
    private Button _addAllInRangeButton;

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

        // Initialize empty set to hold scan results
        // Use HashSet here because order isn't important
        // and we're going to sort by RSSI later anyways.
        _foundBssids = new HashSet<Bssid>();

        // Get the system WiFi manager for scanning
        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);

        // Timer used to periodically initiate network scans
        _scanTimer = new Timer();

        _bssidListView = (ListView)findViewById(R.id.bssid_listview);
        _addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        _addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);

        _addManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextEntryDialog dialog = new TextEntryDialog();
                Bundle arguments = new Bundle();
                arguments.putString("title", "Add BSSID");
                dialog.setArguments(arguments);
                FragmentManager fragmentManager = getFragmentManager();
                dialog.show(fragmentManager, "add_bssid_dialog");
            }
        });
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
        List<ScanResult> results = _wifiManager.getScanResults();
        _foundBssids.clear();
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
            case R.id.scan_settings_menu:
                Intent intent = new Intent(this, ScannerSettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTextEntryDialogOk(String tag, String text) {
        if (!tag.equals("add_bssid_dialog")) return;
        Bssid bssid;
        try {
            bssid = new Bssid(text);
        } catch (IllegalArgumentException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Invalid BSSID");
            builder.setMessage("BSSID must be in the format xx:xx:xx:xx:xx:xx!");
            builder.setCancelable(false);
            builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
            return;
        }

        addBssidToProfile(bssid);
    }

    @Override
    public void onTextEntryDialogCancel(String tag) {

    }
}
