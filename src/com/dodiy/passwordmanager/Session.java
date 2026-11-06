package com.dodiy.passwordmanager;

import java.util.ArrayList;

/** The unlocked vault, held in memory only while the app is in the foreground. */
final class Session {
    private Session() {}

    static byte[] key;          // vault data key
    static byte[] inner;        // vault key wrapped with the master password (used to check / change it)
    static ArrayList<Entry> entries;
    static ArrayList<Person> people;
    static ArrayList<Portfolio> portfolios;

    static boolean isOpen() { return key != null && entries != null; }

    static void open(byte[] vaultKey, byte[] wrapped, VaultData d) {
        key = vaultKey;
        inner = wrapped;
        entries = d.entries;
        people = d.people;
        portfolios = d.portfolios;
    }

    static VaultData data() {
        VaultData d = new VaultData();
        d.entries = entries;
        d.people = people;
        d.portfolios = portfolios;
        return d;
    }

    static Person person(String id) {
        if (people == null || id == null || id.isEmpty()) return null;
        for (Person p : people) if (p.id.equals(id)) return p;
        return null;
    }

    static String personName(String id) {
        Person p = person(id);
        return p == null ? "" : p.name;
    }

    static Portfolio portfolio(String id) {
        if (portfolios == null || id == null || id.isEmpty()) return null;
        for (Portfolio p : portfolios) if (p.id.equals(id)) return p;
        return null;
    }

    static void lock() {
        Crypto.wipe(key);
        key = null;
        inner = null;
        if (entries != null) entries.clear();
        entries = null;
        people = null;
        portfolios = null;
    }

    static Entry find(String id) {
        if (entries == null || id == null) return null;
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }
}
