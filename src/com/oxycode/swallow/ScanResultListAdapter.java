package com.oxycode.swallow;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScanResultListAdapter extends BaseAdapter implements CompoundButton.OnCheckedChangeListener {
    private static class ScanResultListItem {
        CheckBox _selectedCheckBox;
        TextView _bssidTextView;
        TextView _ssidTextView;
        ImageView _levelImageView;
        TextView _levelTextView;
    }

    private Context _context;
    private Comparator<ScanResult> _resultSorter;
    private List<ScanResult> _results;

    public ScanResultListAdapter(Context context) {
        this(context, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                // Sort from strongest to weakest signal
                return rhs.level - lhs.level;
            }
        });
    }

    public ScanResultListAdapter(Context context, Comparator<ScanResult> sorter) {
        _context = context;
        _resultSorter = sorter;
    }

    public void updateResults(List<ScanResult> results) {
        Collections.sort(results, _resultSorter);
        _results = results;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return _results.size();
    }

    @Override
    public ScanResult getItem(int position) {
        return _results.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ScanResultListItem listItem;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater)_context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.network_scanresult_listitem, null);
            listItem = new ScanResultListItem();
            convertView.setTag(listItem);
        } else {
            listItem = (ScanResultListItem)convertView.getTag();
        }

        ScanResult scanResult = getItem(position);
        String ssid = scanResult.SSID;
        String bssid = scanResult.BSSID;
        int level = scanResult.level;



        return convertView;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

    }
}
