package com.dhruv.glaautologin;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Intent serviceIntent = new Intent(this, AutoLoginService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Intent intent = getIntent();
        if (intent != null && (Intent.ACTION_VIEW.equals(intent.getAction()) || "android.net.conn.CAPTIVE_PORTAL".equals(intent.getAction()))) {
            Toast.makeText(this, "GLA AutoLogin: Authenticating portal...", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "GLA AutoLogin is running in the background", Toast.LENGTH_SHORT).show();
        }
        
        finish(); // Intentionally hides the UI
    }
}
