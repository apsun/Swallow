package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

public class ProfileManagerActivity extends Activity implements TextEntryDialog.Listener {
    private static final String TAG = "SWAL";

    private TreeSet<NetworkProfile> _profiles;

    private SharedPreferences _preferences;
    private ListView _profileListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_manager);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        _preferences = getPreferences(MODE_PRIVATE);
        _profileListView = (ListView)findViewById(R.id.profile_listview);
        _profiles = new TreeSet<NetworkProfile>(new Comparator<NetworkProfile>() {
            @Override
            public int compare(NetworkProfile lhs, NetworkProfile rhs) {
                return lhs.getName().compareToIgnoreCase(rhs.getName());
            }
        });

        for (Map.Entry<String, ?> profiles : _preferences.getAll().entrySet()) {
            String profileName = profiles.getKey();
            HashSet<Bssid> profileBssids = (HashSet<Bssid>)profiles.getValue();
            NetworkProfile profile = new NetworkProfile(profileName, profileBssids);
            _profiles.add(profile);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showNetworkScanner(NetworkProfile profile) {
        Intent intent = new Intent(this, NetworkScannerActivity.class);
        intent.putExtra("profile", profile);
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.add_profile_button:
                TextEntryDialog dialog = new TextEntryDialog();
                Bundle arguments = new Bundle();
                arguments.putString("title", "Create a new profile");
                dialog.setArguments(arguments);
                FragmentManager fragmentManager = getFragmentManager();
                dialog.show(fragmentManager, "set_profile_name_dialog");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTextEntryDialogOk(String tag, String text) {
        if (!tag.equals("set_profile_name_dialog")) return;
        NetworkProfile profile = new NetworkProfile(text);
        showNetworkScanner(profile);
    }

    @Override
    public void onTextEntryDialogCancel(String tag) {

    }
}
