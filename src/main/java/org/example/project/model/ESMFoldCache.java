package org.example.project.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory LRU cache for ESMFold results.
 * Maps sequence strings to their parsed ProteinStructure, avoiding redundant API calls.
 */
public final class ESMFoldCache {

    private static final int MAX_ENTRIES = 50;

    private final Map<String, ProteinStructure> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ProteinStructure> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public ProteinStructure get(String sequence) {
        return cache.get(sequence);
    }

    public void put(String sequence, ProteinStructure structure) {
        cache.put(sequence, structure);
    }

    public boolean contains(String sequence) {
        return cache.containsKey(sequence);
    }

    public int size() {
        return cache.size();
    }
}
