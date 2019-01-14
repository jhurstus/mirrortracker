package org.lucubrate.mirrortracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.GeofencingEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;

public class GeofenceReceiver extends BroadcastReceiver {
    private static final String TAG = "GeofenceReceiver";

    // This MUST match action filter for this receiver in the manifest.
    static String GEOFENCE_INTENT_ACTION = "org.lucubrate.mirrortracker.GEOFENCE_LOCATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !GEOFENCE_INTENT_ACTION.equals(intent.getAction())) {
            Log.e(TAG, "unexpected intent received");
            return;
        }
        Log.d(TAG, "received geofence intent");

        GeofencingEvent e = GeofencingEvent.fromIntent(intent);
        if (e.hasError()) {
            Log.e(TAG, "GeofencingEvent error: " + Integer.toString(e.getErrorCode()));
            return;
        }

        if (!NetworkCheck.isNetworkAvailable(context)) {
            Log.d(TAG, "ignoring geofence evt because we can't ping it to the network now.");
            return;
        }

        int geofenceTransition = e.getGeofenceTransition();
        if (geofenceTransition == GEOFENCE_TRANSITION_ENTER ||
                geofenceTransition == GEOFENCE_TRANSITION_EXIT) {
            DebugLog.getInstance(context.getFilesDir()).logGeofencingEvent(e);
            Location loc = e.getTriggeringLocation();
            if (loc != null) {
                Log.d(TAG, "sending fused location to db");

                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                if (u == null) {
                    Log.e(TAG, "no firebase user for which to update location");
                    return;
                }
                FirebaseDB.getInstance(u.getUid(), null).updateLocation(context, loc);
            } else {
                Log.d(TAG, "location unavailable");
            }
        }
    }
}