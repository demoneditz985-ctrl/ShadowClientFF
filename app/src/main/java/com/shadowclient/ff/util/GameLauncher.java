package com.shadowclient.ff.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Launches the game (or opens its store page when it is not installed).
 *
 * <p>Android 11+ hides other apps unless they are declared in &lt;queries&gt; —
 * see AndroidManifest.xml.
 */
public final class GameLauncher {

    public static final String PKG_FF = "com.dts.freefireth";      // Free Fire
    public static final String PKG_FFMAX = "com.dts.freefiremax";  // Free Fire MAX

    private GameLauncher() {
    }

    public static boolean isInstalled(Context c, String pkg) {
        try {
            return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** @return true when the game was launched, false when we opened the store page instead. */
    public static boolean launch(Context c, String pkg) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                c.getApplicationContext().startActivity(i);
                return true;
            }
        } catch (Exception ignored) {
        }
        openStore(c, pkg);
        return false;
    }

    public static void openStore(Context c, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.getApplicationContext().startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.getApplicationContext().startActivity(i);
            } catch (Exception e2) {
                Ui.toast(c, "Game not installed");
            }
        }
    }

    public static String titleFor(String pkg) {
        return TextUtils.equals(pkg, PKG_FFMAX) ? "Free Fire MAX" : "Free Fire";
    }

    /** Package name for the dashboard's current game selection ("FF" / "FFMAX"). */
    public static String pkgForGame(String game) {
        return "FFMAX".equals(game) ? PKG_FFMAX : PKG_FF;
    }
}
