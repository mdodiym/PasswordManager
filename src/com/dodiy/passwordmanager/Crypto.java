package com.dodiy.passwordmanager;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Pure-Java crypto helpers (no Android APIs, so they can be unit tested on a desktop JVM). */
final class Crypto {
    private Crypto() {}

    static final SecureRandom RNG = new SecureRandom();
    static final int IV_LEN = 12;
    static final int TAG_BITS = 128;

    static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    /** PBKDF2-HMAC-SHA256 to a 256-bit key. */
    static byte[] deriveKey(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /** AES-256-GCM. Returns iv || ciphertext+tag. */
    static byte[] seal(byte[] key, byte[] plain) throws GeneralSecurityException {
        byte[] iv = random(IV_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(ct, 0, out, IV_LEN, ct.length);
        return out;
    }

    /** Opposite of {@link #seal}. Throws AEADBadTagException when the key is wrong or data was changed. */
    static byte[] open(byte[] key, byte[] blob) throws GeneralSecurityException {
        if (blob == null || blob.length < IV_LEN + 16) throw new GeneralSecurityException("Data is too short");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN));
        return c.doFinal(blob, IV_LEN, blob.length - IV_LEN);
    }

    static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    static byte[] unb64(String s) { return Base64.getDecoder().decode(s); }
    static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static String str(byte[] b) { return new String(b, StandardCharsets.UTF_8); }

    static void wipe(byte[] b) { if (b != null) Arrays.fill(b, (byte) 0); }
    static void wipe(char[] c) { if (c != null) Arrays.fill(c, '\0'); }
}
