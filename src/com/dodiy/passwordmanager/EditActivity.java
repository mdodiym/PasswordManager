package com.dodiy.passwordmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public final class EditActivity extends BaseActivity {
    private static final int REQ_LOGO = 1;

    private Entry original;      // null for a new login
    private String provider = "";
    private boolean providerChosen;   // true once the user picked a logo by hand
    private boolean showPw;
    private EditText title, username, password, url, notes;
    private ImageView logo;
    private TextView providerName;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_edit);
        if (!Session.isOpen()) return;   // BaseActivity.onStart sends us to the lock screen
        title = findViewById(R.id.title);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        url = findViewById(R.id.url);
        notes = findViewById(R.id.notes);
        logo = findViewById(R.id.logo);
        providerName = findViewById(R.id.providerName);

        String id = getIntent().getStringExtra("id");
        original = Session.find(id);
        ((TextView) findViewById(R.id.heading)).setText(original == null ? "New login" : "Edit login");
        findViewById(R.id.delete).setVisibility(original == null ? View.GONE : View.VISIBLE);
        if (b != null) {
            provider = b.getString("provider", "");
            providerChosen = b.getBoolean("chosen");
        } else if (original != null) {
            provider = original.provider;
            providerChosen = true;
            title.setText(original.title);
            username.setText(original.username);
            password.setText(original.password);
            url.setText(original.url);
            notes.setText(original.notes);
        } else {
            String pre = getIntent().getStringExtra("provider");
            if (pre != null) {
                provider = pre;
                providerChosen = true;
                Provider p = Providers.get(this, pre);
                if (p != null) title.setText(p.title);
            }
        }
        bindLogo();

        title.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) {
                if (!providerChosen) {
                    Provider p = Providers.match(EditActivity.this, s.toString());
                    provider = p == null ? "" : p.slug;
                }
                bindLogo();
            }
        });
        findViewById(R.id.logoRow).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivityForResult(new Intent(EditActivity.this, PickerActivity.class), REQ_LOGO);
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        findViewById(R.id.showPw).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setShowPw(!showPw); }
        });
        findViewById(R.id.generate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MainActivity.generatorDialog(EditActivity.this, new MainActivity.OnPassword() {
                    @Override public void use(String pw) { password.setText(pw); setShowPw(true); }
                });
            }
        });
        findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.confirm(EditActivity.this, "Delete this login?", "\"" + original.title + "\" will be removed from the vault.", "Delete", new Runnable() {
                    @Override public void run() {
                        Session.entries.remove(original);
                        if (persist()) { finishAffinityToMain(); }
                    }
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("provider", provider);
        out.putBoolean("chosen", providerChosen);
    }

    private void setShowPw(boolean on) {
        showPw = on;
        password.setInputType(InputType.TYPE_CLASS_TEXT | (on ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        password.setTypeface(android.graphics.Typeface.MONOSPACE);
        password.setSelection(password.length());
        ((TextView) findViewById(R.id.showPw)).setText(on ? "Hide" : "Show");
    }

    private void bindLogo() {
        Provider p = Providers.get(this, provider);
        logo.setImageDrawable(new LogoDrawable(p, title.getText().toString()));
        providerName.setText(p != null ? p.title : "None (letter icon)");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LOGO && res == RESULT_OK && data != null) {
            provider = data.getStringExtra("slug");
            if (provider == null) provider = "";
            providerChosen = true;
            Provider p = Providers.get(this, provider);
            if (p != null && title.getText().toString().trim().isEmpty()) title.setText(p.title);
            bindLogo();
        }
    }

    private void save() {
        String t = title.getText().toString().trim();
        if (t.isEmpty()) { title.setError("Give it a name"); title.requestFocus(); return; }
        Entry e = original != null ? original : new Entry();
        e.title = t;
        e.provider = provider;
        e.username = username.getText().toString().trim();
        e.password = password.getText().toString();
        e.url = url.getText().toString().trim();
        e.notes = notes.getText().toString();
        e.updated = System.currentTimeMillis();
        if (original == null) Session.entries.add(e);
        if (persist()) {
            Ui.toast(this, "Saved");
            finish();
        }
    }

    private boolean persist() {
        try {
            Vault.save(this);
            return true;
        } catch (Exception ex) {
            Ui.toast(this, "Could not save: " + ex.getMessage());
            return false;
        }
    }

    private void finishAffinityToMain() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private boolean changed() {
        if (original == null) {
            return title.length() + username.length() + password.length() + url.length() + notes.length() > 0;
        }
        return !original.title.equals(title.getText().toString().trim())
                || !original.username.equals(username.getText().toString().trim())
                || !original.password.equals(password.getText().toString())
                || !original.url.equals(url.getText().toString().trim())
                || !original.notes.equals(notes.getText().toString())
                || !original.provider.equals(provider);
    }

    @Override public void onBackPressed() {
        if (title != null && changed()) {
            Ui.confirm(this, "Discard changes?", "Your changes to this login are not saved.", "Discard", new Runnable() {
                @Override public void run() { finish(); }
            });
        } else {
            super.onBackPressed();
        }
    }
}
