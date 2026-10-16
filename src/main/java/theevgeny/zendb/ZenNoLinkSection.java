package theevgeny.zendb;

import theevgeny.zendb.Types.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ZenNoLinkSection {
    private final NMap map;

    public ZenNoLinkSection() {
        this.map = new NMap(null, new ArrayList<>());
    }

    public ZenNoLinkSection(ConcurrentHashMap<Object, Object> backing) {
        this.map = new NMap(null, new ArrayList<>(), backing);
    }

    private ZenNoLinkSection(NMap map) {
        this.map = map;
    }

    public NMap getNMap() { return map; }
    public int size()              { return map.size(); }
    public boolean isEmpty()       { return map.isEmpty(); }
    public boolean contains(Object k) { return map.containsKey(k); }
    public Object get(Object k)    { return map.get(k); }
    public Object remove(Object k) { return map.remove(k); }
    public void clear()            { map.clear(); }

    public void put(Object k, Object v)  { map.put(k, v); }
    public void put(Object k, int v)     { map.put(k, v); }
    public void put(Object k, long v)    { map.put(k, v); }
    public void put(Object k, float v)   { map.put(k, v); }
    public void put(Object k, double v)  { map.put(k, v); }
    public void put(Object k, boolean v) { map.put(k, v); }
    public void put(Object k, String v)  { map.put(k, v); }

    public int getInt(Object k)               { return map.getInt(k); }
    public int getInt(Object k, int d)        { return map.getInt(k, d); }
    public long getLong(Object k)             { return map.getLong(k); }
    public long getLong(Object k, long d)     { return map.getLong(k, d); }
    public float getFloat(Object k)           { return map.getFloat(k); }
    public float getFloat(Object k, float d)  { return map.getFloat(k, d); }
    public double getDouble(Object k)         { return map.getDouble(k); }
    public double getDouble(Object k, double d) { return map.getDouble(k, d); }
    public boolean getBool(Object k)          { return map.getBool(k); }
    public boolean getBool(Object k, boolean d) { return map.getBool(k, d); }
    public String getString(Object k)         { return map.getString(k); }
    public String getString(Object k, String d) { return map.getString(k, d); }

    public ZenNoLinkSection getSection(Object key) {
        Object existing = map.get(key);
        if (existing instanceof ConcurrentHashMap<?, ?> m) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Object, Object> casted = (ConcurrentHashMap<Object, Object>) m;
            return new ZenNoLinkSection(casted);
        }
        ConcurrentHashMap<Object, Object> created = new ConcurrentHashMap<>();
        map.put(key, created);
        return new ZenNoLinkSection(created);
    }

    public ZenNoLinkSection findSection(Object key) {
        Object existing = map.get(key);
        if (existing instanceof ConcurrentHashMap<?, ?> m) {
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Object, Object> casted = (ConcurrentHashMap<Object, Object>) m;
            return new ZenNoLinkSection(casted);
        }
        return null;
    }

    public NList getOrCreateList(Object key) {
        NList l = map.getList(key);
        if (l != null) return l;
        map.put(key, new ArrayList<>());
        NList created = map.getList(key);
        if (created == null) {
            throw new IllegalStateException("Failed to create list at key: " + key);
        }
        return created;
    }

    public NSet getOrCreateSet(Object key) {
        NSet s = map.getSet(key);
        if (s != null) return s;
        map.put(key, new LinkedHashSet<>());
        NSet created = map.getSet(key);
        if (created == null) {
            throw new IllegalStateException("Failed to create set at key: " + key);
        }
        return created;
    }

    public ConcurrentHashMap<Object, Object> toMap() {
        return new ConcurrentHashMap<>(map.getBacking());
    }

    @SuppressWarnings("unchecked")
    public void putAll(Map<?, ?> source) {
        for (Map.Entry<?, ?> e : source.entrySet()) {
            map.put(e.getKey(), NMap.unwrap(e.getValue()));
        }
    }
}