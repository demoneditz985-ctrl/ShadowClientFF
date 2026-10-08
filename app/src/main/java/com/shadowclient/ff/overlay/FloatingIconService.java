package com.shadowclient.ff.overlay;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.shadowclient.ff.Config;
import com.shadowclient.ff.R;
import com.shadowclient.ff.ShadowApp;
import com.shadowclient.ff.ui.DashboardActivity;
import com.shadowclient.ff.util.Prefs;
import com.shadowclient.ff.util.Ui;

/**
 * The floating Shadow Client icon: draggable, snaps to the nearest edge, tap to open the
 * dashboard, × to hide. Runs as a foreground service so the system does not kill it while
 * the user is in-game.
 */
public class FloatingIconService extends Service {

    public static final String ACTION_START = "com.shadowclient.ff.action.OVERLAY_START";
    public static final String ACTION_STOP = "com.shadowclient.ff.action.OVERLAY_STOP";
    private static final int NOTIF_ID = 0x5C01;

    private WindowManager wm;
    private View view;
    private WindowManager.LayoutParams lp;

    public static void start(Context c) {
        Intent i = new Intent(c, FloatingIconService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, FloatingIconService.class).setAction(ACTION_STOP);
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
                .setContentText(getString(R.string.dash_floating_icon) + " · " + getString(R.string.dash_status_ready))
                .setOngoing(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .addAction(0, "STOP", stopPi)
                .build();
    }

    private void showOverlay() {
        if (view != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        view = LayoutInflater.from(this).inflate(R.layout.overlay_icon, null);

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
        lp.x = (int) Prefs.num(this, Prefs.KEY_OVERLAY_X, -1);
        lp.y = (int) Prefs.num(this, Prefs.KEY_OVERLAY_Y, -1);

        ImageView main = view.findViewById(R.id.overlayMain);
        ImageView close = view.findViewById(R.id.overlayClose);

        main.setOnClickListener(v -> {
            Intent i = new Intent(this, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(i);
            } catch (Exception ignored) {
            }
        });

        close.setOnClickListener(v -> {
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
        });

        view.setOnTouchListener(new DragListener());

        try {
            wm.addView(view, lp);
        } catch (Exception e) {
            view = null;
            Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
            stopSelf();
        }
    }

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
                    try {
                        wm.updateViewLayout(view, lp);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    snapToEdge();
                    return moved;   // a tap (no movement) still reaches the click listener
                default:
                    return false;
            }
        }
    }

    private void snapToEdge() {
        try {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int iconW = view.getWidth() > 0 ? view.getWidth() : (int) Ui.dp(this, 58);
            int iconH = view.getHeight() > 0 ? view.getHeight() : (int) Ui.dp(this, 82);

            lp.x = Math.min(Math.max(lp.x, 0), Math.max(0, screenW - iconW));
            lp.y = Math.min(Math.max(lp.y, (int) Ui.dp(this, 40)), Math.max(0, screenH - iconH - (int) Ui.dp(this, 24)));

            wm.updateViewLayout(view, lp);
            Prefs.setNum(this, Prefs.KEY_OVERLAY_X, lp.x);
            Prefs.setNum(this, Prefs.KEY_OVERLAY_Y, lp.y);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
        view = null;
        Prefs.setFlag(this, Prefs.KEY_OVERLAY, false);
        super.onDestroy();
    }
}
