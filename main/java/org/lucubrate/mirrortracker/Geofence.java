package org.lucubrate.mirrortracker;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * POJO representing a location geofence.
 */
@IgnoreExtraProperties
public class Geofence {
    public String label;
    public double lat;
    public double lng;
    public int radius;

    // Default constructor required for FirebaseDB integration.
    public Geofence() {}
}
