package com.dodiy.passwordmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static void toast(Context c, CharSequence s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    static void setLogo(ImageView v, Context c, String slug, String title) {
        v.setImageDrawable(new LogoDrawable(Providers.get(c, slug), title));
    }

    static String version(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return "Version " + pi.versionName + " (build " + pi.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "";
        }
    }

    static EditText passwordField(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setBackgroundResource(R.drawable.field);
        e.setTextColor(c.getColor(R.color.text));
        e.setHintTextColor(c.getColor(R.color.text2));
        int p = dp(c, 14);
        e.setPadding(p, 0, p, 0);
        e.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52)));
        return e;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 22);
        l.setPadding(p, dp(c, 8), p, 0);
        return l;
    }

    static TextView text(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(c.getColor(colorRes));
        return t;
    }

    static void gap(LinearLayout l, int dp) {
        View v = new View(l.getContext());
        l.addView(v, new LinearLayout.LayoutParams(1, dp(l.getContext(), dp)));
    }

    static void confirm(Activity a, String title, String msg, String yes, final Runnable onYes) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg)
                .setPositiveButton(yes, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { onYes.run(); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Runs slow work (PBKDF2) on a background thread and the result on the UI thread. */
    abstract static class Task<T> {
        abstract T work() throws Exception;
        abstract void done(T result, Exception error);

        final void run(final Activity a) {
            new Thread() {
                @Override public void run() {
                    T r = null;
                    Exception err = null;
                    try { r = work(); } catch (Exception e) { err = e; }
                    final T fr = r;
                    final Exception fe = err;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!a.isDestroyed()) done(fr, fe);
                        }
                    });
                }
            }.start();
        }
    }
}
