package com.dodiy.passwordmanager;

import java.util.ArrayList;

/** The unlocked vault, held in memory only while the app is in the foreground. */
final class Session {
    private Session() {}

    static byte[] key;          // vault data key
    static byte[] inner;        // vault key wrapped with the master password (used to check / change it)
    static ArrayList<Entry> entries;

    static boolean isOpen() { return key != null && entries != null; }

    static void open(byte[] vaultKey, byte[] wrapped, ArrayList<Entry> list) {
        key = vaultKey;
        inner = wrapped;
        entries = list;
    }

    static void lock() {
        Crypto.wipe(key);
        key = null;
        inner = null;
        if (entries != null) entries.clear();
        entries = null;
    }

    static Entry find(String id) {
        if (entries == null || id == null) return null;
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }
}
