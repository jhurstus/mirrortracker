package org.lucubrate.mirrortracker;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Firebase realtime database data binding.
 */
public class FirebaseDB  {

    final private static String TAG = "FirebaseDB";

    private FirebaseDatabase mDB;
    private DatabaseReference showPrivateInfo;
    private DatabaseReference user;
    // Only set persistence once per activation, otherwise firebase crashes.
    private static boolean hasSetPersistence = false;

    private LocationService mService;

    FirebaseDB(String uid, LocationService service) {
        mDB = FirebaseDatabase.getInstance();
        if (!hasSetPersistence) {
            hasSetPersistence = true;
            mDB.setPersistenceEnabled(true);
        }

        mService = service;

        showPrivateInfo = mDB.getReference("mirror/config/showPrivateInfo");
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

        user = mDB.getReference("users/" + uid);
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
