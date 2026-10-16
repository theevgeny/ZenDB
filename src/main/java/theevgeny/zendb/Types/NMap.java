package theevgeny.zendb.Types;

import theevgeny.zendb.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NMap {
    private final ZenDB db;
    private final List<Object> path;
    private final ConcurrentHashMap<Object, Object> backing;

    public NMap(ZenDB db, List<Object> path) {
        this(db, path, new ConcurrentHashMap<>());
    }

    public NMap(ZenDB db, List<Object> path, ConcurrentHashMap<Object, Object> backing) {
        this.db = db;
        this.path = path;
        this.backing = backing;
    }

    public ConcurrentHashMap<Object, Object> getBacking() { return backing; }
    public List<Object> getPath() { return path; }
    public ZenDB getDb() { return db; }

    public int size() { return backing.size(); }
    public boolean isEmpty() { return backing.isEmpty(); }
    public boolean containsKey(Object key) { return backing.containsKey(key); }

    public Object put(Object key, Object value) {
        Object raw = unwrap(value);
        if (db != null && !Serializer.isSupported(raw)) {
            throw new IllegalArgumentException("Unsupported value type: "
                    + (raw == null ? "null" : raw.getClass().getName()));
        }
        Object old = backing.put(key, raw);
        if (db != null) db.getWalManager().log(WALManager.OP_PUT, path, key, raw);
        return old;
    }

    public Object remove(Object key) {
        Object old = backing.remove(key);
        if (old != null && db != null) db.getWalManager().log(WALManager.OP_REMOVE, path, key, null);
        return old;
    }

    public void clear() {
        backing.clear();
        if (db != null) db.getWalManager().log(WALManager.OP_CLEAR, path, null, null);
    }

    public Object get(Object key) { return backing.get(key); }

    public NMap getMap(Object key) {
        Object v = backing.get(key);
        if (v instanceof ConcurrentHashMap<?, ?> m) {
            List<Object> p = new ArrayList<>(path); p.add(key);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Object, Object> casted = (ConcurrentHashMap<Object, Object>) m;
            return new NMap(db, p, casted);
        }
        return null;
    }

    public NList getList(Object key) {
        Object v = backing.get(key);
        if (v instanceof List<?> l) {
            List<Object> p = new ArrayList<>(path); p.add(key);
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) l;
            return new NList(db, p, casted);
        }
        return null;
    }

    public NSet getSet(Object key) {
        Object v = backing.get(key);
        if (v instanceof Set<?> s) {
            List<Object> p = new ArrayList<>(path); p.add(key);
            @SuppressWarnings("unchecked")
            Set<Object> casted = (Set<Object>) s;
            return new NSet(db, p, casted);
        }
        return null;
    }

    public int getInt(Object k)              { return getInt(k, 0); }
    public int getInt(Object k, int def)     { Object v = backing.get(k); return v instanceof Integer i ? i : def; }
    public long getLong(Object k)            { return getLong(k, 0L); }
    public long getLong(Object k, long def)  { Object v = backing.get(k); return v instanceof Long l ? l : def; }
    public float getFloat(Object k)          { return getFloat(k, 0F); }
    public float getFloat(Object k, float d) { Object v = backing.get(k); return v instanceof Float f ? f : d; }
    public double getDouble(Object k)        { return getDouble(k, 0D); }
    public double getDouble(Object k, double d) { Object v = backing.get(k); return v instanceof Double x ? x : d; }
    public boolean getBool(Object k)         { return getBool(k, false); }
    public boolean getBool(Object k, boolean d) { Object v = backing.get(k); return v instanceof Boolean b ? b : d; }
    public String getString(Object k)        { return getString(k, ""); }
    public String getString(Object k, String d) { Object v = backing.get(k); return v instanceof String s ? s : d; }

    @SuppressWarnings("unchecked")
    public static Object unwrap(Object value) {
        if (value instanceof NMap m)  return m.getBacking();
        if (value instanceof NList l) return l.getBacking();
        if (value instanceof NSet s)  return s.getBacking();
        if (value instanceof ConcurrentHashMap<?, ?>) return value;
        if (value instanceof Map<?, ?> m) {
            ConcurrentHashMap<Object, Object> r = new ConcurrentHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) r.put(unwrap(e.getKey()), unwrap(e.getValue()));
            return r;
        }
        if (value instanceof List<?> l) {
            List<Object> r = new ArrayList<>(l.size());
            for (Object o : l) r.add(unwrap(o));
            return r;
        }
        if (value instanceof Set<?> s) {
            Set<Object> r = new LinkedHashSet<>();
            for (Object o : s) r.add(unwrap(o));
            return r;
        }
        return value;
    }
}