package com.example.p2pmessenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Single place for the logged-in user's uid, name and server session token. */
public final class Session {
    public static final String PREFS = "P2PMeshPrefs";
    private Session() {}

    private static SharedPreferences prefs() {
        Context c = PertoApp.context();
        return c == null ? null : c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String get(String key) {
        SharedPreferences p = prefs();
        String v = p == null ? "" : p.getString(key, "");
        return v == null ? "" : v;
    }

    public static String uid()   { return get("user_uid"); }
    public static String name()  { return get("user_name"); }
    public static String email() { return get("user_email"); }
    public static String token() { return get("session_token"); }

    /** Logged in = a completed login/sign-up on this build (older installs must log in once again). */
    public static boolean isLoggedIn() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean("logged_in", false) && !uid().isEmpty();
    }

    public static void save(String uid, String name, String email, String token) {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit()
                .putString("user_uid", uid == null ? "" : uid)
                .putString("USER_UID", uid == null ? "" : uid)
                .putString("user_name", name == null ? "" : name)
                .putString("user_email", email == null ? "" : email)
                .putString("session_token", token == null ? "" : token)
                .putBoolean("logged_in", true)
                .apply();
    }

    public static void clear() {
        SharedPreferences p = prefs();
        if (p != null) p.edit().clear().apply();
    }
}
