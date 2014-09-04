package com.oxycode.swallow;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;

public class ProfileManagerActivity extends Activity {
    private static final String TAG = "SWAL";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_manager);
    }

    @Override
     public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.profile_manager_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
}
