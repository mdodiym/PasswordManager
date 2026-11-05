package com.dodiy.passwordmanager;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 one-time codes from a base32 secret or an otpauth:// link (as shown under a 2FA QR code). */
final class Totp {
    final byte[] key;
    final String algorithm;   // HmacSHA1 / HmacSHA256 / HmacSHA512
    final int digits;
    final int period;

    private Totp(byte[] key, String algorithm, int digits, int period) {
        this.key = key;
        this.algorithm = algorithm;
        this.digits = digits;
        this.period = period;
    }

    /** Null if the text is not a usable secret. */
    static Totp parse(String text) {
        if (text == null) return null;
        String s = text.trim();
        String alg = "HmacSHA1";
        int digits = 6, period = 30;
        try {
            if (s.toLowerCase(Locale.ROOT).startsWith("otpauth://")) {
                int q = s.indexOf('?');
                if (q < 0) return null;
                String secret = null;
                for (String kv : s.substring(q + 1).split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq < 0) continue;
                    String k = kv.substring(0, eq).toLowerCase(Locale.ROOT), v = URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
                    if (k.equals("secret")) secret = v;
                    else if (k.equals("digits")) digits = Integer.parseInt(v);
                    else if (k.equals("period")) period = Integer.parseInt(v);
                    else if (k.equals("algorithm")) alg = "Hmac" + v.toUpperCase(Locale.ROOT);
                }
                s = secret;
            }
            byte[] key = base32(s);
            if (key == null || key.length < 5 || digits < 6 || digits > 8 || period < 10 || period > 300) return null;
            if (!alg.equals("HmacSHA1") && !alg.equals("HmacSHA256") && !alg.equals("HmacSHA512")) return null;
            return new Totp(key, alg, digits, period);
        } catch (Exception e) {
            return null;
        }
    }

    String code(long unixMillis) {
        try {
            long counter = (unixMillis / 1000) / period;
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            int mod = 1;
            for (int i = 0; i < digits; i++) mod *= 10;
            String c = Integer.toString(bin % mod);
            while (c.length() < digits) c = "0" + c;
            return c;
        } catch (Exception e) {
            return "------";
        }
    }

    int secondsLeft(long unixMillis) {
        return period - (int) ((unixMillis / 1000) % period);
    }

    static byte[] base32(String s) {
        if (s == null) return null;
        String in = s.replace(" ", "").replace("-", "").replace("=", "").toUpperCase(Locale.ROOT);
        if (in.isEmpty()) return null;
        String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] out = new byte[in.length() * 5 / 8];
        int buffer = 0, bits = 0, n = 0;
        for (int i = 0; i < in.length(); i++) {
            int v = abc.indexOf(in.charAt(i));
            if (v < 0) return null;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out[n++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return out;
    }
}
