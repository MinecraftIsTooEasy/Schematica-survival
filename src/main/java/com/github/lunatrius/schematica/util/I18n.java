package com.github.lunatrius.schematica.util;

import java.lang.reflect.Method;
import java.util.Locale;

public final class I18n {
    private static boolean initialized;
    private static Method translateMethod;

    private I18n() {
    }

    public static String tr(String key, String fallback) {
        String translated = translateRaw(key);
        if (translated == null || translated.isEmpty() || translated.equals(key)) {
            return fallback;
        }
        return translated;
    }

    public static String trf(String key, String fallback, Object... args) {
        String pattern = tr(key, fallback);
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (Exception ignored) {
            try {
                return String.format(Locale.ROOT, fallback, args);
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private static String translateRaw(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        init();
        if (translateMethod == null) {
            return key;
        }
        try {
            Object result = translateMethod.invoke(null, key);
            return result instanceof String ? (String) result : key;
        } catch (Throwable ignored) {
            return key;
        }
    }

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> statCollector = Class.forName("net.minecraft.StatCollector");
            translateMethod = statCollector.getMethod("translateToLocal", String.class);
        } catch (Throwable ignored) {
            translateMethod = null;
        }
    }
}
