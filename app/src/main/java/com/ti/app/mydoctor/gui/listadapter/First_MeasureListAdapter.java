package com.ti.app.mydoctor.gui.listadapter;

import java.util.List;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

public class First_MeasureListAdapter extends ArrayAdapter<MeasureListDataItem> {
	
	private boolean mIsAllMeasure;
	
	public First_MeasureListAdapter(Context context, int resource, int textViewResouce, List<MeasureListDataItem> listItem, boolean isAllMeasure) {
	    super(context, resource, textViewResouce, listItem);   
	    this.mIsAllMeasure = isAllMeasure;
	  }
	
	  /*public View getView(int position, View convertView, ViewGroup parent) {
	    View v = super.getView(position, convertView, parent);
	
	    if (v != convertView && v != null) {
	      ViewHolder holder = new ViewHolder();
	
	      ImageView iv = (ImageView) v.findViewById(R.id.icon_sent);
	      holder.measureDetailsIcon = iv;
	      
	      TextView tv_data = (TextView) v.findViewById(R.id.date_timestamp);
	      holder.measureDeatilsView = tv_data;
	      Typeface fontDataLabel = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Condensed.ttf");
	      tv_data.setTypeface(fontDataLabel);
	      holder.measureDeatilsView = tv_data;
	      
	      TextView tv_hour = (TextView) v.findViewById(R.id.hour_timestamp);
	      Typeface fontHourLabel = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf");
	      tv_hour.setTypeface(fontHourLabel);
	      holder.measureDeatilsView = tv_hour;
	      
	      View contentView = v.findViewById(R.id.content);
	      contentView.setVisibility(View.GONE);
	
	      v.setTag(holder);
	    }
	
	    ViewHolder holder = (ViewHolder) v.getTag();
	    String hours = getItem(position).hours;
	    String icon = getItem(position).icon;
	
	    holder.measureDetailsIcon.setImageResource(Integer.valueOf(icon));
	    holder.measureDeatilsView.setText(hours);
	
	    return v;
	  }*/
	  
	  public View getView(int position, View convertView, ViewGroup parent) {
	        View v = super.getView(position, convertView, parent);

	        if (v != convertView && v != null) {
	          ViewHolder holder = new ViewHolder();

	          if(mIsAllMeasure) {
		          ImageView iv = (ImageView) v.findViewById(R.id.icon_sent);
		          holder.measureDetailsIcon = iv;
		          GWTextView tv = (GWTextView) v.findViewById(R.id.timestamp);
		          holder.measureDeatilsView = tv;
	          } else {
	        	  ImageView iv = (ImageView) v.findViewById(R.id.icon_sent);
		          holder.measureDetailsIcon = iv;
		          GWTextView tv = (GWTextView) v.findViewById(R.id.hour_timestamp);
		          holder.measureDeatilsView = tv;
	          }

	          v.setTag(holder);
	        }

	        ViewHolder holder = (ViewHolder) v.getTag();
	        String hours = getItem(position).hours;
	        String icon = getItem(position).icon;

	        holder.measureDetailsIcon.setImageResource(Integer.valueOf(icon));
	        holder.measureDeatilsView.setText(hours);

	        return v;
	      }
	  
	  private class ViewHolder { 
	    	public ImageView measureDetailsIcon;
	    	public GWTextView measureDeatilsView;
	    }
	  
}
