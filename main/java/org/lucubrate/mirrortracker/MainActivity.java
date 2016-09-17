package org.lucubrate.mirrortracker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

/** Show a splash screen while app initializes. */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(SignedOutActivity.createIntent(this));
        } else {
            startActivity(SignedInActivity.createIntent(this));
        }
        finish();
    }
}
