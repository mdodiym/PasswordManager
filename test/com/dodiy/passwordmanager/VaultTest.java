package com.dodiy.passwordmanager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/** Plain-JVM checks of the crypto, backup and logo-path code. Run with test/run.sh. */
public final class VaultTest {
    static int passed, failed;

    static void check(String name, boolean ok) {
        if (ok) passed++; else { failed++; System.out.println("FAIL: " + name); }
    }

    static boolean catalogOk() {
        java.util.Set<String> types = new java.util.HashSet<>(Arrays.asList(Field.TEXT, Field.HIDDEN, Field.MULTILINE, Field.HIDDEN_MULTILINE,
                Field.EMAIL, Field.PHONE, Field.URL, Field.NUMBER, Field.PIN, Field.DATE, Field.TOTP));
        for (String[] f : FieldCatalog.FIELDS) if (f.length != 3 || !types.contains(f[2])) return false;
        return true;
    }

    static VaultData data(ArrayList<Entry> l) {
        VaultData d = new VaultData();
        d.entries.addAll(l);
        return d;
    }

    public static void main(String[] args) throws Exception {
        // AES-GCM
        byte[] k = Crypto.random(32);
        byte[] blob = Crypto.seal(k, Crypto.utf8("hello"));
        check("seal/open round trip", "hello".equals(Crypto.str(Crypto.open(k, blob))));
        check("random IV each time", !Arrays.equals(blob, Crypto.seal(k, Crypto.utf8("hello"))));
        try { Crypto.open(Crypto.random(32), blob); check("wrong key rejected", false); }
        catch (Exception e) { check("wrong key -> AEADBadTag", Vault.isWrongPassword(e)); }
        blob[blob.length - 1] ^= 1;
        try { Crypto.open(k, blob); check("tamper rejected", false); } catch (Exception e) { check("tamper -> AEADBadTag", Vault.isWrongPassword(e)); }

        // PBKDF2
        byte[] salt = Crypto.random(16);
        check("kdf deterministic", Arrays.equals(Crypto.deriveKey("pw12345678".toCharArray(), salt, 1000), Crypto.deriveKey("pw12345678".toCharArray(), salt, 1000)));
        check("kdf differs by password", !Arrays.equals(Crypto.deriveKey("pw12345678".toCharArray(), salt, 1000), Crypto.deriveKey("pw12345679".toCharArray(), salt, 1000)));
        long t0 = System.nanoTime();
        Crypto.deriveKey("pw12345678".toCharArray(), salt, Vault.ITERATIONS);
        System.out.printf("PBKDF2 %d iterations on this JVM: %d ms%n", Vault.ITERATIONS, (System.nanoTime() - t0) / 1_000_000);

        // Old 0.1.0002 layering (fingerprint AND password), with a software key standing in for the Keystore key
        byte[] bioKey = Crypto.random(32);
        char[] master = "Correct horse 9!".toCharArray();
        byte[] vaultKey = Crypto.random(32);
        byte[] inner = Crypto.seal(Crypto.deriveKey(master, salt, 1000), vaultKey);
        byte[] outer = Crypto.seal(bioKey, inner);
        byte[] gotInner = Crypto.open(bioKey, outer);
        check("both factors open the vault key", Arrays.equals(vaultKey, Crypto.open(Crypto.deriveKey(master, salt, 1000), gotInner)));
        try { Crypto.open(Crypto.deriveKey("wrong password".toCharArray(), salt, 1000), gotInner); check("wrong master rejected", false); }
        catch (Exception e) { check("wrong master -> AEADBadTag", Vault.isWrongPassword(e)); }
        try { Crypto.open(Crypto.deriveKey(master, salt, 1000), outer); check("password alone (no biometric) fails", false); }
        catch (Exception e) { check("password alone cannot open outer layer", true); }

        // Entries JSON + backup
        ArrayList<Entry> list = new ArrayList<>();
        Entry a = new Entry(); a.title = "Google"; a.provider = "google"; a.username = "me@gmail.com"; a.password = "p@ss \"quoted\" ✓"; a.notes = "line1\nline2";
        Entry b = new Entry(); b.title = "Work GitHub"; b.provider = "github"; b.username = "dodiy";
        list.add(a); list.add(b);
        ArrayList<Entry> back = Vault.parse(Crypto.utf8(Vault.toJson(data(list)))).entries;
        check("json round trip size", back.size() == 2);
        check("json round trip fields", back.get(0).password.equals(a.password) && back.get(0).notes.equals(a.notes) && back.get(1).provider.equals("github"));
        byte[] file = Vault.exportBackup(data(list), "backup-pass".toCharArray());
        check("backup does not contain plaintext", !Crypto.str(file).contains("me@gmail.com"));
        ArrayList<Entry> restored = Vault.importBackup(file, "backup-pass".toCharArray()).entries;
        check("backup restore", restored.size() == 2 && restored.get(0).username.equals("me@gmail.com"));
        try { Vault.importBackup(file, "nope-nope".toCharArray()); check("backup wrong password", false); }
        catch (Exception e) { check("backup wrong password -> AEADBadTag", Vault.isWrongPassword(e)); }
        try { Vault.importBackup(Crypto.utf8("{\"x\":1}"), "x".toCharArray()); check("non-backup rejected", false); }
        catch (org.json.JSONException e) { check("non-backup -> JSONException", true); }

        // merge
        ArrayList<Entry> mine = new ArrayList<>();
        Entry a2 = a.copy(); a2.updated = a.updated - 1000; a2.password = "old";
        mine.add(a2);
        int n = Vault.merge(mine, restored);
        check("merge adds new and replaces older", n == 2 && mine.size() == 2 && mine.get(0).password.equals(a.password));
        check("merge is idempotent", Vault.merge(mine, restored) == 0);

        // extra fields survive save / backup
        Entry withFields = new Entry(); withFields.title = "Maybank";
        Field pin = new Field("PIN", Field.PIN); pin.value = "123456"; withFields.fields.add(pin);
        Field q = new Field("Security question", Field.TEXT); q.value = "First school?"; withFields.fields.add(q);
        ArrayList<Entry> one = new ArrayList<>(); one.add(withFields);
        Entry wf = Vault.parse(Crypto.utf8(Vault.toJson(data(one)))).entries.get(0);
        check("fields round trip", wf.fields.size() == 2 && wf.fields.get(0).type.equals(Field.PIN) && wf.fields.get(0).value.equals("123456") && wf.fields.get(0).isHidden());
        Entry old = Vault.parse(Crypto.utf8("{\"v\":1,\"entries\":[{\"id\":\"x\",\"title\":\"Old\"}]}")).entries.get(0);
        check("entries from 0.1.0002 load with no fields", old.fields.isEmpty() && old.title.equals("Old"));
        check("field catalog types valid", catalogOk());
        check("old vault has no owner fields", old.ownerId.isEmpty() && old.portfolioId.isEmpty());

        // People, portfolios, Owner / Assignee / Project Manager
        VaultData vd = new VaultData();
        Person ali = new Person(); ali.name = "Ali"; ali.email = "ali@x.my";
        Person siti = new Person(); siti.name = "Siti"; siti.role = "Finance";
        vd.people.add(ali); vd.people.add(siti);
        Portfolio office = new Portfolio(); office.name = "Office"; office.ownerId = ali.id;
        vd.portfolios.add(office);
        Entry bank = new Entry(); bank.title = "Maybank"; bank.portfolioId = office.id; bank.ownerId = ali.id; bank.assigneeId = siti.id; bank.managerId = ali.id;
        vd.entries.add(bank);
        VaultData vd2 = Vault.parse(Crypto.utf8(Vault.toJson(vd)));
        check("people/portfolios round trip", vd2.people.size() == 2 && vd2.portfolios.get(0).ownerId.equals(ali.id) && vd2.people.get(1).role.equals("Finance"));
        Entry b2 = vd2.entries.get(0);
        check("login owner/assignee/manager round trip", b2.portfolioId.equals(office.id) && b2.ownerId.equals(ali.id) && b2.assigneeId.equals(siti.id) && b2.managerId.equals(ali.id));
        check("uses counts logins and portfolios", Vault.uses(vd2, ali.id) == 2 && Vault.uses(vd2, siti.id) == 1);
        byte[] bf = Vault.exportBackup(vd2, "backup-pass".toCharArray());
        VaultData fromBackup = Vault.importBackup(bf, "backup-pass".toCharArray());
        VaultData empty = new VaultData();
        Vault.merge(empty, fromBackup);
        check("backup restores people and portfolios", empty.people.size() == 2 && empty.portfolios.size() == 1 && empty.entries.size() == 1);
        check("removePerson clears references", Vault.removePerson(vd2, ali.id) == 2 && vd2.people.size() == 1
                && vd2.entries.get(0).ownerId.isEmpty() && vd2.entries.get(0).managerId.isEmpty()
                && vd2.entries.get(0).assigneeId.equals(siti.id) && vd2.portfolios.get(0).ownerId.isEmpty());
        Vault.removePortfolio(vd2, office.id);
        check("removePortfolio keeps logins", vd2.portfolios.isEmpty() && vd2.entries.size() == 1 && vd2.entries.get(0).portfolioId.isEmpty());

        // 2FA codes, RFC 6238 test vectors (SHA-1)
        Totp t8 = Totp.parse("otpauth://totp/Test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&digits=8&period=30");
        check("totp 59", t8 != null && t8.code(59_000L).equals("94287082"));
        check("totp 1111111109", t8.code(1111111109_000L).equals("07081804"));
        check("totp 2000000000", t8.code(2000000000_000L).equals("69279037"));
        Totp t6 = Totp.parse("gezd gnbv gy3t qojq gezd gnbv gy3t qojq");
        check("totp plain secret, 6 digits", t6 != null && t6.code(59_000L).equals("287082") && t6.secondsLeft(59_000L) == 1);
        check("totp rejects junk", Totp.parse("not a secret!") == null && Totp.parse("") == null);

        // a 0.1.0002 vault's password-wrapped key is exactly what the new format stores as "pw"
        byte[] legacyInner = Crypto.seal(Crypto.deriveKey(master, salt, 1000), vaultKey);
        check("upgrade keeps password unlock", Arrays.equals(vaultKey, Crypto.open(Crypto.deriveKey(master, salt, 1000), legacyInner)));

        // generator
        for (int i = 0; i < 200; i++) {
            String p = PasswordGen.generate(12, true, true, true, true);
            boolean u = false, l = false, d = false, s = false;
            for (char c : p.toCharArray()) {
                if (PasswordGen.UPPER.indexOf(c) >= 0) u = true;
                else if (PasswordGen.LOWER.indexOf(c) >= 0) l = true;
                else if (PasswordGen.DIGITS.indexOf(c) >= 0) d = true;
                else if (PasswordGen.SYMBOLS.indexOf(c) >= 0) s = true;
            }
            if (p.length() != 12 || !(u && l && d && s)) { check("generator: " + p, false); break; }
        }
        check("generator digits only", PasswordGen.generate(10, false, false, true, false).matches("[2-9]{10}"));
        check("strength", PasswordGen.strength("short").startsWith("Too short") && PasswordGen.strength("Abcdefgh1234!x").equals("Strong"));

        // every bundled logo parses, and its command count matches the source
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8));
        String line;
        int icons = 0, bad = 0;
        while ((line = r.readLine()) != null) {
            String[] f = line.split("\t", -1);
            if (f.length != 5 || !f[2].matches("[0-9A-F]{6}")) { bad++; continue; }
            int cmds = f[4].replaceAll("[^MLCZ]", "").length();
            try {
                android.graphics.Path p = PathData.parse(f[4]);
                if (p.count != cmds || cmds == 0) { bad++; System.out.println("path mismatch " + f[0]); }
            } catch (RuntimeException e) { bad++; System.out.println("path error " + f[0] + ": " + e); }
            icons++;
        }
        r.close();
        check("all " + icons + " logo paths parse", bad == 0 && icons > 3000);
        android.graphics.Path g = PathData.parse("M1.5 -2L3 4C1 2 3 4 5 -6.25Z");
        check("parser values", g.log.toString().equals("M1.5,-2.0 L3.0,4.0 C5.0,-6.25 Z "));

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
