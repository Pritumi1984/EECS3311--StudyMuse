package com.studymuse.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Helpers for reading the loosely typed JSON arguments that the LLM sends. */
final class ToolArgs {

    private ToolArgs() {
    }

    static String text(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString().trim();
    }

    static boolean hasText(Map<String, Object> args, String key) {
        String t = text(args, key);
        return t != null && !t.isEmpty();
    }

    /** Integer argument; numbers sent as text ("10") are accepted. Returns def when missing, null when invalid. */
    static Integer integer(Map<String, Object> args, String key, Integer def) {
        Object v = args.get(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double decimal(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return v == null ? null : Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A list argument, given either as a JSON array or as comma-separated text. */
    static List<String> list(Map<String, Object> args, String key) {
        Object v = args.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
        } else if (v != null) {
            for (String s : Arrays.asList(v.toString().split(","))) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }
}
