package com.dodiy.passwordmanager;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

public final class DetailActivity extends BaseActivity {
    private String id;
    private boolean reveal;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_detail);
        id = getIntent().getStringExtra("id");
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.edit).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(DetailActivity.this, EditActivity.class).putExtra("id", id));
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!Session.isOpen()) return;
        Entry e = Session.find(id);
        if (e == null) { finish(); return; }
        bind(e);
    }

    @Override protected void onPause() {
        super.onPause();
        reveal = false;
    }

    private void bind(final Entry e) {
        Provider p = Providers.get(this, e.provider);
        Ui.setLogo((ImageView) findViewById(R.id.logo), this, e.provider, e.title);
        ((TextView) findViewById(R.id.title)).setText(e.title.isEmpty() ? "(no name)" : e.title);
        ((TextView) findViewById(R.id.provider)).setText(p == null ? "No provider logo" : p.title);
        LinearLayout fields = findViewById(R.id.fields);
        fields.removeAllViews();

        if (!e.username.isEmpty()) field(fields, "Username / email", e.username, false, "Copy", new Runnable() {
            @Override public void run() { App.copySecret(e.username, false); Ui.toast(DetailActivity.this, "Username copied"); }
        }, null, null);

        if (!e.password.isEmpty()) {
            field(fields, "Password", reveal ? e.password : "••••••••••", true, reveal ? "Hide" : "Show", new Runnable() {
                @Override public void run() { reveal = !reveal; bind(e); }
            }, "Copy", new Runnable() {
                @Override public void run() { App.copySecret(e.password, true); Ui.toast(DetailActivity.this, "Password copied. Clipboard clears in 45 seconds."); }
            });
        }

        if (!e.url.isEmpty()) field(fields, "Website", e.url, false, "Open", new Runnable() {
            @Override public void run() {
                String u = e.url.contains("://") ? e.url : "https://" + e.url;
                startExternal(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
            }
        }, "Copy", new Runnable() {
            @Override public void run() { App.copySecret(e.url, false); Ui.toast(DetailActivity.this, "Website copied"); }
        });

        if (!e.notes.isEmpty()) field(fields, "Notes", e.notes, false, null, null, null, null);

        java.text.DateFormat df = DateFormat.getMediumDateFormat(this);
        java.text.DateFormat tf = DateFormat.getTimeFormat(this);
        Date c = new Date(e.created), u = new Date(e.updated);
        ((TextView) findViewById(R.id.dates)).setText("Created " + df.format(c) + " " + tf.format(c) + "\nLast changed " + df.format(u) + " " + tf.format(u));
    }

    private void field(LinearLayout parent, String label, String value, boolean mono,
                       String b1, final Runnable a1, String b2, final Runnable a2) {
        View v = getLayoutInflater().inflate(R.layout.item_field, parent, false);
        ((TextView) v.findViewById(R.id.label)).setText(label);
        TextView val = v.findViewById(R.id.value);
        val.setText(value);
        if (mono) val.setTypeface(Typeface.MONOSPACE);
        TextView t1 = v.findViewById(R.id.btn1), t2 = v.findViewById(R.id.btn2);
        button(t1, b1, a1);
        button(t2, b2, a2);
        parent.addView(v);
    }

    private static void button(TextView t, String label, final Runnable action) {
        if (label == null) { t.setVisibility(View.GONE); return; }
        t.setText(label);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
    }
}
