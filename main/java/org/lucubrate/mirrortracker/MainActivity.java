package org.lucubrate.mirrortracker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Show a splash screen while app initializes.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get fine grained location permission if not already available.
        if (getPackageManager().checkPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION, getPackageName()) !=
                PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            authRedirect();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            if (TextUtils.equals(permissions[i], Manifest.permission.ACCESS_FINE_LOCATION)) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    authRedirect();
                    return;
                }
            }
        }
        finish();
    }

    /** Sends user to sign in or sign out activity depending on their auth state. */
    private void authRedirect() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(SignedOutActivity.createIntent(this));
        } else {
            startActivity(SignedInActivity.createIntent(this));
        }
        finish();
    }
}
