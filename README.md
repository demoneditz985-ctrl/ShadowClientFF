# Shadow Client

Black-and-neon Android client for the Free Fire community — your branding, your keys, your
floating icon, your Telegram.

<p align="center">
  <img src="artwork/shadow_logo_master.png" width="220" alt="Shadow Client logo">
</p>

## Download

Every push to `main` builds a signed APK automatically:

**➡️ [Download the latest APK](https://github.com/demoneditz985-ctrl/ShadowClientFF/releases/latest)**

* Android **9+** (`minSdk 28`), targets `SDK 35`, so it also installs on Android 16/17.
* ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`.

## What's inside

| | |
|---|---|
| 🌑 **Dark black theme** | Pure-black surfaces, violet/magenta neon accents, drifting particle background |
| 🔑 **Key login** | `SHADOWCLIENT` works out of the box; add more keys in `assets/keys.txt`, or run the bundled key server for revocable, device-locked keys |
| 🎈 **Floating icon** | Your logo, draggable anywhere, snaps to the edge, tap to open, × to hide |
| 🌐 **Node list** | Add your own hosts, real TCP ping per node, Free Fire / Free Fire MAX selector |
| ✈️ **Telegram only** | Join button wired to your invite link — no WhatsApp branding, links or icons anywhere |
| 🖼️ **Your logo everywhere** | Adaptive launcher icon, splash, login, dashboard, notification |

## Screens

`Splash` → `ACCESS KEY` (login) → `Dashboard`
— active module card, activate toggle, game selector, node list with ping, floating-icon switch,
account card (masked key, activation date, expiry), Telegram community button.

## Quick start for the owner

1. Install the APK and log in with **`SHADOWCLIENT`**.
2. Read **[SETUP.md](SETUP.md)** — it covers keys, the key server, branding, signing and the
   engine plug-in point.
3. Want to hand out keys? Either edit `app/src/main/assets/keys.txt`, or set
   `Config.KEY_API_URL` to your own `server/key-server.js` deployment and manage keys over HTTP.

## Repository layout

```
app/src/main/java/com/shadowclient/ff/
  auth/KeyManager.java        key validation, device binding, sessions
  core/ServerRepo.java        node list + real TCP ping
  core/ShadowEngine.java      engine plug-in point (see SETUP.md §5)
  overlay/FloatingIconService.java   the floating logo
  ui/                         Splash, Login, Dashboard, particle background
server/key-server.js          dependency-free key server (issue / revoke / reset devices)
tools/make_icons.py           rebuild every icon size from one master image
keystore/shadowclient.jks     release signing key (keep it safe!)
```

## Notes

* Written from scratch as a clean-branded build: no third-party client code, no borrowed
  assets, no copied package name.
* The tunnel engine is not bundled — the app says so plainly instead of faking a connection.
  See SETUP.md §5 to drop yours in.
* Original `dripclient-wire-v3.2.0.apk` stays in the repo untouched, for reference only.
