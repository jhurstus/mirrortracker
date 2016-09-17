package org.lucubrate.mirrortracker;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.lucubrate.mirrortracker.BR;  // KEEP: needed for Android Studio.

/**
 * Firebase realtime database data binding.
 */
public class FirebaseDB {
    /** Android databinding object that connects to Firebase realtime database. */
    public static class Model extends BaseObservable {
        private Model(boolean hidePrivateInfo) {
            this.hidePrivateInfo = hidePrivateInfo;
        }

        /**
         * @return Whether private/sensitive info on the mirror display should be hidden.
         */
        @Bindable
        public boolean isHidePrivateInfo() {
            return hidePrivateInfo;
        }

        public void setHidePrivateInfo(boolean hidePrivateInfo) {
            this.hidePrivateInfo = hidePrivateInfo;
            notifyPropertyChanged(BR.hidePrivateInfo);
        }

        private boolean hidePrivateInfo;
    }

    final private static String TAG = "FirebaseDB";

    private FirebaseDatabase mDB;
    private Model mModel;
    private DatabaseReference hidePrivateInfo;

    FirebaseDB() {
        mDB = FirebaseDatabase.getInstance();

        mModel = new Model(false);

        hidePrivateInfo = mDB.getReference("mirror/config/hidePrivateInfo");
        hidePrivateInfo.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mModel.setHidePrivateInfo(dataSnapshot.getValue(Boolean.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });
    }

    /**
     * @return Android SignedInActivity databinding model, bound to FirebaseDB.
     */
    public Model getModel() {
        return mModel;
    }
}
