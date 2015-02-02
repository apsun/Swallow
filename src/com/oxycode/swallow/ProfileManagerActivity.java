package com.oxycode.swallow;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.oxycode.swallow.provider.NetworkProfileContract;

public class ProfileManagerActivity extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    private static interface SetProfileNameDialogHandler {
        void onSave(String name);
    }

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
        public View newView(final Context context, Cursor cursor, ViewGroup parent) {
            View view = super.newView(context, cursor, parent);
            CheckBox enabledCheckBox = (CheckBox)view.findViewById(R.id.profile_enabled_checkbox);
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

            return view;
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

    private void showNetworkScanner(long profileRowId) {
        Intent intent = new Intent(this, ProfileEditorActivity.class);
        intent.putExtra(ProfileEditorActivity.EXTRA_PROFILE_ROW_ID, profileRowId);
        startActivity(intent);
    }

    private void showSetProfileNameDialog(final SetProfileNameDialogHandler handler) {
        View promptView = getLayoutInflater().inflate(R.layout.textedit_dialog, null);
        final EditText editText = (EditText)promptView.findViewById(R.id.textedit_dialog_edittext);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setView(promptView)
            .setTitle(R.string.profile_name)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String profileName = editText.getText().toString();
                    handler.onSave(profileName);
                }
            })
            .setNegativeButton(R.string.cancel, null);

        final AlertDialog alert = builder.create();

        // Initially disable the save button (since the textbox is empty)
        // This must be done after the call to alert.show();
        // before then alert.getButton() will return null.
        alert.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button button = alert.getButton(DialogInterface.BUTTON_POSITIVE);
                button.setEnabled(false);
            }
        });

        // Enable/disable the save button depending on whether the
        // textbox is empty
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                Button button = alert.getButton(DialogInterface.BUTTON_POSITIVE);
                boolean hasText = !TextUtils.isEmpty(s);
                button.setEnabled(hasText);
            }
        });

        // Display the keyboard when the alert is shown
        alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alert.show();
    }

    private void showDuplicateNameErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.duplicate_name_title)
            .setMessage(R.string.duplicate_name_message)
            .setNeutralButton(R.string.ok, null);

        AlertDialog alert = builder.create();
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
                        ContentValues values = new ContentValues();
                        values.put(NetworkProfileContract.Profiles.NAME, name);
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
                Uri uri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, rowId);
                getContentResolver().delete(uri, null, null);
                return true;
            case R.id.profile_context_menu_rename:
                showSetProfileNameDialog(new SetProfileNameDialogHandler() {
                    @Override
                    public void onSave(String name) {
                        ContentValues values = new ContentValues();
                        values.put(NetworkProfileContract.Profiles.NAME, name);
                        Uri uri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, rowId);
                        getContentResolver().update(uri, values, null, null);
                    }
                });
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
}
