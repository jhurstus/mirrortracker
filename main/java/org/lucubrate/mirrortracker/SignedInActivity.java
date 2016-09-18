package org.lucubrate.mirrortracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.google.firebase.auth.FirebaseAuth;

import org.lucubrate.mirrortracker.databinding.ActivitySignedInBinding;

/** Main activity, shown to signed-in users. */
public class SignedInActivity extends AppCompatActivity {

    private FirebaseDB mDB;
    private SharedPreferences mPrefs;

    public static Intent createIntent(Context context) {
        Intent i = new Intent();
        i.setClass(context, SignedInActivity.class);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(Preferences.PREFERENCE_FILE_NAME.toString(), MODE_PRIVATE);

        mDB = new FirebaseDB(
                FirebaseAuth.getInstance().getCurrentUser().getUid(),
                mPrefs,
                this);
        ActivitySignedInBinding binding = DataBindingUtil.setContentView(
                this, R.layout.activity_signed_in);
        binding.setModel(mDB.getModel());
        binding.setHandler(mDB);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /** Sync button handler. */
    public void sync(View view) {
    }

    /** Sign Out button handler. */
    public void signOut(View view) {
        FirebaseAuth.getInstance().signOut();
        startActivity(SignedOutActivity.createIntent(this));
        finish();
    }
}
