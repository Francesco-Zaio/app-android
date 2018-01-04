package com.ti.app.mydoctor.gui.adapter;

import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

public class MeasureListAdapter extends SimpleAdapter {

	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;

	public MeasureListAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		this.data = data;
		this.from = from;
		this.to = to;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = super.getView(position, convertView, parent);
		
		if (v != convertView && v != null) {
			ViewHolder holder = new ViewHolder();
			
			String strIcon = (String) data.get(position).get(from[0]);
			ImageView imageItem = v.findViewById(to[0]);
			imageItem.setImageResource(Integer.valueOf(strIcon));

			holder.measureDetailsIcon = imageItem;
			
			String strTopLabel = (String) data.get(position).get(from[1]);
			GWTextView topLabel = v.findViewById(to[1]);
			topLabel.setText(strTopLabel);
			holder.measureTopLabelTV = topLabel;

			if (from.length > 2) {
				String strBottomLabel = (String) data.get(position).get(from[2]);
				GWTextView bottomLabel = v.findViewById(to[2]);
				bottomLabel.setText(strBottomLabel);
				holder.measureBottomLabelTV = bottomLabel;
			}
			
			v.setTag(holder);
	    }
	
		ViewHolder holder = (ViewHolder) v.getTag();
	    String icon = (String) data.get(position).get(from[0]);
        holder.measureDetailsIcon.setImageResource(Integer.valueOf(icon));
	    String topLbl = (String) data.get(position).get(from[1]);
        holder.measureTopLabelTV.setText(topLbl);
        if (from.length > 2) {
            String bottomLbl = (String) data.get(position).get(from[2]);
            holder.measureBottomLabelTV.setText(bottomLbl);
        }
	    return v;
	}
	
	private class ViewHolder { 
    	ImageView measureDetailsIcon;
    	GWTextView measureTopLabelTV;
    	GWTextView measureBottomLabelTV;
    }

}
