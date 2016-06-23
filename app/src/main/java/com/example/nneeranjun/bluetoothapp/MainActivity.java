package com.example.nneeranjun.bluetoothapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

    }
    public void startBluetooth(View view) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = sharedPreferences.edit();
        editor.putString("user","client");
        editor.commit();
        Intent intent = new Intent(getApplicationContext(), PreviewTask.class);
        startActivity(intent);
    }

    public void startCamera(View view){
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        editor = sharedPreferences.edit();
        editor.putString("user","host");
        editor.commit();
        startActivity(new Intent(getApplicationContext(),PreviewTask.class));
    }
}
