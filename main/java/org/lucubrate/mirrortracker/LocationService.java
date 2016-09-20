package org.lucubrate.mirrortracker;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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

import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.LOCATION_DATA_EXTRA;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RECEIVER;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RESULT_DATA_KEY;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.SUCCESS_RESULT;

/**
 * Background service that periodically pulls device location via GMS location services.
 */
public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    final private static String TAG = "LocationService";
    final public static String STOP_EXTRA = "stop";
    private GoogleApiClient mGoogleApiClient;
    private SharedPreferences mPrefs;
    private LocationListener mLocationListener;
    private FetchAddressReceiver mFetchAddressReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LocationService creating.");
        mPrefs = getSharedPreferences(Preferences.PREFERENCE_FILE_NAME.toString(), MODE_PRIVATE);

        if (!shouldTrackLocation()) {
            stopSelf();
            return;
        }

        mFetchAddressReceiver = new FetchAddressReceiver(new Handler());

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        Log.i(TAG, "Created LocationService.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "starting LocationService");

        if (intent != null && intent.getBooleanExtra(STOP_EXTRA, false)) {
            stopTrackingLocation();
        } else if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
        return START_STICKY;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (!shouldTrackLocation()) {
            stopTrackingLocation();
            return;
        }
        startTrackingLocation();
    }

    private void startTrackingLocation() {
        if (getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission denied");
            return;
        }
        Log.i(TAG, "starting tracking");

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.i(TAG, location.toString());
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

    private void stopTrackingLocation() {
        Log.i(TAG, "stopping tracking");
        if (mLocationListener != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(
                    mGoogleApiClient, mLocationListener);
        }
        stopSelf();
    }

    private boolean shouldTrackLocation() {
        return Geocoder.isPresent() &&
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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "destroying LocationService");
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
                // Address[addressLines=[0:"373 Clipper Street",1:"San Francisco, CA 94114",2:"USA"],feature=373,admin=California,sub-admin=null,locality=San Francisco,thoroughfare=Clipper Street,postalCode=94114,countryCode=US,countryName=United States,hasLatitude=true,latitude=37.748692,hasLongitude=true,longitude=-122.433177,phone=null,url=null,extras=null]
                Address address = resultData.getParcelable(RESULT_DATA_KEY);
                Log.i(TAG, "got result " + address.getAdminArea());
            }
        }
    }
}
