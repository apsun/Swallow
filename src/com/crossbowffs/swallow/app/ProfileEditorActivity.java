package com.crossbowffs.swallow.app;

import android.app.ActionBar;
import android.app.AlertDialog;
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
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.crossbowffs.swallow.R;
import com.crossbowffs.swallow.provider.NetworkProfileContract;
import com.crossbowffs.swallow.utils.DialogUtils;
import com.crossbowffs.swallow.utils.PreferenceUtils;

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
        private HashSet<String> _whitelist;
        private ArrayList<ScanResult> _results;

        public ScanResultListAdapter() {
            this(new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult lhs, ScanResult rhs) {
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

        public void updateWhitelist(HashSet<String> whitelist) {
            _whitelist = whitelist;
            notifyDataSetChanged();
        }

        public ArrayList<ScanResult> getAllNetworks() {
            return _results;
        }

        public boolean updateNetworks(List<ScanResult> networks) {
            _results.clear();

            boolean showShsOnly = _preferences.getBoolean(PREF_KEY_SHOW_SHS_ONLY, false);
            int minSignalStrength = PreferenceUtils.getInt(_preferences, PREF_KEY_MINIMUM_SIGNAL_STRENGTH, -80);

            boolean filtered = false;
            for (ScanResult network : networks) {
                // Ignore non-shs networks?
                if (showShsOnly && !"shs".equalsIgnoreCase(network.SSID)) {
                    filtered = true;
                    continue;
                }

                // Is network signal strong enough?
                if (minSignalStrength > network.level) {
                    filtered = true;
                    continue;
                }

                _results.add(network);
            }

            // Sort the networks by signal strength
            Collections.sort(_results, _resultSorter);

            notifyDataSetChanged();

            return filtered;
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
                    if (isChecked) {
                        addBssid(bssid);
                    } else {
                        removeBssid(bssid);
                    }
                }
            });

            return convertView;
        }
    }

    private class RefreshBssidsTask extends AsyncTask<Void, Void, HashSet<String>> {
        @Override
        protected HashSet<String> doInBackground(Void... params) {
            Uri uri = NetworkProfileContract.Bssids.CONTENT_URI;
            String[] projection = {NetworkProfileContract.Bssids.BSSID};
            String where = NetworkProfileContract.Bssids.PROFILE_ID + "=?";
            String[] whereArgs = {String.valueOf(_profileRowId)};
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
        protected void onPostExecute(HashSet<String> result) {
            onWhitelistRefreshCompleted(result);
        }
    }

    private class AddBssidTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            if (params.length == 1) {
                addSingle(params[0]);
            } else if (params.length > 1) {
                addMulti(params);
            }
            return null;
        }

        private ContentValues createContentValues(long profileId, String bssid) {
            ContentValues values = new ContentValues();
            values.put(NetworkProfileContract.Bssids.PROFILE_ID, profileId);
            values.put(NetworkProfileContract.Bssids.BSSID, bssid);
            return values;
        }

        private void addSingle(String bssid) {
            ContentValues values = createContentValues(_profileRowId, bssid);
            Uri uri;
            try {
                uri = getContentResolver().insert(NetworkProfileContract.Bssids.CONTENT_URI, values);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to add BSSID to profile", e);
                throw e;
            }

            long bssidRowId = ContentUris.parseId(uri);
            if (bssidRowId < 0) {
                Log.d(TAG, "BSSID database insert returned " + bssidRowId);
            }
        }

        private void addMulti(String[] bssids) {
            long profileId = _profileRowId;
            ContentValues[] values = new ContentValues[bssids.length];
            for (int i = 0; i < values.length; ++i) {
                values[i] = createContentValues(profileId, bssids[i]);
            }

            int insertedCount;
            try {
                insertedCount = getContentResolver().bulkInsert(NetworkProfileContract.Bssids.CONTENT_URI, values);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to bulk add BSSIDs to profile", e);
                throw e;
            }

            Log.d(TAG, "Tried to insert " + values.length + " BSSID(s), " + insertedCount + " succeeded");
        }
    }

    private class DeleteBssidTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String where = NetworkProfileContract.Bssids.BSSID + "=? AND " +
                           NetworkProfileContract.Bssids.PROFILE_ID + "=?";
            String[] whereArgs = {params[0], String.valueOf(_profileRowId)};
            int deletedRows = getContentResolver().delete(NetworkProfileContract.Bssids.CONTENT_URI, where, whereArgs);
            Log.d(TAG, "Tried to delete 1 BSSID, " + deletedRows + " succeeded");
            return null;
        }
    }

    private static final String TAG = ProfileEditorActivity.class.getSimpleName();

    public static final String EXTRA_PROFILE_ROW_ID = "profileId";

    private static final String PREF_KEY_SHOW_SHS_ONLY = "pref_show_shs_only";
    private static final String PREF_KEY_SCAN_RATE = "pref_scan_rate";
    private static final String PREF_KEY_MINIMUM_SIGNAL_STRENGTH = "pref_minimum_signal_strength";

    private static final String INST_NETWORKS = "networks";

    private static final Pattern BSSID_PATTERN = Pattern.compile("^([0-9a-f]{2}:){5}([0-9a-f]{2})$");

    private SharedPreferences _preferences;
    private Handler _handler;
    private Runnable _scanWifiNetworksTask;
    private WifiManager _wifiManager;
    private BroadcastReceiver _scanReceiver;
    private ContentObserver _contentObserver;
    private ScanResultListAdapter _listAdapter;
    private long _profileRowId;
    private AsyncTask<?, ?, ?> _refreshTask;

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
                } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                    onWifiStateChanged();
                }
            }
        };

        // Content observer is necessary just in case the database is updated
        // outside of the activity (who knows why?)
        _contentObserver = new ContentObserver(_handler) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "Database modified, refreshing whitelist");
                beginRefreshWhitelist();
            }
        };

        // Initialize list adapter
        ScanResultListAdapter listAdapter = new ScanResultListAdapter();
        setListAdapter(listAdapter);
        _listAdapter = listAdapter;

        // Restore state if the device was rotated
        if (savedInstanceState != null) {
            ArrayList<ScanResult> rawList = savedInstanceState.getParcelableArrayList(INST_NETWORKS);
            updateNetworks(rawList);
            Log.d(TAG, "Restored WiFi network list from saved instance state");
        }

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

        // Begin listening for whitelist database changes
        Uri profilesUri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, _profileRowId);
        getContentResolver().registerContentObserver(profilesUri, false, _contentObserver);

        // Load the BSSID whitelist (async)
        beginRefreshWhitelist();

        // Register receiver for WiFi events
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(_scanReceiver, filter);

        // In case WiFi state changed while the Activity was paused
        // This also starts scanning for WiFi networks if appropriate
        onWifiStateChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop scanning for WiFi networks
        // This saves battery, since we are not uselessly
        // refreshing the WiFi list in the background.
        cancelEnqueuedWifiScan();

        // Stop listening for WiFi events
        unregisterReceiver(_scanReceiver);

        // Cancel any whitelist refreshes in progress
        cancelWhitelistRefresh();

        // Unregister listener for whitelist database changes
        getContentResolver().unregisterContentObserver(_contentObserver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(INST_NETWORKS, _listAdapter.getAllNetworks());
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

    private boolean isValidBssid(String bssid) {
        Matcher matcher = BSSID_PATTERN.matcher(bssid);
        return matcher.matches();
    }

    private void addBssid(String bssid) {
        new AddBssidTask().execute(bssid);
    }

    private void addAllNetworksInRange() {
        List<ScanResult> networks = _listAdapter.getAllNetworks();
        String[] bssids = new String[networks.size()];
        for (int i = 0; i < bssids.length; ++i) {
            bssids[i] = networks.get(i).BSSID;
        }
        new AddBssidTask().execute(bssids);
    }

    private void removeBssid(String bssid) {
        new DeleteBssidTask().execute(bssid);
    }

    private void setEmptyText(int textId) {
        TextView emptyView = (TextView)getListView().getEmptyView();
        emptyView.setText(textId);
    }

    private void cancelWhitelistRefresh() {
        if (_refreshTask != null) {
            _refreshTask.cancel(true);
            _refreshTask = null;
        }
    }

    private void beginRefreshWhitelist() {
        cancelWhitelistRefresh();
        _refreshTask = new RefreshBssidsTask().execute();
    }

    private void onWhitelistRefreshCompleted(HashSet<String> whitelist) {
        Log.d(TAG, "BSSID whitelist refreshed");
        _listAdapter.updateWhitelist(whitelist);
        _refreshTask = null;
    }

    private void startWifiScan() {
        _wifiManager.startScan();
    }

    private void updateNetworks(List<ScanResult> results) {
        if (_listAdapter.updateNetworks(results)) {
            setEmptyText(R.string.all_networks_filtered);
        } else {
            setEmptyText(R.string.no_networks_available);
        }
    }

    private void onWifiScanCompleted() {
        if (_wifiManager.isWifiEnabled()) {
            List<ScanResult> results = _wifiManager.getScanResults();
            updateNetworks(results);
            enqueueWifiScan();
        }
    }

    private void onWifiStateChanged() {
        if (_wifiManager.isWifiEnabled()) {
            setEmptyText(R.string.no_networks_available);
            startWifiScan();
        } else {
            updateNetworks(Collections.<ScanResult>emptyList());
            setEmptyText(R.string.enable_wifi);
            cancelEnqueuedWifiScan();
        }
    }

    private void enqueueWifiScan() {
        // Make sure we don't try to scan multiple times
        cancelEnqueuedWifiScan();

        int delay = PreferenceUtils.getInt(_preferences, PREF_KEY_SCAN_RATE, 5);
        if (delay > 0) {
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
                public boolean validateInput(String text, EditText editText) {
                    if (TextUtils.isEmpty(text)) {
                        editText.setError(null);
                        return false;
                    }

                    if (isValidBssid(text.toLowerCase())) {
                        editText.setError(null);
                        return true;
                    }

                    editText.setError(getString(R.string.invalid_bssid_format));
                    return false;
                }

                @Override
                public void onSubmit(String text) {
                    addBssid(text.toLowerCase());
                }

                @Override
                public void customizeDialog(AlertDialog dialog, EditText editText) {
                    editText.setHint(R.string.network_bssid_hint);
                    editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(17)});
                }
            }
        );
    }
}
