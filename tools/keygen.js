#!/usr/bin/env node
/**
 * Shadow Client — key generator
 * =============================
 * No dependencies. Two modes:
 *
 *   OFFLINE (keys baked into the APK's assets/keys.txt)
 *     node tools/keygen.js --n 10 --label VIP --expiry 2027-12-31 --devices 1
 *     node tools/keygen.js --n 5 --write          # append straight into app/src/main/assets/keys.txt
 *
 *   SERVER (live keys — revocable, no rebuild needed)
 *     node tools/keygen.js --server https://your-host --admin YOUR_ADMIN_TOKEN --n 10 --label VIP
 *     node tools/keygen.js --server https://your-host --admin TOKEN --list
 *     node tools/keygen.js --server https://your-host --admin TOKEN --revoke SC-AB12-CD34-EF56
 *     node tools/keygen.js --server https://your-host --admin TOKEN --reset SC-AB12-CD34-EF56
 *
 * Options
 *   --n <count>       how many keys (default 1)
 *   --label <text>    name shown in the app's account card (default "KEY")
 *   --expiry <date>   LIFETIME or yyyy-MM-dd (default LIFETIME)
 *   --devices <n>     1 = locks to first phone, 0 = unlimited (default 1)
 *   --prefix <text>   key prefix (default SC)
 *   --csv <file>      also write a CSV of what was generated
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// ------------------------------------------------------------------ args

function parseArgs(argv) {
    const a = {};
    for (let i = 2; i < argv.length; i++) {
        const k = argv[i];
        if (!k.startsWith('--')) continue;
        const name = k.slice(2);
        const next = argv[i + 1];
        if (next && !next.startsWith('--')) {
            a[name] = next;
            i++;
        } else {
            a[name] = true;
        }
    }
    return a;
}

const args = parseArgs(process.argv);
const COUNT = parseInt(args.n || '1', 10);
const LABEL = typeof args.label === 'string' ? args.label : 'KEY';
const EXPIRY = typeof args.expiry === 'string' ? args.expiry : 'LIFETIME';
const DEVICES = typeof args.devices === 'string' ? parseInt(args.devices, 10) : 1;
const PREFIX = typeof args.prefix === 'string' ? args.prefix.toUpperCase() : 'SC';
const ASSET = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'keys.txt');

const C = {
    reset: '\x1b[0m', dim: '\x1b[2m', bold: '\x1b[1m',
    violet: '\x1b[35m', green: '\x1b[32m', red: '\x1b[31m', yellow: '\x1b[33m'
};

function group(str) {
    return str.match(/.{1,4}/g).join('-');
}

function makeKey(seq) {
    const rand = crypto.randomBytes(6).toString('hex').toUpperCase();
    return `${PREFIX}-${rand.slice(0, 4)}-${rand.slice(4, 8)}-${rand.slice(8, 12)}`;
}

function validateExpiry(x) {
    if (x.toUpperCase() === 'LIFETIME') return 'LIFETIME';
    if (!/^\d{4}-\d{2}-\d{2}$/.test(x)) {
        console.error(`${C.red}--expiry must be LIFETIME or yyyy-MM-dd${C.reset}`);
        process.exit(1);
    }
    return x;
}

const EXPIRY_FINAL = validateExpiry(EXPIRY);

// ------------------------------------------------------------------ server mode

async function api(base, token, method, endpoint, body) {
    const url = base.replace(/\/$/, '') + endpoint;
    const res = await fetch(url, {
        method,
        headers: {
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/json'
        },
        body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    try {
        return JSON.parse(text);
    } catch (e) {
        throw new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`);
    }
}

async function serverMode() {
    const base = args.server;
    const token = args.admin;
    if (typeof token !== 'string') {
        console.error(`${C.red}--admin TOKEN is required for server mode${C.reset}`);
        process.exit(1);
    }

    try {
        if (args.list) {
            const r = await api(base, token, 'GET', '/admin/keys');
            console.log(`${C.bold}${r.count} key(s) on ${base}${C.reset}\n`);
            console.log('KEY'.padEnd(24) + 'LABEL'.padEnd(16) + 'EXPIRY'.padEnd(13) + 'DEV  STATUS');
            console.log('-'.repeat(70));
            for (const k of r.keys) {
                const status = k.revoked ? `${C.red}revoked${C.reset}`
                    : k.expired ? `${C.yellow}expired${C.reset}`
                        : `${C.green}active${C.reset}`;
                console.log(
                    k.key.padEnd(24) + String(k.label).slice(0, 15).padEnd(16) +
                    String(k.expiry).padEnd(13) + `${k.devices}/${k.max_devices === 0 ? '∞' : k.max_devices}`.padEnd(5) + ' ' + status);
            }
            return;
        }

        if (args.revoke) {
            const r = await api(base, token, 'DELETE', '/admin/keys/' + encodeURIComponent(args.revoke));
            console.log(r.ok ? `${C.green}revoked ${r.key}${C.reset}` : `${C.red}${r.reason}${C.reset}`);
            return;
        }

        if (args.reset) {
            const r = await api(base, token, 'POST', '/admin/reset-device', { key: args.reset });
            console.log(r.ok ? `${C.green}devices reset for ${r.key} — the buyer can activate on a new phone${C.reset}`
                : `${C.red}${r.reason}${C.reset}`);
            return;
        }

        // create
        const made = [];
        console.log(`${C.violet}Creating ${COUNT} key(s) on ${base}${C.reset}\n`);
        for (let i = 0; i < COUNT; i++) {
            const r = await api(base, token, 'POST', '/admin/keys', {
                label: COUNT > 1 ? `${LABEL}-${i + 1}` : LABEL,
                expiry: EXPIRY_FINAL,
                max_devices: DEVICES
            });
            if (r.ok) {
                made.push(r.key);
                console.log(`  ${C.green}${r.key}${C.reset}  ${r.label}  ${r.expiry}  devices=${r.max_devices}`);
            } else {
                console.log(`  ${C.red}failed: ${r.reason}${C.reset}`);
            }
        }
        maybeCsv(made);
        console.log(`\n${C.dim}These are stored on your key server — no APK rebuild needed.${C.reset}`);
        console.log(`${C.dim}Check any time with: node tools/keygen.js --server ${base} --admin TOKEN --list${C.reset}`);
    } catch (e) {
        console.error(`${C.red}Key server error: ${e.message}${C.reset}`);
        console.error(`${C.dim}Is server/key-server.js running? Start it with: SC_TOKEN=... ADMIN_TOKEN=${token} node server/key-server.js${C.reset}`);
        process.exit(1);
    }
}

// ------------------------------------------------------------------ offline mode

function offlineMode() {
    const lines = [];
    const keys = [];
    for (let i = 0; i < COUNT; i++) {
        const key = makeKey(i);
        const label = COUNT > 1 ? `${LABEL}-${i + 1}` : LABEL;
        keys.push(key);
        lines.push(`${key}|${label}|${EXPIRY_FINAL}|${DEVICES}`);
    }

    console.log(`${C.violet}${C.bold}Shadow Client — ${COUNT} key(s)${C.reset}`);
    console.log(`${C.dim}label=${LABEL}  expiry=${EXPIRY_FINAL}  max_devices=${DEVICES}${C.reset}\n`);
    for (const k of keys) console.log(`  ${C.green}${k}${C.reset}`);

    if (args.write) {
        let current = '';
        try {
            current = fs.readFileSync(ASSET, 'utf8');
        } catch (e) {
            current = '# Shadow Client — extra access keys\n# Format: KEY|LABEL|EXPIRY|MAX_DEVICES\n';
        }
        const addition = '\n# added ' + new Date().toISOString().slice(0, 16).replace('T', ' ') + '\n'
            + lines.join('\n') + '\n';
        fs.writeFileSync(ASSET, current.trimEnd() + '\n' + addition);
        console.log(`\n${C.green}Appended to ${path.relative(process.cwd(), ASSET)}${C.reset}`);
        console.log(`${C.yellow}Now rebuild the APK (push to main, or Gradle assembleRelease) for these keys to work.${C.reset}`);
    } else {
        console.log(`\n${C.dim}Paste these lines into app/src/main/assets/keys.txt:${C.reset}\n`);
        for (const l of lines) console.log('  ' + l);
        console.log(`\n${C.dim}or re-run with --write to append them automatically.${C.reset}`);
    }

    maybeCsv(keys);
    console.log(`\n${C.bold}Reminder:${C.reset} the built-in owner key ${C.violet}SHADOWCLIENT${C.reset} always works.`);
    console.log(`${C.dim}Offline keys require an APK rebuild. For keys you can revoke instantly, use server mode.${C.reset}`);
}

// ------------------------------------------------------------------ csv

function maybeCsv(keys) {
    if (!args.csv || typeof args.csv !== 'string') return;
    const rows = ['key,label,expiry,max_devices'];
    keys.forEach((k, i) => {
        rows.push([k, COUNT > 1 ? `${LABEL}-${i + 1}` : LABEL, EXPIRY_FINAL, DEVICES].join(','));
    });
    fs.writeFileSync(args.csv, rows.join('\n') + '\n');
    console.log(`${C.green}CSV written: ${args.csv}${C.reset}`);
}

// ------------------------------------------------------------------ go

(function main() {
    if (args.help || args.h) {
        console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0].replace(/^\/\*\*?/, ''));
        return;
    }
    if (typeof COUNT !== 'number' || isNaN(COUNT) || COUNT < 1) {
        console.error(`${C.red}--n must be a positive number${C.reset}`);
        process.exit(1);
    }
    if (typeof args.server === 'string') serverMode();
    else offlineMode();
})();
