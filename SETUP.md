# Shadow Client — setup guide

Everything below takes minutes and needs no Android Studio.

---

## 1. What this app is right now

| Part | State |
|---|---|
| Dark black + neon violet theme, your logo everywhere | ✅ finished |
| Splash → key screen → dashboard flow | ✅ finished |
| Key system (`SHADOWCLIENT` + local list + optional key server) | ✅ finished |
| Floating icon (your logo), draggable, tap to open, × to hide | ✅ finished |
| Node list with real TCP ping, select game (FF / FF MAX) | ✅ finished |
| Telegram community button → your invite link | ✅ finished |
| Tunnel engine (the actual proxying/VPN part) | ⛔ **not included** — see §5 |

No WhatsApp branding, links or icons exist anywhere in this project, by design.

---

## 2. Build an APK

**On GitHub (already wired up).** Every push to `main` builds a signed APK and attaches it to the
`latest` release:

```
https://github.com/demoneditz985-ctrl/ShadowClientFF/releases/latest
```

The workflow lives in `.github/workflows/build.yml`. You can also run it by hand:
**Actions → Build Shadow Client APK → Run workflow**.

**On your own machine.** `gradle assembleRelease` (JDK 17, Android SDK 35). The APK lands in
`app/build/outputs/apk/release/`.

### Signing

The release keystore is committed at `keystore/shadowclient.jks`:

| | |
|---|---|
| alias | `shadowclient` |
| store / key password | `shadowclient2026` |

**Keep this file safe.** Android identifies an app by its signing key: if you lose it you can no
longer update an installed copy of Shadow Client — users would have to uninstall first. To change
the password without editing files, set `SC_STORE_PASSWORD`, `SC_KEY_PASSWORD`, `SC_KEY_ALIAS`
as environment variables (or GitHub secrets) before building.

---

## 3. Keys

### Offline mode (default)

`Config.KEY_API_URL` is empty, so the app validates on its own:

* **`SHADOWCLIENT`** — the owner key, built in. Never expires, no device lock. Anyone who types it
  gets in.
* Any extra keys you list in `app/src/main/assets/keys.txt`:

  ```
  SC-BETA-0001|BetaTester|2027-01-31|1
  SC-VIP-0002|VIP-Aman|LIFETIME|1
  SC-RESELL-10|Reseller-10slots|LIFETIME|10
  ```

  `KEY|LABEL|EXPIRY|MAX_DEVICES` — expiry is `LIFETIME` or `yyyy-MM-dd`, devices `1` locks the key
  to the first phone that uses it, `0` means unlimited.

Honest caveat: an offline check lives inside the APK and a determined person can extract it.
Changing the key list also requires shipping a new APK.

### Server mode (recommended)

Run your own key server — `server/key-server.js`, zero dependencies:

```bash
SC_TOKEN=my-app-secret ADMIN_TOKEN=my-admin-secret node server/key-server.js
```

Deploy it anywhere Node runs (Render, Railway, Fly.io, a VPS, Cloudflare Workers with small
changes). Then point the app at it in
`app/src/main/java/com/shadowclient/ff/Config.java`:

```java
public static String KEY_API_URL   = "https://your-host/validate";
public static String KEY_API_TOKEN = "my-app-secret";
```

Rebuild once and from then on you control keys remotely:

```bash
# create a key for a buyer
curl -X POST https://your-host/admin/keys \
  -H "Authorization: Bearer my-admin-secret" \
  -H "Content-Type: application/json" \
  -d '{"label":"Aman","expiry":"2027-01-31","max_devices":1}'

# list every key
curl https://your-host/admin/keys -H "Authorization: Bearer my-admin-secret"

# revoke instantly (the key stops working on the next check)
curl -X DELETE https://your-host/admin/keys/SC-AB12-CD34-EF56 \
  -H "Authorization: Bearer my-admin-secret"

# buyer changed phones
curl -X POST https://your-host/admin/reset-device \
  -H "Authorization: Bearer my-admin-secret" \
  -H "Content-Type: application/json" -d '{"key":"SC-AB12-CD34-EF56"}'
```

If the server is unreachable, a key that was already activated keeps working for 3 days
(`Config.OFFLINE_GRACE_MS`) so users are not locked out by a hiccup.

---

## 4. Branding, icons, links

| What | Where |
|---|---|
| App name shown by the launcher | `app/src/main/res/values/strings.xml` → `app_name` |
| Package id (`com.shadowclient.ff`) | `app/build.gradle` → `applicationId` + `namespace`, and the Java package folders |
| Logo used in-app | `app/src/main/res/drawable-nodpi/logo_shadow.png` (and `logo_shadow_round.png`) |
| Launcher icon | regenerate from a new master image: see `artwork/` and the script note below |
| Floating icon | `app/src/main/res/drawable-nodpi/ic_overlay_icon.png` |
| Telegram link | `Config.TELEGRAM_URL` |
| Brand string in notification/status | `Config.BRAND` |

To rebuild every icon size from a new square master image (`artwork/shadow_logo_master.png`):

```bash
pip install Pillow
python3 tools/make_icons.py artwork/shadow_logo_master.png
```

---

## 5. Engine plug-in point

`app/src/main/java/com/shadowclient/ff/core/ShadowEngine.java` is the single place where a tunnel
engine is registered. Until one is installed, the dashboard honestly reports
`ENGINE NOT INSTALLED` and the module button explains this instead of pretending to connect.

Two common ways to finish it:

1. **VpnService engine** — drop in a client such as sing-box / Xray / hysteria as a native library
   (`app/src/main/jniLibs/<abi>/`) or a Maven dependency, write a class implementing
   `ShadowEngine.Tunnel`, and call `ShadowEngine.install(...)` from `ShadowApp.onCreate()`.
   The node list, ping readout and floating icon already feed it.
2. **Your own native library** — if you have the `.so` from your previous build, add it under
   `app/src/main/jniLibs/arm64-v8a/` (plus `armeabi-v7a` if you want 32-bit support) and call it
   over JNI from the same `Tunnel` implementation.

`khm` — Android 9+ (`minSdk 28`), `targetSdk 35`, ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`.
