package com.oxycode.swallow.provider;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

public class NetworkProfileProvider extends ContentProvider {
    private static final String TAG = NetworkProfileProvider.class.getSimpleName();

    private static final UriMatcher URI_MATCHER;

    private static final int PROFILES_ID = 0;
    private static final int PROFILE_ID = 1;
    private static final int BSSIDS_ID = 2;
    private static final int BSSID_ID = 3;
    private static final int PROFILE_BSSIDS_ID = 4;
    private static final int PROFILE_BSSID_ID = 5;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Profiles.TABLE, PROFILES_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Profiles.TABLE + "/#", PROFILE_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Bssids.TABLE, BSSIDS_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.Bssids.TABLE + "/#", BSSID_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.ProfileBssids.TABLE, PROFILE_BSSIDS_ID);
        URI_MATCHER.addURI(NetworkProfileContract.AUTHORITY, NetworkProfileContract.ProfileBssids.TABLE + "/#", PROFILE_BSSID_ID);
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
            case PROFILE_BSSID_ID:
                queryBuilder.appendWhere(NetworkProfileContract.ProfileBssids.BSSID_ID + "=" + uri.getLastPathSegment());
            case PROFILE_BSSIDS_ID:
                queryBuilder.setTables(
                    NetworkProfileContract.Bssids.TABLE + " INNER JOIN " + NetworkProfileContract.Profiles.TABLE + " ON " +
                    NetworkProfileContract.Profiles.TABLE + "." + NetworkProfileContract.Profiles._ID + "=" +
                    NetworkProfileContract.Bssids.TABLE + "." + NetworkProfileContract.Bssids.PROFILE_ID
                );
                break;
            default:
                throw new IllegalArgumentException("Invalid query URI: " + uri);
        }

        Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
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
                throw new IllegalArgumentException("Invalid insert URI: " + uri);
        }

        Uri newUri = ContentUris.withAppendedId(NetworkProfileContract.Bssids.CONTENT_URI, row);

        if (row >= 0) {
            ContentResolver contentResolver = getContext().getContentResolver();

            // Notify addition of profile or BSSID
            contentResolver.notifyChange(newUri, null);

            // If a BSSID was added, notify the profile
            // that contains it
            if (uriType == BSSIDS_ID) {
                long profileId = values.getAsLong(NetworkProfileContract.Bssids.PROFILE_ID);
                Uri profileUri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, profileId);
                contentResolver.notifyChange(profileUri, null);
            }

            // No need to care about inserting a new profile,
            // since those are empty anyways and cannot affect
            // the list of BSSIDs
        }

        return newUri;
    }

    @Override
    public int bulkInsert(Uri uri, @NonNull ContentValues[] values) {
        int uriType = URI_MATCHER.match(uri);
        if (uriType != BSSIDS_ID) {
            throw new IllegalArgumentException("Invalid insert URI: " + uri);
        }

        SQLiteDatabase db = _databaseHelper.getWritableDatabase();
        ContentResolver contentResolver = getContext().getContentResolver();
        HashSet<Long> updatedProfileIds = new HashSet<Long>(1);
        int insertCount = 0;

        for (ContentValues value : values) {
            long row = db.insert(NetworkProfileContract.Bssids.TABLE, null, value);
            if (row >= 0) {
                long profileId = value.getAsLong(NetworkProfileContract.Bssids.PROFILE_ID);
                updatedProfileIds.add(profileId);
                ++insertCount;
            }
        }

        if (insertCount > 0) {
            // Blanket-notify of a change in the list of BSSIDs
            Uri bssidsUrl = NetworkProfileContract.Bssids.CONTENT_URI;
            contentResolver.notifyChange(bssidsUrl, null);

            // Notify all profile associated with the added BSSIDs individually
            for (long profileId : updatedProfileIds) {
                Uri profileUrl = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, profileId);
                contentResolver.notifyChange(profileUrl, null);
            }
        }

        return insertCount;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = URI_MATCHER.match(uri);
        SQLiteDatabase db = _databaseHelper.getWritableDatabase();
        int deletedRows;
        Set<Long> updatedProfileIds = null;
        switch (uriType) {
            case PROFILE_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Profiles._ID, uri, selection);
                deletedRows = db.delete(NetworkProfileContract.Profiles.TABLE, selection, selectionArgs);
                break;
            case BSSID_ID:
                selection = getCombinedSelectionString(NetworkProfileContract.Bssids._ID, uri, selection);
            case BSSIDS_ID:
                // We also need to get the ID of the profile containing
                // this BSSID, since it needs to be notified too
                db.beginTransaction();
                try {
                    updatedProfileIds = getProfileIds(db, selection, selectionArgs);
                    deletedRows = db.delete(NetworkProfileContract.Bssids.TABLE, selection, selectionArgs);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid delete URI: " + uri);
        }

        // db.delete() returns 0 when selection is null
        // (but it still deletes all rows)
        if (selection == null || deletedRows > 0) {
            ContentResolver contentResolver = getContext().getContentResolver();

            // Notify deletion of profile or BSSID
            contentResolver.notifyChange(uri, null);

            // If a profile was deleted, notify observers that
            // the list of BSSIDs has also changed
            if (uriType == PROFILE_ID) {
                Uri bssidsUri = NetworkProfileContract.Bssids.CONTENT_URI;
                contentResolver.notifyChange(bssidsUri, null);
            }

            // If a BSSID was deleted, notify observers that
            // the profile containing the BSSID has also changed
            if (uriType == BSSID_ID || uriType == BSSIDS_ID) {
                for (long profileId : updatedProfileIds) {
                    Uri profileUri = ContentUris.withAppendedId(NetworkProfileContract.Profiles.CONTENT_URI, profileId);
                    contentResolver.notifyChange(profileUri, null);
                }
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
                selection = getCombinedSelectionString(NetworkProfileContract.Profiles._ID, uri, selection);
                updatedRows = db.update(NetworkProfileContract.Profiles.TABLE, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Invalid update URI: " + uri);
        }

        if (updatedRows > 0) {
            ContentResolver contentResolver = getContext().getContentResolver();

            // Notify update of profile
            contentResolver.notifyChange(uri, null);

            // If the enabled status of a profile was changed, notify
            // that the list of BSSIDs has also changed
            if (values.containsKey(NetworkProfileContract.Profiles.ENABLED)) {
                Uri bssidsUri = NetworkProfileContract.Bssids.CONTENT_URI;
                contentResolver.notifyChange(bssidsUri, null);
            }
        }
        return updatedRows;
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
            case PROFILE_BSSID_ID:
                return NetworkProfileContract.ProfileBssids.CONTENT_ITEM_TYPE;
            case PROFILE_BSSIDS_ID:
                return NetworkProfileContract.ProfileBssids.CONTENT_TYPE;
            default:
                return null;
        }
    }

    private Set<Long> getProfileIds(SQLiteDatabase db, String selection, String[] selectionArgs) {
        String[] columns = {NetworkProfileContract.Bssids.PROFILE_ID};
        Cursor cursor = db.query(
            NetworkProfileContract.Bssids.TABLE,
            columns,
            selection,
            selectionArgs,
            null, null, null
        );

        int profileIdCol = cursor.getColumnIndexOrThrow(NetworkProfileContract.Bssids.PROFILE_ID);
        HashSet<Long> profileIds = new HashSet<Long>(1);
        while (cursor.moveToNext()) {
            long currProfileId = cursor.getLong(profileIdCol);
            profileIds.add(currProfileId);
        }

        return profileIds;
    }

    private String getCombinedSelectionString(String idColumnName, Uri uri, String selection) {
        String profileWhere = idColumnName + "=" + uri.getLastPathSegment();
        if (TextUtils.isEmpty(selection)) {
            return profileWhere;
        } else {
            return profileWhere + " AND " + selection;
        }
    }
}
