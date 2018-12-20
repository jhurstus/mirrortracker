package org.lucubrate.mirrortracker;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * JobIntentService that writes user {@link Location}s to FirebaseDB.
 *
 * Add jobs with {@link #enqueueWork(Context, Intent)}.
 */
public class FirebaseLocationWriteService extends JobIntentService {
    private final static String TAG = "FirebaseLocationWriteService";

    /** Key for parceled Location object to be written to Firebase DB. */
    static final String LOCATION_DATA_EXTRA = "org.lucubrate.mirrortracker.LOCATION_DATA_EXTRA";

    /**
     * Key for parceled {@link Address} object posted to {@link LocationService} (if running) after
     * writing location to Firebase DB.
     */
    static final String RESULT_DATA_KEY =  "org.lucubrate.mirrortracker.RESULT_DATA_KEY";

    /**
     * Enqueue a job that will upload a passed {@link Location} to the logged-in user's Firebase DB
     * row.
     */
    static void enqueueWork(Context context, Location location) {
        Log.d(TAG, "scheduling firebasedb location write job");
        final Intent work = new Intent(context, FirebaseLocationWriteService.class);
        work.putExtra(LOCATION_DATA_EXTRA, location);
        enqueueWork(context, FirebaseLocationWriteService.class, 0x02, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "handling job");

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        Location location = intent.getParcelableExtra(LOCATION_DATA_EXTRA);

        List<Address> addresses;
        try {
            addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1 /* only need one address, since just getting city/state/country */);
        } catch (IOException ioException) {
            Log.e(TAG, "I/O issue with reverse geocoding", ioException);
            return;
        } catch (IllegalArgumentException illegalArgumentException) {
            Log.e(TAG, "invalid lat/long. " +
                    "Latitude = " + location.getLatitude() +
                    ", Longitude = " +
                    location.getLongitude(), illegalArgumentException);
            return;
        }

        if (addresses == null || addresses.size() == 0) {
            Log.e(TAG, "no address found");
            return;
        }

        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            Log.e(TAG, "no firebase user for which to update location");
            return;
        }

        Log.d(TAG, "syncing new location to server");
        Address address = addresses.get(0);
        LocationEvent e = new LocationEvent(
                location.getTime(), address.getLocality(), address.getAdminArea(),
                address.getCountryName(), location.getLatitude(),
                location.getLongitude(), "");
        DebugLog.getInstance(this.getFilesDir()).logDbWrite();
        FirebaseDB.getInstance(u.getUid(), null).updateLocation(e);
        Log.i(TAG, "updated location in firebase");

        // If LocationService is running, notify it of updated location so it can update its state
        // and/or bound Activity.  We can't just start the service here due to Android O+ background
        // service limitations.
        if (LocationService.started) {
            Intent i = new Intent(getApplicationContext(), LocationService.class);
            Bundle bundle = new Bundle();
            bundle.putParcelable(RESULT_DATA_KEY, address);
            bundle.putParcelable(LOCATION_DATA_EXTRA, location);
            i.putExtras(bundle);

            startService(i);
        }
    }
}

