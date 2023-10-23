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
    public double lat;
    public double lng;
    /** User defined label for this location, or null if not available.  e.g. "home" */
    public String label;

    public LocationEvent(long timestamp, String city, String state, String country, double lat,
                         double lng, String label) {
        this.timestamp = timestamp;
        this.city = city;
        this.state = state;
        this.country = country;
        this.lat = lat;
        this.lng = lng;
        this.label = label;
    }

    /** @noinspection unused*/
    public LocationEvent() {
        // Default constructor required for calls to DataSnapshot.getValue(LocationEvent.class)
    }
}
