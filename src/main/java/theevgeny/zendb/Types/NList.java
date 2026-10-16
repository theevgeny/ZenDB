package theevgeny.zendb.Types;

import theevgeny.zendb.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NList {
    private final ZenDB db;
    private final List<Object> path;
    private final List<Object> backing;

    public NList(ZenDB db, List<Object> path) {
        this(db, path, new ArrayList<>());
    }

    public NList(ZenDB db, List<Object> path, List<Object> backing) {
        this.db = db;
        this.path = path;
        this.backing = backing;
    }

    public List<Object> getBacking() { return backing; }
    public int size() { return backing.size(); }
    public boolean isEmpty() { return backing.isEmpty(); }

    public boolean contains(Object value) {
        return backing.contains(NMap.unwrap(value));
    }

    public int indexOf(Object value) {
        return backing.indexOf(NMap.unwrap(value));
    }

    public Object get(int index) { return backing.get(index); }

    public boolean add(Object value) {
        Object raw = NMap.unwrap(value);
        backing.add(raw);
        if (db != null) db.getWalManager().log(WALManager.OP_LIST_ADD, path, null, raw);
        return true;
    }

    public void add(int index, Object value) {
        Object raw = NMap.unwrap(value);
        backing.add(index, raw);
        if (db != null) db.getWalManager().log(WALManager.OP_LIST_ADD, path, index, raw);
    }

    public Object set(int index, Object value) {
        Object raw = NMap.unwrap(value);
        Object old = backing.set(index, raw);
        if (db != null) db.getWalManager().log(WALManager.OP_LIST_SET, path, index, raw);
        return old;
    }

    /** Удаление по индексу. */
    public Object remove(int index) {
        Object old = backing.remove(index);
        if (db != null) db.getWalManager().log(WALManager.OP_LIST_REMOVE, path, index, null);
        return old;
    }

    /** Удаление по значению. */
    public boolean remove(Object value) {
        Object raw = NMap.unwrap(value);
        boolean removed = backing.remove(raw);
        if (removed && db != null) {
            db.getWalManager().log(WALManager.OP_LIST_REMOVE_VALUE, path, null, raw);
        }
        return removed;
    }

    public void clear() {
        backing.clear();
        if (db != null) db.getWalManager().log(WALManager.OP_CLEAR, path, null, null);
    }

    public NMap getMap(int index) {
        Object v = backing.get(index);
        if (v instanceof ConcurrentHashMap<?, ?> m) {
            List<Object> p = new ArrayList<>(path);
            p.add(index);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<Object, Object> casted = (ConcurrentHashMap<Object, Object>) m;
            return new NMap(db, p, casted);
        }
        return null;
    }

    public NList getList(int index) {
        Object v = backing.get(index);
        if (v instanceof List<?> l) {
            List<Object> p = new ArrayList<>(path);
            p.add(index);
            @SuppressWarnings("unchecked")
            List<Object> casted = (List<Object>) l;
            return new NList(db, p, casted);
        }
        return null;
    }

    public NSet getSet(int index) {
        Object v = backing.get(index);
        if (v instanceof Set<?> s) {
            List<Object> p = new ArrayList<>(path);
            p.add(index);
            @SuppressWarnings("unchecked")
            Set<Object> casted = (Set<Object>) s;
            return new NSet(db, p, casted);
        }
        return null;
    }
}