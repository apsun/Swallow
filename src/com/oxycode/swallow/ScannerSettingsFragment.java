package com.oxycode.swallow;

import android.os.Bundle;
import android.preference.PreferenceFragment;

public class ScannerSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
