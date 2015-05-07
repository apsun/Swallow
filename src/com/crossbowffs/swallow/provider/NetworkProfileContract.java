package com.crossbowffs.swallow.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public final class NetworkProfileContract {
    public static final String AUTHORITY = "com.crossbowffs.swallow.provider.NetworkProfileProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final class Profiles implements BaseColumns {
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/profile";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/profile";

        public static final String TABLE = NetworkProfileHelper.PROFILE_TABLE;
        public static final String NAME = NetworkProfileHelper.PROFILE_KEY_NAME;
        public static final String ENABLED = NetworkProfileHelper.PROFILE_KEY_ENABLED;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(NetworkProfileContract.CONTENT_URI, Profiles.TABLE);
    }

    public static final class Bssids implements BaseColumns {
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/bssid";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/bssid";

        public static final String TABLE = NetworkProfileHelper.BSSID_TABLE;
        public static final String PROFILE_ID = NetworkProfileHelper.BSSID_KEY_PROFILE_ID;
        public static final String BSSID = NetworkProfileHelper.BSSID_KEY_BSSID;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(NetworkProfileContract.CONTENT_URI, Bssids.TABLE);
    }

    public static final class ProfileBssids implements BaseColumns {
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/profile-bssid";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/profile-bssid";

        // These values are for convenience only! They do not represent any
        // real table columns; they are shortcuts to the respective columns
        // in the Profiles and Bssids classes. Be sure to use those when iterating
        // the returned cursor!
        public static final String TABLE = "profile_bssids";
        public static final String PROFILE_ID = Bssids.TABLE + "." + Bssids.PROFILE_ID;
        public static final String PROFILE_NAME = Profiles.TABLE + "." + Profiles.NAME;
        public static final String PROFILE_ENABLED = Profiles.TABLE + "." + Profiles.ENABLED;
        public static final String BSSID_ID = Bssids.TABLE + "." + Bssids._ID;
        public static final String BSSID = Bssids.TABLE + "." + Bssids.BSSID;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(NetworkProfileContract.CONTENT_URI, ProfileBssids.TABLE);
    }
}
