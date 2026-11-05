package com.dodiy.passwordmanager;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Storage layout (app-private, excluded from cloud backup):
 *   meta.json  salt, PBKDF2 iterations, biometric IV, and "outer"
 *   vault.bin  AES-GCM(vaultKey, entries JSON)
 *
 * outer = Keystore-AES-GCM(inner)            needs a biometric
 * inner = AES-GCM(PBKDF2(master password), vaultKey)   needs the master password
 * so both the fingerprint and the master password are needed to reach vaultKey.
 */
final class Vault {
    private Vault() {}

    static final int ITERATIONS = 310_000;
    static final String BACKUP_FORMAT = "dodiy-password-manager-backup";

    private static File meta(Context c) { return new File(c.getNoBackupFilesDir(), "meta.json"); }
    private static File data(Context c) { return new File(c.getNoBackupFilesDir(), "vault.bin"); }

    static boolean exists(Context c) { return meta(c).exists(); }

    static JSONObject readMeta(Context c) throws IOException, JSONException {
        return new JSONObject(Crypto.str(read(meta(c))));
    }

    // ---- setup ---------------------------------------------------------------------------------

    /** Step 2 of setup, after the biometric prompt authorised {@code bioEncrypt}. Opens the session. */
    static void create(Context c, char[] password, Cipher bioEncrypt) throws Exception {
        byte[] salt = Crypto.random(16);
        byte[] kek = Crypto.deriveKey(password, salt, ITERATIONS);
        byte[] vaultKey = Crypto.random(32);
        byte[] inner = Crypto.seal(kek, vaultKey);
        Crypto.wipe(kek);
        byte[] outer = bioEncrypt.doFinal(inner);
        Session.open(vaultKey, inner, new ArrayList<Entry>());
        save(c);   // data file first, so a vault never exists without its data
        writeMeta(c, salt, ITERATIONS, bioEncrypt.getIV(), outer);
    }

    // ---- unlock --------------------------------------------------------------------------------

    static Cipher unlockCipher(Context c) throws Exception {
        JSONObject m = readMeta(c);
        return BioKey.decryptCipher(Crypto.unb64(m.getString("bioIv")));
    }

    /** Biometric step: returns the password-wrapped key. */
    static byte[] openOuter(Context c, Cipher bioDecrypt) throws Exception {
        JSONObject m = readMeta(c);
        return bioDecrypt.doFinal(Crypto.unb64(m.getString("outer")));
    }

    /** Password step. Throws javax.crypto.AEADBadTagException for a wrong password. Opens the session. */
    static void openInner(Context c, byte[] inner, char[] password) throws Exception {
        byte[] vaultKey = unwrap(c, inner, password);
        ArrayList<Entry> list = parseEntries(Crypto.open(vaultKey, read(data(c))));
        Session.open(vaultKey, inner, list);
    }

    private static byte[] unwrap(Context c, byte[] inner, char[] password) throws Exception {
        JSONObject m = readMeta(c);
        byte[] kek = Crypto.deriveKey(password, Crypto.unb64(m.getString("salt")), m.getInt("iter"));
        try {
            return Crypto.open(kek, inner);
        } finally {
            Crypto.wipe(kek);
        }
    }

    // ---- change master password ----------------------------------------------------------------

    /** Checks the current password against the open session. Slow (PBKDF2), run off the UI thread. */
    static boolean checkPassword(Context c, char[] password) {
        try {
            byte[] k = unwrap(c, Session.inner, password);
            Crypto.wipe(k);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Re-wraps the vault key with a new password; {@code bioEncrypt} must be biometric-authorised. */
    static void changePassword(Context c, char[] newPassword, Cipher bioEncrypt) throws Exception {
        byte[] salt = Crypto.random(16);
        byte[] kek = Crypto.deriveKey(newPassword, salt, ITERATIONS);
        byte[] inner = Crypto.seal(kek, Session.key);
        Crypto.wipe(kek);
        writeMeta(c, salt, ITERATIONS, bioEncrypt.getIV(), bioEncrypt.doFinal(inner));
        Session.inner = inner;
    }

    // ---- entries -------------------------------------------------------------------------------

    static void save(Context c) throws Exception {
        writeAtomic(data(c), Crypto.seal(Session.key, Crypto.utf8(entriesJson(Session.entries))));
    }

    static String entriesJson(List<Entry> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (Entry e : list) a.put(e.toJson());
        JSONObject o = new JSONObject();
        o.put("v", 1);
        o.put("entries", a);
        return o.toString();
    }

    static ArrayList<Entry> parseEntries(byte[] json) throws JSONException {
        JSONArray a = new JSONObject(Crypto.str(json)).getJSONArray("entries");
        ArrayList<Entry> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(Entry.fromJson(a.getJSONObject(i)));
        return out;
    }

    // ---- reset ---------------------------------------------------------------------------------

    static void reset(Context c) {
        Session.lock();
        //noinspection ResultOfMethodCallIgnored
        meta(c).delete();
        //noinspection ResultOfMethodCallIgnored
        data(c).delete();
        BioKey.delete();
    }

    // ---- encrypted backup file (password only, so it can be restored on a new phone) ------------

    static byte[] exportBackup(List<Entry> list, char[] password) throws Exception {
        byte[] salt = Crypto.random(16);
        byte[] k = Crypto.deriveKey(password, salt, ITERATIONS);
        byte[] ct = Crypto.seal(k, Crypto.utf8(entriesJson(list)));
        Crypto.wipe(k);
        JSONObject o = new JSONObject();
        o.put("format", BACKUP_FORMAT);
        o.put("v", 1);
        o.put("kdf", "PBKDF2-HMAC-SHA256");
        o.put("iter", ITERATIONS);
        o.put("salt", Crypto.b64(salt));
        o.put("data", Crypto.b64(ct));
        return Crypto.utf8(o.toString(2));
    }

    /** Throws AEADBadTagException for a wrong password, JSONException for a file that is not a backup. */
    static ArrayList<Entry> importBackup(byte[] file, char[] password) throws Exception {
        JSONObject o = new JSONObject(Crypto.str(file));
        if (!BACKUP_FORMAT.equals(o.optString("format"))) throw new JSONException("Not a Dodiy Password Manager backup");
        byte[] k = Crypto.deriveKey(password, Crypto.unb64(o.getString("salt")), o.getInt("iter"));
        try {
            return parseEntries(Crypto.open(k, Crypto.unb64(o.getString("data"))));
        } finally {
            Crypto.wipe(k);
        }
    }

    /** Adds new entries and replaces older copies of the same entry. Returns how many changed. */
    static int merge(List<Entry> into, List<Entry> incoming) {
        int n = 0;
        for (Entry in : incoming) {
            int found = -1;
            for (int i = 0; i < into.size(); i++) if (into.get(i).id.equals(in.id)) { found = i; break; }
            if (found < 0) { into.add(in); n++; }
            else if (in.updated > into.get(found).updated) { into.set(found, in); n++; }
        }
        return n;
    }

    // ---- files ---------------------------------------------------------------------------------

    private static void writeMeta(Context c, byte[] salt, int iter, byte[] bioIv, byte[] outer) throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        o.put("salt", Crypto.b64(salt));
        o.put("iter", iter);
        o.put("bioIv", Crypto.b64(bioIv));
        o.put("outer", Crypto.b64(outer));
        writeAtomic(meta(c), Crypto.utf8(o.toString()));
    }

    static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try { return readAll(in); } finally { in.close(); }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }

    private static void writeAtomic(File f, byte[] bytes) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(bytes);
            out.getFD().sync();
        } finally {
            out.close();
        }
        if (!tmp.renameTo(f)) throw new IOException("Could not save " + f.getName());
    }

    static boolean isWrongPassword(Throwable t) {
        for (; t != null; t = t.getCause()) if (t instanceof javax.crypto.AEADBadTagException) return true;
        return false;
    }

    static boolean isGeneral(Throwable t) { return t instanceof GeneralSecurityException; }
}
