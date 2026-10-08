package com.shadowclient.ff.engine.singbox;

import java.util.Collections;
import java.util.List;

import io.nekohasekai.libbox.StringIterator;

/** Adapts a java.util.List to the iterator shape the engine expects. */
public class StringList implements StringIterator {

    private final List<String> items;
    private int index;

    public StringList(List<String> items) {
        this.items = items == null ? Collections.emptyList() : items;
    }

    @Override
    public boolean hasNext() {
        return index < items.size();
    }

    @Override
    public int len() {
        return items.size();
    }

    @Override
    public String next() {
        return index < items.size() ? items.get(index++) : "";
    }
}
