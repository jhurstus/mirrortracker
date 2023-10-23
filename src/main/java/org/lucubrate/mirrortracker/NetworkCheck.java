package org.lucubrate.mirrortracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

class NetworkCheck {
    private NetworkCheck() {
        throw new RuntimeException("Do not instantiate.");
    }

    /** Checks whether device network is connected. */
    static boolean isNetworkUnavailable(Context ctx) {
        ConnectivityManager manager =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return true;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
}
