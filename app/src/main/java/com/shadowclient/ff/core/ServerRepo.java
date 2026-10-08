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
 * The server list shown under "SELECT SERVER".
 *
 * <p>Ships with two DEMO hosts so the UI and the ping readout are alive out of the box —
 * replace them with your own nodes. Servers are stored as JSON in SharedPreferences, so you
 * can also push a list to users later via your key server (see SETUP.md).
 */
public final class ServerRepo {

    public static final String PREF_LIST = "servers_json";
    public static final String PREF_SELECTED = "server_selected";

    private ServerRepo() {
    }

    public static final class Server {
        public String name = "SERVER";
        public String host = "";
        public int port = 443;

        public Server() {
        }

        public Server(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String endpoint() {
            return host + ":" + port;
        }

        public JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("host", host);
                o.put("port", port);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static Server fromJson(JSONObject o) {
            Server s = new Server();
            s.name = o.optString("name", "SERVER");
            s.host = o.optString("host", "");
            s.port = o.optInt("port", 443);
            return s;
        }
    }

    public static List<Server> all(Context c) {
        String raw = Prefs.str(c, PREF_LIST, null);
        List<Server> out = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            out.add(new Server("DEMO · Cloudflare", "1.1.1.1", 443));
            out.add(new Server("DEMO · Google", "8.8.8.8", 443));
            save(c, out);
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Server.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            out.add(new Server("DEMO · Cloudflare", "1.1.1.1", 443));
            save(c, out);
        }
        return out;
    }

    public static void save(Context c, List<Server> list) {
        JSONArray arr = new JSONArray();
        for (Server s : list) arr.put(s.toJson());
        Prefs.set(c, PREF_LIST, arr.toString());
    }

    public static void add(Context c, String name, String host, int port) {
        List<Server> list = all(c);
        list.add(new Server(TextUtils.isEmpty(name) ? host : name, host, port));
        save(c, list);
    }

    public static void remove(Context c, Server s) {
        List<Server> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).host.equals(s.host) && list.get(i).port == s.port) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    public static Server selected(Context c) {
        String raw = Prefs.str(c, PREF_SELECTED, null);
        if (!TextUtils.isEmpty(raw)) {
            try {
                return Server.fromJson(new JSONObject(raw));
            } catch (Exception ignored) {
            }
        }
        List<Server> list = all(c);
        return list.isEmpty() ? null : list.get(0);
    }

    public static void select(Context c, Server s) {
        Prefs.set(c, PREF_SELECTED, s.toJson().toString());
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
