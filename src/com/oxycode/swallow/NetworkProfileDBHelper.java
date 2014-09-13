package com.oxycode.swallow;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NetworkProfileDBHelper extends SQLiteOpenHelper {
    public static final String PROFILE_TABLE = "profiles";
    public static final String PROFILE_KEY_ROWID = "_id";
    public static final String PROFILE_KEY_NAME = "name";
    public static final String PROFILE_KEY_ENABLED = "enabled";

    public static final String BSSID_TABLE = "bssids";
    public static final String BSSID_KEY_ROWID = "_id";
    public static final String BSSID_KEY_BSSID = "bssid";

    public static final String PROFILE_MAP_TABLE = "profile_map";
    public static final String PROFILE_MAP_ROWID = "_id";
    public static final String PROFILE_MAP_PROFILE_ID = "profile";
    public static final String PROFILE_MAP_ACCESS_POINT_ID = "access_point";

    private static final String DATABASE_NAME = "com.oxycode.swallow.NetworkProfiles.db";
    private static final int DATABASE_VERSION = 1;

    private static final String CREATE_PROFILE_TABLE =
        "CREATE TABLE " + PROFILE_TABLE + "(" +
            PROFILE_KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PROFILE_KEY_NAME + " TEXT NOT NULL UNIQUE," +
            PROFILE_KEY_ENABLED + " INTEGER NOT NULL," +
        ");";

    private static final String CREATE_ACCESS_POINT_TABLE =
        "CREATE TABLE " + BSSID_TABLE + "(" +
            BSSID_KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            BSSID_KEY_BSSID + " TEXT NOT NULL UNIQUE" +
        ");";

    private static final String CREATE_PROFILE_MAP_TABLE =
        "CREATE TABLE " + PROFILE_MAP_TABLE + "(" +
            PROFILE_MAP_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            "FOREIGN KEY(" + PROFILE_MAP_PROFILE_ID + ") " +
            "REFERENCES " + PROFILE_TABLE + "(" + PROFILE_KEY_ROWID + ") NOT NULL," +
            "FOREIGN KEY(" + PROFILE_MAP_ACCESS_POINT_ID + ") " +
            "REFERENCES " + BSSID_TABLE + "(" + BSSID_KEY_ROWID + ") NOT NULL" +
        ");";

    public NetworkProfileDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PROFILE_TABLE);
        db.execSQL(CREATE_ACCESS_POINT_TABLE);
        db.execSQL(CREATE_PROFILE_MAP_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // We don't need this right now, but have this
        // just for safety (in case someone downgrades the app)
        db.execSQL("DROP TABLE IF EXISTS " + PROFILE_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + BSSID_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + PROFILE_MAP_TABLE);
        onCreate(db);
    }
}
