package com.ti.app.mydoctor.gui.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class MainMenuListAdapter extends BaseExpandableListAdapter {
	private static final int[] EMPTY_STATE_SET = {};
    private static final int[] GROUP_EXPANDED_STATE_SET =
            {android.R.attr.state_expanded};
    private static final int[][] GROUP_STATE_SETS = {
         EMPTY_STATE_SET, // 0
         GROUP_EXPANDED_STATE_SET // 1
	};
	
    //GROUP
    private List<? extends Map<String, ?>> groupItem;
    private String[] fromGroup;
	private int[] toGroup;
    
	//CHILD
    private List<? extends Map<String, ?>> tempChild;
    private ArrayList< List<? extends Map<String, ?>> > childItem;// = new ArrayList<Object>();
    private String[] fromChildIt;
	private int[] toChildIt;
	
	public LayoutInflater minflater;
	public Activity activity;
	private final Context context;
	
	public MainMenuListAdapter(Context context,
			String[] fromGroup, int[] toGroup,
			String[] fromChildIt, int[] toChildIt) {
		this.context = context;

		this.fromGroup = fromGroup;
		this.toGroup = toGroup;

		this.fromChildIt = fromChildIt;
		this.toChildIt = toChildIt;
	}
	
	public void setInflater(LayoutInflater mInflater, Activity act) {
		this.minflater = mInflater;
		activity = act;
	}
	
	public void setGroupList(List<? extends Map<String, ?>> groupList) {
		this.groupItem = groupList;
	}
	
	public void setChildItemList(ArrayList< List<? extends Map<String, ?>> > childItem) {
		this.childItem = childItem;
	}

	@Override
	public Object getChild(int arg0, int arg1) {
		return null;
	}

	@Override
	public long getChildId(int groupPosition, int childPosition) {
		return 0;
	}

	@Override
	public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
		tempChild = childItem.get(groupPosition);
        
        if (convertView == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.device_list_main_menu_child_item,parent,false);
        }

        String el_label = (String) tempChild.get(childPosition).get(fromChildIt[0]);
        TextView textLabel = (TextView) convertView.findViewById(toChildIt[0]);
		textLabel.setText(el_label);

        convertView.setTag(tempChild.get(childPosition));
        return convertView;
	}

	@Override
	public int getChildrenCount(int groupPosition) {
		
		List<? extends Map<String, ?>> childList = childItem.get(groupPosition);
		
		if( childList!= null)
			return childList.size();
		else
			return 0;		
	}

	@Override
	public Object getGroup(int groupPosition) {
		return null;
	}

	@Override
	public int getGroupCount() {
		return groupItem.size();
	}

	@Override
	public void onGroupCollapsed(int groupPosition) {
		super.onGroupCollapsed(groupPosition);
	}

	@Override
	public void onGroupExpanded(int groupPosition) {
		super.onGroupExpanded(groupPosition);
	}
	
	@Override
	public long getGroupId(int groupPosition) {
		return 0;
	}

	@Override
	public View getGroupView(int groupPosition, boolean isExpanded,	View convertView, ViewGroup parent) {
		if (convertView == null)
        {
            LayoutInflater layoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.device_list_main_menu_group_item,parent,false);
        }

        String el_icon = (String) groupItem.get(groupPosition).get(fromGroup[0]);
		ImageView imageItem = (ImageView) convertView.findViewById(toGroup[0]);
		imageItem.setImageResource(Integer.valueOf(el_icon));
		
		String el_label = (String) groupItem.get(groupPosition).get(fromGroup[1]);
		TextView textView = (TextView)convertView.findViewById(toGroup[1]);
        textView.setText(el_label);
        
        convertView.setTag(groupItem.get(groupPosition));
		
			
		View ind = convertView.findViewById( R.id.explist_indicator);
		if( ind != null ) {
			ImageView indicator = (ImageView)ind;
			if( getChildrenCount( groupPosition ) == 0 ) {
				indicator.setVisibility( View.INVISIBLE );
			} else {
				indicator.setVisibility( View.VISIBLE );
				int stateSetIndex = ( isExpanded ? 1 : 0) ;
				Drawable drawable = indicator.getDrawable();
				drawable.setState(GROUP_STATE_SETS[stateSetIndex]);
			}
		}
		
		return convertView;
	}
	
	@Override
	public boolean hasStableIds() {
		return false;
	}

	@Override
	public boolean isChildSelectable(int groupPosition, int childPosition) {
		return true;
	}
}
