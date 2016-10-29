package org.lucubrate.mirrortracker;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
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
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
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
public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, FirebaseDbObserver {
    final private static String TAG = "LocationService";

    private GoogleApiClient mGoogleApiClient;
    private SharedPreferences mPrefs;
    private LocationListener mLocationListener;
    private FetchAddressReceiver mFetchAddressReceiver;
    private LocationEvent mLastLocation;
    private FirebaseDbObserver mActivity;
    private FirebaseDB mDB;
    private List<Geofence> mGeofences;

    private boolean mShowPrivateInfo;

    void setFireBaseDbObserver(FirebaseDbObserver o) {
        mActivity = o;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LocationService creating.");

        // Don't bother running service if not auth'ed.  We can't update the DB in that case.
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            stopSelf();
            return;
        }

        mPrefs = getSharedPreferences(Preferences.PREFERENCE_FILE_NAME.toString(), MODE_PRIVATE);
        mDB = new FirebaseDB(u.getUid(), this);
        mFetchAddressReceiver = new FetchAddressReceiver(new Handler());
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
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
        if (mGeofences == null || mGeofences.isEmpty() || mGoogleApiClient == null) {
            return false;
        }
        if (!mGoogleApiClient.isConnected()) {
            if (!mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }
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
            LocationServices.GeofencingApi.addGeofences(mGoogleApiClient, fenceReq, pendingIntent);
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
        LocationServices.GeofencingApi.removeGeofences(mGoogleApiClient, labels);
    }

    private void handleGeofenceIntent(Intent intent) {
        GeofencingEvent e = GeofencingEvent.fromIntent(intent);
        if (e.hasError()) {
            Log.e(TAG, "GeofencingEvent error: " + Integer.toString(e.getErrorCode()));
        } else {
            int geofenceTransition = e.getGeofenceTransition();
            if (geofenceTransition == GEOFENCE_TRANSITION_ENTER ||
                    geofenceTransition == GEOFENCE_TRANSITION_EXIT) {
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

        if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }

        if (intent != null) {
            handleGeofenceIntent(intent);
        }

        return START_STICKY;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        updateLocationTracking();
    }

    private void updateLocationTracking() {
        if (shouldTrackLocation()) {
            startTrackingLocation();
        } else {
            stopTrackingLocation();
        }
    }

    private void startTrackingLocation() {
        if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
            return;
        }

        if (getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission denied");
            return;
        }
        Log.i(TAG, "starting tracking");

        addGeofences();

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                sendGeocodeRequest(location);
            }
        };

        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setFastestInterval(60 * 1000)  // receive updates no faster than minutely
                .setInterval(30 * 60 * 1000)  // try to update at least every 30 minutes
                .setSmallestDisplacement(15);  // no updates for deltas < 15 meters.

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, req, mLocationListener);
    }

    void stopTrackingLocation() {
        Log.i(TAG, "stopping tracking");
        removeGeofences();
        if (mLocationListener != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, mLocationListener);
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
        startService(intent);
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
        stopTrackingLocation();
        super.onDestroy();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Do nothing.  This is only running on my phones, which have play services.
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    private class FetchAddressReceiver extends ResultReceiver {
        FetchAddressReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode == SUCCESS_RESULT && resultData != null) {
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