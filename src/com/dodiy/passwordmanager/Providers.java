package com.dodiy.passwordmanager;

import android.content.Context;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** All bundled logos: ~3,400 from Simple Icons (assets/providers.txt) plus a few made here. */
final class Providers {
    private Providers() {}

    private static List<Provider> all;
    private static HashMap<String, Provider> bySlug;

    /** Shown first in the picker. */
    static final String[] POPULAR = {
            "google", "microsoft", "apple", "facebook", "github", "x", "instagram", "linkedin", "amazon",
            "yahoo", "gitlab", "discord", "slack", "telegram", "whatsapp", "tiktok", "reddit", "netflix",
            "spotify", "paypal", "dropbox", "steam", "epicgames", "playstation", "xbox", "proton", "gmail",
            "icloud", "okta", "auth0", "zoom", "youtube", "twitch", "snapchat", "pinterest", "shopee", "grab",
            "binance", "coinbase", "openai", "anthropic", "adobe", "atlassian", "bitbucket", "notion", "figma",
            "canva", "maybank", "cimb", "lazada"
    };

    /** Brands missing from Simple Icons (removed at the brand's request). Shown as brand-coloured badges. */
    private static final String[][] EXTRAS = {
            // slug, title, hex, letters, aliases
            {"microsoft", "Microsoft", "737373", "", "Microsoft account|Live|Hotmail|MSN|Entra|Azure AD"},
            {"linkedin", "LinkedIn", "0A66C2", "in", ""},
            {"amazon", "Amazon", "FF9900", "a", "Amazon.com"},
            {"amazonwebservices", "Amazon Web Services", "232F3E", "aws", "AWS"},
            {"yahoo", "Yahoo", "6001D2", "Y!", "Yahoo Mail"},
            {"slack", "Slack", "4A154B", "S", ""},
            {"xbox", "Xbox", "107C10", "X", "Xbox Live"},
            {"nintendo", "Nintendo", "E60012", "N", "Nintendo Account"},
            {"adobe", "Adobe", "FA0F00", "A", "Adobe ID|Creative Cloud"},
            {"salesforce", "Salesforce", "00A1E0", "SF", ""},
            {"openai", "OpenAI", "10A37F", "AI", "ChatGPT"},
            {"microsoftoutlook", "Microsoft Outlook", "0078D4", "O", "Outlook|Hotmail"},
            {"microsoftteams", "Microsoft Teams", "6264A7", "T", "Teams"},
            {"microsoftonedrive", "Microsoft OneDrive", "0078D4", "OD", "OneDrive"},
            {"microsoftazure", "Microsoft Azure", "0078D4", "Az", "Azure"},
            {"microsoft365", "Microsoft 365", "D83B01", "365", "Office 365|Office"},
            {"skype", "Skype", "00AFF0", "S", ""},
            {"oracle", "Oracle", "F80000", "O", ""},
            {"ibm", "IBM", "0F62FE", "IBM", ""},
            {"canva", "Canva", "00C4CC", "C", ""},
            {"maybank", "Maybank", "FFC800", "M", "Maybank2u|MAE"},
            {"cimb", "CIMB", "EC1C24", "C", "CIMB Clicks|OCTO"},
            {"lazada", "Lazada", "0F146D", "L", ""},
            {"walmart", "Walmart", "0071CE", "W", ""},
            {"disneyplus", "Disney+", "113CCF", "D+", "Disney Plus|Hotstar"},
            {"primevideo", "Prime Video", "00A8E1", "P", "Amazon Prime"},
            {"hulu", "Hulu", "1CE783", "h", ""},
            {"metamask", "MetaMask", "F6851B", "M", ""},
            {"bing", "Microsoft Bing", "008373", "b", "Bing"},
            {"twilio", "Twilio", "F22F46", "T", "Authy"},
            {"agoda", "Agoda", "5542F6", "a", ""},
            {"touchngo", "Touch 'n Go eWallet", "005ABB", "TnG", "TNG|Touch n Go"},
            {"publicbank", "Public Bank", "D7262D", "PB", "PBe"},
            {"rhb", "RHB", "005BAA", "RHB", ""},
            {"hongleongbank", "Hong Leong Bank", "002D74", "HL", "HLB Connect"},
            {"bankislam", "Bank Islam", "E20E78", "BI", "GO by Bank Islam"},
            {"myeg", "MyEG", "0057A8", "EG", ""},
            {"mysejahtera", "MySejahtera", "1E88E5", "MS", ""},
            {"lhdn", "LHDN MyTax", "1A3C8C", "LH", "MyTax|Hasil"},
            {"kwsp", "KWSP i-Akaun", "0B5BA8", "KW", "EPF|i-Akaun"},
    };

    static synchronized List<Provider> all(Context c) {
        if (all == null) load(c);
        return all;
    }

    static void preload(final Context c) {
        new Thread("providers") {
            @Override public void run() { all(c.getApplicationContext()); }
        }.start();
    }

    static Provider get(Context c, String slug) {
        if (slug == null || slug.isEmpty()) return null;
        all(c);
        return bySlug.get(slug);
    }

    static List<Provider> popular(Context c) {
        ArrayList<Provider> out = new ArrayList<>();
        for (String s : POPULAR) {
            Provider p = get(c, s);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Exact match of a typed title against a provider name, slug or alias ("gmail", "Twitter" ...). */
    static Provider match(Context c, String title) {
        String t = norm(title);
        if (t.length() < 2) return null;
        all(c);
        Provider p = bySlug.get(t);
        if (p != null) return p;
        for (Provider q : all) {
            if (q.normTitle.equals(t)) return q;
        }
        for (Provider q : all) {
            for (String a : q.normAliases) if (a.equals(t)) return q;
        }
        return null;
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace("+", "plus").replaceAll("[^a-z0-9]", "");
    }

    private static void load(Context c) {
        ArrayList<Provider> list = new ArrayList<>(3600);
        HashMap<String, Provider> map = new HashMap<>(5000);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("providers.txt"), StandardCharsets.UTF_8), 1 << 16);
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.split("\t", -1);
                    if (f.length < 5) continue;
                    Provider p = new Provider(f[0], f[1], Color.parseColor("#" + f[2]), f[3], Provider.GLYPH, f[4]);
                    list.add(p);
                    map.put(p.slug, p);
                }
            } finally {
                r.close();
            }
        } catch (Exception e) {
            android.util.Log.e("Providers", "Could not load logos", e);
        }
        for (String[] x : EXTRAS) {
            if (map.containsKey(x[0])) continue;
            int kind = "microsoft".equals(x[0]) ? Provider.MICROSOFT : Provider.LETTERS;
            Provider p = new Provider(x[0], x[1], Color.parseColor("#" + x[2]), x[4], kind, x[3]);
            list.add(p);
            map.put(p.slug, p);
        }
        Collections.sort(list, new Comparator<Provider>() {
            @Override public int compare(Provider a, Provider b) { return a.title.compareToIgnoreCase(b.title); }
        });
        bySlug = map;
        all = Collections.unmodifiableList(list);
    }
}
