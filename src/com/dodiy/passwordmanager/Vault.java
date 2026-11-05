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
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

/**
 * Storage layout (app-private, excluded from cloud backup):
 *   meta.json  v2: salt, PBKDF2 iterations, "pw" and optionally "bio" + "bioIv"
 *   vault.bin  AES-GCM(vaultKey, entries JSON)
 *
 * pw  = AES-GCM(PBKDF2(master password), vaultKey)   opens the vault with the master password
 * bio = Keystore-AES-GCM(vaultKey)                     opens the vault with a fingerprint
 * Either one unlocks the vault. Version 0.1.0002 (meta v1) needed both; see the legacy* methods.
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

    static boolean isLegacy(Context c) {
        try { return readMeta(c).optInt("v", 1) < 2; } catch (Exception e) { return false; }
    }

    static boolean bioEnabled(Context c) {
        try {
            JSONObject m = readMeta(c);
            return m.optInt("v", 1) >= 2 && m.has("bio");
        } catch (Exception e) {
            return false;
        }
    }

    // ---- setup ---------------------------------------------------------------------------------

    /** Creates a vault that opens with the master password, and opens the session. */
    static void create(Context c, char[] password) throws Exception {
        byte[] salt = Crypto.random(16);
        byte[] kek = Crypto.deriveKey(password, salt, ITERATIONS);
        byte[] vaultKey = Crypto.random(32);
        byte[] pw = Crypto.seal(kek, vaultKey);
        Crypto.wipe(kek);
        Session.open(vaultKey, pw, new ArrayList<Entry>());
        save(c);   // data file first, so a vault never exists without its data
        JSONObject m = new JSONObject();
        m.put("v", 2);
        m.put("salt", Crypto.b64(salt));
        m.put("iter", ITERATIONS);
        m.put("pw", Crypto.b64(pw));
        writeMeta(c, m);
    }

    // ---- unlock with the master password -------------------------------------------------------

    /** Throws javax.crypto.AEADBadTagException for a wrong password. Opens the session. Slow (PBKDF2). */
    static void unlockWithPassword(Context c, char[] password) throws Exception {
        JSONObject m = readMeta(c);
        byte[] pw = Crypto.unb64(m.getString("pw"));
        byte[] vaultKey = unwrap(m, pw, password);
        Session.open(vaultKey, pw, parseEntries(Crypto.open(vaultKey, read(data(c)))));
    }

    private static byte[] unwrap(JSONObject m, byte[] wrapped, char[] password) throws Exception {
        byte[] kek = Crypto.deriveKey(password, Crypto.unb64(m.getString("salt")), m.getInt("iter"));
        try {
            return Crypto.open(kek, wrapped);
        } finally {
            Crypto.wipe(kek);
        }
    }

    // ---- unlock with a fingerprint -------------------------------------------------------------

    /** Throws KeyPermanentlyInvalidatedException when Android removed the key (fingerprints changed). */
    static Cipher bioUnlockCipher(Context c) throws Exception {
        return BioKey.decryptCipher(Crypto.unb64(readMeta(c).getString("bioIv")));
    }

    static void unlockWithBio(Context c, Cipher authorised) throws Exception {
        JSONObject m = readMeta(c);
        byte[] vaultKey = authorised.doFinal(Crypto.unb64(m.getString("bio")));
        Session.open(vaultKey, Crypto.unb64(m.getString("pw")), parseEntries(Crypto.open(vaultKey, read(data(c)))));
    }

    /** Fresh Keystore key + cipher; show it in a biometric prompt, then call {@link #enableBio}. */
    static Cipher prepareEnableBio() throws Exception {
        BioKey.delete();
        BioKey.create();
        return BioKey.encryptCipher();
    }

    static void enableBio(Context c, Cipher authorised) throws Exception {
        JSONObject m = readMeta(c);
        m.put("bio", Crypto.b64(authorised.doFinal(Session.key)));
        m.put("bioIv", Crypto.b64(authorised.getIV()));
        writeMeta(c, m);
    }

    static void disableBio(Context c) throws Exception {
        JSONObject m = readMeta(c);
        m.remove("bio");
        m.remove("bioIv");
        writeMeta(c, m);
        BioKey.delete();
    }

    // ---- vaults made by 0.1.0002 (fingerprint AND password) -------------------------------------

    static Cipher legacyCipher(Context c) throws Exception {
        return BioKey.decryptCipher(BioKey.LEGACY_ALIAS, Crypto.unb64(readMeta(c).getString("bioIv")));
    }

    /** Fingerprint step of the old format: returns the password-wrapped key. */
    static byte[] legacyOpenOuter(Context c, Cipher authorised) throws Exception {
        return authorised.doFinal(Crypto.unb64(readMeta(c).getString("outer")));
    }

    /** Password step of the old format; on success rewrites the vault in the new format and opens it. */
    static void legacyUnlockAndUpgrade(Context c, byte[] inner, char[] password) throws Exception {
        JSONObject m = readMeta(c);
        byte[] vaultKey = unwrap(m, inner, password);
        ArrayList<Entry> list = parseEntries(Crypto.open(vaultKey, read(data(c))));
        JSONObject n = new JSONObject();
        n.put("v", 2);
        n.put("salt", m.getString("salt"));
        n.put("iter", m.getInt("iter"));
        n.put("pw", Crypto.b64(inner));       // same PBKDF2(password) wrapping, so it is the new "pw"
        writeMeta(c, n);
        BioKey.delete(BioKey.LEGACY_ALIAS);
        Session.open(vaultKey, inner, list);
    }

    // ---- change master password ----------------------------------------------------------------

    /** Checks the current password against the open session. Slow (PBKDF2), run off the UI thread. */
    static boolean checkPassword(Context c, char[] password) {
        try {
            byte[] k = unwrap(readMeta(c), Session.inner, password);
            Crypto.wipe(k);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Re-wraps the vault key with a new password. Fingerprint unlock keeps working. */
    static void changePassword(Context c, char[] newPassword) throws Exception {
        JSONObject m = readMeta(c);
        byte[] salt = Crypto.random(16);
        byte[] kek = Crypto.deriveKey(newPassword, salt, ITERATIONS);
        byte[] pw = Crypto.seal(kek, Session.key);
        Crypto.wipe(kek);
        m.put("salt", Crypto.b64(salt));
        m.put("iter", ITERATIONS);
        m.put("pw", Crypto.b64(pw));
        writeMeta(c, m);
        Session.inner = pw;
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
        BioKey.delete(BioKey.LEGACY_ALIAS);
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

    private static void writeMeta(Context c, JSONObject m) throws Exception {
        writeAtomic(meta(c), Crypto.utf8(m.toString()));
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
}
