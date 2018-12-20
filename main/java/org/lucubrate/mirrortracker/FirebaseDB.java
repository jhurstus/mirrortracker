package org.lucubrate.mirrortracker;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

/**
 * Firebase realtime database data binding.
 */
class FirebaseDB  {
    final private static String TAG = "FirebaseDB";

    private DatabaseReference showPrivateInfo;
    private DatabaseReference user;
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
    void updateLocation(LocationEvent e) {
        if (e != null) {
            user.child("location").setValue(e);
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
