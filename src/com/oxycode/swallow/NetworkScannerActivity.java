package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ListActivity;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.util.*;

public class NetworkScannerActivity extends ListActivity implements TextEntryDialog.Listener {
    private static final String TAG = NetworkScannerActivity.class.getName();
    private static final String ADD_BSSID_DIALOG_TAG = "add_bssid_dialog";
    private static final String PREF_SHOW_SHS_ONLY_KEY = "pref_show_shs_only";
    private static final String PREF_SCAN_RATE_KEY = "pref_scan_rate";
    private static final String PREF_MINIMUM_SIGNAL_STRENGTH_KEY = "pref_minimum_signal_strength";

    private Timer _scanTimer;
    private TimerTask _scanTask;
    private WifiManager _wifiManager;
    private BroadcastReceiver _scanReceiver;

    private NetworkProfile _profile;

    private ListView _bssidListView;
    private Button _addManuallyButton;
    private Button _addAllInRangeButton;
    private MenuItem _refreshMenuItem;

    private SharedPreferences _preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener _preferenceChangedListener;

    private int _minimumSignalStrength;
    private int _scanRate;
    private boolean _showShsOnly;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_scanner_activity);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Get the profile we're editing from the intent
        _profile = getIntent().getParcelableExtra("profile");

        // Get the system WiFi manager for scanning
        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);

        // Timer used to periodically initiate network scans
        _scanTimer = new Timer();
        _scanTask = null;

        // Broadcast receiver that notifies us when a
        // WiFi scan has completed
        _scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    onWifiScanCompleted();
                }
            }
        };

        // _bssidListView = (ListView)findViewById(R.id.bssid_listview);
        _addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        _addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);

        _addManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextEntryDialog dialog = new TextEntryDialog();
                Bundle arguments = new Bundle();
                arguments.putString(TextEntryDialog.TITLE, getString(R.string.enter_bssid));
                dialog.setArguments(arguments);
                FragmentManager fragmentManager = getFragmentManager();
                dialog.show(fragmentManager, ADD_BSSID_DIALOG_TAG);
            }
        });

        _preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Attatch our preference change listener so we can update
        // the preference fields here as soon as they are set
        _preferenceChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (PREF_MINIMUM_SIGNAL_STRENGTH_KEY.equals(key)) {
                    _minimumSignalStrength = readPrefMinimumStrength();
                } else if (PREF_SCAN_RATE_KEY.equals(key)) {
                    _scanRate = readPrefScanRate();
                    stopScanTmer();
                    startScanTimer();
                } else if (PREF_SHOW_SHS_ONLY_KEY.equals(key)) {
                    _showShsOnly = readPrefShowShsOnly();
                }
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.network_scanner_options_menu, menu);
        _refreshMenuItem = menu.findItem(R.id.refresh_networks_button);
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (_refreshMenuItem.getActionView() == null) {
                    _refreshMenuItem.setActionView(R.layout.refresh_layout);
                }
            }
        });
    }

    private void onWifiScanCompleted() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _refreshMenuItem.setActionView(null);
            }
        });
        List<ScanResult> results = _wifiManager.getScanResults();
        Collections.sort(results, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                // Compare by subtracting lhs from rhs, since we want
                // the higher strength networks to come first
                return rhs.level - lhs.level;
            }
        });

        Log.d(TAG, "--BEGIN WIFI LIST--");
        for (ScanResult result : results) {
            if (_showShsOnly && !"shs".equalsIgnoreCase(result.SSID)) continue;
            if (_minimumSignalStrength > result.level) continue;
            Log.d(TAG, String.format("[%s] %s -> %d", result.SSID, result.BSSID, result.level));
        }
        Log.d(TAG, "--END WIFI LIST--");
    }

    private int readPrefMinimumStrength() {
        return Integer.parseInt(_preferences.getString(PREF_MINIMUM_SIGNAL_STRENGTH_KEY, null));
    }

    private int readPrefScanRate() {
        return Integer.parseInt(_preferences.getString(PREF_SCAN_RATE_KEY, null));
    }

    private boolean readPrefShowShsOnly() {
        return _preferences.getBoolean(PREF_SHOW_SHS_ONLY_KEY, false);
    }

    private void stopScanTmer() {
        if (_scanTask != null) {
            _scanTask.cancel();
            _scanTask = null;
        }
    }

    private void startScanTimer() {
        if (_scanRate >= 0) {
            _scanTask = new TimerTask() {
                @Override
                public void run() {
                    startWifiScan();
                }
            };

            // TODO: Fix this hack!
            // For some reason onCreateOptionsMenu is called AFTER onResume
            // This means that if we scan with 0 delay, we will crash with a NPE
            // since the refresh menu item hasn't been set yet.
            _scanTimer.scheduleAtFixedRate(_scanTask, _scanRate * 1000, _scanRate * 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reload preferences
        // This is necessary because we don't listen for
        // preference change events when the activity is paused.
        _minimumSignalStrength = readPrefMinimumStrength();
        _scanRate = readPrefScanRate();
        _showShsOnly = readPrefShowShsOnly();

        // Start listening for preference change events
        _preferences.registerOnSharedPreferenceChangeListener(_preferenceChangedListener);

        // Begin scanning for WiFi networks
        registerReceiver(_scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        startScanTimer();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop scanning for WiFi networks
        // This saves battery, since we are not uselessly
        // refreshing the WiFi list in the background.
        stopScanTmer();
        unregisterReceiver(_scanReceiver);

        // Stop listening for preference change events
        _preferences.unregisterOnSharedPreferenceChangeListener(_preferenceChangedListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.refresh_networks_button:
                startWifiScan();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTextEntryDialogOk(String tag, String text) {
        if (!tag.equals(ADD_BSSID_DIALOG_TAG)) return;
        Bssid bssid;
        try {
            bssid = new Bssid(text);
        } catch (IllegalArgumentException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.invalid_bssid_title));
            builder.setMessage(getString(R.string.invalid_bssid_message));
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
