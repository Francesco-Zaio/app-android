package com.ti.app.mydoctor.gui;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.scmodule.ServerCertificateManager;
import com.ti.app.telemed.core.syncmodule.SendMeasuresService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.exceptions.XmlException;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.DragSortController.Direction;
import com.ti.app.mydoctor.gui.customview.DragSortListView;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.MeasureListAdapter;
import com.ti.app.mydoctor.util.MediaScannerNotifier;

public class ShowMeasure extends ActionBarListActivity{

    public static final String ALL_MEASURES = "ALL";
    public static final String SHOW_MEASURE_KEY = "SHOW_MEASURE_KEY";
    public static final String SELECTED_MEASURE_KEY = "SELECTED_MEASURE_KEY";

	private static final String TAG = "ShowMeasure";
	
	//La misura selezionata dall'utente: Si possono visualizzare i dettagli della misura, eliminarla oppure riprovare l'invio
	private Measure selected_measure;

	private static final String KEY_ICON_SENT = "icon_sent";
	private static final String KEY_LABEL = "label";
	private static final String KEY_TIMESTAMP = "timestamp";
	private static final String KEY_DATE = "date";
	private static final String KEY_HOUR = "hour";
	
	// Intent request codes
    private static final int MEASURE_DETAILS = 1;
    
	//Dialog
	private static final int ALERT_DIALOG = 0;
	private static final int DELETE_CONFIRM_DIALOG = 1;
	private static final int SIMPLE_DIALOG = 7;
	private static final int SEND_MEASURES_DIALOG = 8;
	private static final int SWIPE_DELETE_CONFIRM_DIALOG = 11;
	
	//Bundle
	private static final String DELETE_TYPE = "DELETE_TYPE";
	private static final String MEASURE_NUMBER = "MEASURE_NUMBER";

	private MeasureListAdapter listAdapter;
	private Bundle dataBundle = null;
	private Bundle measuresResultBundle = null;
	private ArrayList<HashMap<String, String>> measures = null;
	private ArrayList<Measure> listaMisure = null;
    int numMeasureToSend;
	private DragSortListView mListView;
	private String currentOperation = "";
	private MeasureManager measureManager;
	private GWTextView titleTV;
	private boolean silent = false;
	private Context context;
	private String[] filterIds = null;
	private boolean isImageManager = false;

	private DragSortListView.RemoveListener onRemove = 
            new DragSortListView.RemoveListener() {
		    	@Override
		        public void remove(int which) {
		    		//Viene eliminata la singola misura
		    		selected_measure = listaMisure.get(which);
					measureManager.deleteMeasure(selected_measure);
					measures.clear();
					populateActivity(currentOperation);
					if(!measures.isEmpty())
						Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
		        }
		    	
                @Override
				public void removingRequest(int which, Direction direction) {
					selected_measure = listaMisure.get(which);					
                    Log.i(TAG, "Elimino misura: " + selected_measure.getMeasureType());
                    if (!silent){
                        dataBundle = new Bundle();
                        dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
                        dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + AppResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + "?");
                        showDialog(SWIPE_DELETE_CONFIRM_DIALOG);
                    }

				}

				@Override
				public void itemChangeLayout(int which, int mode) {
				}				
        };
        
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		setContentView(R.layout.measure_list);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		measureManager = MeasureManager.getMeasureManager();
		
		// create the grid item mapping
		String[] from;
		int[] to;
		
		measures = new ArrayList<>();
		
		Bundle data = getIntent().getExtras();
		String activityTitle = data.getString(SHOW_MEASURE_KEY);
		
		if(activityTitle != null) {			
			if(activityTitle.equals("ALL")) {
				from = new String[] { KEY_ICON_SENT, KEY_LABEL, KEY_TIMESTAMP };
				to = new int[] { R.id.icon_sent, R.id.label, R.id.timestamp };
				listAdapter = new MeasureListAdapter(this, measures, R.layout.all_measure_item_layout, from, to);
				setListAdapter(listAdapter);
				populateActivity("ALL");
				currentOperation = "ALL";
			}
			else {
				from = new String[] { KEY_ICON_SENT, KEY_DATE, KEY_HOUR };
				to = new int[] { R.id.icon_sent, R.id.date_timestamp, R.id.hour_timestamp };
				//listAdapter = new DeviceListAdapter(this, measures, R.layout.measure_item_layout, from, to);
				listAdapter = new MeasureListAdapter(this, measures, R.layout.measure_item_layout, from, to);
				setListAdapter(listAdapter);
				populateActivity(data.getString(SELECTED_MEASURE_KEY));
				currentOperation = data.getString(SELECTED_MEASURE_KEY);
			}

            ActionBar customActionBar = this.getSupportActionBar();
            if (customActionBar != null) {
                //Setta il gradiente di sfondo della action bar
                Drawable cd = ContextCompat.getDrawable(getApplicationContext(),R.drawable.action_bar_background_color);
                customActionBar.setBackgroundDrawable(cd);

                customActionBar.setDisplayShowCustomEnabled(true);
                customActionBar.setDisplayShowTitleEnabled(false);

                //Setta l'icon
                customActionBar.setIcon(R.drawable.icon_action_bar);

                //Ricava la TextView dell'ActionBar
                LayoutInflater inflator = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View titleView = inflator.inflate(R.layout.actionbar_title, null);
                titleTV = (GWTextView) titleView.findViewById(R.id.actionbar_title_label);
                titleTV.setText(R.string.show_measures);
                customActionBar.setCustomView(titleView);

                //L'icona dell'App diventa tasto per tornare nella Home
                customActionBar.setHomeButtonEnabled(true);
                customActionBar.setDisplayHomeAsUpEnabled(true);
            }
			
			if (activityTitle.equals("ALL")) {
				setTitle(getString(R.string.manageMeasure));
			}
			else {
				setTitle(getString(R.string.measure_title) + " " + activityTitle);
			}
		}
		
		mListView = (DragSortListView) getListView();
		mListView.setRemoveListener(onRemove);
		mListView.setOnItemClickListener(listViewItemClickListener);

		mListView.setDivider(null); //rimuove la linea di bordo
		mListView.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		registerForContextMenu(mListView);	
		
		silent = false;

        String selecteMeasType = data.getString(SELECTED_MEASURE_KEY);
        if (selecteMeasType != null) {
            isImageManager = selecteMeasType.equalsIgnoreCase(GWConst.KMsrImg);
		}
	}

	private void retrySendAllMeasure() {
     	String idUser = UserManager.getUserManager().getCurrentUser().getId();
        int numMeasures = DbManager.getDbManager().getNumNotSentMeasures(idUser);
    	if(numMeasures == 0) {
    		if (!silent)
    			showDialog(SIMPLE_DIALOG);
    	}
    	else {
    		if (!silent){
	    		measuresResultBundle = new Bundle();
	    		measuresResultBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
	    		measuresResultBundle.putInt(MEASURE_NUMBER, numMeasures);
    			showDialog(SEND_MEASURES_DIALOG);
    		}
    	}
	}

	private void setTitle(String title) {
		if (titleTV == null)
			titleTV = (GWTextView)findViewById(R.id.actionbar_title_label);
		titleTV.setText(title);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		switch(id) {
		case SEND_MEASURES_DIALOG:
			builder.setTitle(measuresResultBundle.getString(AppConst.TITLE));
			builder.setTitle(getString(R.string.send) + " " + measuresResultBundle.getInt(MEASURE_NUMBER) + " " + getString(R.string.sendToPlatform) + "?");
			builder.setPositiveButton(AppResourceManager.getResource().getString("yes"), send_measures_dialog_click_listener);
			builder.setNegativeButton(AppResourceManager.getResource().getString("no"), send_measures_dialog_click_listener);
			break;
		case SIMPLE_DIALOG:
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("KMsgNoMeasureToSend"));
			builder.setNeutralButton(AppResourceManager.getResource().getString("okButton"), simple_dialog_click_listener);
			break;
		case ALERT_DIALOG:
			builder.setTitle(dataBundle.getString(AppConst.TITLE));
			builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
			builder.setNeutralButton(AppResourceManager.getResource().getString("okButton"), alert_dialog_click_listener);
			break;
		case DELETE_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(AppConst.TITLE));
			builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
			builder.setPositiveButton(AppResourceManager.getResource().getString("confirmButton"), delete_confirm_dialog_click_listener);
			builder.setNegativeButton(AppResourceManager.getResource().getString("cancelButton"), delete_confirm_dialog_click_listener);
			break;		
		case SWIPE_DELETE_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(AppConst.TITLE));
			builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
			builder.setPositiveButton(AppResourceManager.getResource().getString("confirmButton"), swipe_delete_confirm_dialog_click_listener);
			builder.setNegativeButton(AppResourceManager.getResource().getString("cancelButton"), swipe_delete_confirm_dialog_click_listener);
			builder.setOnKeyListener( new Dialog.OnKeyListener() {
				
				@Override
				public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
					if (keyCode == KeyEvent.KEYCODE_BACK) {
	                    mListView.cancelDrag();
	                    dialog.dismiss();
	                }
	                return true;				
				}
			});
			break;
		}
		
		return builder.create();
	}

	@Override
	public void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.show_measure_menu, menu);
		return true;		
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//Controllo se la lista misura è stata inizializzata
		if(listaMisure != null) {
		     numMeasureToSend = DbManager.getDbManager().getNumNotSentMeasures(UserManager.getUserManager().getCurrentUser().getId());
			//Controllo se nella lista ci sono misure da inviare
			if(numMeasureToSend == 0) {
				menu.findItem(R.id.retry_send_all_measure).setVisible(false);
			} else {
				menu.findItem(R.id.retry_send_all_measure).setVisible(true);
			}			
		} else {
			menu.findItem(R.id.retry_send_all_measure).setVisible(false);
		}
		//return super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Opening submenu in action bar on Hardware menu button click
		/*if(event.getAction() == KeyEvent.ACTION_UP){
		    switch(keyCode) {
		    case KeyEvent.KEYCODE_MENU:

		    	mActionBarMenu.performIdentifierAction(R.id.mi_action_bar_menu_overflow, 0);

		        return true;  
		    }
		}*/
		return super.onKeyUp(keyCode, event);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		
	    switch (item.getItemId()) {
	    case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            finish();
            return true; 
            
	    case R.id.delete_all_measure:
	    	if (!silent){
		    	dataBundle = new Bundle();
		    	dataBundle.putInt(DELETE_TYPE, 2);
		    	dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
		    	if(currentOperation.equals("ALL"))
		    		dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("showMeasureDialogDeleteQuestion3") + "?");
		    	else
		    		dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("showMeasureDialogDeleteQuestion2") + " " + AppResourceManager.getResource().getString("measureType." + currentOperation) + "?");
		    	
	    		showDialog(DELETE_CONFIRM_DIALOG);
	    	}
	        return true;
	    case R.id.retry_send_all_measure:
	    	retrySendAllMeasure();
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		android.view.MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.show_measure_context_menu, menu);
		selected_measure = listaMisure.get(info.position);
		
		menu.setHeaderTitle(AppResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()));
		menu.setHeaderIcon(AppUtil.getSmallIconId(selected_measure.getMeasureType()));
	}

	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		//Handle item selection
	    switch (item.getItemId()) {
	    case R.id.delete_measure:
	    	//L'utente ha selezionato la voce "Elimina misura"
			Log.i(TAG, "Elimino misura: " + selected_measure.getMeasureType());
	    	if (!silent){
	    		dataBundle = new Bundle();
				dataBundle.putInt(DELETE_TYPE, 1);
		    	dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
		    	dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + AppResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + "?");
	    		showDialog(DELETE_CONFIRM_DIALOG);
	    	}
	    	return true;
		default:
		    return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == MEASURE_DETAILS) {
			if(resultCode == RESULT_OK){
				String result=data.getStringExtra("result");     
				if(result.compareToIgnoreCase("delete") == 0){
					//Viene eliminata la singola misura
					measureManager.deleteMeasure(selected_measure);
					measures.clear();
					populateActivity(currentOperation);
					if(!measures.isEmpty())
						Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
					
					listAdapter.notifyDataSetChanged();
				}
			}
			if (resultCode == RESULT_CANCELED) {    
				//Write your code if there's no result
			}
		}
	}
	
	private final Handler serverCertificateManagerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case ServerCertificateManager.ADD_CERTIFICATE_FAILURE:
				Log.i(TAG, "Errore nel salvataggio del certificato");
				createAlertDialog(AppResourceManager.getResource().getString("warningTitle"), AppResourceManager.getResource().getString("saveServerCertFailure"));
				break;
			}
		}
	};
	
	/**
	 * Listener per i click sulla dialog SIMPLE_DIALOG
	 */
	private DialogInterface.OnClickListener simple_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(SIMPLE_DIALOG);
			}
		}
	};
	
	/**
	 * Listener per i click sulla dialog DELETE_CONFIRM_DIALOG
	 */
	private DialogInterface.OnClickListener delete_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				switch(dataBundle.getInt(DELETE_TYPE)) {
				case 1:
					//Viene eliminata la singola misura
					measureManager.deleteMeasure(selected_measure);
					
					if (selected_measure.getFile() != null && selected_measure.getFile().length < Byte.MAX_VALUE) {
						
						Log.d(TAG, "deleteMeasure delete-file=" + new String(selected_measure.getFile()));			
						File fileToDelete = new File(new String(selected_measure.getFile()));
						new MediaScannerNotifier().delete(ShowMeasure.this, fileToDelete.getAbsolutePath());	
						fileToDelete.delete();						
					}
					
					measures.clear();
					populateActivity(currentOperation);
					if(!measures.isEmpty())
						Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
					break;
				case 2:
					//Vengono eliminate tutte le misure di un certo tipo
					String idUser = UserManager.getUserManager().getCurrentUser().getId();
					String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
					measureManager.deleteMeasures(idUser, idPatient, currentOperation);
					
					if (isImageManager) {
						final File[] files = AppUtil.getDir().listFiles();
						for (File fileToDelete: files){
							new MediaScannerNotifier().delete(ShowMeasure.this, fileToDelete.getAbsolutePath());	
							fileToDelete.delete();						
						}
					}
					
					measures.clear();
					populateActivity(currentOperation);
					break;
				}

				listAdapter.notifyDataSetChanged();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(context, "Misura non eliminata", Toast.LENGTH_SHORT).show();
				break;
			}
			removeDialog(DELETE_CONFIRM_DIALOG);
	}
	};
	
	/**
	 * Listener per i click sulla dialog SWIPE_DELETE_CONFIRM_DIALOG
	 */
	private DialogInterface.OnClickListener swipe_delete_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				//Viene eliminata la singola misura
				measureManager.deleteMeasure(selected_measure);
				measures.clear();
				populateActivity(currentOperation);
				if(!measures.isEmpty())
					Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
				
				listAdapter.notifyDataSetChanged();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				mListView.cancelDrag();
				Toast.makeText(context, "Misura non eliminata", Toast.LENGTH_SHORT).show();
				break;
			}
			removeDialog(DELETE_CONFIRM_DIALOG);
		}		
		
	};
	
	/**
	 * Listener per i click sulla dialog SEND_MEASURES_DIALOG
	 */
	private DialogInterface.OnClickListener send_measures_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(SEND_MEASURES_DIALOG);
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				startSendingAllMeasures();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				break;
			}
		}
	};

	/**
	 * Listener per i click sulla dialog ALERT_DIALOG
	 */
	private DialogInterface.OnClickListener alert_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(ALERT_DIALOG);
			finish();
		}
	};

	/**
	 * Listener per i click sulla listView
	 */
	private OnItemClickListener listViewItemClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view,
				int position, long id) {
			selected_measure = listaMisure.get(position);
			if (!selected_measure.getMeasureType().equals(GWConst.KMsrEcg)) {
				if (selected_measure.getFile() != null && selected_measure.getMeasureType().equalsIgnoreCase(GWConst.KMsrImg)) {
					try {
						File posterFile = new File(new String(selected_measure.getFile()));
						Intent intent = new Intent();
						intent.setAction(android.content.Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.fromFile(posterFile), "image/*");
						startActivity(intent);
					}
					catch (Exception e) {
						e.printStackTrace();
						Log.e(TAG, "openFile", e);
					}
				}
				else {
					Intent intent = new Intent(ShowMeasure.this, MeasureDetails.class);
					intent.putExtra(SELECTED_MEASURE_KEY, selected_measure);
					startActivityForResult(intent, MEASURE_DETAILS);
				}
			}
		}
	};
	
	protected void startSendingAllMeasures() {
        startService(new Intent(this, SendMeasuresService.class));
        Toast.makeText(this, dataBundle.getString(AppConst.MESSAGE), Toast.LENGTH_SHORT).show();
	}

	/**
	 * Metodo che permette di riempire il contenuto dell'activity con gli opportuni valori
	 */
	private void populateActivity(String idMisura) {
		String idUser = UserManager.getUserManager().getCurrentUser().getId();
		
		String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
		String measureType = idMisura.equals("ALL")? null:idMisura;
		listaMisure = measureManager.getMeasureData(idUser, null, null, measureType, idPatient, MeasureManager.BooleanFilter.not);
		if(listaMisure != null) {
            for (Measure misura : listaMisure) {
				HashMap<String, String> map = new HashMap<>();
				map.put(KEY_ICON_SENT, String.valueOf(getIconSent(misura.getSent())));
				map.put(KEY_LABEL, AppResourceManager.getResource().getString("measureType." + misura.getMeasureType()));
				String date = getDate(misura.getTimestamp());
				String hour = getHour(misura.getTimestamp());
				map.put(KEY_DATE, date);
				map.put(KEY_HOUR, hour);
				map.put(KEY_TIMESTAMP, date + " " + hour);
				
				if (filterIds == null || containsFilterIds(misura.getTimestamp())) 
					measures.add(map);
			}

			listAdapter.notifyDataSetChanged();
		}
		else {
			finish();
		}
	}
	
	private boolean containsFilterIds(String timestamp) {
		if (filterIds != null) {
            for (String s : filterIds)
                if (s.equalsIgnoreCase(timestamp))
                    return true;
		}
		return false;
	}
	
	/**
	 * Metodo che restituisce la data in forma dd MMMMM yyyy
	 * @param timestamp  variabile di tipo {@code String} che contiene la stringa da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getDate(String timestamp) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			createAlertDialog(AppResourceManager.getResource().getString("warningTitle"), AppResourceManager.getResource().getString("errorDbRead"));
			return null;
		}
	}
	
	/**
	 * Metodo che restituisce l'orario in forma HH:mm"
	 * @param timestamp variabile di tipo {@code String} che contiene la stringa da convertire in ora
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getHour(String timestamp) {	
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			createAlertDialog(AppResourceManager.getResource().getString("warningTitle"), AppResourceManager.getResource().getString("errorDbRead"));
			return null;
		}
	}
	
	/**
	 * Metodo che si occupa della visualizzazione di una dialog
	 * @param title variabile di tipo {@code String} che contiene il titolo della dialog
	 * @param message variabile di tipo {@code String} che contiene il messaggio da visualizzare all'interno della dialog
	 */
	private void createAlertDialog(String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putString(AppConst.TITLE, title);
		dataBundle.putString(AppConst.MESSAGE, message);
		
		if (!silent)
			showDialog(ALERT_DIALOG);
	}
	
	/**
	 * Metodo che indica quale icona visualizzare a seconda che la misura sia stata inviata o meno
	 * @param sent variabile di tipo {@code String} che indica se la misura è stata inviata o meno
	 * @return variabile di tipo {@code String} che contiene il riferimento all'icona da utilizzare
	 */
	private int getIconSent(boolean sent) {
		if(sent)
			return R.drawable.ic_menu_measure_sended_yellow;
		else
			return R.drawable.ic_menu_measure_no_sended_yellow;
	}
}
