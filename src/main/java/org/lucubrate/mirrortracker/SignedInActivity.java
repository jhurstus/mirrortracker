package org.lucubrate.mirrortracker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.IBinder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;

import org.lucubrate.mirrortracker.databinding.ActivitySignedInBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Main activity, shown to signed-in users. */
public class SignedInActivity extends AppCompatActivity
        implements FirebaseDbObserver, SignedInHandler, DebugLogWriteObserver {

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

        mModel = new Model(
                getString(R.string.loading), true, true, "");
        syncModelToService();

        ActivitySignedInBinding binding = DataBindingUtil.setContentView(
                this, R.layout.activity_signed_in);
        binding.setModel(mModel);
        binding.setHandler(this);

        if (mBound) {
            onLocationUpdated(mService.getLastLocation());
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ((TextView) findViewById(R.id.debugLog)).setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            mService.setFireBaseDbObserver(null);
            mService.setDebugLogWriteOberver(null);
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
    public void onGeofencesUpdated(List<Geofence> geofences) {}

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
                .addOnCompleteListener(task -> {
                    startActivity(SignedOutActivity.createIntent(SignedInActivity.this));
                    finish();
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

    @Override
    public void onLogWritten(List<String> logLines) {
        if (mModel != null) {
            List<String> descending = new ArrayList<>(logLines);
            Collections.reverse(descending);
            mModel.setDebugLog(String.join("\n", descending));
        }
    }

    /** Android databinding model for layout. */
    public static class Model extends BaseObservable {
        private boolean showPrivateInfo;
        private boolean shareLocation;
        private String lastKnownLocation;
        private String debugLog;

        Model(String lastKnownLocation, boolean showPrivateInfo, boolean shareLocation,
              String debugLog) {
            this.lastKnownLocation = lastKnownLocation;
            this.showPrivateInfo = showPrivateInfo;
            this.shareLocation = shareLocation;
            this.debugLog = debugLog;
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

        /**
         * @return Debug log text, if any.
         */
        @Bindable
        public String getDebugLog() {
            return debugLog;
        }

        void setDebugLog(String debugLog) {
            this.debugLog = debugLog;
            notifyPropertyChanged(BR.debugLog);
        }
    }

    private void syncModelToService() {
        if (mBound  && mModel != null) {
            mModel.setShowPrivateInfo(mService.showPrivateInfo());
            mModel.setShareLocation(mService.shareLocation());
            onLocationUpdated(mService.getLastLocation());
        }
    }

    // Bound connection to LocationService.
    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LocationService.LocalBinder binder = (LocationService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.setFireBaseDbObserver(SignedInActivity.this);
            mService.setDebugLogWriteOberver(SignedInActivity.this);
            SignedInActivity.this.onLogWritten(mService.debugLogLines());
            syncModelToService();
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            mBound = false;
        }
    };
}
