package com.ti.app.mydoctor.gui.listadapter;

import java.util.List;
import java.util.Map;

import com.ti.app.mydoctor.gui.customview.GWTextView;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleAdapter;

/*public class DeviceListAdapter extends SimpleAdapter {

	private int[] colors = new int[] { Color.argb(30, 242, 242, 242),
			Color.argb(30, 0, 0, 0) };

	public DeviceListAdapter(Context context, List<? extends Map<String, ?>> data,
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

}*/

public class DeviceListAdapter extends SimpleAdapter {
	private final Context context;
	private List<? extends Map<String, ?>> data;
	private String[] from;
	private int[] to;
	private int reasourceID;
	
	//Dimensioni immagini e testo
	/*private static final int IMAGE_SIZE_LARGE_TABLET = 300;
	private static final int IMAGE_SIZE_MEDIUM_TABLET = 256;
	private static final int IMAGE_SIZE_LARGE_PHONE = 135;
	private static final int IMAGE_SIZE_MEDIUM_PHONE = 100;
	private static final int FONT_SIZE_LARGE_TABLET = 40;
	private static final int FONT_SIZE_LARGE_PHONE = 20;*/
	
	private static final int IMAGE_SIZE_LARGE_TABLET = 400;
	private static final int IMAGE_SIZE_MEDIUM_TABLET = 300;
	private static final int IMAGE_SIZE_LARGE_PHONE = 160;
	private static final int IMAGE_SIZE_MEDIUM_PHONE = 145;
	private static final int FONT_SIZE_LARGE_TABLET = 60;
	private static final int FONT_SIZE_LARGE_PHONE = 35;
	
	//Parametri Display
	private float densityCoefficient;
	private int actionBarPixelHeight;
	
	private int widthPixels;
	private int heightPixels;
	
	private float widthDp;
	private float heightDp;
	private float smallestWidth;
	
	private boolean isTablet;
	private boolean isGrid;	
	private boolean isPortrait = true;
	
	public DeviceListAdapter(Context context, List<? extends Map<String, ?>> data,
			int resource, String[] from, int[] to, boolean isGrid, boolean isPortrait) {
		super(context, data, resource, from, to);
		this.context = context;		
		this.data = data;
		this.from = from;
		this.to = to;
		this.reasourceID = resource;
		this.isGrid = isGrid;
		this.isPortrait = isPortrait;
	}

	/*@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		
		View rowView = inflater.inflate(reasourceID, parent, false);
		
		String el_icon = (String) data.get(position).get(from[0]);
		ImageView imageItem = (ImageView) rowView.findViewById(to[0]);
		imageItem.setImageResource(Integer.valueOf(el_icon));
		
		LinearLayout.LayoutParams layoutParams = (LayoutParams) imageItem.getLayoutParams();
		layoutParams.height = 110;
		layoutParams.width = 110;
		imageItem.setLayoutParams(layoutParams);
		
		String el_label = (String) data.get(position).get(from[1]);
		GWTextView textLabel = (GWTextView) rowView.findViewById(to[1]);
		textLabel.setText(el_label);
						
		String el_modul = (String) data.get(position).get(from[2]);
		GWTextView textModul = (GWTextView) rowView.findViewById(to[2]);
		textModul.setText(el_modul);
				
		return rowView;
	}*/
	
	public void clearData() {
		data.clear();
	}
	
	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		
		// Nascondo le misure non gestite (come ad es. l'Emergenza)
		if (!isGrid && data.get(position).get(from[1]).toString().endsWith("??")) {
			
			LinearLayout ll = new LinearLayout(context);
			ll.setVisibility(View.GONE);
			return ll;			
		}
		
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		View rowView = inflater.inflate(reasourceID, parent, false);
		
		// Nascondo le misure non gestite (come ad es. l'Emergenza)
		if (isGrid && data.get(position).get(from[1]).toString().endsWith("??")) {			
			rowView.setVisibility(View.INVISIBLE);
		}
		
		String el_icon = (String) data.get(position).get(from[0]);
		ImageView imageItem = (ImageView) rowView.findViewById(to[0]);
		imageItem.setImageResource(Integer.valueOf(el_icon));
		
		String el_label = (String) data.get(position).get(from[1]);
		GWTextView textLabel = (GWTextView) rowView.findViewById(to[1]);
		textLabel.setText(el_label);
		
		
						
		GWTextView textModul = null;
		if( !isGrid ) {
			String el_modul = (String) data.get(position).get(from[2]);
			textModul = (GWTextView) rowView.findViewById(to[2]);
			textModul.setText(el_modul);
		}		
		
		//Ricava parametri display corrente
		getDisplayParameter();
				
		//Ricava numero di righe della lista
		int numberOfRow = data.size();
		if( (data.size() == 4) && isGrid ) {
			//In questo caso si è scelto di impostare una griglia 2x2
			numberOfRow = data.size() / 2;
		}
		
				
		if(isPortrait) {
			//PORTRAIT
			/*if( data.size() <= 4 ) {
				ViewGroup.LayoutParams params = rowView.getLayoutParams();
				params.height = (heightPixels - actionBarPixelHeight)/(numberOfRow);
		        rowView.setLayoutParams(params);
		        rowView.requestLayout();
			}*/
			
			if( isGrid ) {
				if( data.size() <= 4 ) {
					ViewGroup.LayoutParams params = rowView.getLayoutParams();
					params.height = (heightPixels - actionBarPixelHeight)/(numberOfRow);
			        rowView.setLayoutParams(params);
			        rowView.requestLayout();
				}
				
				if(data.size() <= 2) {
					
					if( isTablet ) {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_LARGE_TABLET*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_LARGE_TABLET*densityCoefficient);
						textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
						//textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
					} else {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_LARGE_PHONE*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_LARGE_PHONE*densityCoefficient);
						textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_PHONE);
						//textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_PHONE);
					}					
				}
				
				//if(data.size() == 3){
				if(data.size() <= 4){
					if( densityCoefficient >= 2 ) {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_MEDIUM_PHONE*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_MEDIUM_PHONE*densityCoefficient);
					}
					
					ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) textLabel.getLayoutParams();
					mlp.setMargins(1, 0, 0, 0);			
				}
			} /*else {
				if( isTablet ) {
					
					if(data.size() <= 2) {
						
						if( isTablet ) {
							imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_MEDIUM_TABLET*densityCoefficient);
							imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_MEDIUM_TABLET*densityCoefficient);
							
							RelativeLayout.LayoutParams paramsLabel = (RelativeLayout.LayoutParams)( ((TextView)textLabel).getLayoutParams() );
							paramsLabel.setMargins(40, 70, 0, 0); //substitute parameters for left, top, right, bottom
							textLabel.setLayoutParams(paramsLabel);							
							textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
							
							RelativeLayout.LayoutParams paramsModul = (RelativeLayout.LayoutParams)( ((TextView)textModul).getLayoutParams() );
							paramsModul.setMargins(40, 0, 0, 0); //substitute parameters for left, top, right, bottom
							textModul.setLayoutParams(paramsModul);
							textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
						} 
					} 
				}
			}*/
			
		} else {
			//LANDSCAPE
			if( isGrid ) {
				if( data.size() <= 3 ) {
					ViewGroup.LayoutParams params = rowView.getLayoutParams();
					params.height = (heightPixels - actionBarPixelHeight);
					rowView.setLayoutParams(params);
			        rowView.requestLayout();
				}
				
				if(data.size() <= 2) {
					
					if( isTablet ) {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_LARGE_TABLET*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_LARGE_TABLET*densityCoefficient);
						textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
						//textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
					} else {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_LARGE_PHONE*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_LARGE_PHONE*densityCoefficient);
						textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_PHONE);
						//textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_PHONE);
					}
				} else if (data.size() == 3) {
					if( densityCoefficient >= 2 ) {
						imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_MEDIUM_PHONE*densityCoefficient);
						imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_MEDIUM_PHONE*densityCoefficient);
					}
				}
				
			} /*else {
				if( isTablet ) {
					if( data.size() <= 4 ) {
						ViewGroup.LayoutParams params = rowView.getLayoutParams();
						params.height = (heightPixels - actionBarPixelHeight)/(numberOfRow);
				        rowView.setLayoutParams(params);
				        rowView.requestLayout();
					}
					
					if(data.size() <= 2) {
						
						if( isTablet ) {
							imageItem.getLayoutParams().height = (int) (IMAGE_SIZE_MEDIUM_TABLET*densityCoefficient);
							imageItem.getLayoutParams().width = (int) (IMAGE_SIZE_MEDIUM_TABLET*densityCoefficient);
							
							RelativeLayout.LayoutParams paramsLabel = (RelativeLayout.LayoutParams)( ((TextView)textLabel).getLayoutParams() );
							paramsLabel.setMargins(40, 70, 0, 0); //substitute parameters for left, top, right, bottom
							textLabel.setLayoutParams(paramsLabel);							
							textLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
							
							RelativeLayout.LayoutParams paramsModul = (RelativeLayout.LayoutParams)( ((TextView)textModul).getLayoutParams() );
							paramsModul.setMargins(40, 0, 0, 0); //substitute parameters for left, top, right, bottom
							textModul.setLayoutParams(paramsModul);
							textModul.setTextSize(TypedValue.COMPLEX_UNIT_SP, FONT_SIZE_LARGE_TABLET);
						} 
					} 
				} else {
					if( data.size() < 3 ) {
						ViewGroup.LayoutParams params = rowView.getLayoutParams();
						params.height = (heightPixels - actionBarPixelHeight)/(numberOfRow);
				        rowView.setLayoutParams(params);
				        rowView.requestLayout();
					}
				}				
			}*/
		}
		
		return rowView;
	}
	
	private void getDisplayParameter() {
		
		//Ricava caratteristiche dello schermo
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);
		
		//Ricava la densità dei pixel del display
		
		densityCoefficient = metrics.density;
		//Ricava altezza in pixel dell'actionbar
		
		actionBarPixelHeight = (int) ( dipToPixels(context, 48) + (35*densityCoefficient) );
		
		//Ricava altezza e larghezza display in base alla SO version
		// SDK_INT = 1;
		widthPixels = metrics.widthPixels;
		heightPixels = metrics.heightPixels;
		
		if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17) {
			try {
			    widthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
			    heightPixels = ( (Integer) Display.class.getMethod("getRawHeight").invoke(display) );
			} catch (Exception ignored) {
			}
		}
		// includes window decorations (statusbar bar/menu bar)
		if (Build.VERSION.SDK_INT >= 17) {
			try {
			    Point realSize = new Point();
			    Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
			    widthPixels = realSize.x;
			    heightPixels = realSize.y;
			} catch (Exception ignored) {
			}
		}
		
		//Ricava le dimensioni del diplay in funzione dei dp
		widthDp = widthPixels/densityCoefficient;
		heightDp = heightPixels/densityCoefficient;
		
		smallestWidth = Math.min(widthDp, heightDp);
				
		isTablet = (smallestWidth >= 600);			
	}

	private static float dipToPixels(Context context, float dipValue) {
	    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
	    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
	}

}
