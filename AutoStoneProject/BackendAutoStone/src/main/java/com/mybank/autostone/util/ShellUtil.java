package com.mybank.autostone.util;

public final class ShellUtil {
    private ShellUtil() {}

    /** Wraps a value in single quotes for safe use as one shell argument, escaping any embedded quotes. */
    public static String quote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }
}