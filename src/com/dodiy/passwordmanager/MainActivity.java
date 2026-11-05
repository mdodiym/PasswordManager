package com.dodiy.passwordmanager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import javax.crypto.Cipher;

public final class MainActivity extends BaseActivity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;

    private final ArrayList<Entry> shown = new ArrayList<>();
    private Adapter adapter;
    private EditText search;
    private TextView empty, count;
    private char[] pendingBackupPassword;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        count = findViewById(R.id.count);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                startActivity(new Intent(MainActivity.this, DetailActivity.class).putExtra("id", shown.get(pos).id));
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, EditActivity.class)); }
        });
        findViewById(R.id.lock).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { lockNow(); }
        });
        findViewById(R.id.menu).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(v); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (Session.isOpen()) refresh();
    }

    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Entry e : Session.entries) {
            if (q.isEmpty()) { shown.add(e); continue; }
            Provider p = Providers.get(this, e.provider);
            String hay = (e.title + " " + e.username + " " + e.url + " " + (p == null ? "" : p.title)).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) shown.add(e);
        }
        Collections.sort(shown, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.title.compareToIgnoreCase(b.title); }
        });
        int n = Session.entries.size();
        count.setText(n == 1 ? "1 login saved" : n + " logins saved");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(n == 0 ? "No logins yet.\n\nTap ＋ Add login, pick the provider (Google, Microsoft, Apple, Facebook, GitHub …) and its logo shows next to the name."
                : "Nothing matches \"" + search.getText() + "\".");
        adapter.notifyDataSetChanged();
    }

    private void lockNow() {
        Session.lock();
        finishAffinity();
        startActivity(new Intent(this, LockActivity.class));
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Password generator");
        m.add(0, 2, 0, "All provider logos");
        m.add(0, 3, 0, "Export encrypted backup");
        m.add(0, 4, 0, "Restore from backup");
        m.add(0, 8, 0, Vault.bioEnabled(this) ? "Fingerprint unlock: On (tap to turn off)" : "Fingerprint unlock: Off (tap to turn on)");
        m.add(0, 5, 0, "Change master password");
        m.add(0, 6, 0, "Lock now");
        m.add(0, 7, 0, "About");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1: generatorDialog(MainActivity.this, null); return true;
                    case 2: startActivity(new Intent(MainActivity.this, PickerActivity.class).putExtra("browse", true)); return true;
                    case 3: askBackupPassword(true); return true;
                    case 4: askBackupPassword(false); return true;
                    case 5: changePassword(); return true;
                    case 6: lockNow(); return true;
                    case 7: about(); return true;
                    case 8: toggleBio(); return true;
                }
                return false;
            }
        });
        pm.show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(Ui.version(this) + "\n\n"
                        + "Your logins are stored only on this phone, encrypted with AES-256. The vault opens with your master password, or with your fingerprint if you turned that on (the key for it lives in the Android Keystore).\n\n"
                        + "The vault locks as soon as you leave the app, screenshots are blocked, and copied passwords are cleared from the clipboard after 45 seconds.\n\n"
                        + Providers.all(this).size() + " provider logos are included. Brand logos from Simple Icons (CC0); they are trademarks of their owners.")
                .setPositiveButton("OK", null).show();
    }

    // ---- password generator (also used by EditActivity) ----------------------------------------

    interface OnPassword { void use(String pw); }

    static void generatorDialog(final android.app.Activity a, final OnPassword onUse) {
        LinearLayout box = Ui.column(a);
        final TextView out = Ui.text(a, "", 20, R.color.text);
        out.setTypeface(android.graphics.Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        final TextView lenLabel = Ui.text(a, "", 14, R.color.text2);
        final SeekBar len = new SeekBar(a);
        len.setMax(56);           // 8..64
        len.setProgress(12);      // 20
        final CheckBox up = new CheckBox(a), lo = new CheckBox(a), di = new CheckBox(a), sy = new CheckBox(a);
        up.setText("Uppercase A-Z"); lo.setText("Lowercase a-z"); di.setText("Numbers 2-9"); sy.setText("Symbols !@#$");
        up.setChecked(true); lo.setChecked(true); di.setChecked(true); sy.setChecked(true);
        box.addView(out);
        box.addView(lenLabel);
        box.addView(len);
        box.addView(up); box.addView(lo); box.addView(di); box.addView(sy);
        final Runnable regen = new Runnable() {
            @Override public void run() {
                int n = len.getProgress() + 8;
                lenLabel.setText("Length: " + n);
                out.setText(PasswordGen.generate(n, up.isChecked(), lo.isChecked(), di.isChecked(), sy.isChecked()));
            }
        };
        len.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { regen.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        android.widget.CompoundButton.OnCheckedChangeListener cl = new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { regen.run(); }
        };
        up.setOnCheckedChangeListener(cl); lo.setOnCheckedChangeListener(cl); di.setOnCheckedChangeListener(cl); sy.setOnCheckedChangeListener(cl);
        regen.run();
        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("Password generator")
                .setView(box)
                .setPositiveButton(onUse != null ? "Use this" : "Copy", null)
                .setNeutralButton("New one", null)
                .setNegativeButton("Close", null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface di2) {
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { regen.run(); }
                });
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String pw = out.getText().toString();
                        if (onUse != null) onUse.use(pw);
                        else { App.copySecret(pw, true); Ui.toast(a, "Copied. Clipboard clears in 45 seconds."); }
                        d.dismiss();
                    }
                });
            }
        });
        d.show();
    }

    // ---- change master password ----------------------------------------------------------------

    private void changePassword() {
        LinearLayout box = Ui.column(this);
        final EditText cur = Ui.passwordField(this, "Current master password");
        final EditText nw = Ui.passwordField(this, "New master password");
        final EditText again = Ui.passwordField(this, "New password again");
        box.addView(cur); Ui.gap(box, 10); box.addView(nw); Ui.gap(box, 10); box.addView(again);
        final AlertDialog d = new AlertDialog.Builder(this).setTitle("Change master password").setView(box)
                .setPositiveButton("Change", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final String c = cur.getText().toString(), n = nw.getText().toString();
                        if (n.length() < 8) { nw.setError("At least 8 characters"); return; }
                        if (!n.equals(again.getText().toString())) { again.setError("Doesn't match"); return; }
                        d.dismiss();
                        Ui.toast(MainActivity.this, "Checking…");
                        new Ui.Task<Boolean>() {
                            @Override Boolean work() {
                                char[] ch = c.toCharArray();
                                try { return Vault.checkPassword(MainActivity.this, ch); } finally { Crypto.wipe(ch); }
                            }
                            @Override void done(Boolean ok, Exception e) {
                                if (ok == null || !ok) { Ui.toast(MainActivity.this, "Current master password is wrong. Nothing changed."); return; }
                                saveNewPassword(n);
                            }
                        }.run(MainActivity.this);
                    }
                });
            }
        });
        d.show();
    }

    private void saveNewPassword(final String newPassword) {
        new Ui.Task<Void>() {
            @Override Void work() throws Exception {
                char[] ch = newPassword.toCharArray();
                try { Vault.changePassword(MainActivity.this, ch); } finally { Crypto.wipe(ch); }
                return null;
            }
            @Override void done(Void r, Exception e) {
                Ui.toast(MainActivity.this, e == null ? "Master password changed" : "Not changed: " + e.getMessage());
            }
        }.run(this);
    }

    // ---- fingerprint unlock on / off -------------------------------------------------------------

    private void toggleBio() {
        if (Vault.bioEnabled(this)) {
            Ui.confirm(this, "Turn off fingerprint unlock?", "You will need your master password to open the vault.", "Turn off", new Runnable() {
                @Override public void run() {
                    try {
                        Vault.disableBio(MainActivity.this);
                        Prefs.setBioDeclined(MainActivity.this, true);
                        Ui.toast(MainActivity.this, "Fingerprint unlock is off");
                    } catch (Exception e) {
                        Ui.toast(MainActivity.this, "Could not change it: " + e.getMessage());
                    }
                }
            });
            return;
        }
        String why = Bio.unavailableReason(this);
        if (why != null) { Ui.toast(this, why); return; }
        Cipher c;
        try { c = Vault.prepareEnableBio(); } catch (Exception e) { Ui.toast(this, "Could not set it up: " + e.getMessage()); return; }
        Bio.prompt(this, "Turn on fingerprint unlock", "Confirm with your fingerprint", c, new Bio.Callback() {
            @Override public void onSuccess(Cipher cipher) {
                try {
                    Vault.enableBio(MainActivity.this, cipher);
                    Prefs.setBioDeclined(MainActivity.this, false);
                    Ui.toast(MainActivity.this, "Fingerprint unlock is on");
                } catch (Exception e) {
                    Ui.toast(MainActivity.this, "Could not turn it on: " + e.getMessage());
                }
            }
            @Override public void onError(CharSequence msg, boolean cancelled) {
                if (!cancelled) Ui.toast(MainActivity.this, msg);
            }
        });
    }

    // ---- backup ----------------------------------------------------------------------------------

    private void askBackupPassword(final boolean export) {
        LinearLayout box = Ui.column(this);
        TextView info = Ui.text(this, export
                ? "The backup file is encrypted with the password below (only a password, no fingerprint, so you can restore it on a new phone). Your master password is a good choice."
                : "Choose the backup file next, then it is decrypted with this password. Logins in the backup are added; existing ones are kept unless the backup copy is newer.", 14, R.color.text2);
        box.addView(info);
        Ui.gap(box, 12);
        final EditText pw = Ui.passwordField(this, "Backup password");
        box.addView(pw);
        final EditText again = Ui.passwordField(this, "Backup password again");
        if (export) { Ui.gap(box, 10); box.addView(again); }
        final AlertDialog d = new AlertDialog.Builder(this).setTitle(export ? "Export encrypted backup" : "Restore from backup").setView(box)
                .setPositiveButton(export ? "Choose where to save" : "Choose file", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String p = pw.getText().toString();
                        if (p.length() < 8) { pw.setError("At least 8 characters"); return; }
                        if (export && !p.equals(again.getText().toString())) { again.setError("Doesn't match"); return; }
                        pendingBackupPassword = p.toCharArray();
                        d.dismiss();
                        if (export) {
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("application/octet-stream")
                                    .putExtra(Intent.EXTRA_TITLE, "dodiy-passwords-" + new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()) + ".dpmbackup");
                            startExternalForResult(i, REQ_EXPORT);
                        } else {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                            startExternalForResult(i, REQ_IMPORT);
                        }
                    }
                });
            }
        });
        d.show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        final char[] pw = pendingBackupPassword;
        pendingBackupPassword = null;
        if (res != RESULT_OK || data == null || data.getData() == null || pw == null || !Session.isOpen()) { Crypto.wipe(pw); return; }
        final Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            final ArrayList<Entry> copy = new ArrayList<>(Session.entries);
            Ui.toast(this, "Encrypting backup…");
            new Ui.Task<Void>() {
                @Override Void work() throws Exception {
                    try {
                        byte[] bytes = Vault.exportBackup(copy, pw);
                        OutputStream out = getContentResolver().openOutputStream(uri, "wt");
                        try { out.write(bytes); } finally { out.close(); }
                    } finally { Crypto.wipe(pw); }
                    return null;
                }
                @Override void done(Void r, Exception e) {
                    Ui.toast(MainActivity.this, e == null ? "Backup saved (" + copy.size() + " logins)" : "Backup failed: " + e.getMessage());
                }
            }.run(this);
        } else if (req == REQ_IMPORT) {
            Ui.toast(this, "Decrypting backup…");
            new Ui.Task<ArrayList<Entry>>() {
                @Override ArrayList<Entry> work() throws Exception {
                    try {
                        InputStream in = getContentResolver().openInputStream(uri);
                        byte[] bytes;
                        try { bytes = Vault.readAll(in); } finally { in.close(); }
                        return Vault.importBackup(bytes, pw);
                    } finally { Crypto.wipe(pw); }
                }
                @Override void done(ArrayList<Entry> list, Exception e) {
                    if (e != null) {
                        Ui.toast(MainActivity.this, Vault.isWrongPassword(e) ? "Wrong backup password" : "Could not restore: " + e.getMessage());
                        return;
                    }
                    if (!Session.isOpen()) return;
                    int n = Vault.merge(Session.entries, list);
                    try { Vault.save(MainActivity.this); } catch (Exception ex) { Ui.toast(MainActivity.this, "Could not save: " + ex.getMessage()); return; }
                    refresh();
                    Ui.toast(MainActivity.this, "Restored " + n + " of " + list.size() + " logins");
                }
            }.run(this);
        }
    }

    // ---- list ------------------------------------------------------------------------------------

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_row, parent, false);
            final Entry e = shown.get(pos);
            Ui.setLogo((ImageView) v.findViewById(R.id.logo), MainActivity.this, e.provider, e.title);
            ((TextView) v.findViewById(R.id.title)).setText(e.title.isEmpty() ? "(no name)" : e.title);
            ((TextView) v.findViewById(R.id.subtitle)).setText(e.username.isEmpty() ? "No username" : e.username);
            TextView action = v.findViewById(R.id.action);
            action.setText("Copy");
            action.setVisibility(e.password.isEmpty() ? View.GONE : View.VISIBLE);
            action.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    App.copySecret(e.password, true);
                    Ui.toast(MainActivity.this, "Password copied. Clipboard clears in 45 seconds.");
                }
            });
            return v;
        }
    }
}
