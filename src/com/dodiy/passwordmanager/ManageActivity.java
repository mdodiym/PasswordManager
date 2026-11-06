package com.dodiy.passwordmanager;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/** The People list or the Portfolios list (extra "what" = "people" / "portfolios"). */
public final class ManageActivity extends BaseActivity {
    private boolean people;
    private final ArrayList<Object> rows = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView empty, count;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_manage);
        people = !"portfolios".equals(getIntent().getStringExtra("what"));
        ((TextView) findViewById(R.id.heading)).setText(people ? "People" : "Portfolios");
        ((TextView) findViewById(R.id.add)).setText(people ? "＋  Add person" : "＋  New portfolio");
        empty = findViewById(R.id.empty);
        count = findViewById(R.id.count);
        empty.setText(people
                ? "No people yet.\n\nAdd the people who own or look after your logins. You can then pick them as Owner, Assignee or Project Manager on a login, and as the Owner of a portfolio."
                : "No portfolios yet.\n\nA portfolio groups logins, e.g. Office, Family or Client A, and has an Owner from your People list.");
        ListView list = findViewById(R.id.list);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.item_row, parent, false);
                Object o = rows.get(i);
                ImageView logo = v.findViewById(R.id.logo);
                TextView t = v.findViewById(R.id.title), s = v.findViewById(R.id.subtitle);
                v.findViewById(R.id.action).setVisibility(View.GONE);
                if (o instanceof Person) {
                    Person p = (Person) o;
                    logo.setImageDrawable(new LogoDrawable(null, p.name));
                    t.setText(p.name);
                    int uses = Vault.uses(Session.data(), p.id);
                    String sub = p.subtitle();
                    s.setText((sub.isEmpty() ? "" : sub + " · ") + (uses == 0 ? "not used yet" : "used in " + uses));
                } else {
                    Portfolio p = (Portfolio) o;
                    logo.setImageDrawable(new LogoDrawable(null, p.name));
                    t.setText(p.name);
                    int n = 0;
                    for (Entry e : Session.entries) if (p.id.equals(e.portfolioId)) n++;
                    String owner = Session.personName(p.ownerId);
                    s.setText("Owner: " + (owner.isEmpty() ? "none" : owner) + " · " + n + (n == 1 ? " login" : " logins"));
                }
                return v;
            }
        };
        list.setAdapter(adapter);
        final Pick.OnPicked refresh = new Pick.OnPicked() {
            @Override public void picked(String id) { refresh(); }
        };
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                Object o = rows.get(i);
                if (o instanceof Person) Pick.editPerson(ManageActivity.this, (Person) o, refresh);
                else Pick.editPortfolio(ManageActivity.this, (Portfolio) o, refresh);
            }
        });
        findViewById(R.id.add).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (people) Pick.editPerson(ManageActivity.this, null, refresh);
                else Pick.editPortfolio(ManageActivity.this, null, refresh);
            }
        });
        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (Session.isOpen()) refresh();
    }

    private void refresh() {
        rows.clear();
        if (people) rows.addAll(Pick.sortedPeople()); else rows.addAll(Pick.sortedPortfolios());
        int n = rows.size();
        count.setText(n + (people ? (n == 1 ? " person" : " people") : (n == 1 ? " portfolio" : " portfolios")));
        empty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }
}
