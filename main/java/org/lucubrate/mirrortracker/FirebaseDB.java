package org.lucubrate.mirrortracker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Switch;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Firebase realtime database data binding.
 */
public class FirebaseDB implements SignedInHandler, LocationEventObserver {

    @Override
    public void onLocationUpdated(LocationEvent e) {
        if (e != null) {
            mModel.setLastKnownLocation(e.getCity() + ", " + e.getState());
        }
    }

    /** Android databinding object that connects to Firebase realtime database. */
    public static class Model extends BaseObservable {
        private boolean showPrivateInfo;
        private boolean shareLocation;
        private String lastKnownLocation;

        Model(String lastKnownLocation, boolean showPrivateInfo, boolean shareLocation) {
            this.lastKnownLocation = lastKnownLocation;
            this.showPrivateInfo = showPrivateInfo;
            this.shareLocation = shareLocation;
        }

        /**
         * @return Last known user location, if known.
         */
        @Bindable
        public String getLastKnownLocation() {
            return lastKnownLocation;
        }

        void setLastKnownLocation(String lastKnownLocation) {
            this.lastKnownLocation = lastKnownLocation;
            notifyPropertyChanged(BR.lastKnownLocation);
        }

        /**
         * @return Whether private/sensitive info on the mirror display should be shown.
         */
        @Bindable
        public boolean isShowPrivateInfo() {
            return showPrivateInfo;
        }

        void setShowPrivateInfo(boolean showPrivateInfo) {
            this.showPrivateInfo = showPrivateInfo;
            notifyPropertyChanged(BR.showPrivateInfo);
        }

        /**
         * @return Whether to share device location with mirror.
         */
        @Bindable
        public boolean isShareLocation() {
            return shareLocation;
        }

        void setShareLocation(boolean shareLocation) {
            this.shareLocation = shareLocation;
            notifyPropertyChanged(BR.shareLocation);
        }
    }

    final private static String TAG = "FirebaseDB";

    private SharedPreferences mPrefs;
    private Context mContext;
    private FirebaseDatabase mDB;
    private Model mModel;
    private DatabaseReference showPrivateInfo;
    private DatabaseReference user;
    // Only set persistence once per activation, otherwise firebase crashes.
    private static boolean hasSetPersistence = false;

    private LocationService mService;
    private boolean mBound = false;

    FirebaseDB(String uid, SharedPreferences prefs, Context context) {
        mPrefs = prefs;
        mContext = context;

        mDB = FirebaseDatabase.getInstance();
        if (!hasSetPersistence) {
            hasSetPersistence = true;
            mDB.setPersistenceEnabled(true);
        }

        mModel = new Model(mContext.getString(R.string.loading), true, true);

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

        user = mDB.getReference("users/" + uid);
        user.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists() || !dataSnapshot.hasChildren()) {
                  return;
                }
                boolean share = dataSnapshot.child("shareLocation").getValue(Boolean.class);
                mPrefs.edit()
                        .putBoolean(Preferences.SHARE_LOCATION_PREF_KEY.toString(), share)
                        .commit();
                Intent i = new Intent(mContext, LocationService.class);
                if (!share) {
                    i.putExtra(LocationService.STOP_EXTRA, true);
                    if (mBound) {
                        mContext.unbindService(mConnection);
                        mBound = false;
                    }
                }
                mContext.startService(i);
                mModel.setShareLocation(share);

                if (share) {
                    Intent bindIntent = new Intent(mContext, LocationService.class);
                    mContext.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
                }
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
    Model getModel() {
        return mModel;
    }

    @Override
    public void onShowPrivateInfoChecked(View view) {
        showPrivateInfo.setValue(((Switch) view).isChecked());
    }

    @Override
    public void onShareLocationChecked(View view) {
        user.child("shareLocation").setValue(((Switch) view).isChecked());
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setLocationEventObserver(FirebaseDB.this);
            onLocationUpdated(mService.getLastLocation());
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    void onStop() {
        if (mBound) {
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }
}
