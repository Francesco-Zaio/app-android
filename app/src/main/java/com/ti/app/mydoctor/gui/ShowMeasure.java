package com.ti.app.mydoctor.gui;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
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
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.syncmodule.SendMeasureService;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.DragSortController.Direction;
import com.ti.app.mydoctor.gui.customview.DragSortListView;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.adapter.MeasureListAdapter;
import com.ti.app.telemed.core.util.Util;


import static com.ti.app.mydoctor.gui.DocumentSendActivity.DOCUMENT_KEY;

public class ShowMeasure extends ActionBarListActivity{

    public static final String MEASURE_TYPE_KEY = "MEASURE_TYPE";
    public static final String MEASURE_FAMILY_KEY = "MEASURE_FAMILY";
    public static final String MEASURE_KEY = "MEASURE_KEY";

	private static final String TAG = "ShowMeasure";

	//La misura selezionata dall'utente: Si possono visualizzare i dettagli della misura, eliminarla oppure riprovare l'invio
	private Measure selected_measure;

	private static final String KEY_ICON_SENT = "icon_sent";
	private static final String KEY_LABEL = "label";
	private static final String KEY_TIMESTAMP = "timestamp";
	private static final String KEY_DATE = "date";
	private static final String KEY_HOUR = "hour";
	private static final String KEY_URGENT = "urgent";

	
	// Intent request codes
    private static final int MEASURE_DETAILS = 1;
	private static final int NEW_DOCUMENT = 2;

	//Dialog
	private static final int ALERT_DIALOG = 0;
	private static final int DELETE_CONFIRM_DIALOG = 1;
	private static final int WARNING_DIALOG = 2;
	private static final int SIMPLE_DIALOG = 7;
	private static final int  NEW_DOCUMENT_DIALOG = 9;
	private static final int SWIPE_DELETE_CONFIRM_DIALOG = 10;

	//Bundle
	private static final String DELETE_TYPE = "DELETE_TYPE";

	private MeasureListAdapter listAdapter;
	private Bundle dataBundle = null;
	private ArrayList<HashMap<String, String>> measures = null;
	private ArrayList<Measure> listaMisure = null;

	private CharSequence[] docNames = new CharSequence[MeasureManager.DocumentType.values().length];

	private DragSortListView mListView;
	private String currentMeasureType = null;
    private Measure.MeasureFamily currentMeasureFamily = null;
	private MeasureManager measureManager;
	private GWTextView titleTV;
	private Context context;
	private String[] filterIds = null;
	ProgressDialog progressDialog = null;

	private DragSortListView.RemoveListener onRemove = 
            new DragSortListView.RemoveListener() {
		    	@Override
		        public void remove(int which) {
		    		//Viene eliminata la singola misura
		    		selected_measure = listaMisure.get(which);
					measureManager.deleteMeasure(selected_measure);
					populateActivity();
					if(!measures.isEmpty())
						Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
		        }
		    	
                @Override
				public void removingRequest(int which, Direction direction) {
					selected_measure = listaMisure.get(which);					
                    Log.i(TAG, "Elimino misura: " + selected_measure.getMeasureType());
                    dataBundle = new Bundle();
                    dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
                    if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA)
                        dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteMeasureConfirm") + "?");
                    else
                        dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteDocumentConfirm") + "?");
                    showDialog(SWIPE_DELETE_CONFIRM_DIALOG);
				}

				@Override
				public void itemChangeLayout(int which, int mode) {
				}				
        };
        
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MeasureManager.getMeasureManager().setHandler(handler);

		context = getApplicationContext();
		setContentView(R.layout.drag_sort_list);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		measureManager = MeasureManager.getMeasureManager();
		
		// create the grid item mapping
		String[] from;
		int[] to;
		
		measures = new ArrayList<>();
		
		Bundle data = getIntent().getExtras();
		if (data != null) {
            currentMeasureType = data.getString(MEASURE_TYPE_KEY);
            currentMeasureFamily = Measure.MeasureFamily.get(data.getInt(MEASURE_FAMILY_KEY));
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
            titleTV = titleView.findViewById(R.id.actionbar_title_label);
            titleTV.setText(R.string.show_measures);
            customActionBar.setCustomView(titleView);

            //L'icona dell'App diventa tasto per tornare nella Home
            customActionBar.setHomeButtonEnabled(true);
            customActionBar.setDisplayHomeAsUpEnabled(true);
        }

        if(currentMeasureType == null || currentMeasureType.isEmpty()) {
			from = new String[] { KEY_ICON_SENT, KEY_LABEL, KEY_TIMESTAMP, KEY_URGENT };
			to = new int[] { R.id.icon_sent, R.id.label, R.id.timestamp };
			listAdapter = new MeasureListAdapter(this, measures, R.layout.all_measure_item_layout, from, to);
			setListAdapter(listAdapter);
			if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA)
				setTitle(getString(R.string.manageMeasure));
			else
				setTitle(getString(R.string.manageDocuments));
		}
		else {
			from = new String[] { KEY_ICON_SENT, KEY_DATE, KEY_HOUR, KEY_URGENT };
			to = new int[] { R.id.icon_sent, R.id.date_timestamp, R.id.hour_timestamp };
			listAdapter = new MeasureListAdapter(this, measures, R.layout.measure_list_item, from, to);
			setListAdapter(listAdapter);
			String s = "";
			if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA) {
				s = getString(R.string.measure_title) + " ";
			}
			s = s + AppResourceManager.getResource().getString("measureType."+currentMeasureType);
			setTitle(s);
		}

		if (currentMeasureFamily ==  Measure.MeasureFamily.DOCUMENTO) {
			int i = 0;
			for (MeasureManager.DocumentType dt : MeasureManager.DocumentType.values()) {
				docNames[i] =  AppResourceManager.getResource().getString("measureType." + dt.toString());
				i++;
			}
		}

        populateActivity();

		mListView = (DragSortListView) getListView();
		mListView.setRemoveListener(onRemove);
		mListView.setOnItemClickListener(listViewItemClickListener);
		mListView.setDivider(null); //rimuove la linea di bordo
		mListView.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		registerForContextMenu(mListView);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		MeasureManager.getMeasureManager().setHandler(null);
	}

	private void setTitle(String title) {
		if (titleTV == null)
			titleTV = findViewById(R.id.actionbar_title_label);
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
            case SIMPLE_DIALOG:
                builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
                builder.setMessage(AppResourceManager.getResource().getString("KMsgNoMeasureToSend"));
                builder.setNeutralButton(AppResourceManager.getResource().getString("okButton"), simple_dialog_click_listener);
                break;
			case WARNING_DIALOG:
				builder.setTitle(dataBundle.getString(AppConst.TITLE));
				builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
				builder.setNeutralButton(AppResourceManager.getResource().getString("okButton"), null);
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
			case NEW_DOCUMENT_DIALOG:
				builder.setTitle(R.string.new_document);
				builder.setItems(docNames, new_document_click_listener);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (measures.isEmpty())
                            finish();
                    }
                });
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
		if (Measure.MeasureFamily.BIOMETRICA.equals(currentMeasureFamily)) {
			menu.findItem(R.id.new_document).setVisible(false);
		}
		if(listaMisure == null || listaMisure.isEmpty()) {
			menu.findItem(R.id.delete_all).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.delete_all:
				dataBundle = new Bundle();
				dataBundle.putInt(DELETE_TYPE, 2);
				dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
				if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA)
					dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteAllMeasuresConfirm") + "?");
				else
					dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteAllDocumentsConfirm") + "?");
				showDialog(DELETE_CONFIRM_DIALOG);
				return true;
			case R.id.new_document:
				showDialog(NEW_DOCUMENT_DIALOG);
			default:
				return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		selected_measure = listaMisure.get(info.position);

		android.view.MenuInflater inflater = getMenuInflater();
		if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA)
			inflater.inflate(R.menu.show_measure_context_menu, menu);
		else
			inflater.inflate(R.menu.show_document_context_menu, menu);

		if (selected_measure.getSent())
			menu.findItem(R.id.send).setVisible(false);
		
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
				dataBundle = new Bundle();
				dataBundle.putInt(DELETE_TYPE, 1);
				dataBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
				if (currentMeasureFamily == Measure.MeasureFamily.BIOMETRICA)
					dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteMeasureConfirm") + "?");
				else
					dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteDocumentConfirm") + "?");
				showDialog(DELETE_CONFIRM_DIALOG);
				return true;
			case R.id.send:
				if (!Util.isNetworkConnected()) {
					dataBundle = new Bundle();
					dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("noConnection"));
					showDialog(WARNING_DIALOG);
				} else {
					SendMeasureService.enqueueWork(this, selected_measure, null);
					Toast.makeText(this, AppResourceManager.getResource().getString("KMsgSendMeasureStart"), Toast.LENGTH_SHORT).show();
				}
                return true;
			default:
				return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode,resultCode,data);
		switch (requestCode) {
			case MEASURE_DETAILS:
				if(resultCode == RESULT_OK){
					String result=data.getStringExtra("result");
					if(result.compareToIgnoreCase("delete") == 0){
						//Viene eliminata la singola misura
						measureManager.deleteMeasure(selected_measure);
						populateActivity();
						if(!measures.isEmpty())
							Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
						listAdapter.notifyDataSetChanged();
					}
				}
				break;
			case NEW_DOCUMENT:
				if (resultCode == RESULT_OK)
					Toast.makeText(context, AppResourceManager.getResource().getString("KMsgSendDocumentStart"), Toast.LENGTH_LONG).show();
				populateActivity();
				invalidateOptionsMenu();
				break;
		}
	}
	
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
					if (measureManager.deleteMeasure(selected_measure))
						Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
					populateActivity();
					invalidateOptionsMenu();
					break;
				case 2:
					//Vengono eliminate tutte le misure di un certo tipo
					String idUser = UserManager.getUserManager().getCurrentUser().getId();
					String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
					boolean result;
					if (currentMeasureType != null && !currentMeasureType.isEmpty())
						result = measureManager.deleteMeasures(idUser, idPatient, currentMeasureType);
					else
						result = measureManager.deleteMeasures(idUser, idPatient, currentMeasureFamily);
					if (result) {
                        progressDialog = new ProgressDialog(ShowMeasure.this);
                        progressDialog.setIndeterminate(true);
                        progressDialog.setCancelable(true);
                        progressDialog.setMessage(AppResourceManager.getResource().getString("KMsgDeleting"));
                        progressDialog.show();
                    } else {
                        dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("ErrDeleting"));
                        showDialog(ALERT_DIALOG);
                    }
					break;
				}

				listAdapter.notifyDataSetChanged();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
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
				populateActivity();
				if(!measures.isEmpty())
					Toast.makeText(context, AppResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_SHORT).show();
				listAdapter.notifyDataSetChanged();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				mListView.cancelDrag();
				break;
			}
			removeDialog(DELETE_CONFIRM_DIALOG);
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
	 * Listener per il dialogo scelta tipo di nuovo documento
	 */
	private DialogInterface.OnClickListener new_document_click_listener = new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int which) {
		    Intent intent = new Intent(ShowMeasure.this, DocumentSendActivity.class);
			intent.putExtra(DOCUMENT_KEY, MeasureManager.DocumentType.values()[which].toString());
			startActivityForResult(intent, NEW_DOCUMENT);
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
				if (Measure.MeasureFamily.DOCUMENTO.equals(selected_measure.getFamily())) {
                    Intent intent = new Intent(ShowMeasure.this, DocumentDetails.class);
                    intent.putExtra(MEASURE_KEY, selected_measure);
                    startActivityForResult(intent, MEASURE_DETAILS);
				}
				else {
					Intent intent = new Intent(ShowMeasure.this, MeasureDetails.class);
					intent.putExtra(MEASURE_KEY, selected_measure);
					startActivityForResult(intent, MEASURE_DETAILS);
				}
			}
		}
	};

	/**
	 * Metodo che permette di riempire il contenuto dell'activity con gli opportuni valori
	 */
	private void populateActivity() {
		String idUser = UserManager.getUserManager().getCurrentUser().getId();
		measures.clear();
		String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
		listaMisure = measureManager.getMeasureData(idUser, null, null,
				currentMeasureType, idPatient, false, currentMeasureFamily, 150);
		for (Measure misura : listaMisure) {
			HashMap<String, String> map = new HashMap<>();
			map.put(KEY_ICON_SENT, String.valueOf(getIconSent(misura.getSent())));
			map.put(KEY_LABEL, AppResourceManager.getResource().getString("measureType." + misura.getMeasureType()));
			String date = getDate(misura.getTimestamp());
			String hour = getHour(misura.getTimestamp());
			map.put(KEY_DATE, date);
			map.put(KEY_HOUR, hour);
			map.put(KEY_TIMESTAMP, date + " " + hour);
			map.put(KEY_URGENT, misura.getUrgent()?"true":"false");

			if (filterIds == null || containsFilterIds(misura.getTimestamp()))
				measures.add(map);
		}
		listAdapter.notifyDataSetChanged();
        if (Measure.MeasureFamily.DOCUMENTO.equals(currentMeasureFamily) && measures.isEmpty())
            showDialog(NEW_DOCUMENT_DIALOG);
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
			Log.e(TAG, "getDate: timestamp parse Error");
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
			Log.e(TAG, "getHour: timestamp parse Error");
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
		if (title!= null && !title.isEmpty())
			dataBundle.putString(AppConst.TITLE, title);
		dataBundle.putString(AppConst.MESSAGE, message);
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

	private final MyHandler handler = new MyHandler(this);

	private static class MyHandler extends Handler {
		private final WeakReference<ShowMeasure> mOuter;

		private MyHandler(ShowMeasure outer) {
			mOuter = new WeakReference<>(outer);
		}

		@Override
		public void handleMessage(Message msg) {
			ShowMeasure outer = mOuter.get();
			super.handleMessage(msg);
			switch (msg.what) {
				case MeasureManager.OPERATION_COMPLETED:
					if (outer.progressDialog!=null)
						outer.progressDialog.dismiss();
					outer.populateActivity();
                    outer.invalidateOptionsMenu();
					break;
				case MeasureManager.ERROR_OCCURED:
					if (outer.progressDialog!=null)
						outer.progressDialog.dismiss();
					outer.populateActivity();
					outer.dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("ErrDeleting"));
					outer.showDialog(ALERT_DIALOG);
					break;
			}
		}
	}

}
