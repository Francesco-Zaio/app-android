package com.ti.app.mydoctor.gui;

import java.io.File;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.security.cert.X509Certificate;

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
import android.widget.TextView;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.connectionmodule.ConnectionManager;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.scmodule.ServerCertificateManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.webmodule.WebSocket;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.exceptions.XmlException;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.DragSortController.Direction;
import com.ti.app.mydoctor.gui.customview.DragSortListView;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.MeasureListAdapter;
import com.ti.app.mydoctor.util.DialogManager;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.MediaScannerNotifier;
import com.ti.app.mydoctor.util.Util;

public class ShowMeasure extends ActionBarListActivity{
	
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
	private static final int MEASURE_SEND_DIALOG = 2;
	private static final int PROGRESS_DIALOG = 3;
	private static final int STATUS_DIALOG = 4;
	private static final int CERT_PROBLEM_DIALOG = 5;
	private static final int CERT_INFO_DIALOG = 6;
	private static final int SIMPLE_DIALOG = 7;
	private static final int SEND_MEASURES_DIALOG = 8;
	private static final int ALERT_DIALOG_WITH_SAVE = 9;
	private static final int SWIPE_MEASURE_SEND_DIALOG = 10;
	private static final int SWIPE_DELETE_CONFIRM_DIALOG = 11;
	
	//Bundle
	private static final String DELETE_TYPE = "DELETE_TYPE";
	private static final String SEND_STATUS = "SEND_STATUS";
    private static final String SEND_TOTAL = "SEND_TOTAL";
	private static final String SEND_SUCCESS = "SEND_SUCCESS";
	private static final String SEND_FAILURE = "SEND_FAILURE";
    private static final String SEND_FAILURE_REASON = "SEND_FAILURE_REASON";
	private static final String MEASURE_NUMBER = "MEASURE_NUMBER";
	
	//private DeviceListAdapter listAdapter;
	private MeasureListAdapter listAdapter;
	
	private Bundle dataBundle = null;
	private Bundle measureResultBundle = null;
	private Bundle measuresResultBundle = null;
	
	//Bundle che contiene informazioni sui certificati esportati dal server
	private Bundle serverCertBundle;
	//Bundle che contiene i certificati esportati dal server
	private X509Certificate[] serverCertificate;

	private ArrayList<HashMap<String, String>> measures = null;
	private ArrayList<Measure> listaMisure = null;
	private ArrayList<Measure> measuresToSend = null;
	
	private DragSortListView mListView;
	
	//Contatore delle misure inviate
	private int counter = 0;
	//Contatore dei fallimenti nell'invio della misura
	private int failCounter = 0;
	//Il numero delle misure che devono essere inviate
	private int totMeasureToSend;
	
	private String currentOperation = "";
	
	private MeasureManager measureManager;
	private ConnectionManager connectionManager;
	private ServerCertificateManager scMananger;
	
	private boolean multipleSend;
	
	private static ProgressDialog progressDialog;
	
	private ActionBar customActionBar;
	private Menu mActionBarMenu;
	//private TextView titleTV;
	private GWTextView titleTV;
	
	private boolean silent = false;
	
	private Context context;
	private SilentSendEventListener silentSendEventListener;

	private String[] filterIds = null;
		
	private boolean isImageManager = false;
	private String msgTrasf;

	private DragSortListView.RemoveListener onRemove = 
            new DragSortListView.RemoveListener() {
		    	@Override
		        public void remove(int which) {
		    		//listAdapter.remove(listAdapter.getItem(which));
		    		
		    		//Viene eliminata la singola misura
		    		selected_measure = listaMisure.get(which);
					measureManager.deleteMeasure(selected_measure);
					measures.clear();
					populateActivity(currentOperation);
					if(!measures.isEmpty())
						Toast.makeText(context, ResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
		        }
		    	
                @Override
				public void removingRequest(int which, Direction direction) {
					               	
					selected_measure = listaMisure.get(which);					
					
					if(selected_measure.getSent() && direction == Direction.RIGHT) {
						//Se la misura è già stata inviata
						//viene cancellato lo swipe
						mListView.cancelDrag();
						return;
            		}					
					
                	if(direction == Direction.RIGHT) {          		
                		
                		Log.i(TAG, "Riprovo invio misura " + selected_measure.getMeasureType());
            			multipleSend = false;
            			if (!silent){
            				measureResultBundle = new Bundle();
            				measureResultBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()));
            				if(selected_measure.getMeasureType().equals("OS"))
            					measureResultBundle.putString(GWConst.MESSAGE, getBodyOxy(selected_measure.getXml()));
            				else if(selected_measure.getMeasureType().equals("SP"))
            					measureResultBundle.putString(GWConst.MESSAGE, getBodySP(selected_measure.getXml()));
            				else
            					measureResultBundle.putString(GWConst.MESSAGE, getBody(selected_measure.getXml()));
            						
            				showDialog(SWIPE_MEASURE_SEND_DIALOG);
            			}     		
                		
                		
                	} else if(direction == Direction.LEFT) {
                		
                		Log.i(TAG, "Elimino misura: " + selected_measure.getMeasureType());
            	    	if (!silent){
            	    		dataBundle = new Bundle();
            				dataBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
            		    	dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + "?");		    	
            	    		showDialog(SWIPE_DELETE_CONFIRM_DIALOG);
            	    	}             		
                		
                	}
                	
            		//Attivare nel caso in cui non si voglia veder sparire
            		//l'elemento prima della sua cancellazione
            		//mListView.cancelDrag();
            		//////////////////////////////////////////////////////           		
				}

				@Override
				public void itemChangeLayout(int which, int mode) {
					// TODO Auto-generated method stub
					
				}				
        };
        
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		setContentView(R.layout.measure_list);
		//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		measureManager = MeasureManager.getMeasureManager();
		measureManager.setHandler(measureManagerHandler);
		
		connectionManager = ConnectionManager.getConnectionManager(context);
		connectionManager.setHandler(connectionManagerHandler);
		
		scMananger = ServerCertificateManager.getScMananger();
		scMananger.setHandler(serverCertificateManagerHandler);
		
		// create the grid item mapping
		String[] from = null;
		int[] to = null;				
		
		measures = new ArrayList<HashMap<String, String>>();
		
		Bundle data = getIntent().getExtras();
		String activityTitle = data.getString(GWConst.SHOW_MEASURE_TITLE);
		
		String measureMode = data.getString(GWConst.MEASURE_MODE);
		Log.d(TAG, "measureMode=" + measureMode);
		if (measureMode != null && (measureMode.equalsIgnoreCase(GWConst.MEASURE_MODE_ON))) {
			String filterMeasure = Util.getRegistryValue(GWConst.MEASURE_MODE);
			Log.d(TAG, "filterMeasure=" + filterMeasure);
			
			if (filterMeasure != null) {				
				filterIds  = filterMeasure.split(",");
				Log.d(TAG, "filterIds=" + filterIds.length);
			}
		}
		
		if(activityTitle != null) {			
			if(activityTitle.equals("ALL")) {
				from = new String[] { KEY_ICON_SENT, KEY_LABEL, KEY_TIMESTAMP };
				to = new int[] { R.id.icon_sent, R.id.label, R.id.timestamp };
				//listAdapter = new DeviceListAdapter(this, measures, R.layout.all_measure_item_layout, from, to);
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
				populateActivity(data.getString(GWConst.SELECTED_MEASURE));
				currentOperation = data.getString(GWConst.SELECTED_MEASURE);
			}
			
			//getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
			//titleTV = (TextView) findViewById(R.id.title);
			//titleTV.setText(R.string.show_measures);
			//Inizializza l'ActionBAr
			customActionBar = this.getSupportActionBar();
			//Setta il gradiente di sfondo della action bar
			Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
			customActionBar.setBackgroundDrawable(cd);
			
			customActionBar.setDisplayShowCustomEnabled(true);
			customActionBar.setDisplayShowTitleEnabled(false);
			
			//Setta l'icon
			customActionBar.setIcon(R.drawable.icon_action_bar);

			//Ricava la TextView dell'ActionBar
			LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View titleView = inflator.inflate(R.layout.actionbar_title, null);
			titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
			titleTV.setText(R.string.show_measures);
			customActionBar.setCustomView(titleView);
			
			//L'icona dell'App diventa tasto per tornare nella Home
			customActionBar.setHomeButtonEnabled(true);
			customActionBar.setDisplayHomeAsUpEnabled(true);
			
			if (activityTitle.equals("ALL")) {
				setTitle(getString(R.string.manageMeasure));
			}
			else {
				setTitle(getString(R.string.measure_title) + " " + activityTitle);
				
				//TODO possibile modifica per abbreviare la stringa del titolo
				/*String measureTitle = Util.truncate(activityTitle, 10);
				setTitle(getString(R.string.measure_title) + " " + measureTitle);*/
			}
		}
		
		/*ListView lv = getListView();
		lv.setDivider(null); //rimuove la linea di bordo
		lv.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		lv.setTextFilterEnabled(false);
		lv.setOnItemClickListener(listViewItemClickListener);
		registerForContextMenu(lv);	*/
		
		mListView = (DragSortListView) getListView();
		mListView.setRemoveListener(onRemove);
		mListView.setOnItemClickListener(listViewItemClickListener);
		//mListView.setOnItemLongClickListener(new CliccaItemLongDemoListener());
		
		mListView.setDivider(null); //rimuove la linea di bordo
		mListView.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		registerForContextMenu(mListView);	
		
		silent = false;

		msgTrasf = ResourceManager.getResource().getString("KMsgTrasf");
		
		if (data.getString(GWConst.SELECTED_MEASURE) != null) {
			isImageManager = data.getString(GWConst.SELECTED_MEASURE).equalsIgnoreCase(GWConst.KMsrImg);
			
			if (isImageManager)
				msgTrasf = ResourceManager.getResource().getString("KMsgTrasfImg");
		}
		
		if (data.containsKey(GWConst.SEND_ALL)) {
			
			multipleSend = true;
	    	String idUser = UserManager.getUserManager().getCurrentUser().getId();
	    	String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
	    	
	    	//Ottengo le misure che devono essere inviate
	    	measuresToSend = measureManager.getNotSendedMeasures(idUser, idPatient, data.getString(GWConst.SEND_MEASURE_TYPE));
			
	    	measuresToSend = purgeList(measuresToSend);
	    	
	    	if(measuresToSend == null) 
	    		showDialog(SIMPLE_DIALOG);
	    	else
	    		startSendingAllMeasures();
		}
	}
	
	private ArrayList<Measure> purgeList(ArrayList<Measure> list) {
		
		if (list == null)
			return null;
				
		ArrayList<Measure> result = new ArrayList<Measure>();
		
		for (int i = 0; i < list.size(); i++) {
			
			if (filterIds == null || containsFilterIds(list.get(i).getTimestamp())) {
				result.add(list.get(i));
			}
		}
		return result;
	}

	private void retrySendAllMeasure() {
		multipleSend = true;
    	String idUser = UserManager.getUserManager().getCurrentUser().getId();
    	String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
    	
    	//Ottengo le misure che devono essere inviate
    	measuresToSend = measureManager.getNotSendedMeasures(idUser, idPatient, currentOperation);
    	
    	measuresToSend = purgeList(measuresToSend);
    	
    	if(measuresToSend == null) {
    		
    		if (!silent)
    			showDialog(SIMPLE_DIALOG);
    	}
    	else {
    		if (!silent){
	    		measuresResultBundle = new Bundle();
	    		measuresResultBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
	    		measuresResultBundle.putInt(MEASURE_NUMBER, measuresToSend.size());
	    			    		
    			showDialog(SEND_MEASURES_DIALOG);
    		}
    	}
	}

	private void setTitle(String title) {
		if (titleTV == null)
			titleTV = (GWTextView)findViewById(R.id.actionbar_title_label);
			//titleTV = (TextView) findViewById(R.id.title);
				
		titleTV.setText(title);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		measureManager.setHandler(measureManagerHandler);
		connectionManager.setHandler(connectionManagerHandler);
		scMananger.setHandler(serverCertificateManagerHandler);
	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
		/*AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);*/
		//Identifico quale dialog deve essere visualizzata
		switch(id) {
		case SEND_MEASURES_DIALOG:
			builder.setTitle(measuresResultBundle.getString(GWConst.TITLE));
			builder.setTitle(getString(R.string.send) + " " + measuresResultBundle.getInt(MEASURE_NUMBER) + " " + getString(R.string.sendToPlatform) + "?");
			builder.setPositiveButton(ResourceManager.getResource().getString("yes"), send_measures_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("no"), send_measures_dialog_click_listener);
			break;
		case SIMPLE_DIALOG:
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("KMsgNoMeasureToSend"));
			builder.setNeutralButton(ResourceManager.getResource().getString("okButton"), simple_dialog_click_listener);
			break;
		case MEASURE_SEND_DIALOG:   
			builder.setTitle(measureResultBundle.getString(GWConst.TITLE));
			
			String measureStr = measureResultBundle.getString(GWConst.MESSAGE);
			
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Patient patient = DbManager.getDbManager().getPatientData(selected_measure.getIdPatient());
			String questionStr = String.format(txt, patient.getName(), patient.getSurname());
			
			builder.setMessage(measureStr + "\n" + questionStr);

			/*View show_measure_dialog_view = inflater.inflate(R.layout.show_measure_custom_dialog, null);
			TextView measureTV = (TextView) show_measure_dialog_view.findViewById(R.id.measure_dialog_tv);
			TextView questionTV = (TextView) show_measure_dialog_view.findViewById(R.id.question_dialog_tv);
			
			((Button) show_measure_dialog_view.findViewById(R.id.measure_dialog_button)).setVisibility(View.GONE);
			
			measureTV.setText(measureResultBundle.getString(GWConst.MESSAGE));
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Log.i(TAG, "ID PAZIENTE: " + selected_measure.getIdPatient());
			Patient patient = DbManager.getDbManager().getPatientData(selected_measure.getIdPatient());
			String msg = String.format(txt, patient.getName(), patient.getSurname());
			questionTV.setText(msg);

			builder.setView(show_measure_dialog_view);*/
			builder.setPositiveButton(ResourceManager.getResource().getString("MeasureResultDialog.sendBtn"), measure_send_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), measure_send_dialog_click_listener);
			break;	
		case SWIPE_MEASURE_SEND_DIALOG:
			builder.setTitle(measureResultBundle.getString(GWConst.TITLE));
			
			String measureSwipeStr = measureResultBundle.getString(GWConst.MESSAGE);
			
			String txtSwipe = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Patient patientSwipe = DbManager.getDbManager().getPatientData(selected_measure.getIdPatient());
			String questionSwipeStr = String.format(txtSwipe, patientSwipe.getName(), patientSwipe.getSurname());
			
			builder.setMessage(measureSwipeStr + "\n" + questionSwipeStr);

			/*View show_swipe_measure_dialog_view = inflater.inflate(R.layout.show_measure_custom_dialog, null);
			TextView swipe_measureTV = (TextView) show_swipe_measure_dialog_view.findViewById(R.id.measure_dialog_tv);
			TextView swipe_questionTV = (TextView) show_swipe_measure_dialog_view.findViewById(R.id.question_dialog_tv);
			
			((Button) show_swipe_measure_dialog_view.findViewById(R.id.measure_dialog_button)).setVisibility(View.GONE);
			
			swipe_measureTV.setText(measureResultBundle.getString(GWConst.MESSAGE));
			String swipe_txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Log.i(TAG, "ID PAZIENTE: " + selected_measure.getIdPatient());
			Patient swipe_patient = DbManager.getDbManager().getPatientData(selected_measure.getIdPatient());
			String swipe_msg = String.format(swipe_txt, swipe_patient.getName(), swipe_patient.getSurname());
			swipe_questionTV.setText(swipe_msg);

			builder.setView(show_swipe_measure_dialog_view);*/
			builder.setPositiveButton(ResourceManager.getResource().getString("MeasureResultDialog.sendBtn"), swipe_measure_send_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), swipe_measure_send_dialog_click_listener);
			builder.setOnKeyListener( new Dialog.OnKeyListener() {
				
				@Override
				public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
					// TODO Auto-generated method stub
					if (keyCode == KeyEvent.KEYCODE_BACK) {
	                    mListView.cancelDrag();
	                    dialog.dismiss();
	                }
	                return true;				
				}
			});
			break;
		case ALERT_DIALOG:
			builder.setTitle(dataBundle.getString(GWConst.TITLE));
			builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
			builder.setNeutralButton(ResourceManager.getResource().getString("okButton"), alert_dialog_click_listener);
			break;
		case ALERT_DIALOG_WITH_SAVE:
			builder.setTitle(dataBundle.getString(GWConst.TITLE));
			builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
			builder.setNeutralButton(ResourceManager.getResource().getString("okButton"), alert_dialog_with_save_click_listener);
			break;
		case DELETE_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(GWConst.TITLE));
			builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
			builder.setPositiveButton(ResourceManager.getResource().getString("confirmButton"), delete_confirm_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("cancelButton"), delete_confirm_dialog_click_listener);
			break;		
		case SWIPE_DELETE_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(GWConst.TITLE));
			builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
			builder.setPositiveButton(ResourceManager.getResource().getString("confirmButton"), swipe_delete_confirm_dialog_click_listener);
			builder.setNegativeButton(ResourceManager.getResource().getString("cancelButton"), swipe_delete_confirm_dialog_click_listener);
			builder.setOnKeyListener( new Dialog.OnKeyListener() {
				
				@Override
				public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
					// TODO Auto-generated method stub
					if (keyCode == KeyEvent.KEYCODE_BACK) {
	                    mListView.cancelDrag();
	                    dialog.dismiss();
	                }
	                return true;				
				}
			});
			break;
		case CERT_PROBLEM_DIALOG:
        	builder.setTitle(ResourceManager.getResource().getString("serverCertDialogTitle1"));
        	View custom_dialog_view = inflater.inflate(R.layout.custom_dialog, null);
        	TextView upperTV = (TextView) custom_dialog_view.findViewById(R.id.upper_dialog_tv);
        	TextView lowerTV = (TextView) custom_dialog_view.findViewById(R.id.lower_dialog_tv);
        	upperTV.setText(ResourceManager.getResource().getString("serverCertDialogProblemMsg"));
        	lowerTV.setText(serverCertBundle.getString(GWConst.MESSAGE_LOWER));
        	builder.setView(custom_dialog_view);
        	builder.setPositiveButton(ResourceManager.getResource().getString("serverCertButtonAccept"), cert_problem_dialog_click_listener);
        	builder.setNeutralButton(ResourceManager.getResource().getString("serverCertButtonView"), cert_problem_dialog_click_listener);
        	builder.setNegativeButton(ResourceManager.getResource().getString("cancelButton"), cert_problem_dialog_click_listener);
        	return builder.create();
		case CERT_INFO_DIALOG:
        	builder.setTitle(ResourceManager.getResource().getString("serverCertDialogTitle2"));
        	//Il certificato del server � sempre il primo della lista
        	X509Certificate certificato = serverCertificate[0];
        	
        	Principal subject = certificato.getSubjectDN();
        	Principal issuer = certificato.getIssuerDN();
        	Log.i(TAG, "Subject: " + subject.toString());
        	Log.i(TAG, "Issuer: " + issuer.toString());
        	Date startValidity = certificato.getNotBefore();
        	Date endValidity = certificato.getNotAfter();
        	
        	String subjectCNText = "";
        	String subjectOUText = "";
        	String subjectOText = "";
        	StringTokenizer st = new StringTokenizer(subject.toString(), ",");
        	while (st.hasMoreTokens()) {
        		String token = st.nextToken();
        		if(token.contains("CN=")) {
        			subjectCNText = token.substring(3);
        		}
        		else if(token.contains("OU=")) {
        			subjectOUText = token.substring(3);
        		}
        		else if(token.contains("O=")) {
        			subjectOText = token.substring(2);
        		}
        	}
        	String issuerCNText = "";
        	String issuerOUText = "";
        	String issuerOText = "";
        	st = new StringTokenizer(issuer.toString(), ",");
        	while(st.hasMoreTokens()) {
        		String token = st.nextToken();
        		if(token.contains("CN=")) {
        			issuerCNText = token.substring(3);
        		}
        		else if(token.contains("OU=")) {
        			issuerOUText = token.substring(3);
        		}
        		else if(token.contains("O=")) {
        			issuerOText = token.substring(2);
        		}
        	}
        	
        	View cert_info_custom_dialog_view = inflater.inflate(R.layout.cert_info_custom_dialog, null);
        	builder.setView(cert_info_custom_dialog_view);
        	
        	TextView subjectTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.subjectTV);
        	TextView subjectCNTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.subjectCNTV);
        	TextView subjectOTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.subjectOTV);
        	TextView subjectUOTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.subjectUOTV);
        	
        	TextView issuerTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.issuerTV);
        	TextView issuerCNTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.issuerCNTV);
        	TextView issuerOTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.issuerOTV);
        	TextView issuerUOTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.issuerUOTV);
        	
        	TextView validityTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.validityTV);
        	TextView startValidityTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.startValidityTV);
        	TextView endValidityTV = (TextView) cert_info_custom_dialog_view.findViewById(R.id.endValidityTV);
        	
        	subjectTV.setText(ResourceManager.getResource().getString("serverCertDialogCertSubject"));
        	issuerTV.setText(ResourceManager.getResource().getString("serverCertDialogCertIssuer"));
        	validityTV.setText(ResourceManager.getResource().getString("serverCertDialogCertValidity"));
        	
        	subjectCNTV.setText(ResourceManager.getResource().getString("serverCertDialogCertCN") + subjectCNText);
        	subjectOTV.setText(ResourceManager.getResource().getString("serverCertDialogCertO") + subjectOText);
        	subjectUOTV.setText(ResourceManager.getResource().getString("serverCertDialogCertOU") + subjectOUText);
        	
        	issuerCNTV.setText(ResourceManager.getResource().getString("serverCertDialogCertCN") + issuerCNText);
        	issuerOTV.setText(ResourceManager.getResource().getString("serverCertDialogCertO") + issuerOText);
        	issuerUOTV.setText(ResourceManager.getResource().getString("serverCertDialogCertOU") + issuerOUText);
        	
        	SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        	startValidityTV.setText(ResourceManager.getResource().getString("serverCertDialogCertStartValidity") + sdf.format(startValidity));
        	endValidityTV.setText(ResourceManager.getResource().getString("serverCertDialogCertEndValidity") + sdf.format(endValidity));
        	
        	builder.setPositiveButton(ResourceManager.getResource().getString("okButton"), cert_info_dialog_click_listener);
        	return builder.create();
		case STATUS_DIALOG:
			int counter = dataBundle.getInt(SEND_STATUS);
            int totalToSend = dataBundle.getInt(SEND_TOTAL);
			int failSendMsg = dataBundle.getInt(SEND_FAILURE);
            int failureReason = dataBundle.getInt(SEND_FAILURE_REASON);

            if (failureReason == MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED)  {
                if (counter == 1 && totalToSend != 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasuresFailureQuota"));
                else if (counter == 1 && totalToSend == 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasureFailureQuota"));
                else
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasuresSomeFailureQuota"));
            } else {
                if (failSendMsg == 0 && counter != 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasuresSuccess"));
                else if (failSendMsg == 0 && counter == 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasureSuccess"));
                else if (failSendMsg == counter && counter != 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasuresFailure"));
                else if (failSendMsg == counter && counter == 1)
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasureFailure"));
                else
                    builder.setMessage(ResourceManager.getResource().getString("KMsgSendMeasuresSomeFailure"));
            }
			
        	builder.setNeutralButton(ResourceManager.getResource().getString("okButton"), status_dialog_click_listener);
        	return builder.create();
		case PROGRESS_DIALOG:        	
	        return createProgressDialog(dataBundle);
		}
		
		return builder.create();
	}
	
	private String getMessage() {
		if (MyDoctorApp.getDeviceManager().getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
			return ResourceManager.getResource().getString("KMsgSendMeasureSuccessImg");
		
		return ResourceManager.getResource().getString("KMsgSendMeasureSuccess");
	}

	@Override
	public void onPrepareDialog(int id, Dialog dialog) {		
		switch(id) {        
        case PROGRESS_DIALOG:
        	    		
    		String msg = dataBundle.getString(GWConst.MESSAGE);
    		
    		Log.d(TAG, "onPrepareDialog msg=" + msg);
    		
    		if (msg.startsWith(WebSocket.FILE_TRANSFER) || msg.equalsIgnoreCase(ResourceManager.getResource().getString("KMsgTrasfImg"))) {
    			    			
    			int value = 0;
    			
    			try {
    				value = Integer.parseInt(msg.substring(WebSocket.FILE_TRANSFER.length()));
    			}
    			catch (Exception e) {
					value = 0;
				}
    			Log.d(TAG, "onPrepareDialog setProgress=" + value);
    			
    			if (dataBundle.getString(GWConst.MESSAGE) != null && !dataBundle.getString(GWConst.MESSAGE).startsWith(WebSocket.FILE_TRANSFER)) {
    				((ProgressDialog)dialog).setMessage(dataBundle.getString(GWConst.MESSAGE));    
    			}
    			
    			((ProgressDialog)dialog).setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    			((ProgressDialog)dialog).setIndeterminate(false);
    			((ProgressDialog)dialog).setMax(100);
    			((ProgressDialog)dialog).setProgress(value);
    			dialog.show();
    		}
    		else {
    			((ProgressDialog)dialog).setIndeterminate(true);
    			
    			((ProgressDialog)dialog).setMessage(dataBundle.getString(GWConst.MESSAGE));        	
        		if(dataBundle.getBoolean(GWConst.MESSAGE_CANCELLABLE)){
        			((ProgressDialog)dialog).getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(true);//Visibility(View.VISIBLE);
        		} else {
        			((ProgressDialog)dialog).getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);//setVisibility(View.INVISIBLE);
        		}  	
    		}
    		
    		break;
        default:
        	super.onPrepareDialog(id, dialog);
        }		
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {	
		//MenuInflater inflater = getSupportMenuInflater();
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.show_measure_menu, menu);
		
		//mActionBarMenu = menu;
		
		return true;		
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		//Controllo se la lista misura è stata inizializzata
		if(listaMisure != null) {
			String idUser = UserManager.getUserManager().getCurrentUser().getId();
	    	String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
			measuresToSend = measureManager.getNotSendedMeasures(idUser, idPatient, currentOperation);
			
			//COntrollo se nella lista ci sono misure da inviare
			if(measuresToSend == null) {
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
		    	dataBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
		    	if(currentOperation.equals("ALL"))
		    		dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showMeasureDialogDeleteQuestion3") + "?");
		    	else
		    		dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showMeasureDialogDeleteQuestion2") + " " + ResourceManager.getResource().getString("measureType." + currentOperation) + "?");		    	
		    	
	    		showDialog(DELETE_CONFIRM_DIALOG);
	    	}
	        return true;
	    case R.id.retry_send_all_measure:
	    	retrySendAllMeasure();
/*	    	
	    	if(measuresToSend == null) {
	    		
	    		if (!silent)
	    			showDialog(SIMPLE_DIALOG);
	    	}
	    	else {
	    		if (!silent){
		    		measuresResultBundle = new Bundle();
		    		measuresResultBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
		    		measuresResultBundle.putInt(MEASURE_NUMBER, measuresToSend.size());
		    			    		
	    			showDialog(SEND_MEASURES_DIALOG);
	    		}
	    	}
	*/    	
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
		
		menu.setHeaderTitle(ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()));
		//menu.setHeaderIcon(Util.getIconId(selected_measure.getMeasureType()));
		menu.setHeaderIcon(Util.getSmallIconId(selected_measure.getMeasureType()));
		if(selected_measure.getSent().equalsIgnoreCase("s")){
			//Un solo modello di device disponibile
			menu.setGroupVisible(R.id.manage_measure_1, false);
			menu.setGroupVisible(R.id.manage_measure_2, true);
		} else {
			//E' possibile selezionare il modello
			menu.setGroupVisible(R.id.manage_measure_1, true);
			menu.setGroupVisible(R.id.manage_measure_2, false);
		}
	}

	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		//Handle item selection
	    switch (item.getItemId()) {
	    case R.id.delete_measure:
		case R.id.delete_measure_2:
	    	//L'utente ha selezionato la voce "Elimina misura"
			Log.i(TAG, "Elimino misura: " + selected_measure.getMeasureType());
	    	if (!silent){
	    		dataBundle = new Bundle();
				dataBundle.putInt(DELETE_TYPE, 1);
		    	dataBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("warningTitle"));
		    	dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showMeasureDialogDeleteQuestion1") + " " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + "?");		    	
	    		showDialog(DELETE_CONFIRM_DIALOG);
	    	}
	    	return true;
	    case R.id.send_measure:
	    	//L'utente ha selezionato la voce "Riprova invio"
			Log.i(TAG, "Riprovo invio misura " + selected_measure.getMeasureType());
			multipleSend = false;
			if (!silent){
				measureResultBundle = new Bundle();
				measureResultBundle.putString(GWConst.TITLE, ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()));
				if(selected_measure.getMeasureType().equals("OS"))
					measureResultBundle.putString(GWConst.MESSAGE, getBodyOxy(selected_measure.getXml()));
				else if(selected_measure.getMeasureType().equals("SP"))
					measureResultBundle.putString(GWConst.MESSAGE, getBodySP(selected_measure.getXml()));
				else
					measureResultBundle.putString(GWConst.MESSAGE, getBody(selected_measure.getXml()));
						
				showDialog(MEASURE_SEND_DIALOG);
			}
	    	return true;
		    default:
		        return super.onOptionsItemSelected(item);
	    }
	}
	
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
						Toast.makeText(context, ResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
					
					listAdapter.notifyDataSetChanged();
				} else if(result.compareToIgnoreCase("send") == 0){
					//Viene inviata la singola misura
					totMeasureToSend = 1;
					counter = 0;
					failCounter = 0;
					measureManager.setMeasureDataToSend(selected_measure);
					
					if (!silent){
						dataBundle = new Bundle();
						dataBundle.putString(GWConst.MESSAGE, msgTrasf);
						dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
						showDialog(PROGRESS_DIALOG);
					}
					Log.i(TAG, "Invio delle misure in corso");
					measureManager.setSendedFromDB(true);
					changeConnectionSettings(false);
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
				createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("saveServerCertFailure"));
				break;
			}
		}
	};

	/**
     * Gestore dei messaggi in arrivo dalla classe ConnectionManager
     */
    private final Handler connectionManagerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		
    		showWarningMessage();
    		
    		switch (msg.what) {
    		case ConnectionManager.CONNECTION_SUCCESS:
    			Log.i(TAG, "Connection Ok --> invio la misura");
    			Log.i(TAG, "Invio misura 1 di " + totMeasureToSend);
    			if (!silent){
    				String message;
        			if(totMeasureToSend > 1)
        				message = msgTrasf + " (1/" + totMeasureToSend + ")";
        			else 
        				message = msgTrasf;
    				dataBundle.putString(GWConst.MESSAGE, message);
    				showDialog(PROGRESS_DIALOG);
    			}
    				
    			measureManager.sendMeasureData();
    			break;
    		case ConnectionManager.CONNECTION_ERROR:
    			Log.i(TAG, "Connection non ok --> impossibile inviare la misura");
    			createAlertDialogWithSave(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("showSettingsApnError"));
    			break;
    		case ConnectionManager.APN_ERROR:
    			Log.i(TAG, "Errore APN --> impossibile inviare la misura");
    			createAlertDialogWithSave(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("changeApnError"));
    			revertToDefaultConnectionSettings();
    			closeProgressDialog();
    			failCounter++;
    			totMeasureToSend = 1;
    			break;
    		}
    	}
    };
	
	/**
	 * Handler che si occupa della gestione dei messaggi che arrivano dal MeasureManager
	 */
	private final Handler measureManagerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			showWarningMessage();
			
			switch (msg.what) {
			case MeasureManager.MEASURE_SENT:
				Log.i(TAG, "Invio misura completato");
				if(!silent){
					
					UserDevice ud = MyDoctorApp.getDeviceManager().getCurrentDevice();
					
					if( ud == null ) {
						dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureSuccess"));
					} else {					
						if (MyDoctorApp.getDeviceManager().getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
							dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureSuccessImg"));
						else
							dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureSuccess"));
					}
					
				} else {
					Toast.makeText(context, ResourceManager.getResource().getString("KMsgSendMeasureSuccess"), Toast.LENGTH_SHORT).show();
				}
				Log.i(TAG, "Aggiorno la misura di " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + " delle ore " + getTimestamp(selected_measure.getTimestamp()) + " come inviata");
				MeasureManager.getMeasureManager().updateMeasureData(selected_measure);
	        	break;
			case MeasureManager.LOGIN_FAILED:
				
				Log.i(TAG, "deviceManagerHandler: Login fallito");
				handleErrorSend(MeasureManager.LOGIN_FAILED);
				break;
			case MeasureManager.SENDING_ERROR:

                Log.i(TAG, "deviceManagerHandler: Errore nell'invio");
                handleErrorSend(MeasureManager.SENDING_ERROR);
                break;
			case MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED:

				Log.i(TAG, "deviceManagerHandler: Errore nell'invio");
				handleErrorSend(MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED);
				break;
			case MeasureManager.ACCEPT_SERVER_CERT:
				
				Log.i(TAG, "deviceManagerHandler: Ricesta accettazione certificato");
				closeProgressDialog();
				//Il server espone dei certificati non riconosciuti dal telefono. Viene chiesto all'utente se intende fidarsi e accettare il certificato
				serverCertificate = (X509Certificate[]) msg.obj;
				
				if (!silent){
					Bundle data = msg.getData();
					serverCertBundle = new Bundle();
					serverCertBundle.putString(GWConst.MESSAGE_LOWER, data.getString(GWConst.EXCEPTION_MSG));
					showDialog(CERT_PROBLEM_DIALOG);
				}
				
				revertToDefaultConnectionSettings();
				break;
				
			case MeasureManager.TRANSFER_FILE:
				Log.i(TAG, "measureManagerHandler: TRANSFER_FILE");								
				dataBundle.putString(GWConst.MESSAGE, ""+msg.obj);
				showDialog(PROGRESS_DIALOG);
				break;
				
			case MeasureManager.SAVE_MEASURE_FAIL:
				
				Log.i(TAG, "deviceManagerHandler: Salvataggio misura fallito");
				handleErrorSend(MeasureManager.SAVE_MEASURE_FAIL);
			
				break;
			case MeasureManager.SAVE_MEASURE_SUCCESS:
				
				Log.i(TAG, "Misura aggiornata su db");				
				counter++;
				Log.i(TAG, "COUNTER: " + counter + " TOTAL SEND: " + totMeasureToSend);
				if (counter == totMeasureToSend) {
					Log.i(TAG, "Tutte le misure sono state inviate");
					Log.i(TAG, "COUNTER: " + counter + " FAIL: " + failCounter);
					closeProgressDialog();
					
					if (!silent){
						dataBundle.putInt(SEND_FAILURE, failCounter);
						dataBundle.putInt(SEND_STATUS, totMeasureToSend);
						showDialog(STATUS_DIALOG);
					} else {
						measureManager.setHandler(null);
						connectionManager.setHandler(null);
						scMananger.setHandler(null);
						counter = 0;
						totMeasureToSend = 0;
						if(silentSendEventListener != null){
							silentSendEventListener.onSilentSendCompleted();
						}
					}
						
					revertToDefaultConnectionSettings();
				}
				else {
					Log.i(TAG, "Invio misura " + (counter + 1) + " di " + totMeasureToSend);				
					
					if (!silent){
						String message = msgTrasf + " (" + (counter + 1) + "/" + totMeasureToSend + ")";
						dataBundle.putString(GWConst.MESSAGE, message);
						showDialog(PROGRESS_DIALOG);
					}
						
					if (measuresToSend != null) {
						selected_measure = measuresToSend.get(counter);
						Log.i(TAG, "Invio la misura di " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + " delle ore " + getTimestamp(selected_measure.getTimestamp()));
						measureManager.setMeasureDataToSend(selected_measure);
						measureManager.sendMeasureData();
					}
				}
				
				if(!silent){
					measures.clear();
					populateActivity(currentOperation);
				}
				break;
			case MeasureManager.ERROR_DB:
				createAlertDialog(ResourceManager.getResource().getString("warningTitle"), (String) msg.obj);
				revertToDefaultConnectionSettings();
				break;
			}
		}
	};
	
	/**
	 * Listener per i click sulla progress dialog PROGRESS_DIALOG
	 */
	private DialogInterface.OnClickListener progress_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			try {
				measureManager.stopSendMeasure();
				revertToDefaultConnectionSettings();
			} catch (Exception e) {
				e.printStackTrace();
				createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("KMsgSendCancelError"));
			}
		}
	};
	
	/**
     * Listener per i click sulla dialog CERT_INFO_DIALOG
     */
    private DialogInterface.OnClickListener cert_info_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			//Premuto il pulsante "Ok"
			case DialogInterface.BUTTON_POSITIVE:
				removeDialog(CERT_INFO_DIALOG);
				
				if (!silent)
					showDialog(CERT_PROBLEM_DIALOG);
			}
		}
	};
    
    /**
     * Listener per i click sulla dialog CERT_PROBLEM_DIALOG
     */
    private DialogInterface.OnClickListener cert_problem_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale pulsante della dialog è stato premuto
			switch (which) {
			//Premuto il pulsante "Annulla"
			case DialogInterface.BUTTON_NEGATIVE:
				removeDialog(CERT_PROBLEM_DIALOG);
				Toast.makeText(context, ResourceManager.getResource().getString("serverCertDialogCertRejected"), Toast.LENGTH_LONG).show();
				break;
			//Premuto il pulsante "Accetta certificato"
			case DialogInterface.BUTTON_POSITIVE:
				removeDialog(CERT_PROBLEM_DIALOG);
				Toast.makeText(context, ResourceManager.getResource().getString("serverCertDialogCertAccepted"), Toast.LENGTH_LONG).show();
				scMananger.addServerCertificate(UserManager.getUserManager().getCurrentUser().getId(), serverCertificate[0]);
				//Il certificato è stato accettato. Chiedo all'utente se vuole riprovare l'invio
				if (multipleSend) {
					if (!silent)
						showDialog(SEND_MEASURES_DIALOG);
				} else {
					if (!silent)
						showDialog(MEASURE_SEND_DIALOG);
				}
				break;
			//Premuto il pulsante "Vedi certificato"
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(CERT_PROBLEM_DIALOG);
				
				if (!silent)
					showDialog(CERT_INFO_DIALOG);
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
     * Listener per i click sulla dialog STATUS_DIALOG
     */
    private DialogInterface.OnClickListener status_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(STATUS_DIALOG);
				break;
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
						Toast.makeText(context, ResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
					break;
				case 2:
					//Vengono eliminate tutte le misure di un certo tipo
					String idUser = UserManager.getUserManager().getCurrentUser().getId();
					String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
					measureManager.deleteAllMeasure(idUser, idPatient, currentOperation);
					
					if (isImageManager) {
						final File[] files = Util.getDir().listFiles();
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
				Toast.makeText(context, "Misura non eliminata", Toast.LENGTH_LONG).show();
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
					Toast.makeText(context, ResourceManager.getResource().getString("KMsgDeleteMeasureConfirm"), Toast.LENGTH_LONG).show();
				
				listAdapter.notifyDataSetChanged();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				mListView.cancelDrag();
				Toast.makeText(context, "Misura non eliminata", Toast.LENGTH_LONG).show();
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
	 * Listener per la selezione degli elementi che compongono la dialog MEASURE_SEND_DIALOG
	 */
	private DialogInterface.OnClickListener measure_send_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(MEASURE_SEND_DIALOG);
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				Log.i(TAG, "Riprova invio misura");
				
				totMeasureToSend = 1;
				counter = 0;
				failCounter = 0;
				measureManager.setMeasureDataToSend(selected_measure);
				
				if (selected_measure != null && selected_measure.getMeasureType().equals(GWConst.KMsrImg)) {
					//isImageManager = true;
					msgTrasf = ResourceManager.getResource().getString("KMsgTrasfImg");
				}
				
				if (!silent){
					dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, msgTrasf);
					dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
					showDialog(PROGRESS_DIALOG);
				}
				Log.i(TAG, "Invio delle misure in corso");
				measureManager.setSendedFromDB(true);
				changeConnectionSettings(false);
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Log.i(TAG, "Riprova invio non effettuato");
				break;
			}
		}
	};
	
	/**
	 * Listener per la selezione degli elementi che compongono la dialog SWIPE_MEASURE_SEND_DIALOG
	 */
	private DialogInterface.OnClickListener swipe_measure_send_dialog_click_listener = new DialogInterface.OnClickListener() {
		//TODO
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(SWIPE_MEASURE_SEND_DIALOG);
			mListView.cancelDrag();
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				Log.i(TAG, "Riprova invio misura");
				
				totMeasureToSend = 1;
				counter = 0;
				failCounter = 0;
				measureManager.setMeasureDataToSend(selected_measure);
				
				if (!silent){
					dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, msgTrasf);
					dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
					showDialog(PROGRESS_DIALOG);
				}
				Log.i(TAG, "Invio delle misure in corso");
				measureManager.setSendedFromDB(true);
				changeConnectionSettings(false);
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Log.i(TAG, "Riprova invio non effettuato");
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
	 * Listener per i click sulla dialog ALERT_DIALOG_WITH_SAVE
	 */
	private DialogInterface.OnClickListener alert_dialog_with_save_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(ALERT_DIALOG_WITH_SAVE);
			measureManager.saveMeasureData("n");
		}
	};
	
	/**
	 * Listener per i click sulla listView
	 */
	private OnItemClickListener listViewItemClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view,
				int position, long id) {
			selected_measure = listaMisure.get(position);
			if (!selected_measure.getMeasureType().equals(ResourceManager.getResource().getString("reverseMeasureType.ECG"))) {
				
				if (selected_measure.getFile() != null && selected_measure.getMeasureType().equalsIgnoreCase(GWConst.KMsrImg)) {					
					try {
						
						/*
						new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp").mkdirs();
						File posterFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp/temp.jpg");
						posterFile.createNewFile();
						BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(posterFile));
						out.write(selected_measure.getFile()); 
						out.close();
						
						Intent intent = new Intent();
						intent.setAction(android.content.Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.fromFile(posterFile), "image/*");
						startActivity(intent);
						*/
						
						CameraDevice.getInstance().showPhoto(selected_measure.getFile());
					}
					catch (Exception e) {
						e.printStackTrace();
						Log.e(TAG, "openFile", e);
					}
				}
				else {
				
					if (!selected_measure.getMeasureType().equalsIgnoreCase(GWConst.KMsrAritm)) {
						
						Intent intent = new Intent(ShowMeasure.this, MeasureDetails.class);
						intent.putExtra(GWConst.SELECTED_MEASURE, selected_measure);
						startActivityForResult(intent, MEASURE_DETAILS);
					}
				}
			}
		}
	};	
	
	/**
	 * Versione customizzata per misura di OSSIMETRIA
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBodyOxy(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				if(name.equals(ResourceManager.getResource().getString("EGwnurseMeasureOxyMed")) || name.equals(ResourceManager.getResource().getString("EGwnurseMeasureOxyFreqMed"))) {
					String value = ((MeasureDetail) (measureList.get(i))).getValue();
					String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
					builder.append(name + " " + value + " " + unit + "\n");
				}
			}
		} catch (XmlException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
		}
		return builder.toString();
	}
	
	protected void startSendingAllMeasures() {
		//Inizializzo i contatori in modo opportuno
		totMeasureToSend = measuresToSend.size();
		counter = 0;
		failCounter = 0;

		Log.i(TAG, "Invio delle misure in corso");	    		
		selected_measure = measuresToSend.get(counter);
		Log.i(TAG, "Invio la misura di " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + " delle ore " + getTimestamp(selected_measure.getTimestamp()));
		measureManager.setMeasureDataToSend(selected_measure);
		dataBundle = new Bundle();
		dataBundle.putString(GWConst.MESSAGE, msgTrasf);
		dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
		
		if (!silent)
			showDialog(PROGRESS_DIALOG);
		measureManager.setSendedFromDB(true);
		changeConnectionSettings(false);
	}

	/**
	 * Versione customizzata per misura di SPIROMETRIA
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBodySP(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				ResourceManager rMananger = ResourceManager.getResource();
				if(name.equals(rMananger.getString("EGwnurseMeasureSpiroPEF")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFEV1")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFVC")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFEV1perc")) || name.equals(rMananger.getString("EGwnurseMeasureSpiroFET"))) {
					String value = ((MeasureDetail) (measureList.get(i))).getValue();
					String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
					builder.append(name + " " + value + " " + unit + "\n");
				}			
			}
			
		} catch (XmlException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
		}
		
		return builder.toString();
	}

	/**
	 * Metodo che permette di costruire il testo della dialog con i dettagli relativi alla misura
	 * @param xml variabile di tipo {@code String} che contiene l'xml della misura
	 * @return variabile di tipo {@code String} che contiene il messaggio da visualizzare sulla dialog
	 */
	private String getBody(String xml) {
		StringBuilder builder = new StringBuilder();
		try {
			(XmlManager.getXmlManager()).parse(xml);
			Vector<Object> measureList = (XmlManager.getXmlManager()).getParsedData();
			
			for (int i = 0; i < measureList.size(); i++) {
				String name = ((MeasureDetail) (measureList.get(i))).getName();
				String value = ((MeasureDetail) (measureList.get(i))).getValue();
				String unit = ((MeasureDetail) (measureList.get(i))).getUnit();
				builder.append(name + " " + value + " " + unit + "\n"); 
			}
		} catch (XmlException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
		}
		
		return builder.toString();
	}

	/**
	 * Metodo che permette di riempire il contenuto dell'activity con gli opportuni valori
	 */
	private void populateActivity(String idMisura) {
		String idUser = UserManager.getUserManager().getCurrentUser().getId();
		
		String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
		listaMisure = measureManager.getMeasureData(idMisura, idUser, idPatient);
		if(listaMisure != null) {
			Iterator<Measure> listaMisureIterator = listaMisure.iterator();
			while(listaMisureIterator.hasNext()) {
				Measure misura = listaMisureIterator.next();
				HashMap<String, String> map = new HashMap<String, String>();
				map.put(KEY_ICON_SENT, String.valueOf(getIconSent(misura.getSent())));
				map.put(KEY_LABEL, ResourceManager.getResource().getString("measureType." + misura.getMeasureType()));
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
			for (int i = 0; i < filterIds.length; i++) {
				if (filterIds[i].equalsIgnoreCase(timestamp)) 
					return true;
			}
		}
		return false;
	}

	/**
	 * Metodo che permette di esprimere una data in forma "dd-MM-yyyy HH:mm"
	 * @param timestamp variabile di tipo {@code String} che contiene la stringa da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getTimestamp(String timestamp) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		try {
			Date data = sdf.parse(timestamp);
			return data.toLocaleString();
		} catch (ParseException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
			return null;
		}
	}
	
	/**
	 * Metodo che restituisce la data in forma dd MMMMM yyyy
	 * @param timestamp  variabile di tipo {@code String} che contiene la stringa da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getDate(String timestamp) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("dd MMM yyyy");
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
			return null;
		}
	}
	
	/**
	 * Metodo che restituisce l'orario in forma HH:mm"
	 * @param timestamp variabile di tipo {@code String} che contiene la stringa da convertire in ora
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getHour(String timestamp) {	
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
		try {
			Date data = sdf.parse(timestamp);
			sdf = new SimpleDateFormat("HH:mm:ss");
			return sdf.format(data);
		} catch (ParseException e) {
			e.printStackTrace();
			createAlertDialog(ResourceManager.getResource().getString("warningTitle"), ResourceManager.getResource().getString("errorDbRead"));
			return null;
		}
	}
	
	/**
	 * Metodo che si occupa della gestione di un errore nella ritrasmissione della misura alla piattaforma
	 */
	private void handleErrorSend(int failureReason) {
		
		if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {
			closeProgressDialog();
			
    		//showToastMessage(ResourceManager.getResource().getString("userBlocked"));
			//Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);
    		
    		finish();
    		
    		/*
    		Util.pushTaskForceNewUserSilent(ShowMeasure.this);
    		Intent intent = new Intent(ShowMeasure.this, MainActivity.class);
    		//intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    		intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    		startActivity(intent);
    		*/
    		return;
		}
		
		counter++;
		failCounter++;

        /* stop sending measures when Service Center cannot accept more measures */
		if (failureReason == MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED) {
			// totMeasureToSend = counter;
            Log.i(TAG, "Raggiunta quota massima misure Service Center");
		}

		if(totMeasureToSend>0){
			if ((counter == totMeasureToSend) || (failureReason == MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED)) {
				Log.i(TAG, "Tutte le misure sono state inviate");
				Log.i(TAG, "COUNTER: " + counter + " FAIL: " + failCounter);
				closeProgressDialog();
				
				if (!silent){
					dataBundle.putInt(SEND_FAILURE, failCounter);
					dataBundle.putInt(SEND_STATUS, counter);
                    dataBundle.putInt(SEND_TOTAL, totMeasureToSend);
                    dataBundle.putInt(SEND_FAILURE_REASON, failureReason);
                    showDialog(STATUS_DIALOG);
				} else {
					measureManager.setHandler(null);
					connectionManager.setHandler(null);
					scMananger.setHandler(null);
					counter = 0;
					totMeasureToSend = 0;
					if(silentSendEventListener != null){
						silentSendEventListener.onSilentSendCompleted();
					}
				}
					
				revertToDefaultConnectionSettings();
			}
			else {
				Log.i(TAG, "Invio misura " + (counter + 1) + " di " + totMeasureToSend);			
				
				if (!silent){
					String message = msgTrasf + "(" + (counter + 1) + "/" + totMeasureToSend + ")";
					dataBundle.putString(GWConst.MESSAGE, message);
					showDialog(PROGRESS_DIALOG);
				}
					
				selected_measure = measuresToSend.get(counter);
				Log.i(TAG, "Invio la misura di " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + " delle ore " + getTimestamp(selected_measure.getTimestamp()));
				measureManager.setMeasureDataToSend(selected_measure);
				measureManager.sendMeasureData();
			}
		}
	}
		
	/**
	 * Metodo che si occupa di chiudere la progress dialog
	 */
	private void closeProgressDialog() {
		if(progressDialog!= null && progressDialog.isShowing()){
			//progressDialog.dismiss();
			removeDialog(PROGRESS_DIALOG);
			progressDialog = null;
		}
	}
	
	/**
	 * Metodo che si occupa della creazione di una progress dialog
	 * @param data oggetto di tipo {@code Bundle} che contiene alcune informazioni relative alla progress dialog
	 * @return oggetto di tipo {@code Dialog} che contiene il riferimento alla progress dialog
	 */
/*
	private Dialog createProgressDialog0(Bundle data) {
		progressDialog = new ProgressDialog(this);		
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);
		progressDialog.setMessage(data.getString(GWConst.MESSAGE));
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, ResourceManager.getResource().getString("cancelButton"),  progress_dialog_click_listener);	
    	return progressDialog;
	}	
*/	
	/**
	 * Metodo che si occupa della creazione di una progress dialog
	 * @param data oggetto di tipo {@code Bundle} che contiene alcune informazioni relative alla progress dialog
	 * @return oggetto di tipo {@code Dialog} che contiene il riferimento alla progress dialog
	 */
	private Dialog createProgressDialog(Bundle data) {
		
		/*
		progressDialog = new ProgressDialog(this);		
		progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);
		progressDialog.setMessage(data.getString(GWConst.MESSAGE));
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, ResourceManager.getResource().getString("cancelButton"),  progress_dialog_click_listener);
		*/
		
		String msg = data.getString(GWConst.MESSAGE);
		
		Log.d(TAG, "createProgressDialog msg=" + msg);
				
		if (msg.startsWith(WebSocket.FILE_TRANSFER) || msg.equalsIgnoreCase(ResourceManager.getResource().getString("KMsgTrasfImg"))) {
					
			int value = 0;
			
			try {
				value = Integer.parseInt(msg.substring(WebSocket.FILE_TRANSFER.length()));
			}
			catch (Exception e) {
				value = 0;
			}
						
			Log.d(TAG, "createProgressDialog setProgress=" + value);
					
			progressDialog = new ProgressDialog(this);		
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setIndeterminate(false);
			progressDialog.setCancelable(false);
			progressDialog.setMax(100);
			progressDialog.setProgress(value);
			
			if (value == 0)
				progressDialog.setMessage(data.getString(GWConst.MESSAGE));
			
			progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, ResourceManager.getResource().getString("cancelButton"),  progress_dialog_click_listener);

		}
		else {
			progressDialog = new ProgressDialog(this);		
			progressDialog.setIndeterminate(true);
			progressDialog.setCancelable(false);
			progressDialog.setMessage(data.getString(GWConst.MESSAGE));
			progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, ResourceManager.getResource().getString("cancelButton"),  progress_dialog_click_listener);

		}
		
		
    	return progressDialog;
	}
	
	/**
	 * Metodo che si occupa della visualizzazione di una dialog
	 * @param title variabile di tipo {@code String} che contiene il titolo della dialog
	 * @param message variabile di tipo {@code String} che contiene il messaggio da visualizzare all'interno della dialog
	 */
	private void createAlertDialog(String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putString(GWConst.TITLE, title);
		dataBundle.putString(GWConst.MESSAGE, message);
		
		if (!silent)
			showDialog(ALERT_DIALOG);
	}
	
	/**
	 * Metodo che si occupa della visualizzazione di una dialog
	 * @param title variabile di tipo {@code String} che contiene il titolo della dialog
	 * @param message variabile di tipo {@code String} che contiene il messaggio da visualizzare all'interno della dialog
	 */
	private void createAlertDialogWithSave(String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putString(GWConst.TITLE, title);
		dataBundle.putString(GWConst.MESSAGE, message);
		
		if (!silent)
			showDialog(ALERT_DIALOG_WITH_SAVE);
	}
	
	/**
	 * Metodo che indica quale icona visualizzare a seconda che la misura sia stata inviata o meno
	 * @param sent variabile di tipo {@code String} che indica se la misura è stata inviata o meno
	 * @return variabile di tipo {@code String} che contiene il riferimento all'icona da utilizzare
	 */
	private int getIconSent(String sent) {
		if(sent.equalsIgnoreCase("S"))
			return R.drawable.ic_menu_measure_sended_yellow;
		else
			return R.drawable.ic_menu_measure_no_sended_yellow;
	}
	
	private void changeConnectionSettings(boolean isConfiguration) {
		
		if (!silent)
			showDialog(PROGRESS_DIALOG);
		connectionManager.changeConnection(isConfiguration);
	}
	
	private void revertToDefaultConnectionSettings() {
		connectionManager.resetDefaultConnection();
	}

	public void sendMeasureSilent(String currentOperation, Context ctx, SilentSendEventListener listener) {
		// TODO
		context = ctx;
		
		this.silentSendEventListener = listener;
		
		this.currentOperation = currentOperation;
		
		silent = true;
		
		measureManager = MeasureManager.getMeasureManager();
		measureManager.setHandler(measureManagerHandler);
		
		connectionManager = ConnectionManager.getConnectionManager(context);
		connectionManager.setHandler(connectionManagerHandler);
		
		scMananger = ServerCertificateManager.getScMananger();
		scMananger.setHandler(serverCertificateManagerHandler);
		
		measures = new ArrayList<HashMap<String, String>>();
		
		multipleSend = true;
    	String idUser = UserManager.getUserManager().getCurrentUser().getId();
    	String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
    	
    	//Ottengo le misure che devono essere inviate
    	measuresToSend = measureManager.getNotSendedMeasures(idUser, idPatient, currentOperation);
    	
    	measuresToSend = purgeList(measuresToSend);
    	
    	if(measuresToSend != null) {
    	
    		totMeasureToSend = measuresToSend.size();
    		counter = 0;
    		failCounter = 0;

    		Log.i(TAG, "Invio delle misure in corso");	    		
    		selected_measure = measuresToSend.get(counter);
    		Log.i(TAG, "Invio la misura di " + ResourceManager.getResource().getString("measureType." + selected_measure.getMeasureType()) + " delle ore " + getTimestamp(selected_measure.getTimestamp()));
    		measureManager.setMeasureDataToSend(selected_measure);    		
    		measureManager.setSendedFromDB(true);
    		changeConnectionSettings(false);
    	}
	}
	
	public interface SilentSendEventListener {
		void onSilentSendCompleted();
	}
	
	protected void showWarningMessage() {
    	
    	if (Util.getRegistryValue(Util.KEY_WARNING_TIMESTAMP, false)) {
    		    		
    		DialogManager.showToastMessage(ShowMeasure.this, ResourceManager.getResource().getString("warningDate"));
			Util.setRegistryValue(Util.KEY_WARNING_TIMESTAMP, false);
		}
	}
}
