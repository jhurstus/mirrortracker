package org.lucubrate.mirrortracker;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.lucubrate.mirrortracker.BR;  // KEEP: needed for Android Studio.

/**
 * Firebase realtime database data binding.
 */
public class FirebaseDB implements SignedInHandler {

    /** Android databinding object that connects to Firebase realtime database. */
    public static class Model extends BaseObservable {
        private Model(boolean showPrivateInfo) {
            this.showPrivateInfo = showPrivateInfo;
        }

        /**
         * @return Whether private/sensitive info on the mirror display should be shown.
         */
        @Bindable
        public boolean isShowPrivateInfo() {
            return showPrivateInfo;
        }

        public void setShowPrivateInfo(boolean showPrivateInfo) {
            this.showPrivateInfo = showPrivateInfo;
            notifyPropertyChanged(BR.showPrivateInfo);
        }

        private boolean showPrivateInfo;
    }

    final private static String TAG = "FirebaseDB";

    private FirebaseDatabase mDB;
    private Model mModel;
    private DatabaseReference showPrivateInfo;

    FirebaseDB() {
        mDB = FirebaseDatabase.getInstance();

        mModel = new Model(false);

        showPrivateInfo = mDB.getReference("mirror/config/showPrivateInfo");
        showPrivateInfo.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mModel.setShowPrivateInfo(dataSnapshot.getValue(Boolean.class));
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

    @Override
    public void onShowPrivateInfoChecked(View view) {
        showPrivateInfo.setValue(((Switch) view).isChecked());
    }
}
