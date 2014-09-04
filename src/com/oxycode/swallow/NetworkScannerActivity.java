package com.oxycode.swallow;

import android.app.Activity;
import android.os.Bundle;

public class NetworkScannerActivity extends Activity {
    private static final String TAG = "SWAL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.network_scanner);
    }
}
