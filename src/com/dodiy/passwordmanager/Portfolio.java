package com.dodiy.passwordmanager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** A group of logins (e.g. "Office", "Family", "Client A") with an Owner from People. */
final class Portfolio {
    String id = UUID.randomUUID().toString();
    String name = "";
    String ownerId = "";
    String notes = "";
    long updated = System.currentTimeMillis();

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("owner", ownerId);
        o.put("notes", notes);
        o.put("updated", updated);
        return o;
    }

    static Portfolio fromJson(JSONObject o) {
        Portfolio p = new Portfolio();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "");
        p.ownerId = o.optString("owner", "");
        p.notes = o.optString("notes", "");
        p.updated = o.optLong("updated", 0);
        return p;
    }
}
