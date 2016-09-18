package org.lucubrate.mirrortracker;

/** Keys for app shared preferences. */
public enum Preferences {
    PREFERENCE_FILE_NAME("mirror"),
    SHARE_LOCATION_PREF_KEY("share")
    ;

    private final String val;

    private Preferences(final String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return val;
    }
}
