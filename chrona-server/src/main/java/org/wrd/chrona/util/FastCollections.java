package org.wrd.chrona.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class FastCollections {
    private static final float FAST_LOAD_FACTOR = 0.75f;

    public static <V> Long2ObjectOpenHashMap<V> newLong2ObjectMap() {
        return new Long2ObjectOpenHashMap<>(16, FAST_LOAD_FACTOR);
    }

    public static <V> Long2ObjectOpenHashMap<V> newLong2ObjectMap(int capacity) {
        return new Long2ObjectOpenHashMap<>(capacity, FAST_LOAD_FACTOR);
    }

    public static <V> Int2ObjectOpenHashMap<V> newInt2ObjectMap() {
        return new Int2ObjectOpenHashMap<>(16, FAST_LOAD_FACTOR);
    }

    public static <V> Int2ObjectOpenHashMap<V> newInt2ObjectMap(int capacity) {
        return new Int2ObjectOpenHashMap<>(capacity, FAST_LOAD_FACTOR);
    }

    public static <K> Reference2IntOpenHashMap<K> newReference2IntMap() {
        return new Reference2IntOpenHashMap<>(16, FAST_LOAD_FACTOR);
    }

    public static <K> Reference2IntOpenHashMap<K> newReference2IntMap(int capacity) {
        return new Reference2IntOpenHashMap<>(capacity, FAST_LOAD_FACTOR);
    }

    public static <K> Object2IntOpenHashMap<K> newObject2IntMap() {
        return new Object2IntOpenHashMap<>(16, FAST_LOAD_FACTOR);
    }

    public static <T> ObjectArrayList<T> newObjectList() {
        return new ObjectArrayList<>();
    }

    public static <T> ObjectArrayList<T> newObjectList(int capacity) {
        return new ObjectArrayList<>(capacity);
    }
}
