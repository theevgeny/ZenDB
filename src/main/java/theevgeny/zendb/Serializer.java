package theevgeny.zendb;

import theevgeny.zendb.Types.NList;
import theevgeny.zendb.Types.NMap;
import theevgeny.zendb.Types.NSet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Serializer {

    public static boolean isSupported(Object o) {
        if (o == null) return true;
        if (o instanceof Boolean || o instanceof Integer || o instanceof Long
                || o instanceof Float || o instanceof Double || o instanceof String) return true;
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet())
                if (!isSupported(e.getKey()) || !isSupported(e.getValue())) return false;
            return true;
        }
        if (o instanceof ZenSection || o instanceof NMap || o instanceof NList || o instanceof NSet) {
            return true;
        }
        if (o instanceof List<?> l) {
            for (Object x : l) if (!isSupported(x)) return false;
            return true;
        }
        if (o instanceof Set<?> s) {
            for (Object x : s) if (!isSupported(x)) return false;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static byte[] encode(Object object) {
        return switch (object) {
            case null -> new byte[]{0};
            case Boolean b -> encode(b.booleanValue());
            case Integer i -> encode(i.intValue());
            case Long l -> encode(l.longValue());
            case Float f -> encode(f.floatValue());
            case Double d -> encode(d.doubleValue());
            case String s -> encode(s);
            case List<?> l -> encode((List<Object>) l);
            case Set<?> s -> encode((Set<Object>) s);
            case Map<?, ?> m -> encode((Map<Object, Object>) m);
            case NList m -> encode(m.getBacking());
            case NSet m -> encode(m.getBacking());
            case NMap m -> encode(m.getBacking());
            case ZenSection m -> encode(m.getNMap().getBacking());
            default -> throw new IllegalArgumentException(
                    "Unsupported type: " + object.getClass().getName());
        };
    }

    public static byte[] encode(boolean value) {
        return new byte[]{1, (byte) (value ? 1 : 0)};
    }

    public static byte[] encode(int value) {
        return new byte[]{2,
                (byte) (value >> 24), (byte) (value >> 16),
                (byte) (value >> 8), (byte) value};
    }

    public static byte[] encode(long value) {
        return new byte[]{3,
                (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
                (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    public static byte[] encode(float value) {
        int c = Float.floatToIntBits(value);
        return new byte[]{4, (byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c};
    }

    public static byte[] encode(double value) {
        long c = Double.doubleToLongBits(value);
        return new byte[]{5,
                (byte) (c >> 56), (byte) (c >> 48), (byte) (c >> 40), (byte) (c >> 32),
                (byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c};
    }

    public static byte[] encode(String value) {
        byte[] str = value.getBytes(StandardCharsets.UTF_8);
        int n = str.length;
        byte[] buf = new byte[n + 5];
        buf[0] = 6;
        buf[1] = (byte) (n >> 24); buf[2] = (byte) (n >> 16);
        buf[3] = (byte) (n >> 8);  buf[4] = (byte) n;
        System.arraycopy(str, 0, buf, 5, n);
        return buf;
    }

    public static byte[] encode(List<Object> list) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(7);
        int n = list.size();
        out.write((n >> 24) & 0xFF); out.write((n >> 16) & 0xFF);
        out.write((n >> 8) & 0xFF);  out.write(n & 0xFF);
        for (Object o : list) {
            byte[] b = encode(o);
            out.write(b, 0, b.length);
        }
        return out.toByteArray();
    }

    public static byte[] encode(Map<Object, Object> map) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(8);
        int n = map.size();
        out.write((n >> 24) & 0xFF); out.write((n >> 16) & 0xFF);
        out.write((n >> 8) & 0xFF);  out.write(n & 0xFF);
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            byte[] k = encode(e.getKey());  out.write(k, 0, k.length);
            byte[] v = encode(e.getValue()); out.write(v, 0, v.length);
        }
        return out.toByteArray();
    }

    public static byte[] encode(Set<Object> set) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(9);
        int n = set.size();
        out.write((n >> 24) & 0xFF); out.write((n >> 16) & 0xFF);
        out.write((n >> 8) & 0xFF);  out.write(n & 0xFF);
        for (Object o : set) {
            byte[] b = encode(o);
            out.write(b, 0, b.length);
        }
        return out.toByteArray();
    }

    public static Object decode(byte[] buffer) {
        if (buffer == null || buffer.length < 1) return null;
        return decodeOfOffset(buffer, 0).value;
    }

    public static final class DecodeResult {
        public final Object value;
        public final int newOffset;
        public DecodeResult(Object value, int newOffset) {
            this.value = value; this.newOffset = newOffset;
        }
    }

    public static DecodeResult decodeOfOffset(byte[] b, int off) {
        if (off < 0 || off >= b.length) return new DecodeResult(null, off + 1);
        switch (b[off]) {
            case 0: return new DecodeResult(null, off + 1);
            case 1:
                if (b.length - off < 2) return new DecodeResult(null, off + 1);
                return new DecodeResult(b[off + 1] != 0, off + 2);
            case 2:
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                return new DecodeResult(
                        ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                                ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF), off + 5);
            case 3:
                if (b.length - off < 9) return new DecodeResult(null, off + 1);
                return new DecodeResult(
                        ((long)(b[off + 1] & 0xFF) << 56) | ((long)(b[off + 2] & 0xFF) << 48) |
                                ((long)(b[off + 3] & 0xFF) << 40) | ((long)(b[off + 4] & 0xFF) << 32) |
                                ((long)(b[off + 5] & 0xFF) << 24) | ((long)(b[off + 6] & 0xFF) << 16) |
                                ((long)(b[off + 7] & 0xFF) << 8)  |  (long)(b[off + 8] & 0xFF), off + 9);
            case 4:
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                return new DecodeResult(Float.intBitsToFloat(
                        ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                                ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF)), off + 5);
            case 5:
                if (b.length - off < 9) return new DecodeResult(null, off + 1);
                return new DecodeResult(Double.longBitsToDouble(
                        ((long)(b[off + 1] & 0xFF) << 56) | ((long)(b[off + 2] & 0xFF) << 48) |
                                ((long)(b[off + 3] & 0xFF) << 40) | ((long)(b[off + 4] & 0xFF) << 32) |
                                ((long)(b[off + 5] & 0xFF) << 24) | ((long)(b[off + 6] & 0xFF) << 16) |
                                ((long)(b[off + 7] & 0xFF) << 8)  |  (long)(b[off + 8] & 0xFF)), off + 9);
            case 6: {
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                int size = ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                        ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF);
                if (size < 0 || b.length - off < size + 5) return new DecodeResult(null, off + 5);
                return new DecodeResult(new String(b, off + 5, size, StandardCharsets.UTF_8), off + size + 5);
            }
            case 7: {
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                int size = ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                        ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF);
                if (size < 0) return new DecodeResult(null, off + 5);
                int cur = off + 5;
                List<Object> list = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    if (b.length - cur < 1) return new DecodeResult(null, cur);
                    DecodeResult r = decodeOfOffset(b, cur);
                    cur = r.newOffset;
                    list.add(r.value);
                }
                return new DecodeResult(list, cur);
            }
            case 8: {
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                int size = ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                        ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF);
                if (size < 0) return new DecodeResult(null, off + 5);
                int cur = off + 5;
                Map<Object, Object> map = new ConcurrentHashMap<>();
                for (int i = 0; i < size; i++) {
                    if (b.length - cur < 1) return new DecodeResult(null, cur);
                    DecodeResult r = decodeOfOffset(b, cur);
                    cur = r.newOffset;
                    Object k = r.value;
                    if (b.length - cur < 1) return new DecodeResult(null, cur);
                    r = decodeOfOffset(b, cur);
                    cur = r.newOffset;
                    map.put(k, r.value);
                }
                return new DecodeResult(map, cur);
            }
            case 9: {
                if (b.length - off < 5) return new DecodeResult(null, off + 1);
                int size = ((b[off + 1] & 0xFF) << 24) | ((b[off + 2] & 0xFF) << 16) |
                        ((b[off + 3] & 0xFF) << 8)  |  (b[off + 4] & 0xFF);
                if (size < 0) return new DecodeResult(null, off + 5);
                int cur = off + 5;
                Set<Object> set = new LinkedHashSet<>();
                for (int i = 0; i < size; i++) {
                    if (b.length - cur < 1) return new DecodeResult(null, cur);
                    DecodeResult r = decodeOfOffset(b, cur);
                    cur = r.newOffset;
                    set.add(r.value);
                }
                return new DecodeResult(set, cur);
            }
            default:
                return new DecodeResult(null, off + 1);
        }
    }
}