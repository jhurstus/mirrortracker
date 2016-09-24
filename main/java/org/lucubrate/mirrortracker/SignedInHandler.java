package org.lucubrate.mirrortracker;

import android.view.View;

/**
 * Event handlers for SignedInActivity.
 */
public interface SignedInHandler {
    public void onShowPrivateInfoChecked(View view);
    public void onShareLocationChecked(View view);
    public void onSignOutClicked(View view);
}