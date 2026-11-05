package com.dodiy.passwordmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Logo picker (returns "slug"), or a browsable list of every bundled logo when started with browse=true. */
public final class PickerActivity extends BaseActivity {
    private static final Object NONE = new Object();

    private final ArrayList<Object> rows = new ArrayList<>();
    private Adapter adapter;
    private EditText search;
    private boolean browse;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_picker);
        browse = getIntent().getBooleanExtra("browse", false);
        ((TextView) findViewById(R.id.heading)).setText(browse ? "Provider logos" : "Choose a provider");
        ((TextView) findViewById(R.id.count)).setText(Providers.all(this).size() + " logos included");
        search = findViewById(R.id.search);
        ListView list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                Object o = rows.get(pos);
                if (o instanceof String) return;
                String slug = o == NONE ? "" : ((Provider) o).slug;
                if (browse) {
                    startActivity(new Intent(PickerActivity.this, EditActivity.class).putExtra("provider", slug));
                    finish();
                    return;
                }
                setResult(RESULT_OK, new Intent().putExtra("slug", slug));
                finish();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        refresh();
    }

    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        rows.clear();
        List<Provider> all = Providers.all(this);
        if (q.isEmpty()) {
            if (!browse) rows.add(NONE);
            rows.add("Popular");
            rows.addAll(Providers.popular(this));
            String cat = null;
            for (Provider p : Providers.malaysia(this)) {
                if (!p.category.equals(cat)) { cat = p.category; rows.add("Malaysia · " + cat); }
                rows.add(p);
            }
            rows.add("All providers A to Z");
            rows.addAll(all);
        } else {
            ArrayList<Provider> starts = new ArrayList<>(), contains = new ArrayList<>();
            String nq = Providers.norm(q);
            for (Provider p : all) {
                String t = p.title.toLowerCase(Locale.ROOT);
                if (t.startsWith(q) || p.slug.startsWith(nq)) starts.add(p);
                else if (p.key.contains(q)) contains.add(p);
            }
            rows.addAll(starts);
            rows.addAll(contains);
            if (!browse) rows.add(NONE);
        }
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int p) { return rows.get(p) instanceof String ? 1 : 0; }
        @Override public boolean isEnabled(int p) { return !(rows.get(p) instanceof String); }

        @Override public View getView(int pos, View v, ViewGroup parent) {
            Object o = rows.get(pos);
            if (o instanceof String) {
                TextView t = v instanceof TextView ? (TextView) v : Ui.text(PickerActivity.this, "", 14, R.color.text2);
                t.setText((String) o);
                t.setTypeface(null, android.graphics.Typeface.BOLD);
                t.setPadding(Ui.dp(PickerActivity.this, 8), Ui.dp(PickerActivity.this, 12), 0, Ui.dp(PickerActivity.this, 2));
                return t;
            }
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_row, parent, false);
            ImageView logo = v.findViewById(R.id.logo);
            TextView title = v.findViewById(R.id.title), sub = v.findViewById(R.id.subtitle);
            v.findViewById(R.id.action).setVisibility(View.GONE);
            if (o == NONE) {
                logo.setImageDrawable(new LogoDrawable(null, "?"));
                title.setText("No logo");
                sub.setText("Use the first letter of the name");
            } else {
                Provider p = (Provider) o;
                logo.setImageDrawable(new LogoDrawable(p, p.title));
                title.setText(p.title);
                String extra = p.category.isEmpty() ? (p.aliases.isEmpty() ? "" : p.aliases.replace("|", ", "))
                        : p.category + " · Malaysia";
                sub.setText(p.kind == Provider.LETTERS ? (extra.isEmpty() ? "Brand-colour badge" : extra + " · brand-colour badge") : extra);
                sub.setVisibility(sub.getText().length() == 0 ? View.GONE : View.VISIBLE);
            }
            if (o == NONE) sub.setVisibility(View.VISIBLE);
            return v;
        }
    }
}
