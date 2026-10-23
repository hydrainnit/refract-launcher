package com.hydrainnit.refract;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API keys the user types in (TMDB, Pexels): one copy each, shared by every feature, encrypted
 * with an AES-GCM key held in the Android Keystore (API 23+) so they are not readable as plain
 * text in the app's files. A key embedded in or typed into a client app can still be extracted
 * from a rooted device, so this protects against casual exposure, not a determined attacker.
 * Values are never logged; screens show them masked unless the user asks to see them.
 *
 * Each service also has a status the Settings page shows, set only from real responses:
 * unverified (saved, not tested), valid, invalid (the service rejected the key), rate_limited,
 * network (no connection) or unavailable (the service failed or is paused).
 */
final class Secrets {
    private Secrets() { }

    static final String TMDB = "tmdb", PEXELS = "pexels";
    static final String ST_NONE = "none", ST_UNVERIFIED = "unverified", ST_VALID = "valid", ST_INVALID = "invalid",
            ST_RATE = "rate_limited", ST_NETWORK = "network", ST_UNAVAILABLE = "unavailable";
    private static final String PREFS = "secrets", ALIAS = "refract_api_keys", PLAIN = "p:", SEALED = "k:";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved key for a service, or "" when none is saved. */
    static String get(Context c, String name) {
        migrate(c, name);
        String v = prefs(c).getString(name, "");
        if (v.isEmpty()) return "";
        try {
            if (v.startsWith(SEALED)) return open(v.substring(SEALED.length()));
            if (v.startsWith(PLAIN)) return new String(Base64.decode(v.substring(PLAIN.length()), Base64.NO_WRAP), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Keystore key lost (e.g. after a system reset of the keystore): the key must be entered again.
        }
        return "";
    }

    static boolean has(Context c, String name) {
        return !get(c, name).isEmpty();
    }

    /** Saves (or, when empty, removes) a service's key; a new key starts as "unverified". */
    static void put(Context c, String name, String value) {
        String v = value == null ? "" : value.trim();
        SharedPreferences.Editor e = prefs(c).edit();
        if (v.isEmpty()) {
            e.remove(name).putString(name + "_status", ST_NONE);
        } else {
            String stored;
            try {
                stored = SEALED + seal(v);
            } catch (Exception ex) {
                // Some TV keystores are broken: fall back to app-private storage (still never logged).
                stored = PLAIN + Base64.encodeToString(v.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            }
            e.putString(name, stored).putString(name + "_status", ST_UNVERIFIED);
        }
        e.putLong(name + "_checked", 0).apply();
    }

    static String status(Context c, String name) {
        if (!has(c, name)) return ST_NONE;
        return prefs(c).getString(name + "_status", ST_UNVERIFIED);
    }

    static void setStatus(Context c, String name, String status) {
        prefs(c).edit().putString(name + "_status", status).putLong(name + "_checked", System.currentTimeMillis()).apply();
    }

    /** Plain-language status for Settings. */
    static String describe(String status) {
        switch (status) {
            case ST_VALID: return "Connected";
            case ST_INVALID: return "Key not accepted";
            case ST_RATE: return "Rate limited \u2014 try later";
            case ST_NETWORK: return "No connection";
            case ST_UNAVAILABLE: return "Service unavailable";
            case ST_UNVERIFIED: return "Saved, not checked";
            default: return "Not set";
        }
    }

    /** The key with all but its last four characters hidden. */
    static String masked(String v) {
        if (v == null || v.isEmpty()) return "";
        int keep = Math.min(4, v.length() / 4);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(12, v.length() - keep); i++) b.append('\u2022');
        return b.append(v.substring(v.length() - keep)).toString();
    }

    /** Older versions kept the TMDB token in plain text in the suggestions settings: move it here once. */
    private static void migrate(Context c, String name) {
        if (!TMDB.equals(name)) return;
        SharedPreferences old = Suggestions.prefs(c);
        String plain = old.getString(Suggestions.K_TOKEN, "").trim();
        if (plain.isEmpty()) return;
        old.edit().remove(Suggestions.K_TOKEN).apply();
        if (!prefs(c).contains(name)) put(c, name, plain);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private static String seal(String v) throws Exception {
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = ci.getIV(), ct = ci.doFinal(v.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    private static String open(String s) throws Exception {
        int i = s.indexOf(':');
        byte[] iv = Base64.decode(s.substring(0, i), Base64.NO_WRAP), ct = Base64.decode(s.substring(i + 1), Base64.NO_WRAP);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(ci.doFinal(ct), StandardCharsets.UTF_8);
    }
}
