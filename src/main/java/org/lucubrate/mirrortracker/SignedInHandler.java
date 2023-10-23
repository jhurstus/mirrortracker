package org.lucubrate.mirrortracker;

import android.view.View;

/**
 * Event handlers for SignedInActivity.
 */
public interface SignedInHandler {
    void onShowPrivateInfoChecked(View view);
    void onShareLocationChecked(View view);
    void onSignOutClicked(View view);
}