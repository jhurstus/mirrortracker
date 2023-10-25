package org.lucubrate.mirrortracker;

import android.text.Editable;
import android.view.View;

/**
 * Event handlers for SignedInActivity.
 */
public interface SignedInHandler {
    void onShowPrivateInfoChecked(View view);
    void onMemoEdited(Editable s);
    void onShareLocationChecked(View view);
    void onSignOutClicked(View view);
}