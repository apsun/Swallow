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
    private static class ScanResultViewHolder {
        CheckBox enabledCheckBox;
        TextView bssidTextView;
        TextView ssidTextView;
        ImageView levelImageView;
        TextView levelTextView;
    }

    private Context _context;
    private LayoutInflater _layoutInflater;
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
        _layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ScanResultViewHolder viewHolder;
        if (convertView == null) {
            convertView = _layoutInflater.inflate(R.layout.network_scanresult_listitem, null);
            viewHolder = new ScanResultViewHolder();
            viewHolder.enabledCheckBox = (CheckBox)convertView.findViewById(R.id.network_scan_enabled_checkbox);
            viewHolder.bssidTextView = (TextView)convertView.findViewById(R.id.network_scan_bssid_textview);
            viewHolder.ssidTextView = (TextView)convertView.findViewById(R.id.network_scan_detail_textview);
            viewHolder.levelImageView = (ImageView)convertView.findViewById(R.id.network_scan_level_imageview);
            viewHolder.levelTextView = (TextView)convertView.findViewById(R.id.network_scan_level_textview);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ScanResultViewHolder)convertView.getTag();
        }

        ScanResult scanResult = getItem(position);
        String ssid = scanResult.SSID;
        String bssid = scanResult.BSSID;
        int level = scanResult.level;

        // TODO: Read checked value
        viewHolder.enabledCheckBox.setChecked(false);
        viewHolder.bssidTextView.setText(bssid);
        viewHolder.ssidTextView.setText(ssid);
        viewHolder.levelTextView.setText(String.valueOf(level));


        return convertView;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

    }
}
