package com.dodiy.passwordmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** Dialogs to choose or edit People and Portfolios. Owner, Assignee and Project Manager all use {@link #person}. */
final class Pick {
    private Pick() {}

    interface OnPicked { void picked(String id); }

    static ArrayList<Person> sortedPeople() {
        ArrayList<Person> l = new ArrayList<>(Session.people);
        Collections.sort(l, new Comparator<Person>() {
            @Override public int compare(Person a, Person b) { return a.name.compareToIgnoreCase(b.name); }
        });
        return l;
    }

    static ArrayList<Portfolio> sortedPortfolios() {
        ArrayList<Portfolio> l = new ArrayList<>(Session.portfolios);
        Collections.sort(l, new Comparator<Portfolio>() {
            @Override public int compare(Portfolio a, Portfolio b) { return a.name.compareToIgnoreCase(b.name); }
        });
        return l;
    }

    /** Choose someone from People ("" for none), or add a new person on the spot. */
    static void person(final Activity a, String title, String currentId, final OnPicked cb) {
        final ArrayList<Person> people = sortedPeople();
        String[] items = new String[people.size() + 2];
        items[0] = "None";
        int checked = 0;
        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);
            items[i + 1] = p.name + (p.role.isEmpty() ? "" : "  (" + p.role + ")");
            if (p.id.equals(currentId)) checked = i + 1;
        }
        items[items.length - 1] = "＋  Add a new person";
        new AlertDialog.Builder(a).setTitle(title)
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) cb.picked("");
                        else if (which <= people.size()) cb.picked(people.get(which - 1).id);
                        else editPerson(a, null, cb);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Add (p == null) or edit a person. Calls back with the id after saving; "Delete" is offered for existing people. */
    static void editPerson(final Activity a, final Person existing, final OnPicked cb) {
        LinearLayout box = Ui.column(a);
        final EditText name = field(a, box, "Name", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        final EditText role = field(a, box, "Role or team (optional)", existing == null ? "" : existing.role, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText email = field(a, box, "Email (optional)", existing == null ? "" : existing.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText phone = field(a, box, "Phone (optional)", existing == null ? "" : existing.phone, InputType.TYPE_CLASS_PHONE);
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(existing == null ? "New person" : "Edit person").setView(box)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { deletePerson(a, existing, cb); }
        });
        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) { name.setError("Enter a name"); return; }
                        Person p = existing != null ? existing : new Person();
                        p.name = n;
                        p.role = role.getText().toString().trim();
                        p.email = email.getText().toString().trim();
                        p.phone = phone.getText().toString().trim();
                        p.updated = System.currentTimeMillis();
                        if (existing == null) Session.people.add(p);
                        if (!save(a)) return;
                        d.dismiss();
                        cb.picked(p.id);
                    }
                });
            }
        });
        d.show();
    }

    private static void deletePerson(final Activity a, final Person p, final OnPicked cb) {
        int uses = Vault.uses(Session.data(), p.id);
        Ui.confirm(a, "Delete " + p.name + "?",
                uses == 0 ? "They are not used anywhere." : "They are set as Owner, Assignee or Project Manager in " + uses + (uses == 1 ? " place" : " places") + ". Those will be cleared; the logins stay.",
                "Delete", new Runnable() {
                    @Override public void run() {
                        Vault.removePerson(Session.data(), p.id);
                        if (save(a)) cb.picked("");
                    }
                });
    }

    /** Choose a portfolio ("" for none), or make a new one. */
    static void portfolio(final Activity a, String currentId, final OnPicked cb) {
        final ArrayList<Portfolio> list = sortedPortfolios();
        String[] items = new String[list.size() + 2];
        items[0] = "None";
        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            Portfolio p = list.get(i);
            String owner = Session.personName(p.ownerId);
            items[i + 1] = p.name + (owner.isEmpty() ? "" : "  (Owner: " + owner + ")");
            if (p.id.equals(currentId)) checked = i + 1;
        }
        items[items.length - 1] = "＋  New portfolio";
        new AlertDialog.Builder(a).setTitle("Portfolio")
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (which == 0) cb.picked("");
                        else if (which <= list.size()) cb.picked(list.get(which - 1).id);
                        else editPortfolio(a, null, cb);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** Add (p == null) or edit a portfolio: name, Owner (from People) and notes. */
    static void editPortfolio(final Activity a, final Portfolio existing, final OnPicked cb) {
        LinearLayout box = Ui.column(a);
        final EditText name = field(a, box, "Portfolio name, e.g. Office", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final String[] owner = {existing == null ? "" : existing.ownerId};
        final TextView ownerRow = Ui.text(a, "", 16, R.color.text);
        ownerRow.setBackgroundResource(R.drawable.field);
        int p16 = Ui.dp(a, 14);
        ownerRow.setPadding(p16, p16, p16, p16);
        final Runnable showOwner = new Runnable() {
            @Override public void run() {
                String n = Session.personName(owner[0]);
                ownerRow.setText("Owner:  " + (n.isEmpty() ? "None" : n) + "   ▾");
            }
        };
        showOwner.run();
        ownerRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                person(a, "Owner", owner[0], new OnPicked() {
                    @Override public void picked(String id) { owner[0] = id; showOwner.run(); }
                });
            }
        });
        Ui.gap(box, 10);
        box.addView(ownerRow);
        final EditText notes = field(a, box, "Notes (optional)", existing == null ? "" : existing.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(existing == null ? "New portfolio" : "Edit portfolio").setView(box)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null);
        if (existing != null) b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) {
                int n = 0;
                for (Entry e : Session.entries) if (existing.id.equals(e.portfolioId)) n++;
                Ui.confirm(a, "Delete portfolio " + existing.name + "?",
                        n == 0 ? "It has no logins." : "Its " + n + (n == 1 ? " login stays" : " logins stay") + " in the vault, without a portfolio.",
                        "Delete", new Runnable() {
                            @Override public void run() {
                                Vault.removePortfolio(Session.data(), existing.id);
                                if (save(a)) cb.picked("");
                            }
                        });
            }
        });
        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String n = name.getText().toString().trim();
                        if (n.isEmpty()) { name.setError("Enter a name"); return; }
                        Portfolio p = existing != null ? existing : new Portfolio();
                        p.name = n;
                        p.ownerId = owner[0];
                        p.notes = notes.getText().toString().trim();
                        p.updated = System.currentTimeMillis();
                        if (existing == null) Session.portfolios.add(p);
                        if (!save(a)) return;
                        d.dismiss();
                        cb.picked(p.id);
                    }
                });
            }
        });
        d.show();
    }

    private static EditText field(Activity a, LinearLayout box, String hint, String value, int type) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setBackgroundResource(R.drawable.field);
        e.setTextColor(a.getColor(R.color.text));
        e.setHintTextColor(a.getColor(R.color.text2));
        int p = Ui.dp(a, 14);
        e.setPadding(p, Ui.dp(a, 12), p, Ui.dp(a, 12));
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (box.getChildCount() > 0) Ui.gap(box, 10);
        box.addView(e, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    static boolean save(Activity a) {
        try {
            Vault.save(a);
            return true;
        } catch (Exception e) {
            Ui.toast(a, "Could not save: " + e.getMessage());
            return false;
        }
    }
}
