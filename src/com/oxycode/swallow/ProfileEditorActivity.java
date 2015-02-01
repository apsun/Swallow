package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.*;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.util.*;

public class ProfileEditorActivity extends ListActivity {
    private static class ScanResultViewHolder {
        CheckBox enabledCheckBox;
        TextView bssidTextView;
        TextView ssidTextView;
        ImageView levelImageView;
        TextView levelTextView;
    }

    private class ScanResultListAdapter extends BaseAdapter implements CompoundButton.OnCheckedChangeListener {
        private LayoutInflater _layoutInflater;
        private Comparator<ScanResult> _resultSorter;
        private List<ScanResult> _results;

        public ScanResultListAdapter() {
            this(new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult lhs, ScanResult rhs) {
                    // Sort from strongest to weakest signal
                    return rhs.level - lhs.level;
                }
            });
        }

        public ScanResultListAdapter(Comparator<ScanResult> sorter) {
            _layoutInflater = (LayoutInflater)ProfileEditorActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            _resultSorter = sorter;
        }

        public void updateResults(List<ScanResult> results) {
            _results.clear();
            _results.addAll(results);
            Collections.sort(_results, _resultSorter);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return _results.size();
        }

        @Override
        public ScanResult getItem(int position) {
            return _results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ScanResultViewHolder viewHolder;
            if (convertView == null) {
                convertView = _layoutInflater.inflate(R.layout.network_scanresult_listitem, null);
                viewHolder = new ScanResultViewHolder();
                viewHolder.enabledCheckBox = (CheckBox)convertView.findViewById(R.id.network_scan_enabled_checkbox);
                viewHolder.enabledCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // TODO: Update selection value
                    }
                });
                viewHolder.bssidTextView = (TextView)convertView.findViewById(R.id.network_scan_bssid_textview);
                viewHolder.ssidTextView = (TextView)convertView.findViewById(R.id.network_scan_detail_textview);
                viewHolder.levelImageView = (ImageView)convertView.findViewById(R.id.network_scan_level_imageview);
                viewHolder.levelTextView = (TextView)convertView.findViewById(R.id.network_scan_level_textview);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ScanResultViewHolder)convertView.getTag();
            }

            ScanResult scanResult = getItem(position);
            String ssid = scanResult.SSID;
            String bssid = scanResult.BSSID;
            int level = scanResult.level;
            // boolean checked = ProfileEditorActivity.this._profile.contains(new Bssid(bssid));

            // TODO: Read checked value
            // viewHolder.enabledCheckBox.setChecked(checked);
            viewHolder.bssidTextView.setText(bssid);
            viewHolder.ssidTextView.setText(ssid);
            viewHolder.levelTextView.setText(String.valueOf(level));

            return convertView;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        }
    }

    private static final String TAG = ProfileEditorActivity.class.getSimpleName();
    private static final String PREF_SHOW_SHS_ONLY_KEY = "pref_show_shs_only";
    private static final String PREF_SCAN_RATE_KEY = "pref_scan_rate";
    private static final String PREF_MINIMUM_SIGNAL_STRENGTH_KEY = "pref_minimum_signal_strength";
    public static final String EXTRA_PROFILE_NAME = "profileName";

    private Timer _scanTimer;
    private TimerTask _scanTask;
    private WifiManager _wifiManager;
    private BroadcastReceiver _scanReceiver;

    // private NetworkProfile.Editor _profile;

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
        String profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        // NetworkProfile profile = null /* get profile somehow */;
        // _profile = profile.edit();

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

        // Get inner views
        _bssidListView = getListView();
        _addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        _addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);

        // Hook up the "add manually" button to the BSSID entry dialog
        _addManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddBssidDialog();
            }
        });

        _addAllInRangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: Handle "add all in range" button click
            }
        });

        // Get shared preferences (we only read the scanner preferences)
        // TODO: Refactor scanner preferences into a separate menu
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
                    stopScanTimer();
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

    private void showAddBssidDialog() {
        View promptView = getLayoutInflater().inflate(R.layout.textedit_dialog, null);
        final EditText editText = (EditText)promptView.findViewById(R.id.textedit_dialog_edittext);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setView(promptView)
            .setTitle(R.string.enter_bssid)
            .setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onAddBssidDialogFinished(editText.getText().toString());
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

        AlertDialog alert = builder.create();
        alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alert.show();
    }

    private void onAddBssidDialogFinished(String bssidText) {
        // Bssid bssid;
        try {
            // bssid = new Bssid(bssidText);
        } catch (IllegalArgumentException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.invalid_bssid_title)
                .setMessage(R.string.invalid_bssid_message)
                .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

            AlertDialog alert = builder.create();
            alert.show();
            return;
        }

        // _profile.put(bssid, true);
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

        // TODO: Remove below, actually implement listview stuff
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

    private void stopScanTimer() {
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
        stopScanTimer();
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
}
