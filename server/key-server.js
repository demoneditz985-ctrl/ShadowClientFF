#!/usr/bin/env node
/**
 * Shadow Client — key server
 * ==========================
 * This is the piece that was lost with your old phone. It validates keys, enforces expiry
 * and binds each key to a device, so a leaked key can be revoked instead of shipped inside
 * the APK. No dependencies — plain Node.js.
 *
 *   node server/key-server.js
 *
 * Environment:
 *   PORT         default 8787
 *   SC_TOKEN     shared secret the app sends as X-SC-Token   (default: change-me-shadow)
 *   ADMIN_TOKEN  secret for the /admin endpoints             (default: change-me-admin)
 *   KEYS_FILE    where keys are stored                       (default: ./keys.json)
 *
 * App side: open app/src/main/java/com/shadowclient/ff/Config.java and set
 *   KEY_API_URL   = "https://your-host/validate"
 *   KEY_API_TOKEN = SC_TOKEN
 *
 * Endpoints
 *   GET  /health
 *   GET  /validate?key=SHADOW-1&hwid=ABC123&v=1.0.0        (X-SC-Token required)
 *   GET  /admin/keys                                       (Bearer ADMIN_TOKEN)
 *   POST /admin/keys      {key?,label,expiry,max_devices}   → creates a key
 *   DELETE /admin/keys/:key                                 → revokes instantly
 *   POST /admin/reset-device {key}                          → lets a buyer move devices
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { URL } = require('url');

const PORT = process.env.PORT || 8787;
const SC_TOKEN = process.env.SC_TOKEN || 'change-me-shadow';
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || 'change-me-admin';
const KEYS_FILE = process.env.KEYS_FILE || path.join(process.cwd(), 'keys.json');

// ---------------------------------------------------------------- storage

function load() {
    try {
        return JSON.parse(fs.readFileSync(KEYS_FILE, 'utf8'));
    } catch (e) {
        return { keys: {} };
    }
}

function save(db) {
    fs.writeFileSync(KEYS_FILE, JSON.stringify(db, null, 2));
}

function normKey(k) {
    return String(k || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase();
}

function genKey() {
    const hex = crypto.randomBytes(6).toString('hex').toUpperCase();
    return 'SC-' + hex.slice(0, 4) + '-' + hex.slice(4, 8) + '-' + hex.slice(8, 12);
}

function today() {
    return new Date().toISOString().slice(0, 10);
}

// ---------------------------------------------------------------- helpers

function json(res, code, obj) {
    const body = JSON.stringify(obj);
    res.writeHead(code, {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'Access-Control-Allow-Origin': '*'
    });
    res.end(body);
}

function readBody(req) {
    return new Promise(resolve => {
        let b = '';
        req.on('data', c => (b += c));
        req.on('end', () => {
            try {
                resolve(b ? JSON.parse(b) : {});
            } catch (e) {
                resolve({});
            }
        });
    });
}

function isAdmin(req) {
    const h = req.headers['authorization'] || '';
    return h === 'Bearer ' + ADMIN_TOKEN;
}

/** Expiry handling: missing / LIFETIME = never expires. */
function isExpired(key) {
    if (!key.expiry || key.expiry === 'LIFETIME') return false;
    return key.expiry < today();
}

// ---------------------------------------------------------------- validate

function validate(url, req, res) {
    if ((req.headers['x-sc-token'] || '') !== SC_TOKEN) {
        return json(res, 401, { ok: false, reason: 'bad_token' });
    }

    const key = normKey(url.searchParams.get('key'));
    const hwid = String(url.searchParams.get('hwid') || '').toUpperCase();

    if (!key) return json(res, 400, { ok: false, reason: 'missing_key' });

    const db = load();
    const rec = db.keys[key];
    if (!rec || rec.revoked) return json(res, 200, { ok: false, reason: 'invalid' });
    if (isExpired(rec)) return json(res, 200, { ok: false, reason: 'expired' });

    const maxDevices = rec.max_devices == null ? 1 : rec.max_devices;

    if (maxDevices !== 0 && hwid) {
        rec.devices = rec.devices || [];
        if (!rec.devices.includes(hwid)) {
            if (rec.devices.length >= maxDevices) {
                return json(res, 200, { ok: false, reason: 'device_limit', label: rec.label });
            }
            rec.devices.push(hwid);
            save(db);
        }
    }

    console.log(`[ok]   ${key}  hwid=${hwid || '-'}  dev=${rec.devices ? rec.devices.length : 0}`);
    return json(res, 200, {
        ok: true,
        label: rec.label || 'KEY',
        expiry: rec.expiry || 'LIFETIME',
        max_devices: maxDevices
    });
}

// ---------------------------------------------------------------- admin

async function admin(req, res, url) {
    if (!isAdmin(req)) return json(res, 401, { ok: false, reason: 'unauthorized' });

    const db = load();
    const parts = url.pathname.split('/').filter(Boolean);   // ["admin","keys", maybe id]

    if (req.method === 'GET' && parts[1] === 'keys') {
        const out = Object.entries(db.keys).map(([k, v]) => ({
            key: k,
            label: v.label,
            expiry: v.expiry || 'LIFETIME',
            max_devices: v.max_devices == null ? 1 : v.max_devices,
            devices: (v.devices || []).length,
            revoked: !!v.revoked,
            expired: isExpired(v)
        }));
        return json(res, 200, { ok: true, count: out.length, keys: out });
    }

    if (req.method === 'POST' && parts[1] === 'keys') {
        const body = await readBody(req);
        const key = normKey(body.key) || normKey(genKey());
        if (db.keys[key]) return json(res, 409, { ok: false, reason: 'exists' });
        db.keys[key] = {
            label: body.label || 'KEY',
            expiry: body.expiry || 'LIFETIME',
            max_devices: body.max_devices == null ? 1 : body.max_devices,
            devices: [],
            created: new Date().toISOString()
        };
        save(db);
        console.log('[new]  ' + key);
        return json(res, 200, { ok: true, key, ...db.keys[key] });
    }

    if (req.method === 'DELETE' && parts[1] === 'keys' && parts[2]) {
        const key = normKey(decodeURIComponent(parts[2]));
        if (!db.keys[key]) return json(res, 404, { ok: false, reason: 'not_found' });
        db.keys[key].revoked = true;
        save(db);
        console.log('[revoke] ' + key);
        return json(res, 200, { ok: true, key, revoked: true });
    }

    if (req.method === 'POST' && parts[1] === 'reset-device') {
        const body = await readBody(req);
        const key = normKey(body.key);
        if (!db.keys[key]) return json(res, 404, { ok: false, reason: 'not_found' });
        db.keys[key].devices = [];
        save(db);
        return json(res, 200, { ok: true, key, devices: [] });
    }

    return json(res, 404, { ok: false, reason: 'not_found' });
}

// ---------------------------------------------------------------- server

const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');

    if (req.method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': '*',
            'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS'
        });
        return res.end();
    }

    if (url.pathname === '/health') {
        const db = load();
        return json(res, 200, { ok: true, service: 'shadow-client-key-server', keys: Object.keys(db.keys).length });
    }

    if (url.pathname === '/validate') return validate(url, req, res);
    if (url.pathname.startsWith('/admin')) return admin(req, res, url);

    json(res, 200, {
        ok: true,
        service: 'Shadow Client key server',
        endpoints: ['/health', '/validate?key=&hwid=', '/admin/keys', '/admin/reset-device']
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`Shadow Client key server on http://0.0.0.0:${PORT}`);
    console.log(`  storage : ${KEYS_FILE}`);
    console.log(`  SC_TOKEN: ${SC_TOKEN === 'change-me-shadow' ? '(default — change this!)' : 'set'}`);
});
