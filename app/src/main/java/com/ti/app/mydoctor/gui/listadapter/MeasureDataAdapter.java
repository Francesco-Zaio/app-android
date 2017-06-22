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

public class MeasureDataAdapter extends SimpleAdapter {
	
	private final Context context;
	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	private int reasourceID;
	
	public enum LayoutType { NORMAL,
					  		 TABLE_ROW }

	private LayoutType lType;

	public MeasureDataAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from,
			int[] to, LayoutType type) {
		super(context, data, resource, from, to);
		this.context = context;		
		this.data = data;
		this.from = from;
		this.to = to;
		this.reasourceID = resource;
		this.lType = type;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		
		View rowView = inflater.inflate(reasourceID, parent, false);
		
		
		switch(lType) {
		case NORMAL:
			String el_label = (String) data.get(position).get(from[0]);
			GWTextView textLabel = (GWTextView) rowView.findViewById(to[0]);
			textLabel.setText(el_label);
							
			String el_modul = (String) data.get(position).get(from[1]);
			GWTextView textModul = (GWTextView) rowView.findViewById(to[1]);
			textModul.setText(el_modul);
			break;
		case TABLE_ROW:
			String read_label = (String) data.get(position).get(from[0]);
			GWTextView readLabel = (GWTextView) rowView.findViewById(to[0]);
			readLabel.setText(read_label);
							
			String theoric_label = (String) data.get(position).get(from[1]);
			GWTextView theoricLabel = (GWTextView) rowView.findViewById(to[1]);
			theoricLabel.setText(theoric_label);
			
			String read_value = (String) data.get(position).get(from[2]);
			GWTextView readValue = (GWTextView) rowView.findViewById(to[2]);
			readValue.setText(read_value);
							
			String theoric_value = (String) data.get(position).get(from[3]);
			GWTextView theoricValue = (GWTextView) rowView.findViewById(to[3]);
			theoricValue.setText(theoric_value);
			break;
			default:
		}	
		
				
		return rowView;
	}
	
	

}
