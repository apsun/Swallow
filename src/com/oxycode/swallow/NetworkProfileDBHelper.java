//      PROFILES                             ACCESS_POINTS
// ------------------               ----------------------------
// _id      (int) <-----------|     _id         (int)
// name     (string)          |---- profile_id  (int)
// enabled  (boolean)               bssid       (string/byte[6])

package com.oxycode.swallow;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class NetworkProfileDBHelper extends SQLiteOpenHelper {
    public static final String PROFILE_TABLE = "profiles";
    public static final String PROFILE_KEY_ID = "_id";
    public static final String PROFILE_KEY_NAME = "name";
    public static final String PROFILE_KEY_ENABLED = "enabled";

    public static final String ACCESS_POINT_TABLE = "access_points";
    public static final String ACCESS_POINT_KEY_ID = "_id";
    public static final String ACCESS_POINT_KEY_PROFILE_ID = "profile_id";
    public static final String ACCESS_POINT_KEY_BSSID = "bssid";

    private static final String DATABASE_NAME = "com.oxycode.swallow.NetworkProfiles.db";
    private static final int DATABASE_VERSION = 1;

    private static final String CREATE_PROFILE_TABLE =
        "CREATE TABLE " + PROFILE_TABLE + "(" +
            PROFILE_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            PROFILE_KEY_NAME + " TEXT NOT NULL UNIQUE," +
            PROFILE_KEY_ENABLED + " INTEGER NOT NULL," +
        ");";

    private static final String CREATE_ACCESS_POINT_TABLE =
        "CREATE TABLE " + ACCESS_POINT_TABLE + "(" +
            ACCESS_POINT_KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            ACCESS_POINT_KEY_BSSID + " TEXT NOT NULL," +
            "FOREIGN KEY(" + ACCESS_POINT_KEY_PROFILE_ID + ") " +
                "REFERENCES " + PROFILE_TABLE + "(" + PROFILE_KEY_ID + ") NOT NULL," +
        ");";

    public NetworkProfileDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PROFILE_TABLE);
        db.execSQL(CREATE_ACCESS_POINT_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // We don't need this right now, but have this
        // just for safety (in case someone downgrades the app)
        db.execSQL("DROP TABLE IF EXISTS " + PROFILE_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + ACCESS_POINT_TABLE);
        onCreate(db);
    }
}
