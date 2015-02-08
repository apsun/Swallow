package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.*;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.oxycode.swallow.provider.NetworkProfileContract;
import com.oxycode.swallow.utils.DialogUtils;
import com.oxycode.swallow.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileEditorActivity extends ListActivity {
    private static class ScanResultViewHolder {
        CheckBox enabledCheckBox;
        TextView bssidTextView;
        TextView ssidTextView;
        TextView levelTextView;
    }

    private class ScanResultListAdapter extends BaseAdapter implements CompoundButton.OnCheckedChangeListener {
        private Comparator<ScanResult> _resultSorter;
        private ArrayList<ScanResult> _results;

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
            _resultSorter = sorter;
            _results = new ArrayList<ScanResult>();
        }

        public void updateResults(List<ScanResult> results) {
            _results.clear();

            boolean showShsOnly = _preferences.getBoolean(PREF_KEY_SHOW_SHS_ONLY, true);
            int minSignalStrength = PreferenceUtils.getInt(_preferences, PREF_KEY_MINIMUM_SIGNAL_STRENGTH, -80);

            for (ScanResult result : results) {
                // Ignore non-shs networks?
                if (showShsOnly && !"shs".equalsIgnoreCase(result.SSID)) {
                    continue;
                }

                // Is network signal strong enough?
                if (minSignalStrength > result.level) {
                    continue;
                }

                _results.add(result);
            }

            // Sort the networks by signal strength
            Collections.sort(_results, _resultSorter);

            notifyDataSetChanged();
        }

        private int getNetworkLevelImageId(int level) {
            if (level > -50) return R.drawable.ic_wifi_signal_4;
            if (level > -60) return R.drawable.ic_wifi_signal_3;
            if (level > -70) return R.drawable.ic_wifi_signal_2;
            return R.drawable.ic_wifi_signal_1;
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
                LayoutInflater layoutInflater = getLayoutInflater();
                convertView = layoutInflater.inflate(R.layout.network_listitem, parent, false);
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
                viewHolder.levelTextView = (TextView)convertView.findViewById(R.id.network_scan_level_textview);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ScanResultViewHolder)convertView.getTag();
            }

            ScanResult scanResult = getItem(position);
            String ssid = scanResult.SSID;
            String bssid = scanResult.BSSID;
            int level = scanResult.level;
            int levelImageId = getNetworkLevelImageId(level);

            // TODO: Read checked value
            // viewHolder.enabledCheckBox.setChecked(checked);
            viewHolder.bssidTextView.setText(bssid);
            viewHolder.ssidTextView.setText(ssid);
            viewHolder.levelTextView.setCompoundDrawablesWithIntrinsicBounds(0, levelImageId, 0, 0);
            viewHolder.levelTextView.setText(String.valueOf(level));

            return convertView;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        }
    }

    private static final String TAG = ProfileEditorActivity.class.getSimpleName();

    public static final String EXTRA_PROFILE_ROW_ID = "profileId";

    private static final String PREF_KEY_SHOW_SHS_ONLY = "pref_show_shs_only";
    private static final String PREF_KEY_SCAN_RATE = "pref_scan_rate";
    private static final String PREF_KEY_MINIMUM_SIGNAL_STRENGTH = "pref_minimum_signal_strength";

    private static final Pattern BSSID_PATTERN = Pattern.compile("^([0-9a-f]{2}:){5}([0-9a-f]{2})$");

    private SharedPreferences _preferences;
    private Handler _handler;
    private Runnable _scanWifiNetworksTask;
    private WifiManager _wifiManager;
    private BroadcastReceiver _scanReceiver;
    private ScanResultListAdapter _listAdapter;
    private long _profileRowId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.editor_activity);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        _preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Get the profile we're editing from the intent
        _profileRowId = getIntent().getLongExtra(EXTRA_PROFILE_ROW_ID, -1);

        // Get the system WiFi manager for scanning
        _wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);

        _handler = new Handler();
        _scanWifiNetworksTask = new Runnable() {
            @Override
            public void run() {
                startWifiScan();
            }
        };

        // Broadcast receiver that notifies us when a WiFi scan has completed
        _scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    onWifiScanCompleted();
                }
            }
        };

        _listAdapter = new ScanResultListAdapter();
        setListAdapter(_listAdapter);

        ListView listView = getListView();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CheckBox enabledCheckBox = (CheckBox)view.findViewById(R.id.network_scan_enabled_checkbox);
                enabledCheckBox.performClick();
            }
        });

        Button addManuallyButton = (Button)findViewById(R.id.add_manually_button);
        addManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddBssidDialog();
            }
        });

        Button addAllInRangeButton = (Button)findViewById(R.id.add_all_in_range_button);
        addAllInRangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addAllNetworksInRange();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Begin scanning for WiFi networks
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(_scanReceiver, filter);
        startWifiScan();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop scanning for WiFi networks
        // This saves battery, since we are not uselessly
        // refreshing the WiFi list in the background.
        cancelEnqueuedWifiScan();
        unregisterReceiver(_scanReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_action_menu, menu);
        return super.onCreateOptionsMenu(menu);
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

    private void addBssid(String bssid) {
        bssid = bssid.toLowerCase();
        Matcher matcher = BSSID_PATTERN.matcher(bssid);
        if (matcher.matches()) {
            ContentValues values = new ContentValues();
            values.put(NetworkProfileContract.Bssids.PROFILE_ID, _profileRowId);
            values.put(NetworkProfileContract.Bssids.BSSID, bssid);
            Uri uri = getContentResolver().insert(NetworkProfileContract.Bssids.CONTENT_URI, values);
            long bssidRowId = ContentUris.parseId(uri);
            if (bssidRowId < 0) {
                Log.d(TAG, "BSSID database insert returned " + bssidRowId + ", probably duplicate");
            }
        } else {
            showInvalidBssidDialog();
        }
    }

    private void addAllNetworksInRange() {
        // TODO: Implement
    }

    private void startWifiScan() {
        _wifiManager.startScan();
    }

    private void onWifiScanCompleted() {
        List<ScanResult> results = _wifiManager.getScanResults();
        _listAdapter.updateResults(results);
        enqueueWifiScan();
    }

    private void enqueueWifiScan() {
        // Make sure we don't try to scan multiple times
        cancelEnqueuedWifiScan();

        int delay = PreferenceUtils.getInt(_preferences, PREF_KEY_SCAN_RATE, 2);
        if (delay > 0) {
            Log.d(TAG, "Enqueued WiFi scan with delay " + delay + " seconds");
            _handler.postDelayed(_scanWifiNetworksTask, delay * 1000);
        }
    }

    private void cancelEnqueuedWifiScan() {
        _handler.removeCallbacks(_scanWifiNetworksTask);
    }

    private void showAddBssidDialog() {
        DialogUtils.showTextEntryDialog(this,
            getString(R.string.network_bssid),
            getString(R.string.add),
            new DialogUtils.TextEntryDialogHandler() {
                @Override
                public void onSubmit(String text) {
                    addBssid(text);
                }
            }
        );
    }

    private void showInvalidBssidDialog() {
        DialogUtils.showMessageDialog(this,
            getString(R.string.invalid_bssid_title),
            getString(R.string.invalid_bssid_message)
        );
    }
}
