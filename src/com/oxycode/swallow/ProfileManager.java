package com.oxycode.swallow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class ProfileManager {
    private static final HashMap<String, NetworkProfile> _profiles;
    private static final NetworkProfile XMT = new NetworkProfile("XMT", new Bssid[] {
        new Bssid("00:1f:41:27:62:69"),
        new Bssid("00:1f:41:27:64:79"), // Weak

        new Bssid("00:22:7f:18:2c:79"),
        new Bssid("00:22:7f:18:33:39"),
        new Bssid("00:22:7f:18:2f:e9"),
        new Bssid("00:22:7f:18:33:19"),
        new Bssid("00:22:7f:18:21:c9"),
        new Bssid("00:22:7f:18:22:99"), // Weak
        new Bssid("00:22:7f:18:27:99"), // Weak

        new Bssid("58:93:96:1b:8c:d9"),
        new Bssid("58:93:96:1b:91:e9"),
        new Bssid("58:93:96:1b:92:19"),
        new Bssid("58:93:96:1b:91:99"),
        new Bssid("58:93:96:1b:8e:99"),
        new Bssid("58:93:96:1b:91:49")
    });

    static {
        _profiles = new HashMap<String, NetworkProfile>();
        addProfile(XMT);
    }

    private ProfileManager() {

    }

    public static void addProfile(NetworkProfile profile) {
        _profiles.put(profile.getName(), profile);
    }

    public static void deleteProfile(String profileName) {
        _profiles.remove(profileName);
    }

    public static void editProfile(String profileName, NetworkProfile newProfile) {
        deleteProfile(profileName);
        addProfile(newProfile);
    }

    public static NetworkProfile getProfile(String profileName) {
        return _profiles.get(profileName);
    }

    public static Set<NetworkProfile> getProfiles(Set<String> profileNames) {
        HashSet<NetworkProfile> profiles = new HashSet<NetworkProfile>();
        for (String profileName : profileNames) {
            profiles.add(getProfile(profileName));
        }
        return profiles;
    }
}
