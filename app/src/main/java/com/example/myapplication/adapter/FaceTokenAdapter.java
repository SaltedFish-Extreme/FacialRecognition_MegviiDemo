package com.example.myapplication.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myapplication.R;

import java.util.List;

public class FaceTokenAdapter extends BaseAdapter {

    private List<String> mFaceTokens;
    private LayoutInflater mLayoutInflater;
    private ItemButtonClickListener mItemButtonClickListener;

    public FaceTokenAdapter() {
        super();
    }

    public List<String> getData() {
        return mFaceTokens;
    }

    public void setData(List<String> data) {
        mFaceTokens = data;
    }

    public void setOnItemButtonClickListener(ItemButtonClickListener listener) {
        mItemButtonClickListener = listener;
    }

    @Override
    public int getCount() {
        return mFaceTokens == null ? 0 : mFaceTokens.size();
    }

    @Override
    public Object getItem(int position) {
        return mFaceTokens == null ? null : mFaceTokens.get(position);
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
        final ViewHolder holder;
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.layout_item_face_token, parent, false);
            holder = new ViewHolder();
            holder.faceTokenNameTv = convertView.findViewById(R.id.tv_face_token);
            holder.unbindGroupTv = convertView.findViewById(R.id.tv_face_unbind);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.unbindGroupTv.setOnClickListener(v -> {
            if (mItemButtonClickListener != null) {
                mItemButtonClickListener.onItemUnbindButtonClickListener(position);
            }
        });

        holder.faceTokenNameTv.setOnLongClickListener(v -> {
            ClipboardManager cmb = (ClipboardManager) v.getContext().getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText(getClass().getCanonicalName(), holder.faceTokenNameTv.getText());
            cmb.setPrimaryClip(clipData);
            Toast.makeText(v.getContext(), "人脸标识已复制", Toast.LENGTH_SHORT).show();
            return true;
        });
        holder.faceTokenNameTv.setText(mFaceTokens.get(position));
        return convertView;
    }

    public interface ItemButtonClickListener {
        void onItemUnbindButtonClickListener(int position);
    }

    public static class ViewHolder {
        TextView faceTokenNameTv;
        TextView unbindGroupTv;
    }
}