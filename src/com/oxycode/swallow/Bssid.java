package com.oxycode.swallow;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bssid implements Parcelable {
    private static final Pattern BSSID_PATTERN = Pattern.compile(
        "^" +
        "([0-9A-Fa-f]{2}):" +
        "([0-9A-Fa-f]{2}):" +
        "([0-9A-Fa-f]{2}):" +
        "([0-9A-Fa-f]{2}):" +
        "([0-9A-Fa-f]{2}):" +
        "([0-9A-Fa-f]{2})" +
        "$"
    );

    public static final Parcelable.Creator<Bssid> CREATOR = new Parcelable.Creator<Bssid>() {
        public Bssid createFromParcel(Parcel in) {
            return new Bssid(in);
        }

        public Bssid[] newArray(int size) {
            return new Bssid[size];
        }
    };

    private final byte b1, b2, b3, b4, b5, b6;

    public Bssid(String bssidString) {
        Matcher matcher = BSSID_PATTERN.matcher(bssidString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("BSSID string is invalid: " + bssidString);
        }

        // Use Integer parse method because Byte.parseByte doesn't
        // handle unsigned values very well, it seems.
        // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6259307
        b1 = (byte)Integer.parseInt(matcher.group(1), 16);
        b2 = (byte)Integer.parseInt(matcher.group(2), 16);
        b3 = (byte)Integer.parseInt(matcher.group(3), 16);
        b4 = (byte)Integer.parseInt(matcher.group(4), 16);
        b5 = (byte)Integer.parseInt(matcher.group(5), 16);
        b6 = (byte)Integer.parseInt(matcher.group(6), 16);
    }

    private Bssid(Parcel in) {
        b1 = in.readByte();
        b2 = in.readByte();
        b3 = in.readByte();
        b4 = in.readByte();
        b5 = in.readByte();
        b6 = in.readByte();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeByte(b1);
        out.writeByte(b2);
        out.writeByte(b3);
        out.writeByte(b4);
        out.writeByte(b5);
        out.writeByte(b6);
    }

    @Override
    public int hashCode() {
        int result = (int)b1;
        result = 31 * result + (int)b2;
        result = 31 * result + (int)b3;
        result = 31 * result + (int)b4;
        result = 31 * result + (int)b5;
        result = 31 * result + (int)b6;
        return result;
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
