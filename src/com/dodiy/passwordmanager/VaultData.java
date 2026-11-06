package com.dodiy.passwordmanager;

import java.util.ArrayList;

/** Everything stored inside the encrypted vault (and in backups). */
final class VaultData {
    ArrayList<Entry> entries = new ArrayList<>();
    ArrayList<Person> people = new ArrayList<>();
    ArrayList<Portfolio> portfolios = new ArrayList<>();
}
