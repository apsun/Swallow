package com.crossbowffs.swallow.utils;

import android.content.SharedPreferences;

public final class PreferenceUtils {
    private PreferenceUtils() { }

    public static int getInt(SharedPreferences preferences, String key, int defaultValue) {
        String strValue = preferences.getString(key, null);

        if (strValue == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
