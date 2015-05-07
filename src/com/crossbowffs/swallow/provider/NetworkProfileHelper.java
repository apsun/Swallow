package com.crossbowffs.swallow.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class NetworkProfileHelper extends SQLiteOpenHelper {
    private static final String TAG = NetworkProfileHelper.class.getSimpleName();

    public static final String PROFILE_TABLE = "profiles";
    public static final String PROFILE_KEY_ID = "_id";
    public static final String PROFILE_KEY_NAME = "name";
    public static final String PROFILE_KEY_ENABLED = "enabled";

    public static final String BSSID_TABLE = "bssids";
    public static final String BSSID_KEY_ID = "_id";
    public static final String BSSID_KEY_PROFILE_ID = "profile_id";
    public static final String BSSID_KEY_BSSID = "bssid";

    private static final String DATABASE_NAME = "networkprofiles.db";
    private static final int DATABASE_VERSION = 1;

    private static final String CREATE_PROFILE_TABLE =
        "CREATE TABLE " + PROFILE_TABLE + "(" +
            PROFILE_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PROFILE_KEY_NAME + " TEXT NOT NULL UNIQUE," +
            PROFILE_KEY_ENABLED + " INTEGER NOT NULL" +
        ");";

    private static final String CREATE_BSSID_TABLE =
        "CREATE TABLE " + BSSID_TABLE + "(" +
            BSSID_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            BSSID_KEY_PROFILE_ID + " INTEGER NOT NULL," +
            BSSID_KEY_BSSID + " TEXT NOT NULL," +
            "FOREIGN KEY(" + BSSID_KEY_PROFILE_ID + ")" +
                "REFERENCES " + PROFILE_TABLE + "(" + PROFILE_KEY_ID + ") ON DELETE CASCADE," +
            "UNIQUE(" + BSSID_KEY_PROFILE_ID + "," + BSSID_KEY_BSSID + ") ON CONFLICT IGNORE" +
        ");";

    public NetworkProfileHelper(Context context) {
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
                bssidValues.put(BSSID_KEY_PROFILE_ID, profileRowId);
                bssidValues.put(BSSID_KEY_BSSID, bssid);
                db.insert(BSSID_TABLE, null, bssidValues);
            }
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys=ON");
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PROFILE_TABLE);
        db.execSQL(CREATE_BSSID_TABLE);
        createDefaultProfiles(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "Upgrading database from v" + oldVersion + " to v" + newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + PROFILE_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + BSSID_TABLE);
        onCreate(db);
    }
}
