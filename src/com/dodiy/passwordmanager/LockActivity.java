package com.dodiy.passwordmanager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
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

/**
 * First run: create the master password, then optionally switch on fingerprint unlock.
 * Later: unlock with the fingerprint OR the master password.
 */
public final class LockActivity extends BaseActivity {
    private enum Mode { SETUP, UNLOCK, OFFER_BIO, LEGACY_FINGERPRINT, LEGACY_PASSWORD, LEGACY_KEY_LOST }

    private static final int TRIES_BEFORE_WAIT = 5;
    private static final long WAIT_MS = 30_000;

    private Mode mode;
    private TextView heading, message, strength, error, primary, secondary, reset;
    private EditText password, confirm;
    private CheckBox show;
    private ProgressBar progress;
    private byte[] legacyInner;          // old format: result of the fingerprint step
    private int wrongTries;
    private long waitUntil;
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
        if (busy) return;
        if (Session.isOpen()) {
            if (mode != Mode.OFFER_BIO) openVault();
            return;
        }
        Mode want;
        if (!Vault.exists(this)) want = Mode.SETUP;
        else if (Vault.isLegacy(this)) {
            want = mode == Mode.LEGACY_KEY_LOST ? Mode.LEGACY_KEY_LOST : legacyInner != null ? Mode.LEGACY_PASSWORD : Mode.LEGACY_FINGERPRINT;
        } else want = Mode.UNLOCK;
        if (mode != want) setMode(want);
        if (!autoPrompted && (want == Mode.LEGACY_FINGERPRINT || (want == Mode.UNLOCK && Vault.bioEnabled(this)))) {
            autoPrompted = true;
            startFingerprint();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (prompt != null) { prompt.cancel(); prompt = null; }
        if (!isChangingConfigurations() && !busy) {
            legacyInner = null;
            autoPrompted = false;
            password.setText("");
            confirm.setText("");
        }
    }

    private void setMode(Mode m) {
        mode = m;
        error.setText("");
        boolean setup = m == Mode.SETUP;
        boolean pwField = setup || m == Mode.UNLOCK || m == Mode.LEGACY_PASSWORD;
        password.setVisibility(pwField ? View.VISIBLE : View.GONE);
        confirm.setVisibility(setup ? View.VISIBLE : View.GONE);
        show.setVisibility(pwField ? View.VISIBLE : View.GONE);
        strength.setVisibility(setup ? View.VISIBLE : View.GONE);
        reset.setVisibility(m == Mode.UNLOCK || m == Mode.LEGACY_FINGERPRINT || m == Mode.LEGACY_PASSWORD ? View.VISIBLE : View.GONE);
        secondary.setVisibility(View.GONE);
        password.setImeOptions(setup ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
        switch (m) {
            case SETUP:
                heading.setText("Create your vault");
                message.setText("Choose a master password. It opens the vault on its own, and you can also turn on fingerprint unlock next.\n\nThere is no way to recover it if you forget it, so pick one you will remember.");
                primary.setText("Create vault");
                break;
            case UNLOCK:
                heading.setText("Vault locked");
                boolean bio = Vault.bioEnabled(this);
                message.setText(bio ? "Unlock with your fingerprint, or type your master password." : "Enter your master password.");
                primary.setText("Unlock");
                if (bio) { secondary.setText("Use fingerprint"); secondary.setVisibility(View.VISIBLE); }
                break;
            case OFFER_BIO:
                heading.setText("Unlock with fingerprint?");
                message.setText("Next time you can open the vault with just your fingerprint. Your master password will still work too.\n\nYou can change this later in ⋮ → Fingerprint unlock.");
                primary.setText("Turn on fingerprint unlock");
                secondary.setText("Not now");
                secondary.setVisibility(View.VISIBLE);
                break;
            case LEGACY_FINGERPRINT:
                heading.setText("Vault locked");
                message.setText("One-time update: confirm your fingerprint, then your master password. After that you can unlock with either one.");
                primary.setText("Unlock with fingerprint");
                break;
            case LEGACY_PASSWORD:
                heading.setText("Vault locked");
                message.setText("Now enter your master password to finish the update.");
                primary.setText("Unlock");
                password.requestFocus();
                break;
            case LEGACY_KEY_LOST:
                heading.setText("Fingerprint key was removed");
                message.setText("Android deleted this app's fingerprint key (this happens when the screen lock or all fingerprints are removed), so this vault can no longer be opened.\n\nReset the vault, then use ⋮ → Restore from backup if you exported a backup file.");
                primary.setText("Reset vault");
                break;
        }
    }

    private void onPrimary() {
        if (busy) return;
        switch (mode) {
            case SETUP: createVault(); break;
            case UNLOCK: case LEGACY_PASSWORD: checkPassword(); break;
            case OFFER_BIO: enableBio(); break;
            case LEGACY_FINGERPRINT: startFingerprint(); break;
            case LEGACY_KEY_LOST: askReset(); break;
        }
    }

    private void onSecondary() {
        if (busy) return;
        if (mode == Mode.UNLOCK) startFingerprint();
        else if (mode == Mode.OFFER_BIO) {
            Prefs.setBioDeclined(this, true);
            openVault();
        }
    }

    // ---- setup -----------------------------------------------------------------------------------

    private void createVault() {
        final String pw = password.getText().toString();
        if (pw.length() < 8) { error.setText("Use at least 8 characters."); return; }
        if (!pw.equals(confirm.getText().toString())) { error.setText("The two passwords don't match."); return; }
        setBusy(true, "Creating vault…");
        new Ui.Task<Void>() {
            @Override Void work() throws Exception {
                char[] chars = pw.toCharArray();
                try { Vault.create(LockActivity.this, chars); } finally { Crypto.wipe(chars); }
                return null;
            }
            @Override void done(Void r, Exception e) {
                setBusy(false, null);
                if (e != null) { error.setText("Could not create the vault: " + e.getMessage()); return; }
                afterPasswordUnlock();
            }
        }.run(this);
    }

    /** After a password unlock: offer fingerprint unlock if it is off and the phone supports it. */
    private void afterPasswordUnlock() {
        password.setText("");
        confirm.setText("");
        if (!Vault.bioEnabled(this) && Bio.unavailableReason(this) == null && !Prefs.bioDeclined(this)) setMode(Mode.OFFER_BIO);
        else openVault();
    }

    private void enableBio() {
        Cipher c;
        try {
            c = Vault.prepareEnableBio();
        } catch (Exception e) {
            error.setText("Could not set up fingerprint unlock: " + e.getMessage());
            return;
        }
        prompt = Bio.prompt(this, "Turn on fingerprint unlock", "Confirm with your fingerprint", c, new Bio.Callback() {
            @Override public void onSuccess(Cipher cipher) {
                prompt = null;
                try {
                    Vault.enableBio(LockActivity.this, cipher);
                    Ui.toast(LockActivity.this, "Fingerprint unlock is on");
                    openVault();
                } catch (Exception e) {
                    error.setText("Could not turn on fingerprint unlock: " + e.getMessage());
                }
            }
            @Override public void onError(CharSequence msg, boolean cancelled) {
                prompt = null;
                if (!cancelled) error.setText(msg);
            }
        });
    }

    // ---- unlock ----------------------------------------------------------------------------------

    private void startFingerprint() {
        String why = Bio.unavailableReason(this);
        if (why != null) { error.setText(why); return; }
        final boolean legacy = mode == Mode.LEGACY_FINGERPRINT;
        Cipher c;
        try {
            c = legacy ? Vault.legacyCipher(this) : Vault.bioUnlockCipher(this);
        } catch (KeyPermanentlyInvalidatedException e) {
            if (legacy) { setMode(Mode.LEGACY_KEY_LOST); return; }
            try { Vault.disableBio(this); } catch (Exception ignored) {}
            setMode(Mode.UNLOCK);
            error.setText("Your phone's fingerprints changed, so fingerprint unlock was switched off for safety. Unlock with your master password, then you can turn it back on.");
            return;
        } catch (Exception e) {
            error.setText("Fingerprint unlock failed: " + e.getMessage());
            return;
        }
        prompt = Bio.prompt(this, "Unlock " + getString(R.string.app_name), legacy ? "Step 1 of 2: fingerprint" : "Or cancel to use your master password", c, new Bio.Callback() {
            @Override public void onSuccess(Cipher cipher) {
                prompt = null;
                try {
                    if (legacy) {
                        legacyInner = Vault.legacyOpenOuter(LockActivity.this, cipher);
                        setMode(Mode.LEGACY_PASSWORD);
                    } else {
                        Vault.unlockWithBio(LockActivity.this, cipher);
                        openVault();
                    }
                } catch (Exception e) {
                    error.setText("Could not unlock: " + e.getMessage());
                }
            }
            @Override public void onError(CharSequence msg, boolean cancelled) {
                prompt = null;
                if (!cancelled) error.setText(msg);
                else if (mode == Mode.UNLOCK) password.requestFocus();
            }
        });
    }

    private void checkPassword() {
        final String pw = password.getText().toString();
        if (pw.isEmpty()) { error.setText("Enter your master password."); return; }
        long wait = waitUntil - SystemClock.elapsedRealtime();
        if (wait > 0) { error.setText("Too many wrong passwords. Try again in " + (wait / 1000 + 1) + " seconds."); return; }
        final boolean legacy = mode == Mode.LEGACY_PASSWORD;
        final byte[] inner = legacyInner;
        if (legacy && inner == null) { setMode(Mode.LEGACY_FINGERPRINT); return; }
        setBusy(true, "Unlocking…");
        new Ui.Task<Void>() {
            @Override Void work() throws Exception {
                char[] chars = pw.toCharArray();
                try {
                    if (legacy) Vault.legacyUnlockAndUpgrade(LockActivity.this, inner, chars);
                    else Vault.unlockWithPassword(LockActivity.this, chars);
                } finally {
                    Crypto.wipe(chars);
                }
                return null;
            }
            @Override void done(Void r, Exception e) {
                setBusy(false, null);
                if (e == null) {
                    wrongTries = 0;
                    legacyInner = null;
                    afterPasswordUnlock();
                    return;
                }
                if (Vault.isWrongPassword(e)) {
                    wrongTries++;
                    password.setText("");
                    if (wrongTries % TRIES_BEFORE_WAIT == 0) {
                        waitUntil = SystemClock.elapsedRealtime() + WAIT_MS * (wrongTries / TRIES_BEFORE_WAIT);
                        error.setText("Wrong master password. Too many tries, please wait " + (WAIT_MS * (wrongTries / TRIES_BEFORE_WAIT) / 1000) + " seconds.");
                    } else {
                        error.setText("Wrong master password.");
                    }
                } else {
                    error.setText("Could not open the vault: " + e.getMessage());
                }
            }
        }.run(this);
    }

    private void openVault() {
        legacyInner = null;
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
        error.setText(on ? what : "");
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
                .setMessage("This permanently deletes every saved login on this phone. It cannot be undone. You can restore a backup file afterwards.\n\nType DELETE to confirm.")
                .setView(box)
                .setPositiveButton("Delete everything", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (!"DELETE".equals(typed.getText().toString().trim())) { Ui.toast(LockActivity.this, "Not reset: type DELETE to confirm"); return; }
                        Vault.reset(LockActivity.this);
                        legacyInner = null;
                        mode = null;
                        Ui.toast(LockActivity.this, "Vault deleted");
                        onResume();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
