package com.dodiy.passwordmanager;

import android.content.Context;
import android.content.SharedPreferences;

/** Small non-secret settings. */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }

    /** The user said "Not now" to fingerprint unlock; don't ask after every unlock. */
    static boolean bioDeclined(Context c) { return p(c).getBoolean("bio_declined", false); }
    static void setBioDeclined(Context c, boolean v) { p(c).edit().putBoolean("bio_declined", v).apply(); }
}
