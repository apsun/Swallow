package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NavUtils;
import android.view.*;
import android.widget.*;
import com.oxycode.swallow.provider.NetworkProfileContract;
import com.oxycode.swallow.utils.DialogUtils;

public class ProfileManagerActivity extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    // This custom adapter adds support for our checkbox view.
    // TODO: Change this to directly extend ResourceCursorAdapter,
    // TODO: so that we can do things like cursor.getCount()
    private static class MyCursorAdapter extends SimpleCursorAdapter {
        private static final int LAYOUT = R.layout.profile_listitem;
        private static final String[] FROM = {
            NetworkProfileContract.Profiles.NAME,
            NetworkProfileContract.Profiles.ENABLED,
            NetworkProfileContract.Profiles._ID
        };
        private static final int[] TO = {
            R.id.profile_name_textview,
            R.id.profile_enabled_checkbox,
            R.id.profile_detail_textview
        };

        public MyCursorAdapter(Context context) {
            super(context, LAYOUT, null, FROM, TO, 0);
            setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (view.getId() == R.id.profile_enabled_checkbox) {
                        boolean enabled = cursor.getLong(columnIndex) != 0;
                        CheckBox checkBox = (CheckBox)view;
                        checkBox.setChecked(enabled);
                        return true;
                    }
                    return false;
                }
            });
        }

        @Override
        public void bindView(@NonNull View view, final Context context, @NonNull Cursor cursor) {
            CheckBox enabledCheckBox = (CheckBox)view.findViewById(R.id.profile_enabled_checkbox);

            // super.bindView() will update the state of the checkbox to
            // that of a different row, so make sure we aren't handling that
            enabledCheckBox.setOnCheckedChangeListener(null);

            super.bindView(view, context, cursor);

            final long rowId = cursor.getLong(cursor.getColumnIndexOrThrow(NetworkProfileContract.Profiles._ID));
            enabledCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ContentValues values = new ContentValues();
                    values.put(NetworkProfileContract.Profiles.ENABLED, isChecked ? 1L : 0L);
                    Uri uri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, rowId);
                    context.getContentResolver().update(uri, values, null, null);
                }
            });
        }
    }

    private static final String TAG = ProfileManagerActivity.class.getSimpleName();

    private CursorAdapter _cursorAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add back button to the action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Initialize cursor loader
        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(0, null, this);

        // Initialize content adapter
        CursorAdapter cursorAdapter = new MyCursorAdapter(this);
        setListAdapter(cursorAdapter);
        _cursorAdapter = cursorAdapter;

        // Register content menu
        ListView listView = getListView();
        registerForContextMenu(listView);

        // Make clicking on the list item toggle the checkbox
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CheckBox enabledCheckBox = (CheckBox)view.findViewById(R.id.profile_enabled_checkbox);
                enabledCheckBox.performClick();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_options_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.add_profile_button:
                showCreateProfileDialog();
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

        // Set context menu title to the name of the profile
        View item = ((AdapterView.AdapterContextMenuInfo)menuInfo).targetView;
        TextView nameTextView = (TextView)item.findViewById(R.id.profile_name_textview);
        menu.setHeaderTitle(nameTextView.getText());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        final long rowId = info.id;
        switch (item.getItemId()) {
            case R.id.profile_context_menu_edit:
                showNetworkScanner(rowId);
                return true;
            case R.id.profile_context_menu_delete:
                showConfirmDeleteDialog(rowId);
                return true;
            case R.id.profile_context_menu_rename:
                showRenameProfileDialog(rowId);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] from = new String[] {
            NetworkProfileContract.Profiles._ID,
            NetworkProfileContract.Profiles.NAME,
            NetworkProfileContract.Profiles.ENABLED
        };
        Uri uri = NetworkProfileContract.Profiles.CONTENT_URI;
        return new CursorLoader(this, uri, from, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        _cursorAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        _cursorAdapter.swapCursor(null);
    }

    private void showNetworkScanner(long profileRowId) {
        Intent intent = new Intent(this, ProfileEditorActivity.class);
        intent.putExtra(ProfileEditorActivity.EXTRA_PROFILE_ROW_ID, profileRowId);
        startActivity(intent);
    }

    private void showRenameProfileDialog(final long rowId) {
        DialogUtils.showTextEntryDialog(this,
            0,
            getString(R.string.profile_name),
            getString(R.string.save),
            new DialogUtils.TextEntryDialogHandler() {
                @Override
                public void onSubmit(String text) {
                    ContentValues values = new ContentValues();
                    values.put(NetworkProfileContract.Profiles.NAME, text);
                    Uri uri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, rowId);
                    getContentResolver().update(uri, values, null, null);
                }
            }
        );
    }

    private void showCreateProfileDialog() {
        DialogUtils.showTextEntryDialog(this,
            0,
            getString(R.string.profile_name),
            getString(R.string.save),
            new DialogUtils.TextEntryDialogHandler() {
                @Override
                public void onSubmit(String text) {
                    ContentValues values = new ContentValues();
                    values.put(NetworkProfileContract.Profiles.NAME, text);
                    values.put(NetworkProfileContract.Profiles.ENABLED, true);
                    Uri uri = getContentResolver().insert(NetworkProfileContract.Profiles.CONTENT_URI, values);
                    long profileRowId = ContentUris.parseId(uri);
                    // TODO: This is an ugly hack, rewrite this to use exceptions instead
                    // TODO: Yes, we know an error occurred, but *which one*, exactly?
                    if (profileRowId < 0) {
                        showDuplicateNameErrorDialog();
                    } else {
                        showNetworkScanner(profileRowId);
                    }
                }
            }
        );
    }

    private void showDuplicateNameErrorDialog() {
        DialogUtils.showMessageDialog(this,
            0,
            getString(R.string.duplicate_name_title),
            getString(R.string.duplicate_name_message)
        );
    }

    private void showConfirmDeleteDialog(final long rowId) {
        DialogUtils.showConfirmationDialog(this,
            R.drawable.ic_action_warning,
            getString(R.string.confirm_delete_profile_title),
            getString(R.string.confirm_delete_profile_message),
            getString(R.string.delete),
            new DialogUtils.ConfirmationDialogHandler() {
                @Override
                public void onConfirm() {
                    Uri uri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, rowId);
                    getContentResolver().delete(uri, null, null);
                }
            }
        );
    }
}
