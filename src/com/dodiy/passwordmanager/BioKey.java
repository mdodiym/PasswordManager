package com.dodiy.passwordmanager;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES key inside the Android Keystore (hardware backed where available). Every use needs a fresh
 * strong biometric (fingerprint, or face on phones where face counts as strong). It wraps the vault
 * key so a fingerprint alone can unlock the vault. Android deletes the key when fingerprints are
 * added or removed; the master password still works then, and fingerprint unlock can be switched on again.
 */
final class BioKey {
    private BioKey() {}

    static final String ALIAS = "dodiy_pm_bio_v2";
    /** Version 0.1.0002 needed fingerprint AND password; only used to move such vaults to the new format. */
    static final String LEGACY_ALIAS = "dodiy_pm_vault_bio_v1";

    private static KeyStore store() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks;
    }

    static void create() throws Exception {
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= 30) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        }
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(b.build());
        g.generateKey();
    }

    static SecretKey key() throws Exception { return key(ALIAS); }

    static SecretKey key(String alias) throws Exception {
        return (SecretKey) store().getKey(alias, null);
    }

    static boolean exists() {
        try { return key() != null; } catch (Exception e) { return false; }
    }

    /** Cipher to encrypt with; usable only after a biometric prompt with this cipher succeeds. */
    static Cipher encryptCipher() throws Exception {
        SecretKey k = key();
        if (k == null) throw new IllegalStateException("Biometric key is missing");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c;
    }

    /** Cipher to decrypt with. Throws KeyPermanentlyInvalidatedException if Android wiped the key. */
    static Cipher decryptCipher(byte[] iv) throws Exception { return decryptCipher(ALIAS, iv); }

    static Cipher decryptCipher(String alias, byte[] iv) throws Exception {
        SecretKey k = key(alias);
        if (k == null) throw new android.security.keystore.KeyPermanentlyInvalidatedException("Biometric key is missing");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
        return c;
    }

    static void delete() { delete(ALIAS); }

    static void delete(String alias) {
        try { store().deleteEntry(alias); } catch (Exception ignored) {}
    }
}
