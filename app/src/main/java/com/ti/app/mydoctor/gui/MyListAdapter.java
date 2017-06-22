package com.ti.app.mydoctor.gui;

import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;

public class MyListAdapter extends SimpleAdapter {

	private int[] colors = new int[] { Color.argb(30, 242, 242, 242),
			Color.argb(30, 0, 0, 0) };

	public MyListAdapter(Context context, List<? extends Map<String, ?>> data,
			int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = super.getView(position, convertView, parent);

		int colorPos = position % colors.length;

		view.setBackgroundColor(colors[colorPos]);
		return view;
	}

}
