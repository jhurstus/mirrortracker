package org.lucubrate.mirrortracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;
import static com.google.android.gms.location.Geofence.NEVER_EXPIRE;

/**
 * Background service that initiates location tracking and provides data to a possibly bound
 * {@link SignedInActivity}.
 * <p>
 * Location is primarily tracked using GMS Geofence enter/exit events.  This is supplemented by a
 * generic location tracker, since it's possible for the Geofence events to fail in various
 * scenarios (not to mention any bugs in the Geofence implementation).
 * <p>
 * This service was originally designed to run continuously in the background and handle all db and
 * model reads/writes.  Since Android O though, that's no longer possible, so location tracking is
 * delegated to {@link FusedLocationReceiver} and {@link GeofenceReceiver}.  Data flow is:
 * (system location updates) --broadcast--> (FusedLocationReceiver|GeofenceReceiver) --call-->
 * FirebaseDB --servicestart--> LocationService.
 */
public class LocationService extends JobIntentService implements FirebaseDbObserver {
    final private static String TAG = "LocationService";

    /**
     * Key for parceled {@link Location} object posted to {@link LocationService} (if running) after
     * writing location to Firebase DB.
     */
    static final String LOCATION_DATA_EXTRA = "org.lucubrate.mirrortracker.LOCATION_DATA_EXTRA";

    /**
     * Key for parceled {@link Address} object posted to {@link LocationService} (if running) after
     * writing location to Firebase DB.
     */
    static final String RESULT_DATA_KEY =  "org.lucubrate.mirrortracker.RESULT_DATA_KEY";

    private SharedPreferences mPrefs;
    private LocationEvent mLastLocation;
    private FirebaseDbObserver mActivity;
    private FirebaseDB mDB;
    private List<Geofence> mGeofences;
    private DebugLog mDebugLog;
    private PendingIntent mGeofencePendingIntent;
    private PendingIntent mFusedLocationPendingIntent;
    private GeofencingClient mGeofencingClient;

    private boolean mShowPrivateInfo;

    /** Whether this service is active. */
    static boolean started = false;

    void setFireBaseDbObserver(FirebaseDbObserver o) {
        mActivity = o;
    }

    void setDebugLogWriteOberver(DebugLogWriteObserver o) {
        mDebugLog.setObserver(o);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        started = true;
        Log.d(TAG, "onCreate");

        mDebugLog = DebugLog.getInstance(this.getFilesDir());
        mDebugLog.logServiceStarted();

        // Don't bother running service if not auth'ed.  We can't update the DB in that case.
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            stopSelf();
            return;
        }

        mPrefs = getSharedPreferences(Preferences.PREFERENCE_FILE_NAME.val, MODE_PRIVATE);
        mDB = FirebaseDB.getInstance(u.getUid(), this);
        mDB.setLocationService(this);

        updateLocationTracking();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");

        // Handle start commands from FirebaseDB, which notify of updated user locations.
        if (intent.hasExtra(RESULT_DATA_KEY) && intent.hasExtra(LOCATION_DATA_EXTRA)) {
            Address address = intent.getParcelableExtra(RESULT_DATA_KEY, Address.class);
            Location location = intent.getParcelableExtra(LOCATION_DATA_EXTRA, Location.class);
            if (address != null && location != null) {
                mLastLocation = new LocationEvent(
                        location.getTime(), address.getLocality(), address.getAdminArea(),
                        address.getCountryName(), location.getLatitude(),
                        location.getLongitude(), "");
                Log.d(TAG, "got updated location from FirebaseDB");
                if (mActivity != null) {
                    mActivity.onLocationUpdated(mLastLocation);
                }
            }
        }

        return START_STICKY;
    }

    /**
     * This service was converted to a JobIntentService so that it would actually be run on Android
     * O+ on boot (as a job) from {@link BootReceiver}.  However, the work is never actually
     * dispatched to {@link #onHandleWork(Intent)} apparently because JobIntentService depends on
     * its implementation of onBind, which we also have to override to permit binding with
     * {@link SignedInActivity}.  All we need for boot is to briefly start this service in order
     * to register Geofences and FusedLocationProvider, so that's fine though.
     * <p>
     * The onBind conflict could very well break functionality at some point in the future though.
     * If that happens, location service registration will need to be separated into a different
     * service than activity binding.
     */
    static void enqueueWork(Context context) {
        Log.d(TAG, "enqueuing locationservice work");
        enqueueWork(context, LocationService.class, 0x1000, new Intent());
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "destroying LocationService");
        started = false;
        mDebugLog.logServiceStopped();
        mDB.setLocationService(null);
        super.onDestroy();
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
    private boolean cantUpdateGeofences() {
        if (mGeofences == null || mGeofences.isEmpty()) {
            return true;
        }

        if (getPackageManager().checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION permission denied");
            return true;
        }
        return false;
    }

    /** Starts tracking geofences. */
    private void addGeofences() {
        if (cantUpdateGeofences()) {
            return;
        }
        Log.d(TAG, "adding geofences " + mGeofences.size());

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

        try {
            getGeofencingClient().addGeofences(fenceReq, getGeofencePendingIntent())
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "successfully added geofences"))
                    .addOnFailureListener(e -> Log.d(TAG, "geofence add failed " + e));
        } catch (SecurityException e) {
            // Should never happen.  Permission checked in canUpdateGeofences.
            Log.d(TAG, "geofence security exception");
        }
    }

    /** Stop tracking geofences. */
    private void removeGeofences() {
        if (cantUpdateGeofences()) {
            return;
        }
        List<String> labels = new ArrayList<>(mGeofences.size());
        for (Geofence f : mGeofences) {
            labels.add(f.label);
        }
        getGeofencingClient().removeGeofences(labels);
        Log.d(TAG, "removed geofences " + labels.size());
    }

    private GeofencingClient getGeofencingClient() {
        if (mGeofencingClient == null) {
            mGeofencingClient = LocationServices.getGeofencingClient(this);
        }
        return mGeofencingClient;
    }

    private PendingIntent getGeofencePendingIntent() {
        if (mGeofencePendingIntent != null) {
            return mGeofencePendingIntent;
        }
        final Intent intent = new Intent();
        intent.setClass(this, GeofenceReceiver.class);
        intent.setAction(GeofenceReceiver.GEOFENCE_INTENT_ACTION);
        mGeofencePendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mGeofencePendingIntent;
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

        LocationRequest req =
                new LocationRequest.Builder(20 * 60 * 1000)  // try to update at least every 20 minutes
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY )
                .setMinUpdateDistanceMeters(15)  // no updates for deltas < 15 meters.
                .build();

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(getApplicationContext());
        client.requestLocationUpdates(req, getFusedLocationPendingIntent());
    }

    void stopTrackingLocation() {
        Log.i(TAG, "stopping tracking");
        removeGeofences();
        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(getApplicationContext());
        client.removeLocationUpdates(getFusedLocationPendingIntent());
    }

    private PendingIntent getFusedLocationPendingIntent() {
        if (mFusedLocationPendingIntent != null) {
            return mFusedLocationPendingIntent;
        }
        final Intent intent = new Intent();
        intent.setClass(this, FusedLocationReceiver.class);
        intent.setAction(FusedLocationReceiver.LOCATION_INTENT_ACTION);
        mFusedLocationPendingIntent = PendingIntent.getBroadcast(
                getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mFusedLocationPendingIntent;
    }

    private boolean shouldTrackLocation() {
        return FirebaseAuth.getInstance().getCurrentUser() != null &&
                Geocoder.isPresent() &&
                mPrefs != null &&
                mPrefs.getBoolean(Preferences.SHARE_LOCATION_PREF_KEY.val, true);
    }

    // Binder to SignedInActivity.
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onLocationUpdated(LocationEvent e) {
        // Do nothing -- UI already updated directly from FirebaseDB callback in #onStartCommand.
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
                .putBoolean(Preferences.SHARE_LOCATION_PREF_KEY.val, share)
                .commit();
        mDB.updateShareLocation(share);
        updateLocationTracking();
    }

    boolean shareLocation() {
        return mPrefs.getBoolean(Preferences.SHARE_LOCATION_PREF_KEY.val, true);
    }

    List<String> debugLogLines() {
        if (mDebugLog != null) {
            return mDebugLog.getLogLines();
        }
        return new ArrayList<>();
    }

    class LocalBinder extends Binder {
        LocationService getService() {
            return LocationService.this;
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return mBinder;
    }

}