package com.shadowclient.ff.engine.singbox;

import android.content.Context;

import com.shadowclient.ff.core.NodeConfig;
import com.shadowclient.ff.core.ShadowEngine;

/**
 * The sing-box core, wired into Shadow Client.
 *
 * <p>Present only when {@code app/libs/libbox.aar} exists (see SETUP.md §5) — it is picked up
 * automatically by {@code EngineInstaller} and the dashboard's activate button then starts a
 * real tunnel through the selected node.
 */
public class SingboxEngine implements ShadowEngine.Tunnel {

    @Override
    public void start(Context context, NodeConfig node, ShadowEngine.Listener listener) {
        listener.onState("CONNECTING", node.endpoint());
        EngineVpnService.start(context, node, listener);
        // EngineVpnService reports RUNNING / ERROR once the core answers
    }

    @Override
    public void stop(Context context) {
        EngineVpnService.stop(context);
    }

    @Override
    public boolean isRunning() {
        return EngineVpnService.isRunning();
    }
}
