package org.lucubrate.mirrortracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
import static com.google.android.gms.location.Geofence.NEVER_EXPIRE;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.LOCATION_DATA_EXTRA;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RECEIVER;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RESULT_DATA_KEY;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.SUCCESS_RESULT;

/**
 * Background service that receives location updates and forwards them to mirror server.
 *
 * Location is primarily tracked using GMS Geofence enter/exit events.  This is supplemented by a
 * generic location tracker, since it's possible for the Geofence events to fail in various
 * scenarios (not to mention any bugs in the Geofence implementation).
 */
public class LocationService extends JobIntentService implements FirebaseDbObserver {
    final private static String TAG = "LocationService";

    private SharedPreferences mPrefs;
    private LocationCallback mLocationCallback;
    private FetchAddressReceiver mFetchAddressReceiver;
    private LocationEvent mLastLocation;
    private FirebaseDbObserver mActivity;
    private FirebaseDB mDB;
    private List<Geofence> mGeofences;
    private DebugLog mDebugLog;

    private boolean mShowPrivateInfo;

    void setFireBaseDbObserver(FirebaseDbObserver o) {
        mActivity = o;
    }

    void setDebugLogWriteOberver(DebugLogWriteObserver o) {
        mDebugLog.setObserver(o);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LocationService creating.");

        if (mDebugLog == null) {
            mDebugLog = new DebugLog(this.getFilesDir());
        }
        mDebugLog.logServiceStarted();

        // Don't bother running service if not auth'ed.  We can't update the DB in that case.
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            stopSelf();
            return;
        }

        mPrefs = getSharedPreferences(Preferences.PREFERENCE_FILE_NAME.toString(), MODE_PRIVATE);
        mDB = new FirebaseDB(u.getUid(), this);
        mFetchAddressReceiver = new FetchAddressReceiver(new Handler());

        updateLocationTracking();
    }

    @Override
    public void onGeofencesUpdated(List<Geofence> geofences) {
        removeGeofences();
        mGeofences = geofences;
        if (shouldTrackLocation()) {
            addGeofences();
        }
    }

    /** Whether the service is in a state in which it can mutate GMS geofence state. */
    private boolean canUpdateGeofences() {
        if (mGeofences == null || mGeofences.isEmpty()) {
            return false;
        }

        if (getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission denied");
            return false;
        }
        return true;
    }

    /** Starts tracking geofences. */
    private void addGeofences() {
        if (!canUpdateGeofences()) {
            return;
        }

        List<com.google.android.gms.location.Geofence> fences = new ArrayList<>(mGeofences.size());
        for (Geofence fence : mGeofences) {
            fences.add(new com.google.android.gms.location.Geofence.Builder()
                    .setRequestId(fence.label)
                    .setCircularRegion(fence.lat, fence.lng, fence.radius)
                    .setExpirationDuration(NEVER_EXPIRE)
                    .setTransitionTypes(GEOFENCE_TRANSITION_ENTER | GEOFENCE_TRANSITION_EXIT)
                    .build());
        }
        GeofencingRequest fenceReq = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build();
        Intent intent = new Intent(this, LocationService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            GeofencingClient client = LocationServices.getGeofencingClient(this);
            client.addGeofences(fenceReq, pendingIntent);
        } catch (SecurityException e) {
            // Should never happen.  Permission checked in canUpdateGeofences.
        }
    }

    /** Stop tracking geofences. */
    private void removeGeofences() {
        if (!canUpdateGeofences()) {
            return;
        }
        List<String> labels = new ArrayList<>(mGeofences.size());
        for (Geofence f : mGeofences) {
            labels.add(f.label);
        }
        GeofencingClient client = LocationServices.getGeofencingClient(this);
        client.removeGeofences(labels);
    }

    private void handleGeofenceIntent(Intent intent) {
        GeofencingEvent e = GeofencingEvent.fromIntent(intent);
        if (e.hasError()) {
            Log.e(TAG, "GeofencingEvent error: " + Integer.toString(e.getErrorCode()));
        } else {
            int geofenceTransition = e.getGeofenceTransition();
            if (geofenceTransition == GEOFENCE_TRANSITION_ENTER ||
                    geofenceTransition == GEOFENCE_TRANSITION_EXIT) {
                mDebugLog.logGeofencingEvent(e);
                Location loc = e.getTriggeringLocation();
                if (loc != null) {
                    sendGeocodeRequest(loc);
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null) {
            handleGeofenceIntent(intent);
        }

        return START_STICKY;
    }

    private void updateLocationTracking() {
        if (shouldTrackLocation()) {
            startTrackingLocation();
        } else {
            stopTrackingLocation();
        }
    }

    private void startTrackingLocation() {
        if (getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission denied");
            return;
        }
        Log.i(TAG, "starting tracking");

        addGeofences();

        mLocationCallback =  new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                mDebugLog.logLocationUpdated(result);
                sendGeocodeRequest(result.getLastLocation());
            }
        };

        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setFastestInterval(5 * 60 * 1000)  // receive updates no faster than 5 minutely
                .setInterval(20 * 60 * 1000)  // try to update at least every 20 minutes
                .setSmallestDisplacement(15);  // no updates for deltas < 15 meters.

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(this);
        client.requestLocationUpdates(req, mLocationCallback, Looper.myLooper());
    }

    void stopTrackingLocation() {
        Log.i(TAG, "stopping tracking");
        removeGeofences();
        if (mLocationCallback != null) {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(this);
            client.removeLocationUpdates(mLocationCallback);
        }
    }

    private boolean shouldTrackLocation() {
        return FirebaseAuth.getInstance().getCurrentUser() != null &&
                Geocoder.isPresent() &&
                mPrefs != null &&
                mPrefs.getBoolean(Preferences.SHARE_LOCATION_PREF_KEY.toString(), true);
    }

    private void sendGeocodeRequest(Location location) {
        Log.i(TAG, "sending geocode request");
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(RECEIVER, mFetchAddressReceiver);
        intent.putExtra(LOCATION_DATA_EXTRA, location);
        FetchAddressIntentService.enqueueWork(getApplicationContext(), intent);
    }

    // Binder to FetchAddressIntentService.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onLocationUpdated(LocationEvent e) {
        // Do nothing -- UI already updated directly from geocoding callback.
    }

    LocationEvent getLastLocation() {
        return mLastLocation;
    }

    void updateShowPrivateInfo(boolean show) {
        mShowPrivateInfo = show;
        mDB.updateShowPrivateInfo(show);
    }

    @Override
    public void onShowPrivateInfoUpdated(boolean show) {
        mShowPrivateInfo = show;
        if (mActivity != null) {
            mActivity.onShowPrivateInfoUpdated(show);
        }
    }

    boolean showPrivateInfo() {
        return mShowPrivateInfo;
    }

    // Only running on my fast phone, so synchronous IO is fine.
    @SuppressLint("ApplySharedPref")
    void updateShareLocation(boolean share) {
        mPrefs.edit()
                .putBoolean(Preferences.SHARE_LOCATION_PREF_KEY.toString(), share)
                .commit();
        mDB.updateShareLocation(share);
        updateLocationTracking();
    }

    boolean shareLocation() {
        return mPrefs.getBoolean(Preferences.SHARE_LOCATION_PREF_KEY.toString(), true);
    }

    List<String> debugLogLines() {
        if (mDebugLog != null) {
            return mDebugLog.getLogLines();
        }
        return new ArrayList<>();
    }

    public class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "destroying LocationService");
        mDebugLog.logServiceStopped();
        super.onDestroy();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d("boot", "handle work");
    }

    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, LocationService.class, 0x01, work);
    }

    private class FetchAddressReceiver extends ResultReceiver {
        FetchAddressReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode == SUCCESS_RESULT && resultData != null) {
                Log.d(TAG, "syncing new location to server");
                Address address = resultData.getParcelable(RESULT_DATA_KEY);
                Location location = resultData.getParcelable(LOCATION_DATA_EXTRA);
                if (address != null && location != null) {
                    mLastLocation = new LocationEvent(
                            location.getTime(), address.getLocality(), address.getAdminArea(),
                            address.getCountryName(), location.getLatitude(),
                            location.getLongitude(), "");
                    mDB.updateLocation(mLastLocation);
                    if (mActivity != null) {
                        mActivity.onLocationUpdated(mLastLocation);
                    }
                }
            }
        }
    }
}