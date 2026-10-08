package com.shadowclient.ff.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.shadowclient.ff.Config;
import com.shadowclient.ff.R;
import com.shadowclient.ff.auth.KeyManager;
import com.shadowclient.ff.core.NodeConfig;
import com.shadowclient.ff.core.ServerRepo;
import com.shadowclient.ff.core.ShadowEngine;
import com.shadowclient.ff.overlay.FloatingIconService;
import com.shadowclient.ff.util.GameLauncher;
import com.shadowclient.ff.util.Prefs;
import com.shadowclient.ff.util.Ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_NOTIF = 1002;
    private static final int REQ_VPN = 1003;

    private TextView chipStatus, tvModuleGame, tvModuleServer, tvPing, tvModuleStatus;
    private TextView chipFF, chipFFMax, tvKeyMasked, tvActivated, tvExpires, tvServerCount, tvFooter;
    private LinearLayout containerServers;
    private SwitchCompat swOverlay;
    private Button btnModule;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Map<String, Integer> pings = new HashMap<>();
    private boolean suppressSwitch;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        if (!KeyManager.isActive(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_dashboard);

        chipStatus = findViewById(R.id.chipStatus);
        tvModuleGame = findViewById(R.id.tvModuleGame);
        tvModuleServer = findViewById(R.id.tvModuleServer);
        tvPing = findViewById(R.id.tvPing);
        tvModuleStatus = findViewById(R.id.tvModuleStatus);
        chipFF = findViewById(R.id.chipFF);
        chipFFMax = findViewById(R.id.chipFFMax);
        containerServers = findViewById(R.id.containerServers);
        tvServerCount = findViewById(R.id.tvServerCount);
        swOverlay = findViewById(R.id.swOverlay);
        btnModule = findViewById(R.id.btnModule);
        tvKeyMasked = findViewById(R.id.tvKeyMasked);
        tvActivated = findViewById(R.id.tvActivated);
        tvExpires = findViewById(R.id.tvExpires);
        tvFooter = findViewById(R.id.tvFooter);

        tvFooter.setText(Config.BRAND + " v" + Config.VERSION + " · " + ShadowEngine.statusLine());

        findViewById(R.id.imgLogo).setOnClickListener(v -> Ui.openUrl(this, Config.TELEGRAM_URL));
        chipFF.setOnClickListener(v -> selectGame("FF"));
        chipFFMax.setOnClickListener(v -> selectGame("FFMAX"));

        findViewById(R.id.btnLaunchFF).setOnClickListener(v -> launchGame(GameLauncher.PKG_FF));
        findViewById(R.id.btnLaunchFFMax).setOnClickListener(v -> launchGame(GameLauncher.PKG_FFMAX));

        findViewById(R.id.btnTelegram).setOnClickListener(v -> Ui.openUrl(this, Config.TELEGRAM_URL));
        findViewById(R.id.btnAddServer).setOnClickListener(v -> addServerDialog());
        findViewById(R.id.btnSignOut).setOnClickListener(v -> signOut());
        btnModule.setOnClickListener(v -> moduleButton());

        swOverlay.setOnCheckedChangeListener((btn, checked) -> {
            if (suppressSwitch) return;
            if (checked) {
                Prefs.setFlag(this, Prefs.KEY_OVERLAY, true);
                ensureOverlayRunning();
            } else {
                Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
                FloatingIconService.stop(this);
            }
        });

        renderAccount();
        renderGame();
        renderServers();
        refreshSwitch();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!KeyManager.isActive(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        renderAccount();
        refreshSwitch();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ account

    private void renderAccount() {
        tvKeyMasked.setText(KeyManager.mask(KeyManager.activeKey(this)));
        tvActivated.setText(KeyManager.prettyDate(KeyManager.activatedAt(this)));
        tvExpires.setText(KeyManager.prettyDate(KeyManager.activeExpiry(this)));
    }

    private void signOut() {
        Ui.confirm(this, getString(R.string.sign_out_title), getString(R.string.sign_out_msg), () -> {
            FloatingIconService.stop(this);
            KeyManager.signOut(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
    }

    // ------------------------------------------------------------------ game + nodes

    private void launchGame(String pkg) {
        if (!GameLauncher.isInstalled(this, pkg)) {
            Ui.toast(this, getString(R.string.launch_not_installed, GameLauncher.titleFor(pkg)));
        }
        GameLauncher.launch(this, pkg);
    }

    private void selectGame(String game) {
        Prefs.set(this, Prefs.KEY_GAME, game);
        renderGame();
        refreshStatus();
    }

    private void renderGame() {
        String game = Prefs.str(this, Prefs.KEY_GAME, "FF");
        boolean ff = !"FFMAX".equals(game);
        style(chipFF, ff);
        style(chipFFMax, !ff);
        chipFF.setText(R.string.ff);
        chipFFMax.setText(R.string.ffmax);
        tvModuleGame.setText(ff ? getString(R.string.ff) : getString(R.string.ffmax));
    }

    private void style(TextView v, boolean active) {
        v.setBackgroundResource(active ? R.drawable.bg_server_row_selected : R.drawable.bg_server_row);
        v.setTextColor(Color.parseColor(active ? "#C98BFF" : "#A79FB8"));
        v.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void renderServers() {
        containerServers.removeAllViews();
        List<NodeConfig> list = ServerRepo.all(this);
        NodeConfig selected = ServerRepo.selected(this);
        tvServerCount.setText(list.size() + " NODES");

        LayoutInflater inf = LayoutInflater.from(this);
        for (NodeConfig n : list) {
            View row = inf.inflate(R.layout.item_server, containerServers, false);
            TextView name = row.findViewById(R.id.rowName);
            TextView host = row.findViewById(R.id.rowHost);
            TextView ping = row.findViewById(R.id.rowPing);

            name.setText(n.name);
            host.setText(n.typeLabel().toUpperCase() + " · " + n.endpoint());
            Integer cached = pings.get(n.host);
            ping.setText(cached == null ? getString(R.string.dash_latency_idle)
                    : (cached < 0 ? "TIMEOUT" : cached + " ms"));

            boolean isSel = selected != null && selected.host.equals(n.host) && selected.port == n.port;
            row.setBackgroundResource(isSel ? R.drawable.bg_server_row_selected : R.drawable.bg_server_row);

            row.setOnClickListener(v -> {
                ServerRepo.select(this, n);
                renderServers();
                refreshStatus();
            });
            row.setOnLongClickListener(v -> {
                Ui.confirm(this, "Remove node?", n.endpoint(), () -> {
                    ServerRepo.remove(this, n);
                    renderServers();
                    refreshStatus();
                });
                return true;
            });

            containerServers.addView(row);
        }
        pingAll();
    }

    private void pingAll() {
        List<NodeConfig> list = ServerRepo.all(this);
        for (int i = 0; i < list.size(); i++) {
            final NodeConfig n = list.get(i);
            final View row = containerServers.getChildAt(i);
            if (row == null) continue;
            final TextView ping = row.findViewById(R.id.rowPing);
            ping.setText("…");
            io.execute(() -> {
                int ms = ServerRepo.ping(n.host, n.port, 2500);
                pings.put(n.host, ms);
                runOnUiThread(() -> ping.setText(ms < 0 ? "TIMEOUT" : ms + " ms"));
            });
        }
    }

    private void addServerDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);
        Spinner spType = content.findViewById(R.id.spType);
        EditText etName = content.findViewById(R.id.etName);
        EditText etHost = content.findViewById(R.id.etHost);
        EditText etPort = content.findViewById(R.id.etPort);
        EditText etId = content.findViewById(R.id.etId);
        EditText etSni = content.findViewById(R.id.etSni);
        EditText etObfs = content.findViewById(R.id.etObfs);

        final String[] labels = {"VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spType.setAdapter(adapter);
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String type = NodeConfig.TYPES[position];
                if (NodeConfig.T_VMESS.equals(type) || NodeConfig.T_VLESS.equals(type)) {
                    etId.setHint(R.string.node_id_uuid);
                } else if (NodeConfig.T_SS.equals(type)) {
                    etId.setHint(R.string.node_id_ss);
                } else {
                    etId.setHint(R.string.node_id_password);
                }
                etObfs.setVisibility(NodeConfig.T_HY2.equals(type) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.node_add_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String host = etHost.getText().toString().trim();
                    if (TextUtils.isEmpty(host)) return;

                    NodeConfig n = new NodeConfig();
                    n.type = NodeConfig.TYPES[spType.getSelectedItemPosition()];
                    n.name = TextUtils.isEmpty(etName.getText().toString().trim())
                            ? n.typeLabel().toUpperCase() + " " + host : etName.getText().toString().trim();
                    n.host = host;
                    try {
                        String p = etPort.getText().toString().trim();
                        if (!p.isEmpty()) n.port = Integer.parseInt(p);
                    } catch (Exception ignored) {
                    }
                    n.id = etId.getText().toString().trim();
                    n.sni = etSni.getText().toString().trim();
                    n.obfsPassword = etObfs.getText().toString().trim();
                    n.tls = !NodeConfig.T_SS.equals(n.type);

                    ServerRepo.add(this, n);
                    ServerRepo.select(this, n);
                    renderServers();
                    refreshStatus();
                })
                .show();
    }

    // ------------------------------------------------------------------ status + engine

    private void refreshStatus() {
        NodeConfig n = ServerRepo.selected(this);
        tvModuleServer.setText(n == null ? "—" : n.endpoint());
        Integer ms = n == null ? null : pings.get(n.host);
        tvPing.setText(ms == null ? getString(R.string.dash_latency_idle) : (ms < 0 ? "TIMEOUT" : ms + " ms"));

        boolean running = ShadowEngine.isRunning();
        tvModuleStatus.setText(ShadowEngine.statusLine());

        if (running) {
            chipStatus.setText(R.string.dash_status_running);
            chipStatus.setTextColor(Color.parseColor("#3DDC97"));
            btnModule.setText(R.string.dash_disconnect);
        } else {
            chipStatus.setText(ShadowEngine.isInstalled() ? R.string.dash_status_ready : R.string.dash_status_offline);
            chipStatus.setTextColor(Color.parseColor(ShadowEngine.isInstalled() ? "#C98BFF" : "#A79FB8"));
            btnModule.setText(R.string.dash_connect);
        }
        tvFooter.setText(Config.BRAND + " v" + Config.VERSION + " · " + ShadowEngine.statusLine());
    }

    private void moduleButton() {
        if (!ShadowEngine.isInstalled()) {
            Ui.info(this, ShadowEngine.statusLine(),
                    "This build has no tunnel engine inside it yet.\n\n"
                            + "Everything else — key system, floating menu, node list, ping, Telegram — "
                            + "works right now. See SETUP.md §5 to drop an engine in (the app picks it up "
                            + "automatically, no other code changes).");
            return;
        }
        NodeConfig n = ServerRepo.selected(this);
        if (n == null || TextUtils.isEmpty(n.host)) {
            Ui.info(this, "No node selected", "Tap ADD SERVER and paste the details of your own node.");
            return;
        }
        if (ServerRepo.isDemo(n)) {
            Ui.info(this, "Demo entry selected",
                    "This entry is only a ping test target.\n\nTap ADD SERVER and enter your own node "
                            + "(host, port, UUID/password) to connect.");
            return;
        }
        if (ShadowEngine.isRunning()) {
            ShadowEngine.stop(this);
            refreshStatus();
            return;
        }

        // Android asks the user once per app before any tunnel may be created.
        try {
            Intent consent = VpnService.prepare(this);
            if (consent != null) {
                startActivityForResult(consent, REQ_VPN);
                return;
            }
        } catch (Exception ignored) {
        }
        startEngine();
    }

    private void startEngine() {
        NodeConfig n = ServerRepo.selected(this);
        tvModuleStatus.setText("CONNECTING…");
        ShadowEngine.start(this, n, (state, detail) -> runOnUiThread(() -> {
            tvModuleStatus.setText(state);
            chipStatus.setText(state);
            refreshStatus();
        }));
    }

    // ------------------------------------------------------------------ overlay

    private void refreshSwitch() {
        suppressSwitch = true;
        swOverlay.setChecked(FloatingIconService.isWanted(this) && Ui.hasOverlay(this));
        suppressSwitch = false;
    }

    private void ensureOverlayRunning() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        if (!Ui.hasOverlay(this)) {
            Ui.info(this, getString(R.string.perm_overlay_title), getString(R.string.perm_overlay_msg));
            Ui.requestOverlay(this, REQ_OVERLAY);
            return;
        }
        FloatingIconService.start(this);
        Ui.toast(this, getString(R.string.overlay_started));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_NOTIF) ensureOverlayRunning();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_VPN) {
            if (result == RESULT_OK) {
                startEngine();
            } else {
                Ui.info(this, "VPN permission denied",
                        "Android needs your permission before Shadow Client can create a tunnel. "
                                + "Tap ACTIVATE MODULE and choose OK to allow it.");
            }
            return;
        }
        if (code == REQ_OVERLAY) {
            refreshSwitch();
            if (Ui.hasOverlay(this) && Prefs.flag(this, Prefs.KEY_OVERLAY, false)) {
                FloatingIconService.start(this);
                Ui.toast(this, getString(R.string.overlay_started));
            }
        }
    }
}
