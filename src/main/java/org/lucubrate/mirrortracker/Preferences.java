package org.lucubrate.mirrortracker;

/** Keys for app shared preferences. */
enum Preferences {
    PREFERENCE_FILE_NAME("mirror"),
    SHARE_LOCATION_PREF_KEY("share")
    ;

    final String val;

    Preferences(final String val) {
        this.val = val;
    }
}
