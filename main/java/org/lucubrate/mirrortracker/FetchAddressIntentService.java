package org.lucubrate.mirrortracker;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.FAILURE_RESULT;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.LOCATION_DATA_EXTRA;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RECEIVER;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.RESULT_DATA_KEY;
import static org.lucubrate.mirrortracker.FetchAddressIntentServiceConstants.SUCCESS_RESULT;

/**
 * Reverse geocoding IntentService.
 *
 * <p>See {@link FetchAddressIntentServiceConstants} for sending/receiving intent info.
 */
public class FetchAddressIntentService extends JobIntentService {
    private final static String TAG = "FetchAddressService";

    protected ResultReceiver mReceiver;

    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, FetchAddressIntentService.class, 0x02, work);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        Log.d(TAG, "handling fetchaddress job");

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        String errorMessage = "";
        Location location = intent.getParcelableExtra(LOCATION_DATA_EXTRA);
        mReceiver = intent.getParcelableExtra(RECEIVER);

        List<Address> addresses = null;
        try {
            addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1 /* only need one address, since just getting city/state/country */);
        }  catch (IOException ioException) {
            errorMessage = "I/O issue with reverse geocoding";
            Log.e(TAG, errorMessage, ioException);
        } catch (IllegalArgumentException illegalArgumentException) {
            errorMessage = "invalid lat/long";
            Log.e(TAG, errorMessage + ". " +
                    "Latitude = " + location.getLatitude() +
                    ", Longitude = " +
                    location.getLongitude(), illegalArgumentException);
        }

        if (!errorMessage.isEmpty()) {
            deliverFailureToReceiver(errorMessage);
        } else if (addresses == null || addresses.size()  == 0) {
            errorMessage = "no address found";
            Log.i(TAG, errorMessage);
            deliverFailureToReceiver(errorMessage);
        } else {
            Bundle bundle = new Bundle();
            bundle.putParcelable(RESULT_DATA_KEY, addresses.get(0));
            bundle.putParcelable(LOCATION_DATA_EXTRA, location);
            mReceiver.send(SUCCESS_RESULT, bundle);
        }
    }

    private void deliverFailureToReceiver(String message) {
        Bundle bundle = new Bundle();
        bundle.putString(RESULT_DATA_KEY, message);
        mReceiver.send(FAILURE_RESULT, bundle);
    }

}
