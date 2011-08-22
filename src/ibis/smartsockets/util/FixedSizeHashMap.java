package ibis.smartsockets.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixedSizeHashMap<K,V> extends LinkedHashMap<K,V> {

    private static final long serialVersionUID = 5652104148565673432L;

    private static final int DEFAULT_MAX_ENTRIES = 25;

    private final int MAX_ENTRIES;

    public FixedSizeHashMap() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public FixedSizeHashMap(int size) {
        super();
        MAX_ENTRIES = size;
    }

    public FixedSizeHashMap(int size, int initialCapacity) {
        super(initialCapacity);
        MAX_ENTRIES = size;
    }

    public FixedSizeHashMap(int size, int initialCapacity, float loadfact) {
        super(initialCapacity, loadfact);
        MAX_ENTRIES = size;
    }

    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
       return size() > MAX_ENTRIES;
    }

}
