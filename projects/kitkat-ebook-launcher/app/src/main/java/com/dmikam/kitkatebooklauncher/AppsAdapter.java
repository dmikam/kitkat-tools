package com.dmikam.kitkatebooklauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AppsAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppItem> apps = new ArrayList<>();

    public AppsAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setData(List<AppItem> newApps) {
        apps.clear();
        if (newApps != null) {
            apps.addAll(newApps);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppItem getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_app_icon);
            holder.tvLabel = convertView.findViewById(R.id.tv_app_label);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AppItem item = getItem(position);
        holder.tvLabel.setText(item.getLabel());
        if (item.getIcon() != null) {
            holder.ivIcon.setImageDrawable(item.getIcon());
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
    }
}

