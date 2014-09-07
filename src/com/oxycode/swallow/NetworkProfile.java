package com.oxycode.swallow;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.*;

public class NetworkProfile implements Parcelable, Iterable<Bssid> {
    public static final Parcelable.Creator<NetworkProfile> CREATOR = new Parcelable.Creator<NetworkProfile>() {
        public NetworkProfile createFromParcel(Parcel in) {
            return new NetworkProfile(in);
        }

        public NetworkProfile[] newArray(int size) {
            return new NetworkProfile[size];
        }
    };

    private final String _name;
    private final HashSet<Bssid> _bssids;

    private NetworkProfile(Parcel in) {
        _name = in.readString();
        Bssid[] bssids = in.createTypedArray(Bssid.CREATOR);
        _bssids = new HashSet<Bssid>(Arrays.asList(bssids));
    }

    public NetworkProfile(String name) {
        _name = name;
        _bssids = new HashSet<Bssid>();
    }

    public NetworkProfile(String name, HashSet<Bssid> bssids) {
        _name = name;
        _bssids = bssids;
    }

    public NetworkProfile(String name, Collection<Bssid> bssids) {
        _name = name;
        _bssids = new HashSet<Bssid>(bssids);
    }

    public boolean add(Bssid bssid) {
        return _bssids.add(bssid);
    }

    public boolean remove(Bssid bssid) {
        return _bssids.remove(bssid);
    }

    public boolean contains(Bssid bssid) {
        for (Bssid b : _bssids) {
            if (b.equals(bssid)) {
                return true;
            }
        }
        return false;
    }

    public String getName() {
        return _name;
    }

    public int size() {
        return _bssids.size();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(getName());
        Bssid[] bssids = new Bssid[_bssids.size()];
        out.writeTypedArray(_bssids.toArray(bssids), flags);
    }

    @Override
    public Iterator<Bssid> iterator() {
        return _bssids.iterator();
    }
}
