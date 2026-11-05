package com.dodiy.passwordmanager;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;

import javax.crypto.Cipher;

/** Thin wrapper over the platform BiometricPrompt (API 28+). */
final class Bio {
    private Bio() {}

    interface Callback {
        void onSuccess(Cipher cipher);
        /** cancelled is true when the user dismissed the prompt themselves. */
        void onError(CharSequence message, boolean cancelled);
    }

    /** Null when a strong biometric is ready to use, otherwise a reason to show the user. */
    @SuppressWarnings("deprecation")
    static String unavailableReason(Activity a) {
        int r;
        if (Build.VERSION.SDK_INT >= 30) {
            r = a.getSystemService(BiometricManager.class).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        } else if (Build.VERSION.SDK_INT == 29) {
            r = a.getSystemService(BiometricManager.class).canAuthenticate();
        } else {
            android.hardware.fingerprint.FingerprintManager fm = a.getSystemService(android.hardware.fingerprint.FingerprintManager.class);
            if (fm == null || !a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) r = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
            else if (!fm.hasEnrolledFingerprints()) r = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED;
            else r = BiometricManager.BIOMETRIC_SUCCESS;
        }
        switch (r) {
            case BiometricManager.BIOMETRIC_SUCCESS: return null;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return "No fingerprint is set up on this phone. Add one in Settings → Security, then come back.";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return "The fingerprint sensor is busy or unavailable right now. Try again in a moment.";
            default:
                return "This phone has no strong biometric sensor (fingerprint, or a face unlock that Android rates as strong). It is required to protect the vault.";
        }
    }

    static CancellationSignal prompt(final Activity a, String title, String subtitle, Cipher cipher, final Callback cb) {
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton("Cancel", a.getMainExecutor(), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        App.leavingOnPurpose = false;
                        cb.onError("Cancelled", true);
                    }
                });
        if (Build.VERSION.SDK_INT >= 29) b.setConfirmationRequired(false);
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        CancellationSignal cancel = new CancellationSignal();
        App.leavingOnPurpose = true;
        b.build().authenticate(new BiometricPrompt.CryptoObject(cipher), cancel, a.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        App.leavingOnPurpose = false;
                        cb.onSuccess(result.getCryptoObject().getCipher());
                    }
                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        App.leavingOnPurpose = false;
                        boolean user = code == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                || code == BiometricPrompt.BIOMETRIC_ERROR_CANCELED;
                        cb.onError(msg, user);
                    }
                });
        return cancel;
    }
}
