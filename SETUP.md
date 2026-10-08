# Shadow Client — setup guide

Everything below needs no Android Studio.

---

## 1. What this app is right now

| Part | State |
|---|---|
| Dark black + neon violet theme, your logo everywhere | ✅ finished |
| Splash → key screen → dashboard flow | ✅ finished |
| Key system (`SHADOWCLIENT` + local list + optional key server) | ✅ finished |
| Floating **menu** — tap the logo in-game → node + ping, OPEN FREE FIRE / FF MAX, dashboard, Telegram, hide | ✅ finished |
| OPEN FREE FIRE / FREE FIRE MAX buttons (dashboard + floating menu) | ✅ finished |
| Node list with real TCP ping, node credentials (VLESS/VMess/Trojan/SS/Hysteria2) | ✅ finished |
| Telegram community button → your invite link | ✅ finished |
| **Tunnel engine — official sing-box core** | ✅ **bundled** (see §5) |
| A node to connect through | ⚠️ **yours to add** — the engine needs a server to dial |

No WhatsApp branding, links or icons exist anywhere in this project, by design.

---

## 2. Build an APK

**On GitHub (already wired up).** Every push to `main` builds signed APKs and publishes them to the
`latest` release:

```
https://github.com/demoneditz985-ctrl/ShadowClientFF/releases/latest
```

Two files are produced — pick one:

| File | Install it on |
|---|---|
| `ShadowClient-1.1.0-arm64.apk` | any phone from ~2018 onwards (**use this first**) |
| `ShadowClient-1.1.0-arm32.apk` | only if the arm64 APK refuses to install (old 32-bit device) |

The APKs are large (~85 MB) because the sing-box core is a native engine per architecture.

**On your own machine.** `gradle assembleRelease` (JDK 17, Android SDK 35).

### Signing

The release keystore is committed at `keystore/shadowclient.jks`:

| | |
|---|---|
| alias | `shadowclient` |
| store / key password | `shadowclient2026` |

**Keep this file safe.** Android identifies an app by its signing key: if you lose it you can no
longer update an installed copy. Override with `SC_STORE_PASSWORD`, `SC_KEY_PASSWORD`,
`SC_KEY_ALIAS` env vars / GitHub secrets if you want different credentials.

---

## 3. Keys

👉 **Full walkthrough: [HOW-TO-GENERATE-KEYS.md](HOW-TO-GENERATE-KEYS.md)**

Short version:

* **`SHADOWCLIENT`** — the owner key, built into the app. Never expires, no device lock.
* **Offline keys** — `node tools/keygen.js --n 10 --label VIP --expiry 2027-12-31 --write`
  appends them to `app/src/main/assets/keys.txt`; rebuild to activate them.
* **Server keys (recommended)** — run `server/key-server.js`, set `Config.KEY_API_URL`, then create
  keys any time with `node tools/keygen.js --server https://your-host --admin TOKEN --n 10`.
  Revocable, expiry-controlled, device-locked, no rebuilds.

---

## 4. Branding, icons, links

| What | Where |
|---|---|
| App name | `app/src/main/res/values/strings.xml` → `app_name` |
| Package id (`com.shadowclient.ff`) | `app/build.gradle` + the Java package folders |
| Logo in-app | `app/src/main/res/drawable-nodpi/logo_shadow.png` |
| Launcher icon | regenerate: `python3 tools/make_icons.py artwork/shadow_logo_master.png` |
| Floating menu icon | `app/src/main/res/drawable-nodpi/ic_overlay_icon.png` |
| Telegram link | `Config.TELEGRAM_URL` |
| Free Fire package names | `util/GameLauncher.java` (`com.dts.freefireth`, `com.dts.freefiremax`) |

---

## 5. The tunnel engine (sing-box)

`app/libs/libbox.aar` is the **official sing-box Android core**, built from upstream source
(`SagerNet/sing-box` v1.14.2, `make lib_android`) by
`.github/workflows/engine-libbox.yml` for arm64-v8a + armeabi-v7a. It is open source (GPL-3.0) and
is the same engine family the old APK used — obtained legally and maintained upstream.

* The engine source lives in `app/src/engineSingbox/` and is only compiled when
  `app/libs/libbox.aar` exists, so the app always builds with or without it.
* `EngineInstaller` finds it at startup and hands it to `ShadowEngine`; the dashboard's
  **ACTIVATE MODULE** button then asks Android for VPN permission and starts a real tunnel.
* Only **Free Fire / Free Fire MAX** are routed through the tunnel
  (`VpnService.addAllowedApplication`) — everything else on the phone is untouched, and the
  engine's own sockets are protected.

### ⚠️ You still need your own node

An engine with no server connects to nothing. Get a node and paste its details into
**ADD SERVER** in the dashboard:

| Field | Example |
|---|---|
| Protocol | VLESS / VMess / Trojan / Shadowsocks / Hysteria2 |
| Host | `node.example.com` or an IP |
| Port | `443` |
| UUID / Password | what your provider gave you |
| SNI / TLS name | usually the same as the host (optional) |
| Obfs password | Hysteria2 only (optional) |

Tip: the two `PING TEST` rows are only there so you can see live ping — they are not real nodes
and the app tells you so if you try to connect through them. Long-press a row to delete it.

### Licensing note

sing-box is GPL-3.0. If you distribute Shadow Client publicly, publish the corresponding source
for the engine (your repo already is the source) or make the app's own source available — the
simplest option is keeping this repository public, which it already is.

---

## 6. If the VPN does not connect

1. **Did you add a real node?** Demo rows never connect.
2. **Did you grant the VPN request?** The first tap shows Android's "Connection request" dialog;
   if you tapped Cancel, tap ACTIVATE MODULE again.
3. **Is the node alive?** A red `TIMEOUT` in the ping column means the host does not answer on that
   port at all.
4. **Credentials wrong?** Wrong UUID/password shows `ERROR` with the engine's message in the
   status line.
5. **Battery optimisation** — some phones kill background services; allow Shadow Client to run in
   the background (the app offers the setting on first run).
6. **Send me the status line text** — it is the engine's own message and tells us exactly what
   failed.

---

## 7. Where everything lives

```
app/src/main/java/com/shadowclient/ff/
  auth/KeyManager.java              key validation, device binding, sessions
  core/NodeConfig.java              node model + sing-box config generation
  core/ServerRepo.java              node list + real TCP ping
  core/ShadowEngine.java            engine plug-in point
  engine/EngineInstaller.java       loads the bundled engine
  overlay/FloatingIconService.java  the floating logo + in-game menu
  ui/                               Splash, Login, Dashboard, particle background
app/src/engineSingbox/…             sing-box platform glue (VpnService)
tools/keygen.js                     key generator (offline + server)
tools/make_icons.py                 rebuild all icon sizes from one master image
server/key-server.js                key server (issue / revoke / reset devices)
.github/workflows/build.yml         builds + publishes the APKs
.github/workflows/engine-libbox.yml builds the sing-box core
```
