package org.lucubrate.mirrortracker;

/** Class that observes changes to FirebaseDB data. */
interface FirebaseDbObserver {
    void onLocationUpdated(LocationEvent e);
    void onShowPrivateInfoUpdated(boolean show);
}