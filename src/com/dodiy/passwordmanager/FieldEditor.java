package com.dodiy.passwordmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The "More fields" part of the edit screen: one row per extra field, plus the "Add field" picker. */
final class FieldEditor {
    private final Activity a;
    private final LinearLayout box;
    final ArrayList<Field> fields = new ArrayList<>();

    FieldEditor(Activity a, LinearLayout box, List<Field> initial) {
        this.a = a;
        this.box = box;
        for (Field f : initial) {
            Field c = new Field(f.label, f.type);
            c.value = f.value;
            fields.add(c);
        }
        render();
    }

    void render() {
        box.removeAllViews();
        for (Field f : fields) box.addView(row(f));
    }

    static int inputType(Field f, boolean visible) {
        switch (f.type) {
            case Field.EMAIL: return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            case Field.PHONE: return InputType.TYPE_CLASS_PHONE;
            case Field.URL: return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
            case Field.NUMBER: return InputType.TYPE_CLASS_NUMBER;
            case Field.DATE: return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
            case Field.PIN: return InputType.TYPE_CLASS_NUMBER | (visible ? InputType.TYPE_NUMBER_VARIATION_NORMAL : InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            case Field.MULTILINE: return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            case Field.HIDDEN_MULTILINE:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD);
            case Field.HIDDEN: case Field.TOTP:
                return InputType.TYPE_CLASS_TEXT | (visible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD);
            default: return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        }
    }

    private View row(final Field f) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, Ui.dp(a, 10));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final TextView label = Ui.text(a, f.label, 13, R.color.text2);
        label.setTypeface(null, Typeface.BOLD);
        label.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rename(f); }
        });
        head.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final EditText value = new EditText(a);
        if (f.isHidden()) {
            final TextView toggle = small("Show");
            toggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean vis = toggle.getText().equals("Show");
                    value.setInputType(inputType(f, vis));
                    value.setTypeface(Typeface.MONOSPACE);
                    value.setSelection(value.length());
                    toggle.setText(vis ? "Hide" : "Show");
                }
            });
            head.addView(toggle);
        }
        TextView remove = small("Remove");
        remove.setTextColor(a.getColor(R.color.error));
        remove.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                fields.remove(f);
                render();
            }
        });
        head.addView(remove);
        row.addView(head);

        value.setText(f.value);
        value.setInputType(inputType(f, false));
        value.setBackgroundResource(R.drawable.field);
        value.setTextColor(a.getColor(R.color.text));
        value.setHintTextColor(a.getColor(R.color.text2));
        value.setTextSize(16);
        int p = Ui.dp(a, 14);
        value.setPadding(p, Ui.dp(a, 12), p, Ui.dp(a, 12));
        value.setMinHeight(Ui.dp(a, 52));
        if (f.isMultiline()) { value.setSingleLine(false); value.setMinLines(3); value.setGravity(Gravity.TOP); }
        else value.setSingleLine(true);
        if (f.isHidden()) value.setTypeface(Typeface.MONOSPACE);
        if (Field.TOTP.equals(f.type)) value.setHint("Secret key or otpauth:// link from the 2FA setup page");
        else if (Field.DATE.equals(f.type)) value.setHint("e.g. 31/12/2030");
        value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        value.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
            @Override public void afterTextChanged(Editable s) { f.value = s.toString(); }
        });
        row.addView(value);

        if (Field.TOTP.equals(f.type)) {
            TextView note = Ui.text(a, "The app will show the current 6-digit code for this login.", 12, R.color.text2);
            note.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), 0, 0);
            row.addView(note);
        }
        return row;
    }

    private TextView small(String s) {
        TextView t = Ui.text(a, s, 13, R.color.primary);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        t.setBackgroundResource(android.R.drawable.list_selector_background);
        return t;
    }

    private void rename(final Field f) {
        final EditText e = new EditText(a);
        e.setText(f.label);
        e.setSingleLine(true);
        e.setSelection(e.length());
        LinearLayout box2 = Ui.column(a);
        box2.addView(e);
        new AlertDialog.Builder(a).setTitle("Field name").setView(box2)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String s = e.getText().toString().trim();
                        if (!s.isEmpty()) { f.label = s; render(); }
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Shows the list of standard fields, grouped. */
    void showPicker() {
        final ArrayList<String[]> rows = new ArrayList<>();   // {group} header rows have length 1
        String group = null;
        for (String[] f : FieldCatalog.FIELDS) {
            if (!f[0].equals(group)) { group = f[0]; rows.add(new String[]{group}); }
            rows.add(f);
        }
        ListView list = new ListView(a);
        list.setDivider(null);
        final AlertDialog[] dialog = new AlertDialog[1];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public boolean isEnabled(int i) { return rows.get(i).length > 1; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int i) { return rows.get(i).length > 1 ? 0 : 1; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                String[] r = rows.get(i);
                TextView t = v instanceof TextView ? (TextView) v : new TextView(a);
                if (r.length == 1) {
                    t.setText(r[0]);
                    t.setTextSize(13);
                    t.setTypeface(null, Typeface.BOLD);
                    t.setTextColor(a.getColor(R.color.primary));
                    t.setPadding(Ui.dp(a, 24), Ui.dp(a, 14), Ui.dp(a, 24), Ui.dp(a, 4));
                } else {
                    t.setText(r[1] + (r[2].equals(Field.TEXT) || r[2].equals(Field.MULTILINE) ? "" : "  ·  " + typeName(r[2])));
                    t.setTextSize(16);
                    t.setTextColor(a.getColor(R.color.text));
                    t.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 12));
                }
                return t;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                String[] r = rows.get(i);
                if (r.length == 1) return;
                dialog[0].dismiss();
                final Field f = new Field(r[1], r[2]);
                if (r[1].equals(FieldCatalog.CUSTOM_TEXT) || r[1].equals(FieldCatalog.CUSTOM_HIDDEN)) {
                    f.label = r[1].equals(FieldCatalog.CUSTOM_HIDDEN) ? "Hidden field" : "Field";
                    fields.add(f);
                    render();
                    rename(f);
                } else {
                    fields.add(f);
                    render();
                }
            }
        });
        dialog[0] = new AlertDialog.Builder(a).setTitle("Add a field").setView(list).setNegativeButton("Cancel", null).show();
    }

    static String typeName(String type) {
        switch (type) {
            case Field.HIDDEN: case Field.HIDDEN_MULTILINE: case Field.PIN: return "hidden";
            case Field.TOTP: return "shows 2FA codes";
            case Field.EMAIL: return "email";
            case Field.PHONE: return "phone";
            case Field.URL: return "link";
            case Field.NUMBER: return "number";
            case Field.DATE: return "date";
            default: return "";
        }
    }

    /** Fields with a value, ready to save. */
    ArrayList<Field> result() {
        ArrayList<Field> out = new ArrayList<>();
        for (Field f : fields) if (!f.value.trim().isEmpty()) out.add(f);
        return out;
    }
}
