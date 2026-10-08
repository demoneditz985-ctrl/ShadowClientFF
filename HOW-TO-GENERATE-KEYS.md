# How to generate Shadow Client keys

Read this once and you can hand out keys forever. Everything here is already in your repo.

There are **two kinds of keys**. Pick per use case — most people end up using both.

| | **Offline keys** | **Server keys** ← recommended for selling |
|---|---|---|
| Where they live | inside the APK (`assets/keys.txt`) | on your key server |
| Make a key | `node tools/keygen.js --n 10 --write` | `node tools/keygen.js --server … --n 10` |
| Needs rebuild? | **yes**, every time | no |
| Can you revoke / change expiry? | no | **yes, instantly** |
| Device lock | yes | yes |
| Good for | beta testers, friends | customers, resellers |

---

## 0. The key that always works

`SHADOWCLIENT` is the **owner key**, built into the app. Never expires, no device lock, works
even with no server. Don't give it to customers — give them generated keys.

---

## 1. Generate offline keys (2 minutes)

```bash
# make 10 keys, each locks to the first phone that uses it, alive until 31 Dec 2027
node tools/keygen.js --n 10 --label VIP --expiry 2027-12-31 --devices 1 --write --csv vip-keys.csv
```

Output:

```
  SC-84DA-DCC5-7FE0
  SC-4B6F-B29A-9AC8
  ...
CSV written: vip-keys.csv
Appended to app/src/main/assets/keys.txt
```

* `--write` pastes them straight into `app/src/main/assets/keys.txt`.
* `--csv vip-keys.csv` gives you a spreadsheet of what you sold to whom.
* **Then rebuild**: push to `main` (GitHub builds it automatically) or run `gradle assembleRelease`.
  Users must install the new APK for the new keys to work.

Manual style, if you prefer editing by hand — one key per line in `app/src/main/assets/keys.txt`:

```
SC-BETA-0001|BetaTester|2027-01-31|1
SC-VIP-0002|Aman|LIFETIME|1
SC-RESELL-10|Reseller-10slots|LIFETIME|10
```

`KEY | LABEL | EXPIRY | MAX_DEVICES`

* **LABEL** – shown to the user in the dashboard ("Hi, Aman" style tracking).
* **EXPIRY** – `LIFETIME` or a date `yyyy-MM-dd`. After that date the key stops working.
* **MAX_DEVICES** – `1` = works on one phone only (first one to activate). `3` = three phones.
  `0` = unlimited.
* Lines starting with `#` are ignored.

---

## 2. Generate server keys (best for selling)

### 2a. Start the key server (once)

```bash
SC_TOKEN=choose-an-app-secret ADMIN_TOKEN=choose-an-admin-secret node server/key-server.js
```

* `SC_TOKEN` – the app's password to the server (goes in the APK).
* `ADMIN_TOKEN` – **your** password for creating/revoking keys. Never put this in the app.

Host it anywhere Node runs: a cheap VPS, Render, Railway, Fly.io, Deta. Note the https address.

### 2b. Point the app at it (once)

`app/src/main/java/com/shadowclient/ff/Config.java`:

```java
public static String KEY_API_URL   = "https://your-host/validate";
public static String KEY_API_TOKEN = "choose-an-app-secret";
```

Rebuild. From now on you never rebuild for keys again.

### 2c. Make keys whenever you sell

```bash
# one key for one customer
node tools/keygen.js --server https://your-host --admin change-me-admin --label Aman --expiry 2027-01-31

# a batch of 20 for reselling, unlimited devices each
node tools/keygen.js --server https://your-host --admin change-me-admin \
  --n 20 --label RESELL --expiry LIFETIME --devices 0 --csv resell-20.csv

# see every key, who used it, how many devices
node tools/keygen.js --server https://your-host --admin change-me-admin --list

# customer got a new phone
node tools/keygen.js --server https://your-host --admin change-me-admin --reset SC-AB12-CD34-EF56

# refund / leaked key -> dead immediately
node tools/keygen.js --server https://your-host --admin change-me-admin --revoke SC-AB12-CD34-EF56
```

The same thing with plain curl, if you're on a phone and only have a browser:

```bash
curl -X POST https://your-host/admin/keys \
  -H "Authorization: Bearer change-me-admin" -H "Content-Type: application/json" \
  -d '{"label":"Aman","expiry":"2027-01-31","max_devices":1}'
```

---

## 3. How the app checks a key

1. User types the key on the login screen → `ACTIVATE`.
2. App sends it to `KEY_API_URL` (or checks `assets/keys.txt` when no server is set).
3. Server says yes → it records the phone's device id and returns the label + expiry.
4. App stores the session (SharedPreferences) and opens the dashboard.
5. **"Keep me signed in"** checked = stays logged in. Unchecked = must enter the key again next open.
6. If your server is down, a key that already worked keeps working for 3 days
   (`Config.OFFLINE_GRACE_MS`), so a hiccup doesn't lock everyone out.

Typing is forgiving: `sc-ab12-cd34-ef56`, `SCAB12CD34EF56` and `SC AB12 CD34 EF56` are the same key.

---

## 4. Rules that keep you safe

1. **Never ship `ADMIN_TOKEN`** in the app — only `SC_TOKEN`.
2. **Never share the keystore** (`keystore/shadowclient.jks`). It's your app's identity.
3. Put the key server behind **https**. Plain http works but users' keys travel in the clear.
4. Prefer `--devices 1`; sharing is the #1 cause of "key not working" complaints.
5. Back up `server/keys.json` (server mode) and `vip-keys.csv` (offline). Your phone reset once
   already — don't lose the list a second time. Best: keep the repo on GitHub and let CI build.

---

## 5. Quick answers

**"Invalid key" but the key looks right**
The user didn't install the latest APK (offline keys need a rebuild), or the server is unreachable
(they'll see "Can't reach the key server"), or the key is expired/revoked.

**"This key is already bound to another device"**
`--devices 1` and someone else activated it first. Run `--reset <KEY>` on the server, or sell with
`--devices 0`.

**Where do I see which key is active?**
Dashboard → account card (masked key, activation date, expiry). Server mode:
`node tools/keygen.js --server … --admin … --list`.

**Can a key be time-limited to 7 days?**
Server mode: set `expiry` to a date 7 days out when creating it. Offline: same, but rebuilding is
needed each time — that's why server mode wins for trials.
