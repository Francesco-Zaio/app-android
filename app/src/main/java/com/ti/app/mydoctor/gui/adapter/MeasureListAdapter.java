package com.ti.app.mydoctor.gui.adapter;

import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

public class MeasureListAdapter extends SimpleAdapter {

	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	private Context context;

	public MeasureListAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		this.context = context;
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
			String urgent = (String) data.get(position).get(from[3]);
			if ("true".equalsIgnoreCase(urgent)) {
                Drawable drawable = ContextCompat.getDrawable(context, R.drawable.urgent);
                int size = (int) Math.round(topLabel.getLineHeight() * 0.75); // The percentage you like (0.8, 0.9, etc.)
                drawable.setBounds(0, 0, size, size); // setBounds(int left, int top, int right, int bottom), in this case, drawable is a square image
                topLabel.setCompoundDrawablePadding(size / 2);
                topLabel.setCompoundDrawables(
                        null, //left
                        null, //top
                        drawable, //right
                        null //bottom
                );
            }
			holder.measureTopLabelTV = topLabel;

            String strBottomLabel = (String) data.get(position).get(from[2]);
            GWTextView bottomLabel = v.findViewById(to[2]);
            bottomLabel.setText(strBottomLabel);
            holder.measureBottomLabelTV = bottomLabel;
			
			v.setTag(holder);
	    }
	
		ViewHolder holder = (ViewHolder) v.getTag();
	    String icon = (String) data.get(position).get(from[0]);
        holder.measureDetailsIcon.setImageResource(Integer.valueOf(icon));
	    String topLbl = (String) data.get(position).get(from[1]);
        holder.measureTopLabelTV.setText(topLbl);
		String urgent = (String) data.get(position).get(from[3]);
		if ("true".equalsIgnoreCase(urgent)) {
			Drawable drawable = ContextCompat.getDrawable(context, R.drawable.urgent);
			int size = (int) Math.round(holder.measureTopLabelTV.getLineHeight() * 0.75); // The percentage you like (0.8, 0.9, etc.)
			drawable.setBounds(0, 0, size, size); // setBounds(int left, int top, int right, int bottom), in this case, drawable is a square image
			holder.measureTopLabelTV.setCompoundDrawablePadding(size / 2);
			holder.measureTopLabelTV.setCompoundDrawables(
			        null, //left
					null, //top
                    drawable, //right
					null //bottom
			);
		} else
            holder.measureTopLabelTV.setCompoundDrawables(
                    null, //left
                    null, //top
                    null, //right
                    null //bottom
            );
        String bottomLbl = (String) data.get(position).get(from[2]);
        holder.measureBottomLabelTV.setText(bottomLbl);

	    return v;
	}
	
	private class ViewHolder { 
    	ImageView measureDetailsIcon;
    	GWTextView measureTopLabelTV;
    	GWTextView measureBottomLabelTV;
    }

}
