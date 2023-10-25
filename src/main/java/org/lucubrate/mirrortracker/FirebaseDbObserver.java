package org.lucubrate.mirrortracker;

import java.util.List;

/** Class that observes changes to FirebaseDB data. */
interface FirebaseDbObserver {
    void onLocationUpdated(LocationEvent e);
    void onShowPrivateInfoUpdated(boolean show);
    void onMemoUpdated(String memo);
    void onGeofencesUpdated(List<Geofence> geofences);
}