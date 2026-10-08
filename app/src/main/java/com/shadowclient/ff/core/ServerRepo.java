package com.shadowclient.ff.core;

import android.content.Context;
import android.text.TextUtils;

import com.shadowclient.ff.util.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * The node list behind "SELECT SERVER".
 *
 * <p>Stored as JSON in SharedPreferences. Ships with two DEMO hosts so the ping readout is
 * alive before you add your own node — replace them via the ADD SERVER dialog.
 */
public final class ServerRepo {

    public static final String PREF_LIST = "servers_json";
    public static final String PREF_SELECTED = "server_selected";

    private ServerRepo() {
    }

    public static List<NodeConfig> all(Context c) {
        String raw = Prefs.str(c, PREF_LIST, null);
        List<NodeConfig> out = new ArrayList<>();
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    out.add(NodeConfig.fromJson(arr.getJSONObject(i)));
                }
            } catch (Exception ignored) {
            }
        }
        if (out.isEmpty()) {
            out.add(demo("PING TEST · Cloudflare", "1.1.1.1", 443));
            out.add(demo("PING TEST · Google", "8.8.8.8", 443));
            save(c, out);
        }
        return out;
    }

    /** A placeholder entry: used only to show a live ping until real node details are added. */
    private static NodeConfig demo(String name, String host, int port) {
        NodeConfig n = new NodeConfig();
        n.name = name;
        n.type = NodeConfig.T_VLESS;
        n.host = host;
        n.port = port;
        n.tls = false;
        n.id = "";
        return n;
    }

    public static boolean isDemo(NodeConfig n) {
        return TextUtils.isEmpty(n.id);
    }

    public static void save(Context c, List<NodeConfig> list) {
        JSONArray arr = new JSONArray();
        for (NodeConfig n : list) arr.put(n.toJson());
        Prefs.set(c, PREF_LIST, arr.toString());
    }

    public static void add(Context c, NodeConfig node) {
        List<NodeConfig> list = all(c);
        // a real node replaces the demo placeholders
        if (!isDemo(node)) {
            List<NodeConfig> cleaned = new ArrayList<>();
            for (NodeConfig n : list) {
                if (!isDemo(n)) cleaned.add(n);
            }
            cleaned.add(node);
            save(c, cleaned);
        } else {
            list.add(node);
            save(c, list);
        }
    }

    public static void remove(Context c, NodeConfig node) {
        List<NodeConfig> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).host.equals(node.host) && list.get(i).port == node.port) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    public static NodeConfig selected(Context c) {
        String raw = Prefs.str(c, PREF_SELECTED, null);
        if (!TextUtils.isEmpty(raw)) {
            try {
                return NodeConfig.fromJson(new JSONObject(raw));
            } catch (Exception ignored) {
            }
        }
        List<NodeConfig> list = all(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public static void select(Context c, NodeConfig node) {
        Prefs.set(c, PREF_SELECTED, node.toJson().toString());
    }

    /** Real TCP connect timing. Returns milliseconds, or -1 when the host does not answer. */
    public static int ping(String host, int port, int timeoutMs) {
        if (TextUtils.isEmpty(host)) return -1;
        Socket socket = new Socket();
        try {
            long t0 = System.nanoTime();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            int ms = (int) ((System.nanoTime() - t0) / 1_000_000L);
            return Math.max(1, ms);
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
