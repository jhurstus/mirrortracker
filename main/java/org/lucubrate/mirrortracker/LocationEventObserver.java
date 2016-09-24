package org.lucubrate.mirrortracker;

/** Class that can receive location updates. */
interface LocationEventObserver {
    void onLocationUpdated(LocationEvent e);
}
