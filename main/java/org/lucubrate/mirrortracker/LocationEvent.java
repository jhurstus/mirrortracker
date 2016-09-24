package org.lucubrate.mirrortracker;

import com.google.firebase.database.IgnoreExtraProperties;

/**
 * POJO representing a timestamped user location.
 */
@IgnoreExtraProperties
public class LocationEvent {
    public long timestamp;
    public String city;
    public String state;
    public String country;
    /** User defined label for this location, or null if not available.  e.g. "home" */
    public String label;

    public LocationEvent(long timestamp, String city, String state, String country, String label) {
        this.timestamp = timestamp;
        this.city = city;
        this.state = state;
        this.country = country;
        this.label = label;
    }

    public LocationEvent() {
        // Default constructor required for calls to DataSnapshot.getValue(LocationEvent.class)
    }
}
