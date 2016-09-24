package org.lucubrate.mirrortracker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Switch;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

import org.lucubrate.mirrortracker.databinding.ActivitySignedInBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Main activity, shown to signed-in users. */
public class SignedInActivity extends AppCompatActivity
        implements FirebaseDbObserver, SignedInHandler {

    private Model mModel;

    private boolean mBound = false;
    private LocationService mService;

    /** Creates an intent to start this activity. */
    static Intent createIntent(Context context) {
        Intent i = new Intent();
        i.setClass(context, SignedInActivity.class);
        return i;
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(SignedOutActivity.createIntent(this));
            finish();
            return;
        }

        Intent i = new Intent(this, LocationService.class);
        startService(i);  // start explicitly to make service sticky
        bindService(i, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new Model(getString(R.string.loading), true, true);
        ActivitySignedInBinding binding = DataBindingUtil.setContentView(
                this, R.layout.activity_signed_in);
        binding.setModel(mModel);
        binding.setHandler(this);

        if (mBound) {
            onLocationUpdated(mService.getLastLocation());
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            mService.setFireBaseDbObserver(null);
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onLocationUpdated(LocationEvent e) {
        if (e != null) {
            mModel.setLastKnownLocation(
                    e.city + ", " + e.state + " at " + getTime(e.timestamp));
        }
    }

    @Override
    public void onShowPrivateInfoUpdated(boolean show) {
        mModel.setShowPrivateInfo(show);
    }

    @Override
    public void onShowPrivateInfoChecked(View view) {
        boolean checked = ((Switch) view).isChecked();
        mModel.setShowPrivateInfo(checked);
        if (mBound) {
            mService.updateShowPrivateInfo(checked);
        }
    }

    @Override
    public void onShareLocationChecked(View view) {
        boolean checked = ((Switch) view).isChecked();
        mModel.setShareLocation(checked);
        if (mBound) {
            mService.updateShareLocation(checked);
            onLocationUpdated(mService.getLastLocation());
        }
    }

    @Override
    public void onSignOutClicked(View view) {
        if (mBound) {
            mService.stopTrackingLocation();
        }
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        startActivity(SignedOutActivity.createIntent(SignedInActivity.this));
                        finish();
                    }
                });
    }

    /** Converts a UNIX UTC timestamp to a local time string, e.g. "8:32 PM". */
    private String getTime(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        c.add(Calendar.MILLISECOND, TimeZone.getTimeZone("UTC").getOffset(c.getTimeInMillis()));

        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(c.getTime());
    }

    /** Android databinding model for layout. */
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
    }

    // Bound connection to LocationService.
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setFireBaseDbObserver(SignedInActivity.this);
            onLocationUpdated(mService.getLastLocation());
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            mBound = false;
        }
    };
}
