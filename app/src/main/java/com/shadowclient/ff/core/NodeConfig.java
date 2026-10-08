package com.shadowclient.ff.core;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * One proxy node — everything the engine needs to connect, plus a conversion to a
 * sing-box outbound object.
 *
 * <p>Supported types: VLESS, VMess, Trojan, Shadowsocks, Hysteria2.
 * Nothing here is secret to the app: users paste in the node details they were given.
 */
public class NodeConfig {

    public static final String T_VLESS = "vless";
    public static final String T_VMESS = "vmess";
    public static final String T_TROJAN = "trojan";
    public static final String T_SS = "shadowsocks";
    public static final String T_HY2 = "hysteria2";

    public static final String[] TYPES = {T_VLESS, T_VMESS, T_TROJAN, T_SS, T_HY2};

    public String name = "NODE";
    public String type = T_VLESS;
    public String host = "";
    public int port = 443;
    public String id = "";        // uuid (vless/vmess) or password (trojan/ss/hysteria2)
    public String method = "aes-128-gcm";   // shadowsocks cipher
    public String sni = "";       // TLS server name (optional)
    public String obfsPassword = "";        // hysteria2 salamander obfs (optional)
    public boolean tls = true;

    public NodeConfig() {
    }

    public String endpoint() {
        return host + ":" + port;
    }

    public String typeLabel() {
        switch (type) {
            case T_VMESS: return "VMess";
            case T_TROJAN: return "Trojan";
            case T_SS: return "Shadowsocks";
            case T_HY2: return "Hysteria2";
            default: return "VLESS";
        }
    }

    // ------------------------------------------------------------ json

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("type", type);
            o.put("host", host);
            o.put("port", port);
            o.put("id", id);
            o.put("method", method);
            o.put("sni", sni);
            o.put("obfs_password", obfsPassword);
            o.put("tls", tls);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static NodeConfig fromJson(JSONObject o) {
        NodeConfig n = new NodeConfig();
        n.name = o.optString("name", "NODE");
        n.type = o.optString("type", T_VLESS);
        n.host = o.optString("host", "");
        n.port = o.optInt("port", 443);
        n.id = o.optString("id", "");
        n.method = o.optString("method", "aes-128-gcm");
        n.sni = o.optString("sni", "");
        n.obfsPassword = o.optString("obfs_password", "");
        n.tls = o.optBoolean("tls", true);
        return n;
    }

    /** sing-box outbound object for this node (tag "proxy"). */
    public JSONObject toOutbound() throws Exception {
        JSONObject o = new JSONObject();
        o.put("tag", "proxy");
        o.put("type", type);
        o.put("server", host);
        o.put("server_port", port);

        switch (type) {
            case T_VMESS: {
                o.put("uuid", id);
                o.put("security", "auto");
                o.put("alter_id", 0);
                break;
            }
            case T_TROJAN: {
                o.put("password", id);
                break;
            }
            case T_SS: {
                o.put("method", TextUtils.isEmpty(method) ? "aes-128-gcm" : method);
                o.put("password", id);
                break;
            }
            case T_HY2: {
                o.put("password", id);
                if (!TextUtils.isEmpty(obfsPassword)) {
                    JSONObject obfs = new JSONObject();
                    obfs.put("type", "salamander");
                    obfs.put("password", obfsPassword);
                    o.put("obfs", obfs);
                }
                break;
            }
            case T_VLESS:
            default: {
                o.put("uuid", id);
                break;
            }
        }

        if (tls && !T_SS.equals(type) && !T_HY2.equals(type)) {
            JSONObject t = new JSONObject();
            t.put("enabled", true);
            if (!TextUtils.isEmpty(sni)) t.put("server_name", sni);
            t.put("insecure", false);
            o.put("tls", t);
        }
        if (!TextUtils.isEmpty(sni) && T_HY2.equals(type)) {
            JSONObject t = new JSONObject();
            t.put("enabled", true);
            t.put("server_name", sni);
            o.put("tls", t);
        }
        return o;
    }

    /**
     * Full sing-box config for an Android tunnel.
     *
     * @param allowedPackages apps that go through the tunnel (empty = everything)
     */
    public String toSingboxConfig(JSONArray allowedPackages) throws Exception {
        JSONObject root = new JSONObject();

        JSONObject log = new JSONObject();
        log.put("level", "warn");
        root.put("log", log);

        JSONArray inbounds = new JSONArray();
        JSONObject tun = new JSONObject();
        tun.put("type", "tun");
        tun.put("tag", "tun-in");
        tun.put("address", new JSONArray().put("172.19.0.1/30"));
        tun.put("mtu", 8500);
        tun.put("auto_route", true);
        tun.put("strict_route", false);
        tun.put("stack", "gvisor");
        if (allowedPackages != null && allowedPackages.length() > 0) {
            tun.put("include_package", allowedPackages);
        }
        inbounds.put(tun);
        root.put("inbounds", inbounds);

        JSONArray outbounds = new JSONArray();
        outbounds.put(toOutbound());
        JSONObject direct = new JSONObject();
        direct.put("type", "direct");
        direct.put("tag", "direct");
        outbounds.put(direct);
        root.put("outbounds", outbounds);

        JSONArray rules = new JSONArray();
        JSONObject dnsRule = new JSONObject();
        dnsRule.put("protocol", "dns");
        dnsRule.put("outbound", "direct");
        rules.put(dnsRule);
        root.put("route", new JSONObject()
                .put("rules", rules)
                .put("auto_detect_interface", true)
                .put("final", "proxy"));

        JSONObject dns = new JSONObject();
        JSONArray servers = new JSONArray();
        servers.put(new JSONObject().put("address", "1.1.1.1").put("detour", "proxy"));
        servers.put(new JSONObject().put("address", "8.8.8.8").put("detour", "proxy"));
        dns.put("servers", servers);
        root.put("dns", dns);

        return root.toString();
    }
}
