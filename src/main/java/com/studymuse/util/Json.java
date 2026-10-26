package com.studymuse.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer so StudyMuse runs on a plain JDK with no external libraries.
 * Objects become LinkedHashMap, arrays become ArrayList, numbers become Double or Long.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ writing

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, true);
        return sb.toString();
    }

    public static String compact(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, false);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, boolean pretty) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> map) {
            sb.append('{');
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                newline(sb, indent + 1, pretty);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(pretty ? ": " : ":");
                write(sb, e.getValue(), indent + 1, pretty);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            if (!map.isEmpty()) {
                newline(sb, indent, pretty);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> list) {
            sb.append('[');
            Iterator<?> it = list.iterator();
            boolean any = it.hasNext();
            while (it.hasNext()) {
                newline(sb, indent + 1, pretty);
                write(sb, it.next(), indent + 1, pretty);
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            if (any) {
                newline(sb, indent, pretty);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void newline(StringBuilder sb, int indent, boolean pretty) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(indent));
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ reading

    /** Parses JSON text. Throws IllegalArgumentException with the position of the first error. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != text.length()) {
            throw p.error("unexpected trailing characters");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("JSON is not an object");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON at position " + pos + ": " + msg);
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            if (pos >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Object literal(String word, Object result) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
            return result;
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw error("expected a key");
                }
                String key = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw error("expected ':'");
                }
                pos++;
                skipWs();
                map.put(key, value());
                skipWs();
                if (pos >= s.length()) {
                    throw error("unterminated object");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value());
                skipWs();
                if (pos >= s.length()) {
                    throw error("unterminated array");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            pos++; // opening quote
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= s.length()) {
                        break;
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (pos + 4 > s.length()) {
                                throw error("bad unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("unterminated string");
        }

        Object number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) {
                throw error("unexpected character '" + s.charAt(start) + "'");
            }
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException ex) {
                throw error("bad number " + num);
            }
        }
    }

    // ------------------------------------------------------------------ helpers for reading maps

    public static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    public static int integer(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static double decimal(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof List ? (List<Object>) v : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }
}
