package com.oxycode.swallow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class NetworkProfileDBAdapter {
    private static final String TAG = NetworkProfileDBAdapter.class.getSimpleName();

    public static final String PROFILE_TABLE = "profiles";
    public static final String PROFILE_KEY_ID = "_id";
    public static final String PROFILE_KEY_NAME = "name";
    public static final String PROFILE_KEY_ENABLED = "enabled";

    public static final String ACCESS_POINT_TABLE = "access_points";
    public static final String ACCESS_POINT_KEY_ID = "_id";
    public static final String ACCESS_POINT_KEY_PROFILE_ID = "profile_id";
    public static final String ACCESS_POINT_KEY_BSSID = "bssid";

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "com.oxycode.swallow.NetworkProfiles.db";
        private static final int DATABASE_VERSION = 1;

        private static final String CREATE_PROFILE_TABLE =
            "CREATE TABLE " + PROFILE_TABLE + "(" +
                PROFILE_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                PROFILE_KEY_NAME + " TEXT NOT NULL UNIQUE," +
                PROFILE_KEY_ENABLED + " INTEGER NOT NULL" +
            ");";

        private static final String CREATE_ACCESS_POINT_TABLE =
            "CREATE TABLE " + ACCESS_POINT_TABLE + "(" +
                ACCESS_POINT_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                ACCESS_POINT_KEY_PROFILE_ID + " INTEGER NOT NULL," +
                ACCESS_POINT_KEY_BSSID + " TEXT NOT NULL," +
                "FOREIGN KEY(" + ACCESS_POINT_KEY_PROFILE_ID + ") " +
                "REFERENCES " + PROFILE_TABLE + "(" + PROFILE_KEY_ID + ")" +
            ");";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        private static void createDefaultProfiles(SQLiteDatabase db) {
            HashMap<String, String[]> profiles = new HashMap<String, String[]>();
            profiles.put("XMB", new String[] {
                "00:1f:41:27:62:69",
                "00:22:7f:18:2c:79",
                "00:22:7f:18:33:39",
                "00:22:7f:18:2f:e9",
                "00:22:7f:18:33:19",
                "00:22:7f:18:21:c9",
                "58:93:96:1b:8c:d9",
                "58:93:96:1b:91:e9",
                "58:93:96:1b:92:19",
                "58:93:96:1b:91:99",
                "58:93:96:1b:8e:99",
                "58:93:96:1b:91:49"
            });

            for (Map.Entry<String, String[]> profile : profiles.entrySet()) {
                String name = profile.getKey();
                String[] bssids = profile.getValue();
                ContentValues profileValues = new ContentValues();
                profileValues.put(PROFILE_KEY_NAME, name);
                profileValues.put(PROFILE_KEY_ENABLED, true);
                long profileRowId = db.insert(PROFILE_TABLE, null, profileValues);
                for (String bssid : bssids) {
                    ContentValues bssidValues = new ContentValues();
                    bssidValues.put(ACCESS_POINT_KEY_PROFILE_ID, profileRowId);
                    bssidValues.put(ACCESS_POINT_KEY_BSSID, bssid);
                    db.insert(ACCESS_POINT_TABLE, null, bssidValues);
                }
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_PROFILE_TABLE);
            db.execSQL(CREATE_ACCESS_POINT_TABLE);
            createDefaultProfiles(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "Upgrading database from v" + oldVersion + " to v" + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + PROFILE_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + ACCESS_POINT_TABLE);
            onCreate(db);
        }
    }

    private DatabaseHelper _helper;
    private SQLiteDatabase _database;
    private Context _context;

    public NetworkProfileDBAdapter(Context context) {
        _context = context;
    }

    public NetworkProfileDBAdapter open() throws SQLiteException {
        _helper = new DatabaseHelper(_context);
        _database = _helper.getWritableDatabase();
        return this;
    }

    public void close() {
        _helper.close();
    }

    public long createProfile(String name, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(PROFILE_KEY_NAME, name);
        values.put(PROFILE_KEY_ENABLED, enabled);
        return _database.insert(PROFILE_TABLE, null, values);
    }

    public boolean deleteProfile(long rowId) {
        return _database.delete(PROFILE_TABLE, PROFILE_KEY_ID + "=" + rowId, null) > 0;
    }

    private Cursor getProfilesInternal(String selection) {
        String[] columns = {PROFILE_KEY_ID, PROFILE_KEY_NAME, PROFILE_KEY_ENABLED};
        return _database.query(PROFILE_TABLE, columns, selection, null, null, null, null);
    }

    public Cursor getAllProfiles(boolean enabledOnly) {
        return getProfilesInternal(enabledOnly ? PROFILE_KEY_ENABLED + "=1" : null);
    }

    public Cursor getProfile(long rowId) {
        return getProfilesInternal(PROFILE_KEY_ID + "=" + rowId);
    }

    public boolean updateProfile(long rowId, String name, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(PROFILE_KEY_NAME, name);
        values.put(PROFILE_KEY_ENABLED, enabled);
        return _database.update(PROFILE_TABLE, values, PROFILE_KEY_ID + "=" + rowId, null) > 0;
    }

    public long addBssidToProfile(long profileRowId, String bssid) {
        ContentValues values = new ContentValues();
        values.put(ACCESS_POINT_KEY_PROFILE_ID, profileRowId);
        values.put(ACCESS_POINT_KEY_BSSID, bssid);
        return _database.insert(ACCESS_POINT_TABLE, null, values);
    }

    public boolean removeBssidFromProfile(long rowId) {
        return _database.delete(ACCESS_POINT_TABLE, ACCESS_POINT_KEY_ID + "=" + rowId, null) > 0;
    }

    private Cursor getBssidsInternal(String selection) {
        String[] columns = {ACCESS_POINT_KEY_ID, ACCESS_POINT_KEY_PROFILE_ID, ACCESS_POINT_KEY_BSSID};
        return _database.query(ACCESS_POINT_TABLE, columns, selection, null, null, null, null);
    }

    public Cursor getAllBssids(boolean enabledProfilesOnly) {
        return getBssidsInternal(null); // TODO
    }

    public Cursor getBssidsForProfile(long profileRowId) {
        return getBssidsInternal(ACCESS_POINT_KEY_PROFILE_ID + "=" + profileRowId);
    }
}
