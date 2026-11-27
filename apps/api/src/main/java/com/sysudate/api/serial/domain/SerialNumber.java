package com.sysudate.api.serial.domain;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public record SerialNumber(String value) {
    private static final Pattern PATTERN = Pattern.compile(
        "^SYSU-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    public SerialNumber {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid serial number");
        }
    }

    public static SerialNumber generate() {
        return new SerialNumber(
            "SYSU-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
    }

    public static SerialNumber parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Invalid serial number");
        }
        String trimmed = raw.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) > 0x7F) {
                throw new IllegalArgumentException("Invalid serial number");
            }
        }
        return new SerialNumber(trimmed.toUpperCase(Locale.ROOT));
    }
}
