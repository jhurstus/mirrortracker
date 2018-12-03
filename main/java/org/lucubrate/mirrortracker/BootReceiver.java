package org.lucubrate.mirrortracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Start location background service on device boot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("boot", "received the boot");
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("boot", "bootin");
            Intent serviceIntent = new Intent(context, LocationService.class);
            LocationService.enqueueWork(context, new Intent());
        }
    }
}
