package com.dodiy.passwordmanager;

import org.json.JSONException;
import org.json.JSONObject;

/** An extra field on a login, e.g. "PIN" or "Security answer". */
final class Field {
    static final String TEXT = "text";
    static final String HIDDEN = "hidden";            // masked, e.g. PIN, CVV, API key
    static final String MULTILINE = "multiline";
    static final String HIDDEN_MULTILINE = "hidden_multiline";   // e.g. recovery codes, SSH key
    static final String EMAIL = "email";
    static final String PHONE = "phone";
    static final String URL = "url";
    static final String NUMBER = "number";
    static final String PIN = "pin";                  // hidden digits
    static final String DATE = "date";
    static final String TOTP = "totp";                // 2FA secret; the app shows the current 6-digit code

    String label = "";
    String value = "";
    String type = TEXT;

    Field() {}

    Field(String label, String type) {
        this.label = label;
        this.type = type;
    }

    boolean isHidden() {
        return HIDDEN.equals(type) || HIDDEN_MULTILINE.equals(type) || PIN.equals(type) || TOTP.equals(type);
    }

    boolean isMultiline() {
        return MULTILINE.equals(type) || HIDDEN_MULTILINE.equals(type);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("label", label);
        o.put("value", value);
        o.put("type", type);
        return o;
    }

    static Field fromJson(JSONObject o) {
        Field f = new Field();
        f.label = o.optString("label", "");
        f.value = o.optString("value", "");
        f.type = o.optString("type", TEXT);
        return f;
    }
}
