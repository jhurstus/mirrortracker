package org.lucubrate.mirrortracker;

import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Locale;

/**
 * Firebase realtime database data binding.
 */
class FirebaseDB  {
    final private static String TAG = "FirebaseDB";

    private final DatabaseReference showPrivateInfo;
    private final DatabaseReference user;
    private static boolean hasSetPersistence = false;

    private LocationService mService;

    private static FirebaseDB db;

    /**
     * @param uid firebase user (from {@link FirebaseAuth#getCurrentUser()} to which per-user data
     *            will be written
     * @param service if available, LocationService to which DB updates will be posted
     * @return singleton FirebaseDB instance
     */
    static FirebaseDB getInstance(@NonNull String uid, @Nullable LocationService service) {
        if (db == null) {
            db = new FirebaseDB(uid, service);
        }
        return db;
    }

    private FirebaseDB(String uid, LocationService service) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();

        // Only set persistence once per activation, otherwise firebase crashes.
        if (!hasSetPersistence) {
            hasSetPersistence = true;
            db.setPersistenceEnabled(true);
        }

        mService = service;

        showPrivateInfo = db.getReference("mirror/config/showPrivateInfo");
        showPrivateInfo.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (mService != null) {
                    Boolean showPrivateInfo = dataSnapshot.getValue(Boolean.class);
                    mService.onShowPrivateInfoUpdated(showPrivateInfo != null && showPrivateInfo);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });

        DatabaseReference geofences = db.getReference("mirror/geofences");
        geofences.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                GenericTypeIndicator<List<Geofence>> t =
                        new GenericTypeIndicator<List<Geofence>>() {};
                if (mService != null) {
                    mService.onGeofencesUpdated(dataSnapshot.getValue(t));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });

        user = db.getReference("users/" + uid);
    }

    /**
     * @param service LocationServing instance to which db updates will be posted
     */
    void setLocationService(@Nullable LocationService service) {
        mService = service;
    }

    /** Updates current user location. */
    void updateLocation(Context context, Location location) {
        Log.d(TAG, "updating location");

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1 /* only need one address, since just getting city/state/country */,
                    addresses -> {
                        if (addresses.size() == 0) {
                            Log.e(TAG, "no address found");
                            return;
                        }

                        Log.d(TAG, "syncing geocoded location to db");
                        Address address = addresses.get(0);
                        LocationEvent ev = new LocationEvent(
                                location.getTime(), address.getLocality(), address.getAdminArea(),
                                address.getCountryName(), location.getLatitude(),
                                location.getLongitude(), "");

                        DatabaseReference.goOnline();
                        user.child("location").setValue(ev);
                        Log.i(TAG, "updated location in firebase");

                        // If LocationService is running, notify it of updated location so it can update its state
                        // and/or bound Activity.  We can't just start the service here due to Android O+ background
                        // service limitations.
                        if (LocationService.started) {
                            Intent i = new Intent(context.getApplicationContext(), LocationService.class);
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(LocationService.RESULT_DATA_KEY, address);
                            bundle.putParcelable(LocationService.LOCATION_DATA_EXTRA, location);
                            i.putExtras(bundle);

                            context.startService(i);
                        }

                        DebugLog.getInstance(context.getFilesDir()).logDbWrite();
                    });
        } catch (IllegalArgumentException illegalArgumentException) {
            Log.e(TAG, "invalid lat/long. " +
                    "Latitude = " + location.getLatitude() +
                    ", Longitude = " +
                    location.getLongitude(), illegalArgumentException);
        }
    }

    /** Updates whether to show private info on mirror. */
    void updateShowPrivateInfo(boolean show) {
        showPrivateInfo.setValue(show);
    }

    /** Updates whether to share location of current user. */
    void updateShareLocation(boolean share) {
        user.child("shareLocation").setValue(share);
    }
}
