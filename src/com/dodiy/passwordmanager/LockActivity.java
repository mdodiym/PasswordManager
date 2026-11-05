package com.dodiy.passwordmanager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import javax.crypto.Cipher;

/** First run: create the master password and bind the fingerprint. Later: fingerprint, then master password. */
public final class LockActivity extends BaseActivity {
    private enum Mode { SETUP, NO_BIOMETRIC, FINGERPRINT, PASSWORD, KEY_LOST }

    private static final int MAX_TRIES = 5;

    private Mode mode;
    private TextView heading, message, strength, error, primary, secondary, reset;
    private EditText password, confirm;
    private CheckBox show;
    private ProgressBar progress;
    private byte[] inner;             // result of the fingerprint step, kept until the password step
    private int wrongTries;
    private boolean busy, autoPrompted;
    private CancellationSignal prompt;

    @Override protected boolean needsUnlock() { return false; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_lock);
        heading = findViewById(R.id.heading);
        message = findViewById(R.id.message);
        strength = findViewById(R.id.strength);
        error = findViewById(R.id.error);
        primary = findViewById(R.id.primary);
        secondary = findViewById(R.id.secondary);
        reset = findViewById(R.id.reset);
        password = findViewById(R.id.password);
        confirm = findViewById(R.id.confirm);
        show = findViewById(R.id.show);
        progress = findViewById(R.id.progress);
        ((TextView) findViewById(R.id.version)).setText(Ui.version(this));

        show.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                int t = InputType.TYPE_CLASS_TEXT | (on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD);
                password.setInputType(t);
                confirm.setInputType(t);
                password.setSelection(password.length());
            }
        });
        password.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (mode == Mode.SETUP) strength.setText(s.length() == 0 ? "" : "Strength: " + PasswordGen.strength(s));
                error.setText("");
            }
        });
        TextView.OnEditorActionListener go = new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_GO) { primary.performClick(); return true; }
                return false;
            }
        };
        password.setOnEditorActionListener(go);
        confirm.setOnEditorActionListener(go);
        primary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onPrimary(); }
        });
        secondary.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onSecondary(); }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askReset(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (Session.isOpen()) { openVault(); return; }
        if (busy) return;
        if (!Vault.exists(this)) {
            String why = Bio.unavailableReason(this);
            Mode want = why == null ? Mode.SETUP : Mode.NO_BIOMETRIC;
            if (mode != want) setMode(want);
            if (why != null) message.setText(why);
        } else if (inner != null) {
            if (mode != Mode.PASSWORD) setMode(Mode.PASSWORD);
        } else if (mode != Mode.KEY_LOST) {
            if (mode != Mode.FINGERPRINT) setMode(Mode.FINGERPRINT);
            if (!autoPrompted) { autoPrompted = true; startFingerprint(); }
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (prompt != null) { prompt.cancel(); prompt = null; }
        // Leaving the screen forgets the fingerprint step, and the next visit asks for it again.
        if (!isChangingConfigurations() && !busy) {
            inner = null;
            autoPrompted = false;
            password.setText("");
            confirm.setText("");
        }
    }

    private void setMode(Mode m) {
        mode = m;
        error.setText("");
        boolean setup = m == Mode.SETUP;
        password.setVisibility(setup || m == Mode.PASSWORD ? View.VISIBLE : View.GONE);
        confirm.setVisibility(setup ? View.VISIBLE : View.GONE);
        show.setVisibility(setup || m == Mode.PASSWORD ? View.VISIBLE : View.GONE);
        strength.setVisibility(setup ? View.VISIBLE : View.GONE);
        reset.setVisibility(setup || m == Mode.NO_BIOMETRIC ? View.GONE : View.VISIBLE);
        secondary.setVisibility(View.GONE);
        password.setImeOptions(setup ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        switch (m) {
            case SETUP:
                heading.setText("Create your vault");
                message.setText("Choose a master password. You will need it and your fingerprint every time you open the app.\n\nThere is no way to recover it if you forget it, so pick one you will remember.");
                primary.setText("Create vault");
                break;
            case NO_BIOMETRIC:
                heading.setText("Fingerprint needed");
                primary.setText("Open security settings");
                secondary.setText("I've set it up, check again");
                secondary.setVisibility(View.VISIBLE);
                break;
            case FINGERPRINT:
                heading.setText("Vault locked");
                message.setText("Step 1 of 2: confirm with your fingerprint.");
                primary.setText("Unlock with fingerprint");
                break;
            case PASSWORD:
                heading.setText("Vault locked");
                message.setText("Step 2 of 2: enter your master password.");
                primary.setText("Unlock");
                password.requestFocus();
                break;
            case KEY_LOST:
                heading.setText("Fingerprint key was removed");
                message.setText("Android deleted this app's fingerprint key (this happens when the screen lock or all fingerprints are removed). The saved vault can no longer be opened on this phone.\n\nReset the vault, then use ⋮ → Restore from backup if you exported a backup file.");
                primary.setText("Reset vault");
                reset.setVisibility(View.GONE);
                break;
        }
    }

    private void onPrimary() {
        if (busy) return;
        switch (mode) {
            case SETUP: startSetup(); break;
            case NO_BIOMETRIC:
                Intent i = new Intent(Build.VERSION.SDK_INT >= 30 ? Settings.ACTION_BIOMETRIC_ENROLL : Settings.ACTION_SECURITY_SETTINGS);
                startExternal(i);
                break;
            case FINGERPRINT: startFingerprint(); break;
            case PASSWORD: checkPassword(); break;
            case KEY_LOST: askReset(); break;
        }
    }

    private void onSecondary() {
        if (mode == Mode.NO_BIOMETRIC) onResume();
    }

    // ---- setup -----------------------------------------------------------------------------------

    private void startSetup() {
        final String pw = password.getText().toString();
        if (pw.length() < 8) { error.setText("Use at least 8 characters."); return; }
        if (!pw.equals(confirm.getText().toString())) { error.setText("The two passwords don't match."); return; }
        String why = Bio.unavailableReason(this);
        if (why != null) { error.setText(why); return; }
        Cipher c;
        try {
            BioKey.delete();
            BioKey.create();
            c = BioKey.encryptCipher();
        } catch (Exception e) {
            error.setText("Could not create the fingerprint key: " + e.getMessage());
            return;
        }
        prompt = Bio.prompt(this, "Link your fingerprint", "Confirm to finish creating the vault", c, new Bio.Callback() {
            @Override public void onSuccess(final Cipher cipher) {
                prompt = null;
                setBusy(true, "Creating vault…");
                new Ui.Task<Void>() {
                    @Override Void work() throws Exception {
                        char[] chars = pw.toCharArray();
                        try { Vault.create(LockActivity.this, chars, cipher); } finally { Crypto.wipe(chars); }
                        return null;
                    }
                    @Override void done(Void r, Exception e) {
                        setBusy(false, null);
                        if (e != null) { error.setText("Could not create the vault: " + e.getMessage()); return; }
                        openVault();
                    }
                }.run(LockActivity.this);
            }
            @Override public void onError(CharSequence msg, boolean cancelled) {
                prompt = null;
                error.setText(cancelled ? "The vault needs your fingerprint. Tap Create vault to try again." : msg);
            }
        });
    }

    // ---- unlock ----------------------------------------------------------------------------------

    private void startFingerprint() {
        String why = Bio.unavailableReason(this);
        if (why != null) { error.setText(why); return; }
        Cipher c;
        try {
            c = Vault.unlockCipher(this);
        } catch (KeyPermanentlyInvalidatedException e) {
            setMode(Mode.KEY_LOST);
            return;
        } catch (Exception e) {
            error.setText("Could not read the vault: " + e.getMessage());
            return;
        }
        prompt = Bio.prompt(this, "Unlock " + getString(R.string.app_name), "Step 1 of 2: fingerprint", c, new Bio.Callback() {
            @Override public void onSuccess(Cipher cipher) {
                prompt = null;
                try {
                    inner = Vault.openOuter(LockActivity.this, cipher);
                    wrongTries = 0;
                    setMode(Mode.PASSWORD);
                } catch (Exception e) {
                    error.setText("Could not unlock: " + e.getMessage());
                }
            }
            @Override public void onError(CharSequence msg, boolean cancelled) {
                prompt = null;
                if (!cancelled) error.setText(msg);
            }
        });
    }

    private void checkPassword() {
        final String pw = password.getText().toString();
        if (pw.isEmpty()) { error.setText("Enter your master password."); return; }
        final byte[] wrapped = inner;
        if (wrapped == null) { setMode(Mode.FINGERPRINT); return; }
        setBusy(true, "Unlocking…");
        new Ui.Task<Void>() {
            @Override Void work() throws Exception {
                char[] chars = pw.toCharArray();
                try { Vault.openInner(LockActivity.this, wrapped, chars); } finally { Crypto.wipe(chars); }
                return null;
            }
            @Override void done(Void r, Exception e) {
                setBusy(false, null);
                if (e == null) { openVault(); return; }
                if (Vault.isWrongPassword(e)) {
                    wrongTries++;
                    password.setText("");
                    if (wrongTries >= MAX_TRIES) {
                        inner = null;
                        setMode(Mode.FINGERPRINT);
                        error.setText("Too many wrong passwords. Confirm your fingerprint again.");
                    } else {
                        error.setText("Wrong master password (" + (MAX_TRIES - wrongTries) + " tries left before fingerprint is needed again).");
                    }
                } else {
                    error.setText("Could not open the vault: " + e.getMessage());
                }
            }
        }.run(this);
    }

    private void openVault() {
        inner = null;
        password.setText("");
        confirm.setText("");
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setBusy(boolean on, String what) {
        busy = on;
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        primary.setEnabled(!on);
        primary.setAlpha(on ? 0.5f : 1f);
        if (on) error.setText(what);
    }

    // ---- reset -----------------------------------------------------------------------------------

    private void askReset() {
        final EditText typed = new EditText(this);
        typed.setHint("Type DELETE");
        typed.setSingleLine(true);
        android.widget.LinearLayout box = Ui.column(this);
        box.addView(typed);
        new AlertDialog.Builder(this)
                .setTitle("Reset vault?")
                .setMessage("This permanently deletes every saved password on this phone. It cannot be undone. You can restore a backup file afterwards.\n\nType DELETE to confirm.")
                .setView(box)
                .setPositiveButton("Delete everything", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (!"DELETE".equals(typed.getText().toString().trim())) { Ui.toast(LockActivity.this, "Not reset: type DELETE to confirm"); return; }
                        Vault.reset(LockActivity.this);
                        inner = null;
                        mode = null;
                        Ui.toast(LockActivity.this, "Vault deleted");
                        onResume();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
