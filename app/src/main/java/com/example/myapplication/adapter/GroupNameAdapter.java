package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.example.myapplication.R;
import com.example.myapplication.util.XTextView;

import java.util.List;

public class GroupNameAdapter extends BaseAdapter {
    private List<String> mGroupNames;
    private LayoutInflater mLayoutInflater;
    private ItemDeleteButtonClickListener mItemDeleteButtonClickListener;

    public GroupNameAdapter() {
        super();
    }

    public List<String> getData() {
        return mGroupNames;
    }

    public void setData(List<String> data) {
        mGroupNames = data;
    }

    public void setOnItemDeleteButtonClickListener(ItemDeleteButtonClickListener listener) {
        mItemDeleteButtonClickListener = listener;
    }

    @Override
    public int getCount() {
        return mGroupNames == null ? 0 : mGroupNames.size();
    }

    @Override
    public Object getItem(int position) {
        return mGroupNames == null ? null : mGroupNames.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (mLayoutInflater == null) {
            mLayoutInflater = LayoutInflater.from(parent.getContext());
        }
        ViewHolder holder;
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.layout_item_group_name, parent, false);
            holder = new ViewHolder();
            holder.groupNameTv = convertView.findViewById(R.id.tv_group_name);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.groupNameTv.setDrawableEndListener(view -> {
            if (mItemDeleteButtonClickListener != null) {
                mItemDeleteButtonClickListener.OnItemDeleteButtonClickListener(position);
            }
        });
        holder.groupNameTv.setText(mGroupNames.get(position));
        return convertView;
    }

    public interface ItemDeleteButtonClickListener {
        void OnItemDeleteButtonClickListener(int position);
    }

    public static class ViewHolder {
        XTextView groupNameTv;
    }
}