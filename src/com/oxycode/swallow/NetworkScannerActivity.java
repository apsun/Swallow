package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

public class NetworkScannerActivity extends Activity implements TextEntryDialog.Listener {
    private static final String TAG = "SWAL";

    private Timer _scanTimer;
    private TimerTask _scanTask;
    private WifiManager _wifiManager;
    private BroadcastReceiver _scanReceiver;

    private TreeSet<ScanResult> _foundNetworks;
    private NetworkProfile _profile;

    private ListView _bssidListView;
    private Button _addManuallyButton;
    private Button _addAllInRangeButton;

    private boolean _isPaused;

    private SharedPreferences _preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener _preferenceChangedListener;

    private int _minimumSignalStrength;
    private int _scanRate;
    private boolean _showShsOnly;

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

        // Initialize empty TreeSet to hold scan results.
        // Networks are sorted by signal strength, from
        // strongest to weakest.
        _foundNetworks = new TreeSet<ScanResult>(new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                // Compare by subtracting lhs from rhs, since we want
                // the higher strength networks to come first
                return lhs.level - rhs.level;
            }
        });

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

        _bssidListView = (ListView)findViewById(R.id.bssid_listview);
        _addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        _addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);

        _addManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextEntryDialog dialog = new TextEntryDialog();
                Bundle arguments = new Bundle();
                arguments.putString("title", getString(R.string.add_bssid_title));
                dialog.setArguments(arguments);
                FragmentManager fragmentManager = getFragmentManager();
                dialog.show(fragmentManager, "add_bssid_dialog");
            }
        });

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        _preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Get the relevant preferences
        _minimumSignalStrength = Integer.parseInt(_preferences.getString("pref_minimum_signal_strength", null));
        _scanRate = Integer.parseInt(_preferences.getString("pref_scan_rate", null));
        _showShsOnly = _preferences.getBoolean("pref_show_shs_only", false);

        // Attatch our preference change listener so
        // we can update the preference fields here
        // as soon as they are set
        _preferenceChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("pref_minimum_signal_strength".equals(key)) {
                    _minimumSignalStrength = Integer.parseInt(sharedPreferences.getString("pref_minimum_signal_strength", null));
                } else if ("pref_scan_rate".equals(key)) {
                    _scanRate = Integer.parseInt(sharedPreferences.getString("pref_scan_rate", null));
                    if (!_isPaused) {
                        restartScanTimer();
                    }
                } else if ("pref_show_shs_only".equals(key)) {
                    _showShsOnly = sharedPreferences.getBoolean("pref_show_shs_only", false);
                }
            }
        };
        _preferences.registerOnSharedPreferenceChangeListener(_preferenceChangedListener);
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
        _foundNetworks.clear();
        for (ScanResult result : _wifiManager.getScanResults()) {
            _foundNetworks.add(result);
        }
    }

    private void restartScanTimer() {
        if (_scanTask != null) {
            _scanTask.cancel();
        }

        if (_scanRate >= 0) {
            _scanTask = new TimerTask() {
                @Override
                public void run() {
                    startWifiScan();
                }
            };

            _scanTimer.scheduleAtFixedRate(_scanTask, 0, _scanRate * 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        _isPaused = false;

        // Register the scan results receiver
        registerReceiver(_scanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        if (_scanRate >= 0) {
            // Create a new WiFi scan task. These cannot be
            // re-used, so we have to create a new one every time.
            _scanTask = new TimerTask() {
                @Override
                public void run() {
                    startWifiScan();
                }
            };

            // Begin the scan timer
            _scanTimer.scheduleAtFixedRate(_scanTask, 0, _scanRate * 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        _isPaused = true;

        // Unregister the scan results receiver
        // This saves battery, since we are not uselessly
        // refreshing the WiFi list in the background.
        unregisterReceiver(_scanReceiver);

        // Stop the WiFi scan task
        if (_scanTask != null) {
            _scanTask.cancel();
            _scanTask = null;
        }
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
