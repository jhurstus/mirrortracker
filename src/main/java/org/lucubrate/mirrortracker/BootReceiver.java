package org.lucubrate.mirrortracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Start location background service on device boot.
 *
 * This is critical in order for background location updates and geofencing to be enabled whenever
 * device is on (as opposed to only after user opens app).
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "received boot signal");

        if (intent == null) {
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "enqueueing job on LocationService to wake it up");
            LocationService.enqueueWork(context);
        }
    }
}
