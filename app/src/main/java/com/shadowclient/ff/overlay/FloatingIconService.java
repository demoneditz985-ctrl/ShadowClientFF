package com.shadowclient.ff.overlay;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shadowclient.ff.Config;
import com.shadowclient.ff.R;
import com.shadowclient.ff.ShadowApp;
import com.shadowclient.ff.core.NodeConfig;
import com.shadowclient.ff.core.ServerRepo;
import com.shadowclient.ff.core.ShadowEngine;
import com.shadowclient.ff.ui.DashboardActivity;
import com.shadowclient.ff.util.GameLauncher;
import com.shadowclient.ff.util.Prefs;
import com.shadowclient.ff.util.Ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The floating Shadow Client logo.
 *
 * <p>Tap it and it expands into a compact on-screen menu — no need to leave the game:
 * node + live ping, "OPEN FREE FIRE", "FREE FIRE MAX", dashboard, Telegram, hide icon.
 * Drag the logo (or the menu header) to move it; it snaps back inside the screen edges.
 */
public class FloatingIconService extends Service {

    public static final String ACTION_START = "com.shadowclient.ff.action.OVERLAY_START";
    public static final String ACTION_STOP = "com.shadowclient.ff.action.OVERLAY_STOP";
    public static final String ACTION_TOGGLE = "com.shadowclient.ff.action.OVERLAY_TOGGLE";
    private static final int NOTIF_ID = 0x5C01;
    private static final long PING_INTERVAL_MS = 5000L;

    private WindowManager wm;
    private View root, panel, icon;
    private WindowManager.LayoutParams lp;
    private TextView panelStatus, panelServer, panelPing, panelNote;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean expanded;

    public static void start(Context c) {
        Intent i = new Intent(c, FloatingIconService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, FloatingIconService.class).setAction(ACTION_STOP);
        c.startService(i);
    }

    /** Shows/hides the panel of an already-running overlay (used by the dashboard). */
    public static void toggle(Context c) {
        Intent i = new Intent(c, FloatingIconService.class).setAction(ACTION_TOGGLE);
        c.startService(i);
    }

    public static boolean isWanted(Context c) {
        return Prefs.flag(c, Prefs.KEY_OVERLAY, false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE.equals(action)) {
            if (root != null) togglePanel();
            return START_STICKY;
        }

        Prefs.setFlag(this, Prefs.KEY_OVERLAY, true);
        startForeground(NOTIF_ID, buildNotification());

        if (!Ui.hasOverlay(this)) {
            Ui.toast(this, getString(R.string.perm_overlay_msg));
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
            return START_NOT_STICKY;
        }

        showOverlay();
        return START_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, FloatingIconService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ShadowApp.CH_OVERLAY)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(Config.BRAND)
                .setContentText(getString(R.string.dash_floating_icon) + " · " + getString(R.string.menu_note))
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .addAction(0, "STOP", stopPi)
                .build();
    }

    // ------------------------------------------------------------------ overlay

    private void showOverlay() {
        if (root != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        root = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null);
        icon = root.findViewById(R.id.overlayIcon);
        panel = root.findViewById(R.id.overlayPanel);
        panelStatus = root.findViewById(R.id.panelStatus);
        panelServer = root.findViewById(R.id.panelServer);
        panelPing = root.findViewById(R.id.panelPing);
        panelNote = root.findViewById(R.id.panelNote);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) Prefs.num(this, Prefs.KEY_OVERLAY_X, 40);
        lp.y = (int) Prefs.num(this, Prefs.KEY_OVERLAY_Y, 220);

        // collapsed icon: tap to open the menu, drag to move
        icon.setOnClickListener(v -> togglePanel());
        icon.setOnTouchListener(new DragListener());

        // menu header: drag handle
        View header = root.findViewById(R.id.panelHeader);
        header.setOnTouchListener(new DragListener());

        root.findViewById(R.id.panelCollapse).setOnClickListener(v -> togglePanel());

        root.findViewById(R.id.btnLaunchFF).setOnClickListener(v -> {
            boolean ok = GameLauncher.launch(this, GameLauncher.PKG_FF);
            if (ok) collapse();
        });
        root.findViewById(R.id.btnLaunchFFMax).setOnClickListener(v -> {
            boolean ok = GameLauncher.launch(this, GameLauncher.PKG_FFMAX);
            if (ok) collapse();
        });
        root.findViewById(R.id.btnDashboard).setOnClickListener(v -> openDashboard());
        root.findViewById(R.id.btnTelegram).setOnClickListener(v ->
                Ui.openUrl(this, Config.TELEGRAM_URL));
        root.findViewById(R.id.btnHideIcon).setOnClickListener(v -> {
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
        });

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null;
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
            return;
        }
        schedulePing();
    }

    private void togglePanel() {
        if (panel == null) return;
        if (expanded) collapse();
        else expand();
    }

    private void expand() {
        expanded = true;
        icon.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        refreshPanel();
        keepOnScreen();
    }

    private void collapse() {
        expanded = false;
        if (panel != null) panel.setVisibility(View.GONE);
        if (icon != null) icon.setVisibility(View.VISIBLE);
        keepOnScreen();
    }

    private void openDashboard() {
        Intent i = new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
            collapse();
        } catch (Exception e) {
            Ui.toast(this, "Open Shadow Client from your app list");
        }
    }

    // ------------------------------------------------------------------ status

    private void schedulePing() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                pingOnce();
                handler.postDelayed(this, PING_INTERVAL_MS);
            }
        }, 400);
    }

    private void pingOnce() {
        final NodeConfig s = ServerRepo.selected(this);
        if (s == null) return;
        io.execute(() -> {
            final int ms = ServerRepo.ping(s.host, s.port, 2500);
            handler.post(() -> {
                if (panelPing != null) {
                    panelPing.setText(ms < 0 ? "TIMEOUT" : ms + " ms");
                }
            });
        });
    }

    private void refreshPanel() {
        NodeConfig s = ServerRepo.selected(this);
        if (panelServer != null) {
            panelServer.setText(s == null ? "—" : s.endpoint());
        }
        if (panelStatus != null) {
            String state = ShadowEngine.statusLine();
            panelStatus.setText(state);
            panelStatus.setTextColor(0xFFC98BFF);
        }
        if (panelNote != null) {
            panelNote.setText(getString(R.string.menu_note));
        }
        pingOnce();
    }

    // ------------------------------------------------------------------ dragging

    private final class DragListener implements View.OnTouchListener {
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = lp.x;
                    startY = lp.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchX);
                    int dy = (int) (e.getRawY() - touchY);
                    if (Math.abs(dx) > 6 || Math.abs(dy) > 6) moved = true;
                    lp.x = startX + dx;
                    lp.y = startY + dy;
                    apply();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    keepOnScreen();
                    savePosition();
                    return moved;   // a plain tap still reaches the click listener
                default:
                    return false;
            }
        }
    }

    private void apply() {
        try {
            if (root != null && wm != null) wm.updateViewLayout(root, lp);
        } catch (Exception ignored) {
        }
    }

    private void keepOnScreen() {
        try {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int w = root != null && root.getWidth() > 0 ? root.getWidth() : (int) Ui.dp(this, 60);
            int h = root != null && root.getHeight() > 0 ? root.getHeight() : (int) Ui.dp(this, 60);

            lp.x = Math.min(Math.max(lp.x, 0), Math.max(0, screenW - w));
            lp.y = Math.min(Math.max(lp.y, (int) Ui.dp(this, 30)),
                    Math.max(0, screenH - h - (int) Ui.dp(this, 20)));
            apply();
        } catch (Exception ignored) {
        }
    }

    private void savePosition() {
        Prefs.setNum(this, Prefs.KEY_OVERLAY_X, lp.x);
        Prefs.setNum(this, Prefs.KEY_OVERLAY_Y, lp.y);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        if (root != null && wm != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
        }
        root = null;
        icon = null;
        panel = null;
        Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
        super.onDestroy();
    }
}
