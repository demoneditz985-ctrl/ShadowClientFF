## Shadow Client 1.1.0 — floating menu, Free Fire buttons, real engine, key generator

**Download (install arm64 unless your phone is old):**
https://github.com/demoneditz985-ctrl/ShadowClientFF/releases/latest

### 1. Floating logo now opens a menu (bug fixed)

The touch listener consumed the gesture, so `setOnClickListener` never fired — that is exactly why
tapping the logo did nothing. The tap is now handled inside the touch handler.

Tap the logo in-game → a compact panel with a **different UI** from the old client:
node + live ping (refreshes every 5 s), **OPEN FREE FIRE**, **FF MAX**, **DASHBOARD**, **TELEGRAM**,
**HIDE FLOATING ICON**. Drag the logo or the panel header to move it; it stays inside the screen and
remembers its position.

### 2. OPEN FREE FIRE / FREE FIRE MAX buttons

Both in the dashboard (games card) and in the floating menu. Launches the game, or opens its store
page when it is not installed. `<queries>` added so Android 11+ can see the game packages.

### 3. VPN: real engine, and why not the old `.so`

The old APK's engine turned out to be **sing-box core** (Hysteria2 / salamander included), with 451
references to its own `com.dripclient.*` Java classes baked into the binary — it cannot be lifted
into another app, and its node config came from that vendor's backend, which is gone.

So the app now ships the **official sing-box Android core**, built from upstream source
(`SagerNet/sing-box` v1.14.2, `make lib_android`) by `.github/workflows/engine-libbox.yml`:
`app/libs/libbox.aar` (56 MB, arm64-v8a + armeabi-v7a). Open source (GPL-3.0), maintained upstream.

* `app/src/engineSingbox/` — VpnService + full platform interface (TUN creation, socket protection,
  interface discovery, default-network monitor, connection-owner lookup, command server).
* Loaded through `EngineInstaller`; the dashboard asks for Android's VPN consent and starts a real
  tunnel. Only Free Fire / FF MAX are routed through it.
* Node credentials now supported: **VLESS, VMess, Trojan, Shadowsocks, Hysteria2** (+ SNI, obfs).
* **Still required: your own node.** No server = nothing to connect to. Demo rows only measure ping.

### 4. Key generation, taught

* `tools/keygen.js` — offline (`--n 10 --write`) and server mode (`--server … --admin …`), plus
  `--list`, `--revoke`, `--reset`, `--csv`.
* `HOW-TO-GENERATE-KEYS.md` — the full guide: both key types, what each field means, how to sell
  keys, how device locking works, and the rules that keep keys safe.
* Owner key `SHADOWCLIENT` still works out of the box.

### Build facts

`com.shadowclient.ff` · v1.1.0 (2) · minSdk 28 (Android 9) · targetSdk 35 · ABI splits
(arm64 87 MB / arm32 80 MB) · signed with the committed keystore · CI builds and publishes on every
push to this branch.

### Honest caveat

I can compile and package the app but there is no Android device here, so the tunnel has not been
run on real hardware. The implementation mirrors sing-box's own Android client closely; if the
status line shows an error, that text is the engine's own message — send it and it will be a quick
fix.

Original `dripclient-wire-v3.2.0.apk` remains untouched in the repo.
