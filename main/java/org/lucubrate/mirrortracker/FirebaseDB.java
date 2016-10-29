package org.lucubrate.mirrortracker;

import android.util.Log;

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
    // Only set persistence once per activation, otherwise firebase crashes.
    private static boolean hasSetPersistence = false;

    private LocationService mService;

    FirebaseDB(String uid, LocationService service) {
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        if (!hasSetPersistence) {
            hasSetPersistence = true;
            db.setPersistenceEnabled(true);
        }

        mService = service;

        showPrivateInfo = db.getReference("mirror/config/showPrivateInfo");
        showPrivateInfo.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mService.onShowPrivateInfoUpdated(dataSnapshot.getValue(Boolean.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });

        DatabaseReference geofences = db.getReference("mirror/geofences");
        geofences.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                GenericTypeIndicator<List<Geofence>> t =
                        new GenericTypeIndicator<List<Geofence>>() {};
                mService.onGeofencesUpdated(dataSnapshot.getValue(t));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });

        user = db.getReference("users/" + uid);
    }

    void updateLocation(LocationEvent e) {
        if (e != null) {
            user.child("location").setValue(e);
        }
    }

    void updateShowPrivateInfo(boolean show) {
        showPrivateInfo.setValue(show);
    }

    void updateShareLocation(boolean share) {
        user.child("shareLocation").setValue(share);
    }
}
