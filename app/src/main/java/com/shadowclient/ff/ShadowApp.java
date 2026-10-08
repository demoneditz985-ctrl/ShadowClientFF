package com.shadowclient.ff;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ShadowApp extends Application {

    public static final String CH_OVERLAY = "shadow_overlay";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                NotificationChannel c = new NotificationChannel(
                        CH_OVERLAY,
                        getString(R.string.overlay_channel),
                        NotificationManager.IMPORTANCE_LOW);
                c.setShowBadge(false);
                c.setDescription(getString(R.string.dash_floating_icon));
                nm.createNotificationChannel(c);
            }
        }
    }
}
