package org.lucubrate.mirrortracker;

import android.Manifest;
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
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.LOCATION_DATA_EXTRA;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RECEIVER;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RESULT_DATA_KEY;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.SUCCESS_RESULT;

/**
 * Background service that periodically pulls device location via GMS location services.
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
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
                .setSmallestDisplacement(5);  // no updates for deltas < 5 meters.

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, req, mLocationListener);
    }

    void stopTrackingLocation() {
        Log.i(TAG, "stopping tracking");
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