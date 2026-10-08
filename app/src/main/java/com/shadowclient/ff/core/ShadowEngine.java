package com.shadowclient.ff.core;

import android.content.Context;

/**
 * ============================ ENGINE PLUG-IN POINT ============================
 *
 * Everything around the tunnel is finished: dark theme, key system, floating icon,
 * server list with real ping, Telegram community.
 *
 * The actual traffic engine is intentionally NOT included in this project, and this
 * app does not pretend otherwise: until an engine is registered here, the dashboard
 * shows "ENGINE NOT INSTALLED" and the module button explains what to do.
 *
 * How to finish it, whenever you are ready:
 *
 *   1. Add your engine to the module — a native library in app/src/main/jniLibs/ or a
 *      dependency, e.g. a VpnService-based client (sing-box, Xray, hysteria, …).
 *   2. Implement the {@link Tunnel} interface below and hand it to {@link #install(Tunnel)}.
 *   3. Call it from DashboardActivity's module button instead of the placeholder dialog.
 *
 * See SETUP.md for the two common layouts (VpnService engine, or remote proxy in front of
 * the game server). Keep it honest and legal for your own users and your own nodes.
 * =============================================================================
 */
public final class ShadowEngine {

    public interface Tunnel {
        void start(Context ctx, NodeConfig node, Listener listener);

        void stop(Context ctx);

        boolean isRunning();
    }

    public interface Listener {
        void onState(String state, String detail);
    }

    private static Tunnel tunnel;

    private ShadowEngine() {
    }

    public static void install(Tunnel t) {
        tunnel = t;
    }

    public static boolean isInstalled() {
        return tunnel != null;
    }

    public static boolean isRunning() {
        return tunnel != null && tunnel.isRunning();
    }

    public static void start(Context c, NodeConfig node, Listener l) {
        if (tunnel == null) {
            l.onState("ENGINE NOT INSTALLED", "See SETUP.md → Engine plug-in point");
            return;
        }
        tunnel.start(c, node, l);
    }

    public static void stop(Context c) {
        if (tunnel != null) tunnel.stop(c);
    }

    public static String statusLine() {
        return isInstalled() ? (isRunning() ? "RUNNING" : "READY") : "ENGINE NOT INSTALLED";
    }
}
