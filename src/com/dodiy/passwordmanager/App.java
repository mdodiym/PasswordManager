package com.dodiy.passwordmanager;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;

/** Locks the vault as soon as the app leaves the screen, and clears copied passwords after a while. */
public final class App extends Application {
    static final long CLIPBOARD_CLEAR_MS = 45_000;
    static final String CLIP_LABEL = "Dodiy Password Manager";

    private int started;
    /** True while we deliberately open another app's screen (file picker, browser, settings). */
    static boolean leavingOnPurpose;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clearClip = new Runnable() {
        @Override public void run() {
            try {
                ClipboardManager cm = getSystemService(ClipboardManager.class);
                ClipDescription d = cm.getPrimaryClipDescription();
                if (d == null || CLIP_LABEL.contentEquals(d.getLabel() == null ? "" : d.getLabel())) cm.clearPrimaryClip();
            } catch (Exception ignored) {}
        }
    };
    private static App instance;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        Providers.preload(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                started++;
                leavingOnPurpose = false;
            }
            @Override public void onActivityStopped(Activity a) {
                started--;
                if (started <= 0 && !a.isChangingConfigurations() && !leavingOnPurpose) Session.lock();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    static void copySecret(String text, boolean sensitive) {
        ClipboardManager cm = instance.getSystemService(ClipboardManager.class);
        ClipData clip = ClipData.newPlainText(CLIP_LABEL, text);
        if (sensitive) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(Build.VERSION.SDK_INT >= 33 ? ClipDescription.EXTRA_IS_SENSITIVE : "android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        cm.setPrimaryClip(clip);
        instance.handler.removeCallbacks(instance.clearClip);
        instance.handler.postDelayed(instance.clearClip, CLIPBOARD_CLEAR_MS);
    }
}
