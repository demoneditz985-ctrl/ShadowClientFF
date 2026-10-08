package com.shadowclient.ff.auth;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import com.shadowclient.ff.Config;
import com.shadowclient.ff.util.Prefs;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Key activation + session handling.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Offline</b> (default, {@link Config#KEY_API_URL} empty): the owner key
 *       {@link Config#MASTER_KEY} plus any extra keys listed in assets/keys.txt.</li>
 *   <li><b>Server</b>: set {@link Config#KEY_API_URL} to your own endpoint and keys become
 *       revocable, with real expiry dates and device binding managed server-side.</li>
 * </ul>
 *
 * <p>Note for whoever runs this app: a purely offline key check lives inside the APK and can
 * always be extracted by someone determined. Use the server mode if keys must actually hold.
 */
public final class KeyManager {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final int ERR_EMPTY = 1;
    public static final int ERR_INVALID = 2;
    public static final int ERR_EXPIRED = 3;
    public static final int ERR_DEVICE = 4;
    public static final int ERR_OFFLINE = 5;
    public static final int ERR_OK = 0;

    private KeyManager() {
    }

    public interface Callback {
        void onDone(int code, String label, long expiry);
    }

    /** Static description of one key, from the local list or the key server. */
    public static final class KeyInfo {
        public String label = "KEY";
        public long expiry = 0L;      // 0 == lifetime
        public int maxDevices = 1;    // 0 == unlimited
    }

    // ---------------------------------------------------------------- public API

    public static void activate(final Context ctx, final String rawKey, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String key = normalize(rawKey);
        if (TextUtils.isEmpty(key)) {
            post(cb, ERR_EMPTY, null, 0L);
            return;
        }
        IO.execute(() -> {
            KeyInfo info = null;
            int code;

            if (!TextUtils.isEmpty(Config.KEY_API_URL)) {
                info = remote(app, key);
                if (info == null) {
                    // server unreachable -> allow a previously activated key inside the grace window
                    if (hasRecentLocalGrant(app, key)) {
                        code = ERR_OK;
                    } else {
                        post(cb, ERR_OFFLINE, null, 0L);
                        return;
                    }
                }
            }

            if (info == null && code != ERR_OK) {
                info = local(app, key);
                if (info == null) {
                    post(cb, ERR_INVALID, null, 0L);
                    return;
                }
                code = ERR_OK;
            }

            if (info.expiry > 0 && info.expiry < System.currentTimeMillis()) {
                post(cb, ERR_EXPIRED, info.label, info.expiry);
                return;
            }

            if (!bindDevice(app, key, info)) {
                post(cb, ERR_DEVICE, info.label, info.expiry);
                return;
            }

            save(app, key, info);
            post(cb, ERR_OK, info.label, info.expiry);
        });
    }

    /** Works fully offline against the stored session (re-checks expiry). */
    public static boolean isActive(Context c) {
        if (!Prefs.flag(c, Prefs.KEY_ACTIVE, false)) return false;
        long exp = Prefs.num(c, Prefs.KEY_EXPIRY, 0L);
        if (exp > 0 && exp < System.currentTimeMillis()) return false;
        return !TextUtils.isEmpty(Prefs.str(c, Prefs.KEY_VALUE, null));
    }

    public static String activeKey(Context c) {
        return Prefs.str(c, Prefs.KEY_VALUE, "");
    }

    public static String activeLabel(Context c) {
        return Prefs.str(c, Prefs.KEY_LABEL, "KEY");
    }

    public static long activeExpiry(Context c) {
        return Prefs.num(c, Prefs.KEY_EXPIRY, 0L);
    }

    public static long activatedAt(Context c) {
        return Prefs.num(c, Prefs.KEY_ACTIVATED_AT, 0L);
    }

    public static void signOut(Context c) {
        Prefs.clearSession(c);
    }

    /** SHADOWCLIENT -> "SHAD••••••NT" style masking for display. */
    public static String mask(String key) {
        if (TextUtils.isEmpty(key)) return "";
        if (key.length() <= 6) return key;
        StringBuilder sb = new StringBuilder(key.substring(0, 4));
        for (int i = 0; i < Math.min(6, key.length() - 6); i++) sb.append('\u2022');
        sb.append(key.substring(key.length() - 2));
        return sb.toString();
    }

    public static String prettyDate(long millis) {
        if (millis <= 0) return "LIFETIME";
        return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(millis));
    }

    // ---------------------------------------------------------------- internals

    private static void post(Callback cb, int code, String label, long expiry) {
        MAIN.post(() -> cb.onDone(code, label, expiry));
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
    }

    /** Stable, non-reversible device fingerprint. */
    public static String deviceId(Context c) {
        String cached = Prefs.str(c, Prefs.KEY_DEVICE, null);
        if (cached != null) return cached;
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
        }
        String id = sha256(androidId == null ? "unknown" : androidId).substring(0, 16).toUpperCase(Locale.US);
        Prefs.set(c, Prefs.KEY_DEVICE, id);
        return id;
    }

    private static boolean bindDevice(Context c, String key, KeyInfo info) {
        if (Config.MASTER_KEY.equals(key)) return true;      // owner key: always allowed
        if (info.maxDevices <= 0) return true;               // unlimited devices
        String slot = "bind_" + sha256(key).substring(0, 12);
        String bound = Prefs.str(c, slot, null);
        String me = deviceId(c);
        if (bound == null) {
            Prefs.set(c, slot, me);
            return true;
        }
        return bound.equals(me);
    }

    private static void save(Context c, String key, KeyInfo info) {
        Prefs.set(c, Prefs.KEY_ACTIVE, "x");
        Prefs.setFlag(c, Prefs.KEY_ACTIVE, true);
        Prefs.set(c, Prefs.KEY_VALUE, key);
        Prefs.set(c, Prefs.KEY_LABEL, info.label);
        Prefs.setNum(c, Prefs.KEY_ACTIVATED_AT, System.currentTimeMillis());
        Prefs.setNum(c, Prefs.KEY_EXPIRY, info.expiry);
        Prefs.setNum(c, Prefs.KEY_LAST_CHECK, System.currentTimeMillis());
    }

    private static boolean hasRecentLocalGrant(Context c, String key) {
        boolean sameKey = key.equals(Prefs.str(c, Prefs.KEY_VALUE, null));
        boolean active = Prefs.flag(c, Prefs.KEY_ACTIVE, false);
        long last = Prefs.num(c, Prefs.KEY_LAST_CHECK, 0L);
        return sameKey && active && (System.currentTimeMillis() - last) < Config.OFFLINE_GRACE_MS;
    }

    /** Reads assets/keys.txt — KEY|LABEL|EXPIRY|MAX_DEVICES. */
    private static KeyInfo local(Context c, String key) {
        if (Config.MASTER_KEY.equals(key)) {
            KeyInfo info = new KeyInfo();
            info.label = "OWNER";
            info.expiry = 0L;
            info.maxDevices = 0;
            return info;
        }
        for (String line : localLines(c)) {
            String[] parts = line.split("\\|");
            if (parts.length == 0) continue;
            if (!normalize(parts[0]).equals(key)) continue;
            KeyInfo info = new KeyInfo();
            info.label = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : "KEY";
            info.expiry = parts.length > 2 ? parseExpiry(parts[2].trim()) : 0L;
            info.maxDevices = parts.length > 3 ? parseInt(parts[3].trim(), 1) : 1;
            return info;
        }
        return null;
    }

    private static List<String> localLines(Context c) {
        List<String> out = new ArrayList<>();
        AssetManager am = c.getAssets();
        try (InputStream in = am.open(Config.LOCAL_KEYS_ASSET);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                out.add(line);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static long parseExpiry(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("LIFETIME")) return 0L;
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s).getTime() + 86_399_000L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    /** Calls the owner's key server. Returns null when unreachable or the key is rejected. */
    private static KeyInfo remote(Context c, String key) {
        HttpURLConnection conn = null;
        try {
            String url = Config.KEY_API_URL
                    + (Config.KEY_API_URL.contains("?") ? "&" : "?")
                    + "key=" + Uri.encode(key)
                    + "&hwid=" + Uri.encode(deviceId(c))
                    + "&v=" + Uri.encode(Config.VERSION);
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-SC-Token", Config.KEY_API_TOKEN);
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            if (status != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) body.append(l);
            }
            JSONObject o = new JSONObject(body.toString());
            if (!o.optBoolean("ok", false)) return null;
            KeyInfo info = new KeyInfo();
            info.label = o.optString("label", "KEY");
            String exp = o.optString("expiry", "");
            if (!exp.isEmpty() && !exp.equalsIgnoreCase("LIFETIME")) {
                try {
                    info.expiry = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(exp).getTime() + 86_399_000L;
                } catch (Exception ignored) {
                }
            }
            info.maxDevices = o.optInt("max_devices", 1);
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String sha256(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "0000000000000000";
        }
    }
}
