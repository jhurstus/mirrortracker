package org.lucubrate.mirrortracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Start location background service on device boot. */
public class BootReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION)) {
            Intent serviceIntent = new Intent(context, LocationService.class);
            context.startService(serviceIntent);
        }
    }
}
