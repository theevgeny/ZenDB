package theevgeny.zendb;

import theevgeny.zendb.Types.*;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class WALManager {

    public static final int OP_PUT               = 1;
    public static final int OP_REMOVE            = 2;
    public static final int OP_CLEAR             = 3;
    public static final int OP_LIST_ADD          = 4;
    public static final int OP_LIST_SET          = 5;
    public static final int OP_LIST_REMOVE       = 6;
    public static final int OP_SET_ADD           = 7;
    public static final int OP_SET_REMOVE        = 8;
    public static final int OP_LIST_REMOVE_VALUE = 10;

    private final ZenDB db;
    private final Path walPath;
    private final Object lock = new Object();
    private FileOutputStream stream;

    public WALManager(ZenDB db, Path walPath) {
        this.db = db;
        this.walPath = walPath;
        try {
            this.stream = new FileOutputStream(walPath.toFile(), true);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open WAL: " + walPath, e);
        }
    }

    public void log(int op, List<Object> path, Object key, Object value) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            body.write(Serializer.encode(op));
            body.write(Serializer.encode(new ArrayList<>(path)));
            body.write(Serializer.encode(key));
            body.write(Serializer.encode(value));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        byte[] record = body.toByteArray();
        synchronized (lock) {
            try {
                stream.write(intToBytes(record.length));
                stream.write(record);
                stream.flush();
            } catch (IOException e) {
                throw new RuntimeException("WAL write error", e);
            }
        }
    }

    public void replay(NMap root) {
        if (!Files.exists(walPath)) return;
        try {
            byte[] data = Files.readAllBytes(walPath);
            int offset = 0;
            while (offset + 4 <= data.length) {
                int len = readInt(data, offset);
                offset += 4;
                if (len <= 0 || offset + len > data.length) break;
                byte[] record = Arrays.copyOfRange(data, offset, offset + len);
                offset += len;
                try {
                    applyRecord(root, record);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("WAL read error", e);
        }
    }

    private void applyRecord(NMap root, byte[] record) {
        Serializer.DecodeResult r = Serializer.decodeOfOffset(record, 0);
        if (!(r.value instanceof Integer op)) return;
        r = Serializer.decodeOfOffset(record, r.newOffset);
        if (!(r.value instanceof List<?> rawPath)) return;
        @SuppressWarnings("unchecked")
        List<Object> path = (List<Object>) rawPath;
        r = Serializer.decodeOfOffset(record, r.newOffset);
        Object key = r.value;
        r = Serializer.decodeOfOffset(record, r.newOffset);
        Object value = r.value;
        applyOp(root, op, path, key, value);
    }

    @SuppressWarnings("unchecked")
    private void applyOp(NMap root, int op, List<Object> path, Object key, Object value) {
        Object container = navigateTo(root, path);
        if (container == null) return;
        switch (op) {
            case OP_PUT -> {
                if (container instanceof Map<?, ?> m) ((Map<Object, Object>) m).put(key, value);
            }
            case OP_REMOVE -> {
                if (container instanceof Map<?, ?> m) ((Map<Object, Object>) m).remove(key);
            }
            case OP_CLEAR -> {
                if (container instanceof Map<?, ?> m) ((Map<Object, Object>) m).clear();
                else if (container instanceof List<?> l) ((List<Object>) l).clear();
                else if (container instanceof Set<?> s) ((Set<Object>) s).clear();
            }
            case OP_LIST_ADD -> {
                if (container instanceof List<?> l) {
                    if (key instanceof Integer i) ((List<Object>) l).add((int) i, value);
                    else ((List<Object>) l).add(value);
                }
            }
            case OP_LIST_SET -> {
                if (container instanceof List<?> l && key instanceof Integer i)
                    ((List<Object>) l).set((int) i, value);
            }
            case OP_LIST_REMOVE -> {
                if (container instanceof List<?> l && key instanceof Integer i)
                    ((List<Object>) l).remove((int) i);
            }
            case OP_LIST_REMOVE_VALUE -> {
                if (container instanceof List<?> l) ((List<Object>) l).remove(value);
            }
            case OP_SET_ADD -> {
                if (container instanceof Set<?> s) ((Set<Object>) s).add(value);
            }
            case OP_SET_REMOVE -> {
                if (container instanceof Set<?> s) ((Set<Object>) s).remove(value);
            }
            default -> {
            }
        }
    }

    private Object navigateTo(NMap root, List<Object> path) {
        Object current = root.getBacking();
        for (Object key : path) {
            if (current instanceof Map<?, ?> m) current = m.get(key);
            else if (current instanceof List<?> l && key instanceof Integer i) current = l.get((int) i);
            else return null;
            if (current == null) return null;
        }
        return current;
    }

    public void clear() {
        synchronized (lock) {
            try {
                stream.close();
                Files.deleteIfExists(walPath);
                stream = new FileOutputStream(walPath.toFile(), true);
            } catch (IOException e) {
                throw new RuntimeException("Failed to clear WAL", e);
            }
        }
    }

    public void close() {
        synchronized (lock) {
            try {
                if (stream != null) stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] intToBytes(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static int readInt(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}