package com.ti.app.mydoctor.gui.adapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.gui.alphabeticalindex.StringMatcher;
import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.app.Activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;
import android.widget.SimpleAdapter;

public class PatientListAdapter extends SimpleAdapter implements SectionIndexer {
	
	private String mSections = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	
	private final Context context;
	private int resourceID;
	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	
	
	public PatientListAdapter(Context context,
			List<? extends Map<String, ?>> data, int resource, String[] from,
			int[] to) {
		super(context, data, resource, from, to);
		this.context = context;		
		this.data = data;
		this.from = from;
		this.to = to;
		this.resourceID = resource;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		
		View rowView = inflater.inflate(resourceID, parent, false);
		
		String cf_label = (String) data.get(position).get(from[0]);
		GWTextView textCF = (GWTextView) rowView.findViewById(to[0]);
		textCF.setText(cf_label);
						
		String patient_surname_label = (String) data.get(position).get(from[1]);
		GWTextView textSurname = (GWTextView) rowView.findViewById(to[1]);
		textSurname.setText(patient_surname_label);
		
		String patient_name_label = (String) data.get(position).get(from[2]);
		GWTextView textName = (GWTextView) rowView.findViewById(to[2]);
		textName.setText(patient_name_label);
				
		return rowView;
	}

	@Override
	public int getPositionForSection(int section) {
		String KEY_PATIENT = "patient";
		
		// If there is no item for current section, previous section will be selected
		for (int i = section; i >= 0; i--) {
			for (int j = 0; j < getCount(); j++) {
				if (i == 0) {
					// For numeric section
					for (int k = 0; k <= 9; k++) {
						/*if (StringMatcher.match(String.valueOf(((String) getItem(j)).charAt(0)), String.valueOf(k)))
							return j;*/
						
						HashMap<String, String> p = (HashMap<String, String>) getItem(j);
						String patientName = p.get(KEY_PATIENT);
						int pos = patientName.lastIndexOf(' ');
						String surname = patientName.substring(pos+1);
						if (StringMatcher.match(String.valueOf(surname.charAt(0)), String.valueOf(mSections.charAt(i))))
							return j;
					}
				} else {
					/*if (StringMatcher.match(String.valueOf(((String) getItem(j)).charAt(0)), String.valueOf(mSections.charAt(i))))
						return j;*/
					
					HashMap<String, String> p = (HashMap<String, String>) getItem(j);
					String patientName = p.get(KEY_PATIENT);
					int pos = patientName.lastIndexOf(' ');
					String surname = patientName.substring(pos+1);
					if (StringMatcher.match(String.valueOf(surname.charAt(0)), String.valueOf(mSections.charAt(i))))
						return j;
				}
			}
		}
		return 0;
	}

	@Override
	public int getSectionForPosition(int position) {
		return 0;
	}

	@Override
	public Object[] getSections() {
		String[] sections = new String[mSections.length()];
		for (int i = 0; i < mSections.length(); i++)
			sections[i] = String.valueOf(mSections.charAt(i));
		return sections;
	}
}
