package com.shadowclient.ff.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class Ui {

    private Ui() {
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void openUrl(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.getApplicationContext().startActivity(i);
        } catch (Exception e) {
            toast(c, "No app can open that link");
        }
    }

    // ------------------------------------------------------------- overlay permission

    public static boolean hasOverlay(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c);
    }

    public static void requestOverlay(Activity a, int reqCode) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivityForResult(i, reqCode);
        } catch (Exception e) {
            try {
                a.startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), reqCode);
            } catch (Exception e2) {
                toast(a, "Open Settings › Apps › Shadow Client › Display over other apps");
            }
        }
    }

    public static void requestIgnoreBattery(Activity a) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName()));
            a.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------- dialogs

    public static void info(Activity a, String title, String msg) {
        new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void confirm(Activity a, String title, String msg, Runnable onYes) {
        new MaterialAlertDialogBuilder(a)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onYes.run())
                .show();
    }
}
