package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.*;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class ProfileManagerActivity extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static interface SetProfileNameDialogHandler {
        void onSave(String name);
    }

    private static final String TAG = ProfileManagerActivity.class.getSimpleName();

    private SharedPreferences _preferences;
    private ListView _profileListView;
    private SimpleCursorAdapter _cursorAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        _profileListView = getListView();
        //_database = new NetworkProfileDBAdapter(this);

        getLoaderManager().initLoader(0, null, this);

        // Add the long-press listview context menu
        registerForContextMenu(_profileListView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_options_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void showNetworkScanner(String profileName) {
        Intent intent = new Intent(this, ProfileEditorActivity.class);
        intent.putExtra(ProfileEditorActivity.EXTRA_PROFILE_NAME, profileName);
        startActivity(intent);
    }

    private void showSetProfileNameDialog(final SetProfileNameDialogHandler handler) {
        View promptView = getLayoutInflater().inflate(R.layout.textedit_dialog, null);
        final EditText editText = (EditText)promptView.findViewById(R.id.textedit_dialog_edittext);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setView(promptView)
            .setTitle(R.string.enter_profile_name)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String profileName = editText.getText().toString();
                    handler.onSave(profileName);
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
                showSetProfileNameDialog(new SetProfileNameDialogHandler() {
                    @Override
                    public void onSave(String name) {
                        //_database.createProfile(name, true);
                        showNetworkScanner(name);
                    }
                });
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
        final long rowId = info.id;
        switch (item.getItemId()) {
            case R.id.profile_context_menu_edit:
                // do something
                return true;
            case R.id.profile_context_menu_delete:
                // do something
                return true;
            case R.id.profile_context_menu_rename:
                showSetProfileNameDialog(new SetProfileNameDialogHandler() {
                    @Override
                    public void onSave(String name) {
                        //_database.updateProfile(rowId, name, true);
                        // TODO: update profile name
                    }
                });
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
}
