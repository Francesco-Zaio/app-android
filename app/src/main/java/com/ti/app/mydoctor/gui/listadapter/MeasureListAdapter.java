package com.ti.app.mydoctor.gui.listadapter;

import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;
import android.widget.LinearLayout.LayoutParams;

public class MeasureListAdapter extends SimpleAdapter {
	
	private final Context context;
	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	private int reasourceID;

	public MeasureListAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
		super(context, data, resource, from, to);
		this.context = context;		
		this.data = data;
		this.from = from;
		this.to = to;
		this.reasourceID = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View v = super.getView(position, convertView, parent);
		
		if (v != convertView && v != null) {
			ViewHolder holder = new ViewHolder();
			
			String strIcon = (String) data.get(position).get(from[0]);
			ImageView imageItem = (ImageView) v.findViewById(to[0]);
			imageItem.setImageResource(Integer.valueOf(strIcon));
			
			/*LinearLayout.LayoutParams layoutParams = (LayoutParams) imageItem.getLayoutParams();
			layoutParams.height = 110;
			layoutParams.width = 110;
			imageItem.setLayoutParams(layoutParams);*/
			
			holder.measureDetailsIcon = imageItem;
			
			String strTopLabel = (String) data.get(position).get(from[1]);
			GWTextView topLabel = (GWTextView) v.findViewById(to[1]);
			topLabel.setText(strTopLabel);
			holder.measureTopLabelTV = topLabel;
							
			String strBottomLabel = (String) data.get(position).get(from[2]);
			GWTextView bottomLabel = (GWTextView) v.findViewById(to[2]);
			bottomLabel.setText(strBottomLabel);
			holder.measureBottomLabelTV = bottomLabel;
			
			v.setTag(holder);
	    }
	
		ViewHolder holder = (ViewHolder) v.getTag();
	    String icon = (String) data.get(position).get(from[0]);
	    String topLbl = (String) data.get(position).get(from[1]);
	    String bottomLbl = (String) data.get(position).get(from[2]);
	
	    holder.measureDetailsIcon.setImageResource(Integer.valueOf(icon));
	    holder.measureTopLabelTV.setText(topLbl);
	    holder.measureBottomLabelTV.setText(bottomLbl);
	
	    return v;
	}
	
	private class ViewHolder { 
    	public ImageView measureDetailsIcon;
    	public GWTextView measureTopLabelTV;
    	public GWTextView measureBottomLabelTV;
    }

}
