package com.dodiy.passwordmanager;

import android.graphics.Path;

/** A brand / identity provider with its logo. */
final class Provider {
    static final int GLYPH = 0;      // single-colour logo path (Simple Icons)
    static final int LETTERS = 1;    // brand-coloured badge with letters (brand not in Simple Icons)
    static final int MICROSOFT = 2;  // four coloured squares, drawn by hand
    static final int IMAGE = 3;      // downloaded app icon, assets/logos/<slug>.png

    final String slug;
    final String title;
    final int color;
    final String aliases;
    final int kind;
    final String data;   // path data for GLYPH, letters for LETTERS, asset path for IMAGE
    String category = "";  // e.g. "Bank" for Malaysian providers
    android.graphics.Bitmap bitmap;
    private Path path;
    final String key;    // lower-case search key
    final String normTitle;
    final String[] normAliases;

    Provider(String slug, String title, int color, String aliases, int kind, String data) {
        this.slug = slug;
        this.title = title;
        this.color = color;
        this.aliases = aliases == null ? "" : aliases;
        this.kind = kind;
        this.data = data;
        this.key = (title + " " + slug + " " + this.aliases.replace('|', ' ')).toLowerCase();
        this.normTitle = Providers.norm(title);
        String[] al = this.aliases.isEmpty() ? new String[0] : this.aliases.split("\\|");
        for (int i = 0; i < al.length; i++) al[i] = Providers.norm(al[i]);
        this.normAliases = al;
    }

    synchronized Path path() {
        if (path == null && kind == GLYPH) path = PathData.parse(data);
        return path;
    }
}
