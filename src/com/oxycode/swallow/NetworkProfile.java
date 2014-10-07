package com.oxycode.swallow;

import java.util.*;

public class NetworkProfile implements Iterable<Map.Entry<Bssid, Boolean>> {
    public static class Editor {
        private String _editorName;
        private Map<Bssid, Boolean> _editorBssids;

        private Editor(String name, Map<Bssid, Boolean> bssids) {
            _editorName = name;
            _editorBssids = bssids;
        }

        public boolean contains(Bssid bssid) {
            return _editorBssids.get(bssid) != null;
        }

        public boolean isEnabled(Bssid bssid) {
            return Boolean.TRUE.equals(_editorBssids.get(bssid));
        }

        public String getName() {
            return _editorName;
        }

        public int count() {
            return _editorBssids.size();
        }

        public boolean put(Bssid bssid, boolean enabled) {
            return _editorBssids.put(bssid, enabled);
        }

        public boolean remove(Bssid bssid) {
            return _editorBssids.remove(bssid);
        }

        public void rename(String name) {
            _editorName = name;
        }

        public NetworkProfile save() {
            return new NetworkProfile(_editorName, _editorBssids, false);
        }
    }

    private final String _name;
    private final Map<Bssid, Boolean> _bssids;

    private NetworkProfile(String name, Map<Bssid, Boolean> bssids, boolean copy) {
        _name = name;
        if (copy) {
            _bssids = new HashMap<Bssid, Boolean>(bssids);
        } else {
            _bssids = bssids;
        }
    }

    public NetworkProfile(String name) {
        this(name, new HashMap<Bssid, Boolean>(), false);
    }

    public NetworkProfile(String name, Map<Bssid, Boolean> bssids) {
        this(name, bssids, true);
    }

    public boolean contains(Bssid bssid) {
        return _bssids.get(bssid) != null;
    }

    public boolean isEnabled(Bssid bssid) {
        return Boolean.TRUE.equals(_bssids.get(bssid));
    }

    public String getName() {
        return _name;
    }

    public int count() {
        return _bssids.size();
    }

    public Editor edit() {
        return new Editor(_name, new HashMap<Bssid, Boolean>(_bssids));
    }

    @Override
    public Iterator<Map.Entry<Bssid, Boolean>> iterator() {
        return _bssids.entrySet().iterator();
    }
}
