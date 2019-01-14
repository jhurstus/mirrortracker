package org.lucubrate.mirrortracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

class NetworkCheck {
    private NetworkCheck() {
        throw new RuntimeException("Do not instantiate.");
    }

    /** Checks whether device network is connected. */
    static boolean isNetworkAvailable(Context ctx) {
        ConnectivityManager manager =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Pings a remote internet host synchronously.
     * Failures (including timeouts) are silently ignored.
     */
    static void pingInternet() {
        URL googPing;
        HttpURLConnection urlConnection = null;
        try {
            googPing = new URL("http://www.google.com/");
            urlConnection = (HttpURLConnection) googPing.openConnection();
            urlConnection.setRequestProperty("User-Agent", "Mirror");
            urlConnection.setRequestProperty("Connection", "close");
            urlConnection.setRequestMethod("HEAD");
            final int PING_TIMEOUT = 1000;
            urlConnection.setConnectTimeout(PING_TIMEOUT);
            urlConnection.connect();
        } catch (IOException e) {
            // We're just *trying* to ping internet.  Ignore failures.
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
