package com.oxycode.swallow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bssid {
    private static final Pattern bssidPattern;
    private final byte b1, b2, b3, b4, b5, b6;

    static {
        bssidPattern = Pattern.compile(
            "^" +
            "([0-9A-Fa-f]{2}):" +
            "([0-9A-Fa-f]{2}):" +
            "([0-9A-Fa-f]{2}):" +
            "([0-9A-Fa-f]{2}):" +
            "([0-9A-Fa-f]{2}):" +
            "([0-9A-Fa-f]{2})" +
            "$"
        );
    }

    public Bssid(byte[] bssidBytes) {
        if (bssidBytes.length != 6) {
            throw new IllegalArgumentException("BSSID must consist of 6 bytes");
        }

        b1 = bssidBytes[0];
        b2 = bssidBytes[1];
        b3 = bssidBytes[2];
        b4 = bssidBytes[3];
        b5 = bssidBytes[4];
        b6 = bssidBytes[5];
    }

    public Bssid(String bssidString) {
        Matcher matcher = bssidPattern.matcher(bssidString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("BSSID string is in an invalid format");
        }

        b1 = Byte.parseByte(matcher.group(1), 16);
        b2 = Byte.parseByte(matcher.group(2), 16);
        b3 = Byte.parseByte(matcher.group(3), 16);
        b4 = Byte.parseByte(matcher.group(4), 16);
        b5 = Byte.parseByte(matcher.group(5), 16);
        b6 = Byte.parseByte(matcher.group(6), 16);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Bssid)) return false;
        Bssid other = (Bssid)o;
        return b1 == other.b1 &&
               b2 == other.b2 &&
               b3 == other.b3 &&
               b4 == other.b4 &&
               b5 == other.b5 &&
               b6 == other.b6;
    }

    @Override
    public String toString() {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x", b1, b2, b3, b4, b5, b6);
    }
}
