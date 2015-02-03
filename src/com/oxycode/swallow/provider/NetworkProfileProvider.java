package com.oxycode.swallow.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class NetworkProfileProvider extends ContentProvider {
    private static final String TAG = NetworkProfileProvider.class.getSimpleName();

    private static final UriMatcher URI_MATCHER;

    private static final int PROFILES_ID = 100;
    private static final int PROFILE_ID = 110;
    private static final int BSSIDS_ID = 200;
    private static final int BSSID_ID = 210;
    private static final int PROFILES_JOIN_BSSIDS_ID = 300;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Profiles.TABLE, PROFILES_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Profiles.TABLE + "/#", PROFILE_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Bssids.TABLE, BSSIDS_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Bssids.TABLE + "/#", BSSID_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.ProfilesJoinBssids.TABLE, PROFILES_JOIN_BSSIDS_ID);
    }

    private NetworkProfileHelper _databaseHelper;

    @Override
    public boolean onCreate() {
        _databaseHelper = new NetworkProfileHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = _databaseHelper.getReadableDatabase();
        switch (uriType) {
            case PROFILE_ID:
                queryBuilder.appendWhere(NetworkProfileContract.Profiles._ID + "=" + uri.getLastPathSegment());
            case PROFILES_ID:
                queryBuilder.setTables(NetworkProfileContract.Profiles.TABLE);
                break;
            case BSSID_ID:
                queryBuilder.appendWhere(NetworkProfileContract.Bssids._ID + "=" + uri.getLastPathSegment());
            case BSSIDS_ID:
                queryBuilder.setTables(NetworkProfileContract.Bssids.TABLE);
                break;
            case PROFILES_JOIN_BSSIDS_ID:
                queryBuilder.setTables(
                    NetworkProfileContract.Bssids.TABLE + " INNER JOIN " + NetworkProfileContract.Profiles.TABLE + " ON " +
                    NetworkProfileContract.Profiles.TABLE + "." + NetworkProfileContract.Profiles._ID + "=" +
                    NetworkProfileContract.Bssids.TABLE + "." + NetworkProfileContract.Bssids.PROFILE_ID
                );
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        int uriType = URI_MATCHER.match(uri);
        switch (uriType) {
            case PROFILE_ID:
                return NetworkProfileContract.Profiles.CONTENT_ITEM_TYPE;
            case PROFILES_ID:
                return NetworkProfileContract.Profiles.CONTENT_TYPE;
            case BSSID_ID:
                return NetworkProfileContract.Bssids.CONTENT_ITEM_TYPE;
            case BSSIDS_ID:
                return NetworkProfileContract.Bssids.CONTENT_TYPE;
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = _databaseHelper.getWritableDatabase();
        long row;
        switch (uriType) {
            case PROFILES_ID:
                row = db.insert(NetworkProfileContract.Profiles.TABLE, null, values);
                break;
            case BSSIDS_ID:
                row = db.insert(NetworkProfileContract.Bssids.TABLE, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        Uri newUri = ContentUris.withAppendedId(NetworkProfileContract.Bssids.CONTENT_URI, row);

        // Only notify change if insert succeeded
        if (row >= 0) {
            getContext().getContentResolver().notifyChange(newUri, null);
        }

        return newUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = _databaseHelper.getWritableDatabase();
        int deletedRows;
        switch (uriType) {
            case PROFILE_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Profiles._ID, uri.getLastPathSegment(), selection);
            case PROFILES_ID:
                deletedRows = db.delete(NetworkProfileContract.Profiles.TABLE, selection, selectionArgs);
                break;
            case BSSID_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Bssids._ID, uri.getLastPathSegment(), selection);
            case BSSIDS_ID:
                deletedRows = db.delete(NetworkProfileContract.Bssids.TABLE, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        // db.delete() returns 0 when selection is null
        // (but it still deletes all rows)
        if (selection == null || deletedRows > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            if (uriType == PROFILE_ID || uriType == PROFILES_ID) {
                // Also notify BSSID URI's, because deleting a profile
                // deletes the BSSID's within it
                // TODO: Implement this
                Log.w(TAG, "Need update!");
            }
        }
        return deletedRows;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = _databaseHelper.getWritableDatabase();
        int updatedRows;
        switch (uriType) {
            case PROFILE_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Profiles._ID, uri.getLastPathSegment(), selection);
            case PROFILES_ID:
                updatedRows = db.update(NetworkProfileContract.Profiles.TABLE, values, selection, selectionArgs);
                break;
            case BSSID_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Bssids._ID, uri.getLastPathSegment(), selection);
            case BSSIDS_ID:
                updatedRows = db.update(NetworkProfileContract.Bssids.TABLE, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (updatedRows > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
            if (uriType == PROFILE_ID || uriType == PROFILES_ID) {
                // Also notify BSSID URI's, when 'enabled' flag is updated
                // TODO: Implement this
                Log.w(TAG, "Need update!");
            }
        }
        return updatedRows;
    }

    private String getCombinedSelectionString(String idColumnName, String rowId, String selection) {
        String profileWhere = idColumnName + " = " + rowId;
        if (TextUtils.isEmpty(selection)) {
            return profileWhere;
        } else {
            return profileWhere + " AND " + selection;
        }
    }
}
