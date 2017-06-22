package com.ti.app.mydoctor.gui.listadapter;

import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleAdapter;

public class UserListAdapter extends SimpleAdapter {
	
	private final Context context;
	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	private int reasourceID;

	public UserListAdapter(Context context,
			List<? extends Map<String, ?>> data, int resource, String[] from,
			int[] to) {
		super(context, data, resource, from, to);
		this.context = context;		
		this.data = data;
		this.from = from;
		this.to = to;
		this.reasourceID = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		
		View rowView = inflater.inflate(reasourceID, parent, false);
		
		String user_surname_label = (String) data.get(position).get(from[0]);
		GWTextView textUserSurnameLabel = (GWTextView) rowView.findViewById(to[0]);
		textUserSurnameLabel.setText(user_surname_label);
		
		String user_name_label = (String) data.get(position).get(from[1]);
		GWTextView textUserNameLabel = (GWTextView) rowView.findViewById(to[1]);
		textUserNameLabel.setText(user_name_label);
					
		String user_cf_label = (String) data.get(position).get(from[2]);
		GWTextView textUserCFLabel = (GWTextView) rowView.findViewById(to[2]);
		textUserCFLabel.setText(user_cf_label);		
					
		return rowView;
	}
	
	

}
