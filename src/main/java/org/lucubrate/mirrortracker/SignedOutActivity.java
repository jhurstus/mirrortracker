package org.lucubrate.mirrortracker;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Collections;

public class SignedOutActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            this::onSignInResult
    );

    static Intent createIntent(Context context) {
        Intent i = new Intent();
        i.setClass(context, SignedOutActivity.class);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_signed_out);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.sign_in);
        setSupportActionBar(toolbar);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            startActivity(SignedInActivity.createIntent(this));
            finish();
        } else {
            Intent signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(Collections.singletonList(
                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                    .setIsSmartLockEnabled(true)
                    .build();
            signInLauncher.launch(signInIntent);
        }
        setContentView(R.layout.activity_signed_out);
    }

    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        if (result.getResultCode() == RESULT_OK) {
            startActivity(SignedInActivity.createIntent(this));
            finish();
        }
    }
}
