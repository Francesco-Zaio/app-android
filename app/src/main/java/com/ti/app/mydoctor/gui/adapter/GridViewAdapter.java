package com.ti.app.mydoctor.gui.adapter;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.ti.app.mydoctor.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class GridViewAdapter extends ArrayAdapter<GridViewAdapter.ImageItem> {
    private Context context;
    private int layoutResourceId;
    private List<ImageItem> data;
    private SparseBooleanArray mSelectedItemsIds;

    public GridViewAdapter(Context context, int layoutResourceId, List<ImageItem> data) {
        super(context, layoutResourceId, data);
        mSelectedItemsIds = new SparseBooleanArray();
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
    }

    static class ViewHolder {
        ImageView image;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewHolder holder;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);
            holder = new ViewHolder();
            holder.image = row.findViewById(R.id.image);
            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        ImageItem item = data.get(position);
        holder.image.setImageBitmap(item.getImage());
        return row;
    }

    @Override
    public void remove(ImageItem object) {
        data.remove(object);
        notifyDataSetChanged();
    }

    public List<GridViewAdapter.ImageItem> getImageItems() {
        return data;
    }

    public void toggleSelection(int position) {
        selectView(position, !mSelectedItemsIds.get(position));
    }

    public void removeSelection() {
        mSelectedItemsIds = new SparseBooleanArray();
        notifyDataSetChanged();
    }

    public void selectView(int position, boolean value) {
        if (value)
            mSelectedItemsIds.put(position, value);
        else
            mSelectedItemsIds.delete(position);
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return mSelectedItemsIds.size();
    }

    public SparseBooleanArray getSelectedIds() {
        return mSelectedItemsIds;
    }

    public class ImageItem {
        private Bitmap image;

        public ImageItem(Bitmap image, String title) {
            super();
            this.image = image;
        }

        public Bitmap getImage() {
            return image;
        }

        public void setImage(Bitmap image) {
            this.image = image;
        }
    }
}
