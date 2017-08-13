package org.lucubrate.mirrortracker;

/**
 * Intent constants for use with {@link FetchAddressIntentService}.
 */
final class FetchAddressIntentServiceConstants {
    private FetchAddressIntentServiceConstants() {}

    private static final String PACKAGE_NAME = "org.lucubrate.mirrortracker";

    /** Return code for a successful address lookup. */
    static final int SUCCESS_RESULT = 0;
    /** Return code for a failed address lookup. */
    static final int FAILURE_RESULT = 1;
    /** Key for ResultReceiver to which address should be returned. */
    static final String RECEIVER = PACKAGE_NAME + ".RECEIVER";
    /** Key for parceled Address object returned to ResultReceiver, or a String error message. */
    static final String RESULT_DATA_KEY = PACKAGE_NAME + ".RESULT_DATA_KEY";
    /** Key for parceled Location object for which to look up address. */
    static final String LOCATION_DATA_EXTRA = PACKAGE_NAME + ".LOCATION_DATA_EXTRA";
}
