package org.lucubrate.mirrortracker;

/**
 * POJO representing a timestamped user location.
 */
public class LocationEvent {
    private long timestamp;
    private String city;
    private String state;
    private String country;
    private String label;

    public long getTimestamp() {
        return timestamp;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }

    /**
     * @return User defined label for this location, or null if not available.  e.g. "home"
     */
    public String getLabel() {
        return label;
    }

    public LocationEvent(long timestamp, String city, String state, String country, String label) {
        this.timestamp = timestamp;
        this.city = city;
        this.state = state;
        this.country = country;
        this.label = label;
    }

}
