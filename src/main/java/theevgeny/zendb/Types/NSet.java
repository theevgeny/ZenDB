package theevgeny.zendb.Types;

import theevgeny.zendb.*;

import java.util.*;

public final class NSet {
    private final ZenDB db;
    private final List<Object> path;
    private final Set<Object> backing;

    public NSet(ZenDB db, List<Object> path) {
        this(db, path, new LinkedHashSet<>());
    }

    public NSet(ZenDB db, List<Object> path, Set<Object> backing) {
        this.db = db;
        this.path = path;
        this.backing = backing;
    }

    public Set<Object> getBacking() { return backing; }
    public int size() { return backing.size(); }
    public boolean isEmpty() { return backing.isEmpty(); }

    public boolean contains(Object value) {
        return backing.contains(NMap.unwrap(value));
    }

    public boolean add(Object value) {
        Object raw = NMap.unwrap(value);
        boolean added = backing.add(raw);
        if (added && db != null) db.getWalManager().log(WALManager.OP_SET_ADD, path, null, raw);
        return added;
    }

    public boolean remove(Object value) {
        Object raw = NMap.unwrap(value);
        boolean removed = backing.remove(raw);
        if (removed && db != null) db.getWalManager().log(WALManager.OP_SET_REMOVE, path, null, raw);
        return removed;
    }

    public void clear() {
        backing.clear();
        if (db != null) db.getWalManager().log(WALManager.OP_CLEAR, path, null, null);
    }

    public Iterator<Object> iterator() {
        return backing.iterator();
    }
}