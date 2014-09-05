package com.oxycode.swallow;

import java.util.Set;

public class NetworkProfile {
    private final String _name;
    private final Bssid[] _bssids;

    public NetworkProfile(String name, Bssid[] bssids) {
        _name = name;
        _bssids = bssids;
    }

    public String getName() {
        return _name;
    }

    public Bssid[] getBssids() {
        return _bssids;
    }

    public boolean containsBssid(Bssid bssid) {
        for (Bssid b : _bssids) {
            if (b.equals(bssid)) {
                return true;
            }
        }
        return false;
    }

    public static boolean profilesContainBssid(Set<NetworkProfile> profiles, Bssid bssid) {
        for (NetworkProfile profile : profiles) {
            if (profile.containsBssid(bssid)) {
                return true;
            }
        }
        return false;
    }
}
