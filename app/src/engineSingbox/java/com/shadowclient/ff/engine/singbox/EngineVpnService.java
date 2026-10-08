package com.shadowclient.ff.engine.singbox;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shadowclient.ff.Config;
import com.shadowclient.ff.R;
import com.shadowclient.ff.ShadowApp;
import com.shadowclient.ff.core.NodeConfig;
import com.shadowclient.ff.core.ShadowEngine;
import com.shadowclient.ff.util.GameLauncher;

import org.json.JSONArray;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.nekohasekai.libbox.BridgeOptions;
import io.nekohasekai.libbox.BridgeSession;
import io.nekohasekai.libbox.CommandServer;
import io.nekohasekai.libbox.CommandServerHandler;
import io.nekohasekai.libbox.ConnectionOwner;
import io.nekohasekai.libbox.InterfaceUpdateListener;
import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.LocalDNSTransport;
import io.nekohasekai.libbox.NeighborUpdateListener;
import io.nekohasekai.libbox.NetworkInterfaceIterator;
import io.nekohasekai.libbox.OverrideOptions;
import io.nekohasekai.libbox.PlatformInterface;
import io.nekohasekai.libbox.PlatformUser;
import io.nekohasekai.libbox.RoutePrefix;
import io.nekohasekai.libbox.RoutePrefixIterator;
import io.nekohasekai.libbox.SetupOptions;
import io.nekohasekai.libbox.ShellSession;
import io.nekohasekai.libbox.StringIterator;
import io.nekohasekai.libbox.SystemProxyStatus;
import io.nekohasekai.libbox.TunOptions;
import io.nekohasekai.libbox.WIFIState;

/**
 * Runs the sing-box core inside a VpnService.
 *
 * <p>Mirrors the platform glue of sing-box's own Android client, so the core behaves the way
 * upstream expects: TUN device creation, interface discovery, socket protection and the
 * command server that owns the runtime.
 */
public class EngineVpnService extends VpnService implements PlatformInterface,
        CommandServerHandler, InterfaceUpdateListener {

    private static final String TAG = "ShadowVpn";
    private static final String EXTRA_CONFIG = "config";
    private static final int NOTIF_ID = 0x5C02;

    private static volatile boolean running;
    private static ShadowEngine.Listener listener;

    private CommandServer commandServer;
    private ConnectivityManager connectivityManager;
    private NetworkCallbackImpl networkCallback;
    private ParcelFileDescriptor tun;
    private boolean coreSetup;

    // ------------------------------------------------------------------ control

    public static void start(Context context, NodeConfig node, ShadowEngine.Listener stateListener) {
        listener = stateListener;
        String config;
        try {
            JSONArray allowed = new JSONArray();
            allowed.put(GameLauncher.PKG_FF);
            allowed.put(GameLauncher.PKG_FFMAX);
            config = node.toSingboxConfig(allowed);
        } catch (Exception e) {
            state("ERROR", "bad node config: " + e.getMessage());
            return;
        }
        Intent i = new Intent(context, EngineVpnService.class)
                .putExtra(EXTRA_CONFIG, config);
        context.startForegroundService(i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, EngineVpnService.class));
    }

    public static boolean isRunning() {
        return running;
    }

    private static void state(String s, String detail) {
        if (listener != null) listener.onState(s, detail);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getStringExtra(EXTRA_CONFIG) == null) {
            return START_NOT_STICKY;
        }
        if (VpnService.prepare(this) != null) {
            state("VPN PERMISSION NEEDED", "grant the VPN request, then tap again");
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification());

        final String config = intent.getStringExtra(EXTRA_CONFIG);

        try {
            prepareCore();
            if (commandServer == null) {
                commandServer = new CommandServer(this, this);
                commandServer.start();
            }
            OverrideOptions override = new OverrideOptions();
            override.setAutoRedirect(false);
            commandServer.startOrReloadService(config, override);
            running = true;
            state("RUNNING", "tunnel established");
            Log.i(TAG, "service started");
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
            running = false;
            state("ERROR", String.valueOf(t.getMessage()));
            stopSelf();
        }
        return START_STICKY;
    }

    private void prepareCore() {
        if (coreSetup) return;
        File base = new File(getFilesDir(), "sing-box");
        File working = new File(base, "working");
        File temp = new File(getCacheDir(), "sing-box-temp");
        //noinspection ResultOfMethodCallIgnored
        base.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        working.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        temp.mkdirs();

        SetupOptions options = new SetupOptions();
        options.setBasePath(base.getAbsolutePath());
        options.setWorkingPath(working.getAbsolutePath());
        options.setTempPath(temp.getAbsolutePath());
        options.setFixAndroidStack(true);
        options.setLogMaxLines(1000);
        options.setDebug(false);
        options.setCrashReportSource("ShadowClient");
        options.setAppVersion(String.valueOf(Config.VERSION));
        options.setAppMarketingVersion(Config.VERSION);
        options.setOomKillerEnabled(false);
        options.setPowerReportEnabled(false);
        Libbox.setup(options);
        coreSetup = true;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, com.shadowclient.ff.ui.DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ShadowApp.CH_OVERLAY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(Config.BRAND)
                .setContentText("Tunnel active")
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (commandServer != null) {
                commandServer.closeService();
                commandServer.close();
            }
        } catch (Throwable t) {
            Log.w(TAG, "close", t);
        }
        commandServer = null;
        releaseTun();
        stopNetworkMonitor();
        state(ShadowEngine.isInstalled() ? "READY" : "OFFLINE", "stopped");
        super.onDestroy();
    }

    private void releaseTun() {
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception ignored) {
            }
            tun = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    // ------------------------------------------------------------------ PlatformInterface: TUN

    @Override
    public int openTun(TunOptions options) {
        if (VpnService.prepare(this) != null) {
            throw new IllegalStateException("android: missing vpn permission");
        }

        Builder builder = new Builder()
                .setSession(Config.BRAND)
                .setMtu(options.getMTU());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        RoutePrefixIterator inet4 = options.getInet4Address();
        while (inet4.hasNext()) {
            RoutePrefix prefix = inet4.next();
            builder.addAddress(prefix.address(), prefix.prefix());
        }
        RoutePrefixIterator inet6 = options.getInet6Address();
        while (inet6.hasNext()) {
            RoutePrefix prefix = inet6.next();
            builder.addAddress(prefix.address(), prefix.prefix());
        }

        if (options.getAutoRoute()) {
            StringIterator dns = options.getDNSServerAddress();
            while (dns.hasNext()) {
                builder.addDnsServer(dns.next());
            }

            RoutePrefixIterator r4 = options.getInet4RouteAddress();
            if (r4.hasNext()) {
                while (r4.hasNext()) {
                    RoutePrefix prefix = r4.next();
                    builder.addRoute(prefix.address(), prefix.prefix());
                }
            } else {
                builder.addRoute("0.0.0.0", 0);
            }

            RoutePrefixIterator r6 = options.getInet6RouteAddress();
            if (r6.hasNext()) {
                while (r6.hasNext()) {
                    RoutePrefix prefix = r6.next();
                    builder.addRoute(prefix.address(), prefix.prefix());
                }
            } else {
                builder.addRoute("::", 0);
            }
        }

        // Only the game (and this app) goes through the tunnel.
        for (String pkg : new String[]{GameLauncher.PKG_FF, GameLauncher.PKG_FFMAX}) {
            try {
                builder.addAllowedApplication(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // game not installed on this phone — skip it
            }
        }

        releaseTun();
        tun = builder.establish();
        if (tun == null) {
            throw new IllegalStateException("android: establish returned null");
        }
        return tun.getFd();
    }

    @Override
    public boolean usePlatformAutoDetectInterfaceControl() {
        return true;
    }

    @Override
    public void autoDetectInterfaceControl(int fd) {
        protect(fd);   // keep the engine's own sockets outside the tunnel
    }

    // ------------------------------------------------------------------ PlatformInterface: network info

    @Override
    public boolean useProcFS() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    @Override
    public NetworkInterfaceIterator getInterfaces() {
        List<io.nekohasekai.libbox.NetworkInterface> out = new ArrayList<>();
        try {
            List<NetworkInterface> javaInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (Network network : connectivityManager.getAllNetworks()) {
                LinkProperties lp = connectivityManager.getLinkProperties(network);
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                if (lp == null || caps == null || lp.getInterfaceName() == null) continue;

                NetworkInterface javaInterface = null;
                for (NetworkInterface candidate : javaInterfaces) {
                    if (candidate.getName().equals(lp.getInterfaceName())) {
                        javaInterface = candidate;
                        break;
                    }
                }
                if (javaInterface == null) continue;

                io.nekohasekai.libbox.NetworkInterface box = new io.nekohasekai.libbox.NetworkInterface();
                box.setName(lp.getInterfaceName());

                List<String> dns = new ArrayList<>();
                for (InetAddress a : lp.getDnsServers()) {
                    if (a.getHostAddress() != null) dns.add(a.getHostAddress());
                }
                box.setDNSServer(new StringList(dns));

                List<String> gateways = new ArrayList<>();
                for (android.net.RouteInfo route : lp.getRoutes()) {
                    if (route.getDestination().getPrefixLength() == 0 && route.getGateway() != null
                            && !route.getGateway().isAnyLocalAddress()
                            && route.getGateway().getHostAddress() != null) {
                        gateways.add(route.getGateway().getHostAddress());
                    }
                }
                box.setGateway(new StringList(gateways));

                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    box.setType(Libbox.InterfaceTypeWIFI);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    box.setType(Libbox.InterfaceTypeCellular);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    box.setType(Libbox.InterfaceTypeEthernet);
                } else {
                    box.setType(Libbox.InterfaceTypeOther);
                }
                box.setIndex(javaInterface.getIndex());
                try {
                    box.setMTU(javaInterface.getMTU());
                } catch (Exception ignored) {
                }

                List<String> addresses = new ArrayList<>();
                for (InterfaceAddress ia : javaInterface.getInterfaceAddresses()) {
                    if (ia.getAddress() == null) continue;
                    addresses.add(ia.getAddress().getHostAddress() + "/" + ia.getNetworkPrefixLength());
                }
                box.setAddresses(new StringList(addresses));

                int flags = 0;
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    flags |= OsConstants.IFF_UP | OsConstants.IFF_RUNNING;
                }
                try {
                    if (javaInterface.isLoopback()) flags |= OsConstants.IFF_LOOPBACK;
                    if (javaInterface.isPointToPoint()) flags |= OsConstants.IFF_POINTOPOINT;
                    if (javaInterface.isUp()) flags |= OsConstants.IFF_UP;
                    if (javaInterface.supportsMulticast()) flags |= OsConstants.IFF_MULTICAST;
                } catch (Exception ignored) {
                }
                box.setFlags(flags);
                box.setMetered(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
                out.add(box);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getInterfaces", t);
        }
        return new NetworkInterfaceList(out);
    }

    @Override
    public void startDefaultInterfaceMonitor(InterfaceUpdateListener listener) {
        stopNetworkMonitor();
        networkCallback = new NetworkCallbackImpl(listener);
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Throwable t) {
            Log.w(TAG, "registerDefaultNetworkCallback", t);
        }
    }

    @Override
    public void closeDefaultInterfaceMonitor(InterfaceUpdateListener listener) {
        stopNetworkMonitor();
    }

    private void stopNetworkMonitor() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
            }
            networkCallback = null;
        }
    }

    private class NetworkCallbackImpl extends ConnectivityManager.NetworkCallback {
        private final InterfaceUpdateListener target;

        NetworkCallbackImpl(InterfaceUpdateListener target) {
            this.target = target;
        }

        @Override
        public void onAvailable(Network network) {
            push(network);
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            push(network);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties lp) {
            push(network);
        }

        private void push(Network network) {
            try {
                LinkProperties lp = connectivityManager.getLinkProperties(network);
                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
                if (lp == null || caps == null || lp.getInterfaceName() == null) return;
                int index = 0;
                NetworkInterface javaInterface = NetworkInterface.getByName(lp.getInterfaceName());
                if (javaInterface != null) index = javaInterface.getIndex();
                boolean expensive = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                target.updateDefaultInterface(lp.getInterfaceName(), index, expensive, false);
            } catch (Throwable t) {
                Log.w(TAG, "push default interface", t);
            }
        }
    }

    @Override
    public ConnectionOwner findConnectionOwner(int ipProtocol, String sourceAddress, int sourcePort,
                                               String destinationAddress, int destinationPort) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            int uid = connectivityManager.getConnectionOwnerUid(ipProtocol,
                    new InetSocketAddress(sourceAddress, sourcePort),
                    new InetSocketAddress(destinationAddress, destinationPort));
            if (uid == Process.INVALID_UID) return null;
            String[] packages = getPackageManager().getPackagesForUid(uid);
            ConnectionOwner owner = new ConnectionOwner();
            owner.setUserId(uid);
            owner.setUserName(packages != null && packages.length > 0 ? packages[0] : "");
            owner.setAndroidPackageNames(new StringList(packages == null
                    ? Collections.emptyList() : Arrays.asList(packages)));
            return owner;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public WIFIState readWIFIState() {
        return null;   // the engine falls back to the interface list
    }

    @Override
    public LocalDNSTransport localDNSTransport() {
        return null;   // DNS is configured from the node's own settings
    }

    @Override
    public boolean underNetworkExtension() {
        return false;
    }

    @Override
    public boolean includeAllNetworks() {
        return false;
    }

    @Override
    public void clearDNSCache() {
    }

    @Override
    public void registerMyInterface(String name) {
    }

    @Override
    public void sendNotification(io.nekohasekai.libbox.Notification notification) {
    }

    @Override
    public void cancelNotification(String identifier, int typeID) {
    }

    // ------------------------------------------------------------------ PlatformInterface: unused features

    @Override
    public boolean usePlatformShell() {
        return false;
    }

    @Override
    public void checkPlatformShell() {
    }

    @Override
    public ShellSession openShellSession(PlatformUser user, String command, StringIterator environ,
                                         String term, int rows, int cols) {
        return null;
    }

    @Override
    public String readSystemSSHHostKey() {
        return null;
    }

    @Override
    public String lookupSFTPServer() {
        return null;
    }

    @Override
    public PlatformUser lookupUser(String username) {
        return null;
    }

    @Override
    public String tailscaleHostname() {
        return null;
    }

    @Override
    public boolean usePlatformBridge() {
        return false;
    }

    @Override
    public BridgeSession createBridge(BridgeOptions options) {
        return null;
    }

    @Override
    public void startNeighborMonitor(NeighborUpdateListener listener) {
    }

    @Override
    public void closeNeighborMonitor(NeighborUpdateListener listener) {
    }

    // ------------------------------------------------------------------ InterfaceUpdateListener

    @Override
    public void updateDefaultInterface(String name, int index, boolean isExpensive, boolean isConstrained) {
    }

    @Override
    public void updateNetworkPath(String name) {
    }

    // ------------------------------------------------------------------ CommandServerHandler

    @Override
    public void serviceReload() {
    }

    @Override
    public void serviceStop() {
        stopSelf();
    }

    @Override
    public void setSystemProxyEnabled(boolean enabled) {
    }

    @Override
    public SystemProxyStatus getSystemProxyStatus() {
        return null;
    }

    @Override
    public void writeDebugMessage(String message) {
        Log.d(TAG, message);
    }

    @Override
    public int connectSSHAgent() {
        return 0;
    }

    @Override
    public void triggerNativeCrash() {
    }
}
