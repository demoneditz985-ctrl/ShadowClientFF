package com.shadowclient.ff.engine.singbox;

import java.util.Collections;
import java.util.List;

import io.nekohasekai.libbox.NetworkInterface;
import io.nekohasekai.libbox.NetworkInterfaceIterator;

/** Adapts a list of libbox network interfaces to the iterator the engine expects. */
public class NetworkInterfaceList implements NetworkInterfaceIterator {

    private final List<NetworkInterface> items;
    private int index;

    public NetworkInterfaceList(List<NetworkInterface> items) {
        this.items = items == null ? Collections.emptyList() : items;
    }

    @Override
    public boolean hasNext() {
        return index < items.size();
    }

    @Override
    public NetworkInterface next() {
        return index < items.size() ? items.get(index++) : null;
    }
}
