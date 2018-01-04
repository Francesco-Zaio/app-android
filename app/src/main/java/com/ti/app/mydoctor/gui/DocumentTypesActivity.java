package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.DragSortListView;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.adapter.MeasureListAdapter;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.syncmodule.SendMeasuresService;
import com.ti.app.telemed.core.usermodule.UserManager;

import java.util.ArrayList;
import java.util.HashMap;

public class DocumentTypesActivity extends ActionBarListActivity{

    public static final String DOCUMENT_KEY = "DOCUMENT_KEY";

	private static final String TAG = "DocumentTypesActivity";

	private static final String KEY_ICON_SENT = "icon_sent";
	private static final String KEY_LABEL = "label";
	private static final String KEY_TIMESTAMP = "timestamp";

	//Dialog
	private static final int ALERT_DIALOG = 0;
	private static final int SIMPLE_DIALOG = 7;
	private static final int SEND_MEASURES_DIALOG = 8;

	//Bundle
	private static final String MEASURE_NUMBER = "MEASURE_NUMBER";

	private MeasureListAdapter listAdapter;
	private Bundle dataBundle = null;
	private Bundle measuresResultBundle = null;
	private ArrayList<HashMap<String, String>> measures = null;
	private MeasureManager measureManager;
	private GWTextView titleTV;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.drag_sort_list);

		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		measureManager = MeasureManager.getMeasureManager();

		// create the grid item mapping
		String[] from;
		int[] to;

		measures = new ArrayList<>();

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
            titleTV.setText(R.string.show_documents);
            customActionBar.setCustomView(titleView);

            //L'icona dell'App diventa tasto per tornare nella Home
            customActionBar.setHomeButtonEnabled(true);
            customActionBar.setDisplayHomeAsUpEnabled(true);
        }

		from = new String[] { KEY_ICON_SENT, KEY_LABEL };
		to = new int[] { R.id.icon_sent, R.id.label };
		listAdapter = new MeasureListAdapter(this, measures, R.layout.document_list_item, from, to);
		setListAdapter(listAdapter);
		populateActivity();
		setTitle(getString(R.string.show_documents));

        DragSortListView mListView = (DragSortListView) getListView();
		mListView.setDragEnabled(false);
		mListView.setOnItemClickListener(listViewItemClickListener);

		mListView.setDivider(null); //rimuove la linea di bordo
		mListView.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		registerForContextMenu(mListView);
	}

	private void retrySendAllDocuments() {
     	String idUser = UserManager.getUserManager().getCurrentUser().getId();
        int numMeasures = measureManager.getNumNotSentMeasures(idUser);
    	if(numMeasures == 0) {
            showDialog(SIMPLE_DIALOG);
    	} else {
            measuresResultBundle = new Bundle();
            measuresResultBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
            measuresResultBundle.putInt(MEASURE_NUMBER, numMeasures);
            showDialog(SEND_MEASURES_DIALOG);
    	}
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
		inflater.inflate(R.menu.show_documents_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
         int numMeasureToSend = measureManager.getNumNotSentMeasures(UserManager.getUserManager().getCurrentUser().getId());
        //Controllo se nella lista ci sono misure da inviare
        if(numMeasureToSend == 0) {
            menu.findItem(R.id.retry_send_all).setVisible(false);
        } else {
            menu.findItem(R.id.retry_send_all).setVisible(true);
        }
		//return super.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection

	    switch (item.getItemId()) {
	    case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            finish();
            return true;
	    case R.id.retry_send_all:
	    	retrySendAllDocuments();
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        populateActivity();
        listAdapter.notifyDataSetChanged();
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


            Intent intent = new Intent(DocumentTypesActivity.this, DocumentSendActivity.class);
            intent.putExtra(DOCUMENT_KEY, MeasureManager.DocumentType.values()[position].toString());
            startActivityForResult(intent, 0);
            /*
		    // TODO visualizzazione/acquisizione documenti
            Toast.makeText(DocumentTypesActivity.this, "TODO Show/Acquire Documents", Toast.LENGTH_SHORT).show();
            */
        }
	};
	
	protected void startSendingAllMeasures() {
        startService(new Intent(this, SendMeasuresService.class));
        Toast.makeText(this, AppResourceManager.getResource().getString("KMsgSendMeasureStart"), Toast.LENGTH_SHORT).show();
	}

	/**
	 * Metodo che permette di riempire il contenuto dell'activity con gli opportuni valori
	 */
	private void populateActivity() {
	    String userId = UserManager.getUserManager().getCurrentUser().getId();
	    measures.clear();
        for (MeasureManager.DocumentType dt : MeasureManager.DocumentType.values()) {
            ArrayList<Measure> ml = measureManager.getMeasureData(userId,null,null,dt.toString(), null, null, null);
            boolean exist = false;
            boolean sent = true;
            for (Measure m:ml) {
                exist = true;
                sent = sent && m.getSent();
            }
            HashMap<String, String> map = new HashMap<>();
            map.put(KEY_ICON_SENT, String.valueOf(getIconSent(exist,sent)));
            map.put(KEY_LABEL, AppResourceManager.getResource().getString("measureType." + dt.toString()));
            measures.add(map);
        }
        listAdapter.notifyDataSetChanged();
	}
	
	/**
	 * Metodo che indica quale icona visualizzare a seconda che la misura sia stata inviata o meno
	 * @param sent variabile di tipo {@code String} che indica se la misura è stata inviata o meno
	 * @return variabile di tipo {@code String} che contiene il riferimento all'icona da utilizzare
	 */
	private int getIconSent(boolean exist, boolean sent) {
	    if (!exist)
	        return R.drawable.ic_menu_measure_no_sended;
		if(sent)
			return R.drawable.ic_menu_measure_sended_yellow;
		else
			return R.drawable.ic_menu_measure_no_sended_yellow;
	}
}
