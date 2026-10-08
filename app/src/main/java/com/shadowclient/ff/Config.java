package com.shadowclient.ff;

/**
 * Single place for everything you may want to change after a fork.
 */
public final class Config {

    private Config() {
    }

    /** Branding. */
    public static final String BRAND = "SHADOW CLIENT";
    public static final String VERSION = "1.0.0";

    /** Community link (Telegram only — no WhatsApp anywhere in this app). */
    public static final String TELEGRAM_URL = "https://t.me/+BBimnHMiSvpiYTBl";

    /**
     * Your own key server, e.g. "https://shadow-keys.example.workers.dev/validate".
     * <p>
     * Leave EMPTY to validate keys offline against {@link #MASTER_KEY} + assets/keys.txt.
     * Set it to switch to server-side validation (recommended: keys can then be revoked,
     * expiry changed and devices reset without shipping a new APK).
     * See server/key-server.js — deployable in ~2 minutes, free tier is enough.
     */
    public static String KEY_API_URL = "";

    /** Shared secret sent to your key server as X-SC-Token (must match the server config). */
    public static String KEY_API_TOKEN = "change-me-shadow";

    /** The owner key. Works offline, never expires, no device lock. */
    public static final String MASTER_KEY = "SHADOWCLIENT";

    /** Extra keys, one per line: KEY|LABEL|EXPIRY|MAX_DEVICES  (EXPIRY = LIFETIME or 2027-12-31) */
    public static final String LOCAL_KEYS_ASSET = "keys.txt";

    /** How long a previously activated key may keep working if the key server is unreachable. */
    public static final long OFFLINE_GRACE_MS = 3L * 24 * 60 * 60 * 1000;
}
