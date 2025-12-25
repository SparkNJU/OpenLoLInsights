package com.example.backend.enums;

public enum ChatMode {
    SIMPLE("simple"),
    REPORT("report"),
    DATA_ANALYSIS("data+analysis");

    private final String value;

    ChatMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ChatMode from(String v) {
        if (v == null) return SIMPLE;
        String x = v.trim();
        for (ChatMode m : values()) {
            if (m.value.equalsIgnoreCase(x)) return m;
        }
        if ("data_analysis".equalsIgnoreCase(x) || "data+analysis".equalsIgnoreCase(x)) {
            return DATA_ANALYSIS;
        }
        return SIMPLE;
    }
}

