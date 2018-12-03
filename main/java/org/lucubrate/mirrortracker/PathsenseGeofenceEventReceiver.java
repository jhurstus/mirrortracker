package org.lucubrate.mirrortracker;

import android.content.Context;
import android.util.Log;

import com.pathsense.android.sdk.location.PathsenseGeofenceEvent;

public class PathsenseGeofenceEventReceiver extends com.pathsense.android.sdk.location.PathsenseGeofenceEventReceiver {
    @Override
    protected void onGeofenceEvent(Context context, PathsenseGeofenceEvent pathsenseGeofenceEvent) {
        if (pathsenseGeofenceEvent.isIngress()) {
            Log.d("georeceiver", "inin");
        } else if (pathsenseGeofenceEvent.isEgress()) {
            Log.d("georeceiver", "outout");
        }
    }
}
