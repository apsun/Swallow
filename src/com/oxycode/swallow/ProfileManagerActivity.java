package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;

public class ProfileManagerActivity extends ListActivity {
    private static final String TAG = ProfileManagerActivity.class.getName();

    private ArrayList<NetworkProfile> _profiles;

    private SharedPreferences _preferences;
    private ListView _profileListView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // TODO: Do we even need to cache the listview?
        _profileListView = getListView();

        // Add the long-press listview context menu
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

    private void showSetProfileNameDialog() {
        View promptView = getLayoutInflater().inflate(R.layout.textedit_dialog, null);
        final EditText editText = (EditText)promptView.findViewById(R.id.textedit_dialog_edittext);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.enter_profile_name)
            .setView(promptView)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // TODO: Do something
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.add_profile_button:
                showSetProfileNameDialog();
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
}
