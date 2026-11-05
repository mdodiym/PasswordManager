package com.dodiy.passwordmanager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

/** Blocks screenshots / recent-apps previews, and sends the user to the lock screen when the vault is locked. */
abstract class BaseActivity extends Activity {
    /** Screens that need the vault open. */
    protected boolean needsUnlock() { return true; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override protected void onStart() {
        super.onStart();
        if (needsUnlock() && !Session.isOpen()) {
            finishAffinity();
            startActivity(new Intent(this, LockActivity.class));
        }
    }

    /** Use for intents that open another app (file picker, browser) so the vault is not locked meanwhile. */
    protected void startExternalForResult(Intent i, int code) {
        App.leavingOnPurpose = true;
        try {
            startActivityForResult(i, code);
        } catch (Exception e) {
            App.leavingOnPurpose = false;
            Ui.toast(this, "No app can handle this on your phone");
        }
    }

    protected void startExternal(Intent i) {
        // Opening a website or settings leaves the app, so the vault locks; that is intended.
        try {
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "No app can open this");
        }
    }
}
