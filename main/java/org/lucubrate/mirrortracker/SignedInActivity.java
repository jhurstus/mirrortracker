package org.lucubrate.mirrortracker;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;

import org.lucubrate.mirrortracker.databinding.ActivitySignedInBinding;

/** Main activity, shown to signed-in users. */
public class SignedInActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener {
    private GoogleApiClient mGoogleApiClient;
    private FirebaseDB mDB;

    public static Intent createIntent(Context context) {
        Intent i = new Intent();
        i.setClass(context, SignedInActivity.class);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(LocationServices.API)
                .build();

        mDB = new FirebaseDB(FirebaseAuth.getInstance().getCurrentUser().getUid());
        ActivitySignedInBinding binding = DataBindingUtil.setContentView(
                this, R.layout.activity_signed_in);
        binding.setModel(mDB.getModel());
        binding.setHandler(mDB);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Do nothing.  This is only running on my phones, which have play services.
    }

    /** Sign Out button handler. */
    public void signOut(View view) {
        FirebaseAuth.getInstance().signOut();
        startActivity(SignedOutActivity.createIntent(this));
        finish();
    }
}
