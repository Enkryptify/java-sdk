package com.enkryptify.internal;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SecretCache {

    private record Entry(String value, Long expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final long ttl;

    public SecretCache(long ttl) {
        this.ttl = ttl;
    }

    public Optional<String> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt() != null && System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void set(String key, String value) {
        Long expiresAt = ttl == -1 ? null : System.currentTimeMillis() + ttl;
        store.put(key, new Entry(value, expiresAt));
    }

    public boolean has(String key) {
        return get(key).isPresent();
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
