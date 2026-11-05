package com.dodiy.passwordmanager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** One saved login. provider is a logo slug from {@link Providers}, or "" for a letter icon. */
final class Entry {
    String id = UUID.randomUUID().toString();
    String provider = "";
    String title = "";
    String username = "";
    String password = "";
    String url = "";
    String notes = "";
    long created = System.currentTimeMillis();
    long updated = created;

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("provider", provider);
        o.put("title", title);
        o.put("username", username);
        o.put("password", password);
        o.put("url", url);
        o.put("notes", notes);
        o.put("created", created);
        o.put("updated", updated);
        return o;
    }

    static Entry fromJson(JSONObject o) {
        Entry e = new Entry();
        e.id = o.optString("id", e.id);
        e.provider = o.optString("provider", "");
        e.title = o.optString("title", "");
        e.username = o.optString("username", "");
        e.password = o.optString("password", "");
        e.url = o.optString("url", "");
        e.notes = o.optString("notes", "");
        e.created = o.optLong("created", e.created);
        e.updated = o.optLong("updated", e.created);
        return e;
    }

    Entry copy() {
        try { return fromJson(toJson()); } catch (JSONException ex) { throw new RuntimeException(ex); }
    }
}
