package com.shadowclient.ff.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String FILE = "shadow_client";

    public static final String KEY_ACTIVE = "active";
    public static final String KEY_VALUE = "key_value";
    public static final String KEY_LABEL = "key_label";
    public static final String KEY_ACTIVATED_AT = "activated_at";
    public static final String KEY_EXPIRY = "expiry";
    public static final String KEY_DEVICE = "device_id";
    public static final String KEY_REMEMBER = "remember";
    public static final String KEY_GAME = "game";
    public static final String KEY_SERVER = "server";
    public static final String KEY_OVERLAY = "overlay_on";
    public static final String KEY_OVERLAY_X = "overlay_x";
    public static final String KEY_OVERLAY_Y = "overlay_y";
    public static final String KEY_LAST_CHECK = "last_check";

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String str(Context c, String k, String def) {
        return get(c).getString(k, def);
    }

    public static void set(Context c, String k, String v) {
        get(c).edit().putString(k, v).apply();
    }

    public static long num(Context c, String k, long def) {
        return get(c).getLong(k, def);
    }

    public static void setNum(Context c, String k, long v) {
        get(c).edit().putLong(k, v).apply();
    }

    public static boolean flag(Context c, String k, boolean def) {
        return get(c).getBoolean(k, def);
    }

    public static void setFlag(Context c, String k, boolean v) {
        get(c).edit().putBoolean(k, v).apply();
    }

    /** Clears the stored session but keeps non-sensitive settings. */
    public static void clearSession(Context c) {
        get(c).edit()
                .remove(KEY_ACTIVE)
                .remove(KEY_VALUE)
                .remove(KEY_LABEL)
                .remove(KEY_ACTIVATED_AT)
                .remove(KEY_EXPIRY)
                .remove(KEY_LAST_CHECK)
                .apply();
    }
}
