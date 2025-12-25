package com.example.backend.util;

import java.util.UUID;

public final class TraceIdUtil {
    private static final ThreadLocal<String> TL = new ThreadLocal<>();

    private TraceIdUtil() {}

    public static String getOrCreate() {
        String v = TL.get();
        if (v == null || v.trim().isEmpty()) {
            v = "t_" + UUID.randomUUID().toString().replace("-", "");
            TL.set(v);
        }
        return v;
    }

    public static void set(String traceId) {
        TL.set(traceId);
    }

    public static void clear() {
        TL.remove();
    }
}

