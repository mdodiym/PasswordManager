package com.dodiy.passwordmanager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** Someone in the People list: can be a portfolio Owner, or a login's Owner, Assignee or Project Manager. */
final class Person {
    String id = UUID.randomUUID().toString();
    String name = "";
    String email = "";
    String phone = "";
    String role = "";     // free text, e.g. "Finance", "Family"
    long updated = System.currentTimeMillis();

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("email", email);
        o.put("phone", phone);
        o.put("role", role);
        o.put("updated", updated);
        return o;
    }

    static Person fromJson(JSONObject o) {
        Person p = new Person();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "");
        p.email = o.optString("email", "");
        p.phone = o.optString("phone", "");
        p.role = o.optString("role", "");
        p.updated = o.optLong("updated", 0);
        return p;
    }

    String subtitle() {
        StringBuilder b = new StringBuilder();
        for (String s : new String[]{role, email, phone}) {
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(s);
        }
        return b.toString();
    }
}
