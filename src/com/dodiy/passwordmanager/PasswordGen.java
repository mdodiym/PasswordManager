package com.dodiy.passwordmanager;

/** Random passwords from SecureRandom, with at least one character from every chosen set. */
final class PasswordGen {
    private PasswordGen() {}

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?";

    static String generate(int length, boolean upper, boolean lower, boolean digits, boolean symbols) {
        StringBuilder sets = new StringBuilder();
        java.util.ArrayList<String> groups = new java.util.ArrayList<>();
        if (upper) groups.add(UPPER);
        if (lower) groups.add(LOWER);
        if (digits) groups.add(DIGITS);
        if (symbols) groups.add(SYMBOLS);
        if (groups.isEmpty()) groups.add(LOWER);
        for (String g : groups) sets.append(g);
        length = Math.max(length, groups.size());
        char[] out = new char[length];
        int i = 0;
        for (String g : groups) out[i++] = g.charAt(Crypto.RNG.nextInt(g.length()));
        for (; i < length; i++) out[i] = sets.charAt(Crypto.RNG.nextInt(sets.length()));
        for (int k = length - 1; k > 0; k--) {   // Fisher-Yates so the guaranteed characters are not at the front
            int j = Crypto.RNG.nextInt(k + 1);
            char t = out[k]; out[k] = out[j]; out[j] = t;
        }
        return new String(out);
    }

    /** Rough strength label for the setup screen. */
    static String strength(CharSequence pw) {
        int n = pw.length();
        boolean u = false, l = false, d = false, s = false;
        for (int i = 0; i < n; i++) {
            char c = pw.charAt(i);
            if (Character.isUpperCase(c)) u = true;
            else if (Character.isLowerCase(c)) l = true;
            else if (Character.isDigit(c)) d = true;
            else s = true;
        }
        int kinds = (u ? 1 : 0) + (l ? 1 : 0) + (d ? 1 : 0) + (s ? 1 : 0);
        if (n < 8) return "Too short (at least 8 characters)";
        if (n >= 14 && kinds >= 3) return "Strong";
        if (n >= 10 && kinds >= 2) return "Good";
        return "Weak, try a longer one";
    }
}
