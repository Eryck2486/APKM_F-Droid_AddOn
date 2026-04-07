package com.apkm.addon.fdroid;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.Objects;


public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            Intent serviceIntent = new Intent(context, ServiceWorker.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("BootReceiver", "Failed to start service on boot: " + e.getMessage());
            }
        }
    }
}
