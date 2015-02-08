package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.ListActivity;
import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileEditorActivity extends ListActivity {
    private static class ScanResultViewHolder {
        CheckBox enabledCheckBox;
        TextView bssidTextView;
        TextView ssidTextView;
        TextView levelTextView;
    }

    private class ScanResultListAdapter extends BaseAdapter {
        private Comparator<ScanResult> _resultSorter;
        private Set<String> _whitelist;
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
            _resultSorter = sorter;
            _whitelist = new HashSet<String>(0);
            _results = new ArrayList<ScanResult>();
        }

        public boolean isBssidWhitelisted(String bssid) {
            return _whitelist.contains(bssid);
        }

        public void updateWhitelist(Set<String> whitelist) {
            _whitelist = whitelist;
            notifyDataSetChanged();
        }

        public List<ScanResult> getAllNetworks() {
            return _results;
        }

        public void updateNetworks(List<ScanResult> networks) {
            _results.clear();

            boolean showShsOnly = _preferences.getBoolean(PREF_KEY_SHOW_SHS_ONLY, true);
            int minSignalStrength = PreferenceUtils.getInt(_preferences, PREF_KEY_MINIMUM_SIGNAL_STRENGTH, -80);

            for (ScanResult network : networks) {
                // Ignore non-shs networks?
                if (showShsOnly && !"shs".equalsIgnoreCase(network.SSID)) {
                    continue;
                }

                // Is network signal strong enough?
                if (minSignalStrength > network.level) {
                    continue;
                }

                _results.add(network);
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
                viewHolder.bssidTextView = (TextView)convertView.findViewById(R.id.network_scan_bssid_textview);
                viewHolder.ssidTextView = (TextView)convertView.findViewById(R.id.network_scan_detail_textview);
                viewHolder.levelTextView = (TextView)convertView.findViewById(R.id.network_scan_level_textview);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ScanResultViewHolder)convertView.getTag();
            }

            ScanResult scanResult = getItem(position);
            String ssid = scanResult.SSID;
            final String bssid = scanResult.BSSID;
            int level = scanResult.level;
            int levelImageId = getNetworkLevelImageId(level);
            boolean enabled = isBssidWhitelisted(bssid);

            viewHolder.enabledCheckBox.setOnCheckedChangeListener(null);
            viewHolder.enabledCheckBox.setChecked(enabled);
            viewHolder.bssidTextView.setText(bssid);
            viewHolder.ssidTextView.setText(ssid);
            viewHolder.levelTextView.setCompoundDrawablesWithIntrinsicBounds(0, levelImageId, 0, 0);
            viewHolder.levelTextView.setText(String.valueOf(level));
            viewHolder.enabledCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    // TODO: GTFO THE UI THREAD!
                    if (isChecked && !addBssid(bssid)) {
                        // TODO: Handle error
                    } else if (!isChecked && !removeBssid(bssid)) {
                        // TODO: Handle error
                    }
                }
            });

            return convertView;
        }
    }

    private class RefreshBssidsTask extends AsyncTask<Long, Void, Set<String>> {
        @Override
        protected Set<String> doInBackground(Long... params) {
            Uri uri = NetworkProfileContract.Bssids.CONTENT_URI;
            String[] projection = {NetworkProfileContract.Bssids.BSSID};
            String where = NetworkProfileContract.Bssids.PROFILE_ID + "=?";
            String[] whereArgs = {params[0].toString()};
            Cursor cursor = getContentResolver().query(uri, projection, where, whereArgs, null);
            int bssidColumn = cursor.getColumnIndexOrThrow(NetworkProfileContract.Bssids.BSSID);
            HashSet<String> whitelist = new HashSet<String>(cursor.getCount());
            while (cursor.moveToNext()) {
                whitelist.add(cursor.getString(bssidColumn));
            }
            cursor.close();
            return whitelist;
        }

        @Override
        protected void onPostExecute(Set<String> whitelist) {
            onWhitelistRefreshCompleted(whitelist);
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
    private ContentObserver _contentObserver;
    private ScanResultListAdapter _listAdapter;
    private long _profileRowId;
    private AsyncTask<?, ?, ?> _activeTask;

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

        _contentObserver = new ContentObserver(_handler) {
            @Override
            public boolean deliverSelfNotifications() {
                // TODO: Do we need this to be true?
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                beginRefreshWhitelist();
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
                // TODO: GTFO THE UI THREAD!
                if (!addAllNetworksInRange()) {
                    // TODO: Handle error
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Begin listening for whitelist database changes
        Uri profilesUri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, _profileRowId);
        getContentResolver().registerContentObserver(profilesUri, false, _contentObserver);

        // Load the BSSID whitelist (async)
        beginRefreshWhitelist();

        // Register receiver for WiFi scan completion event
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(_scanReceiver, filter);

        // Begin scanning for WiFi networks
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

        // Unregister listener for whitelist database changes
        getContentResolver().unregisterContentObserver(_contentObserver);
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

    private boolean addBssid(String bssid) {
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
            return true;
        } else {
            return false;
        }
    }

    private boolean addAllNetworksInRange() {
        List<ScanResult> networks = _listAdapter.getAllNetworks();
        ContentValues[] values = new ContentValues[networks.size()];
        Matcher matcher = BSSID_PATTERN.matcher("");
        for (int i = 0; i < networks.size(); ++i) {
            String bssid = networks.get(i).BSSID.toLowerCase();
            matcher.reset(bssid);
            if (matcher.matches()) {
                ContentValues value = new ContentValues();
                value.put(NetworkProfileContract.Bssids.PROFILE_ID, _profileRowId);
                value.put(NetworkProfileContract.Bssids.BSSID, bssid);
                values[i] = value;
            } else {
                return false;
            }
        }

        int insertedCount = getContentResolver().bulkInsert(NetworkProfileContract.Bssids.CONTENT_URI, values);
        Log.d(TAG, "Tried to insert " + values.length + " BSSID(s), " + insertedCount + " succeeded");
        return true;
    }

    private boolean removeBssid(String bssid) {
        String where = NetworkProfileContract.Bssids.BSSID + "=? AND " + NetworkProfileContract.Bssids.PROFILE_ID + "=?";
        String[] whereArgs = {bssid, String.valueOf(_profileRowId)};
        return getContentResolver().delete(NetworkProfileContract.Bssids.CONTENT_URI, where, whereArgs) == 1;
    }

    private void beginRefreshWhitelist() {
        if (_activeTask != null) {
            _activeTask.cancel(true);
        }
        _activeTask = new RefreshBssidsTask().execute(_profileRowId);
    }

    private void onWhitelistRefreshCompleted(Set<String> whitelist) {
        Log.d(TAG, "BSSID whitelist refreshed");
        _listAdapter.updateWhitelist(whitelist);
        _activeTask = null;
    }

    private void startWifiScan() {
        _wifiManager.startScan();
    }

    private void onWifiScanCompleted() {
        List<ScanResult> results = _wifiManager.getScanResults();
        _listAdapter.updateNetworks(results);
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
                    // TODO: GTFO THE UI THREAD!
                    if (!addBssid(text)) {
                        showInvalidBssidDialog();
                    }
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
