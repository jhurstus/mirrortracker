package org.lucubrate.mirrortracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationResult;

/**
 * BroadcastReceiver that processes updates from
 * {@link com.google.android.gms.location.FusedLocationProviderClient}.
 */
public class FusedLocationReceiver extends BroadcastReceiver {
    private static final String TAG = "FusedLocationReceiver";

    // This MUST match action filter for this receiver in the manifest.
    static String LOCATION_INTENT_ACTION = "org.lucubrate.mirrortracker.FUSED_LOCATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !LOCATION_INTENT_ACTION.equals(intent.getAction())) {
            Log.e(TAG, "unexpected intent received");
            return;
        }

        if (!LocationResult.hasResult(intent)) {
            // FusedLocationProvider currently dispatches separate broadcasts for location and
            // location-availability.  We only care about the former, so silently ignore the latter
            // here.  Code is structured this way so it should continue to function even if
            // FusedLocationProvider merges these broadcasts into a single intent in the future.
            if (LocationAvailability.hasLocationAvailability(intent)) {
                return;
            }
            Log.e(TAG, "no location result " + intent.toString());
            return;
        }
        Log.d(TAG, "received fused location intent");

        LocationResult result = LocationResult.extractResult(intent);
        if (result == null) {
            Log.e(TAG, "no location result present");
            return;
        }

        DebugLog.getInstance(context.getFilesDir()).logLocationUpdated(result);
        Location loc = result.getLastLocation();
        if (loc != null) {
            Log.d(TAG, "sending fused location to db");
            FirebaseLocationWriteService.enqueueWork(context, loc);
        } else {
            Log.d(TAG, "location unavailable");
        }
    }
}