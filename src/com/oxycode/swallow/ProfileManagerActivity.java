package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.*;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.*;

public class ProfileManagerActivity extends Activity implements TextEntryDialog.Listener {
    private static final String TAG = ProfileManagerActivity.class.getName();

    private ArrayList<NetworkProfile> _profiles;

    private SharedPreferences _preferences;
    private ListView _profileListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_manager_activity);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Load the current list of profiles
        _preferences = getSharedPreferences("NetworkProfiles", MODE_PRIVATE);
        Set<? extends Map.Entry<String, ?>> profiles = _preferences.getAll().entrySet();
        _profiles = new ArrayList<NetworkProfile>(profiles.size());

        for (Map.Entry<String, ?> profileEntry : profiles) {
            String profileName = profileEntry.getKey();
            HashSet<Bssid> profileBssids = (HashSet<Bssid>)profileEntry.getValue();
            NetworkProfile profile = new NetworkProfile(profileName, profileBssids);
            _profiles.add(profile);
        }

        _profileListView = (ListView)findViewById(R.id.profile_listview);
        registerForContextMenu(_profileListView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_options_menu, menu);
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
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.profile_context_menu_edit:
                // do something
                return true;
            case R.id.profile_context_menu_delete:
                // do something
                return true;
            case R.id.profile_context_menu_rename:
                // do something
                return true;
            default:
                return super.onContextItemSelected(item);
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
