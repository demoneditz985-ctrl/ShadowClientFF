package com.shadowclient.ff.engine;

import android.content.Context;
import android.util.Log;

import com.shadowclient.ff.core.ShadowEngine;

/**
 * Finds a tunnel engine at runtime and hands it to {@link ShadowEngine}.
 *
 * <p>The app compiles and runs with or without an engine. If
 * {@code com.shadowclient.ff.engine.singbox.SingboxEngine} is present (it is added
 * automatically when {@code app/libs/libbox.aar} exists — see SETUP.md §5), it is
 * installed here and the dashboard's activate button starts a real tunnel.
 */
public final class EngineInstaller {

    private static final String TAG = "ShadowEngine";
    private static final String[] CANDIDATES = {
            "com.shadowclient.ff.engine.singbox.SingboxEngine",
            "com.shadowclient.ff.engine.native_.NativeEngine"
    };

    private EngineInstaller() {
    }

    public static void install(Context context) {
        for (String className : CANDIDATES) {
            try {
                Class<?> cls = Class.forName(className);
                Object instance = cls.getDeclaredConstructor().newInstance();
                if (instance instanceof ShadowEngine.Tunnel) {
                    ShadowEngine.install((ShadowEngine.Tunnel) instance);
                    Log.i(TAG, "engine installed: " + className);
                    return;
                }
            } catch (ClassNotFoundException ignored) {
                // engine not bundled — perfectly normal
            } catch (Throwable t) {
                Log.w(TAG, "engine " + className + " failed to load", t);
            }
        }
        Log.i(TAG, "no tunnel engine bundled — dashboard will report ENGINE NOT INSTALLED");
    }
}
