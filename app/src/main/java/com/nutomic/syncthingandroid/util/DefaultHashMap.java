package com.nutomic.syncthingandroid.util;

import java.util.HashMap;

/**
 * Class implementing a HashMap with a getOrDefault interface.
 * HashMap requires at least API level >= 24 to support getOrDefault natively.
 */
public class DefaultHashMap<K,V> extends HashMap<K,V> {
    public V getOrDefault(Object k, V defaultValue) {
        return containsKey(k) ? this.get(k) : defaultValue;
    }
}
