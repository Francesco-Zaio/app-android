package com.ti.app.mydoctor.gui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.connectionmodule.ConnectionManager;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.mydoctor.devicemodule.DeviceManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.scmodule.ServerCertificateManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.webmodule.WebSocket;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager.TDeviceType;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.DeviceListAdapter;
import com.ti.app.mydoctor.gui.listadapter.MainMenuListAdapter;
import com.ti.app.mydoctor.util.DialogManager;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.security.cert.X509Certificate;

@SuppressWarnings("deprecation")
public class DeviceList extends ActionBarActivity implements OnChildClickListener, DeviceListFragmentListener {
	private static final String TAG = "DeviceList";
	
	private static final String KEY_ICON = "icon";
	private static final String KEY_LABEL = "label";
	private static final String KEY_MODEL = "model";

    private static final int PERMISSIONS_REQUEST = 112;

    // Menu Options
	private Bundle selectedItemBundle;
	private static final String SELECTED_MENU_ITEM = "SELECTED_MENU_ITEM";
	private static final int ITEM_EMPTY = 0;
	private static final int ITEM_MEASURE = 1;
	private static final int ITEM_USER_UPDATES = 3;
	private static final int ITEM_USER_LIST = 4;
	private static final int ITEM_USER_NEW = 5;
	private static final int ITEM_USER_OPTIONS = 6;
	private static final int ITEM_USER_SHEALTH_OPTIONS = 7;
	private static final int ITEM_DEVICES_MANAGEMENT = 8;
	private static final int ITEM_ABOUT = 9;
	private static final int ITEM_LOGOUT = 10;
	private static final int ITEM_EXIT = 11;
	
	// Intent request codes
    private static final int USER_LIST = 1;
    private static final int USER_SELECTION = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int REQUEST_SCAN_DEVICES = 4;
    private static final int REQUEST_DISCOVERABLE = 5;
    public static final int PATIENT_SELECTION = 6;
    public static final int PATIENT_SELECTION_2 = 7;
    public static final int PATIENT_SELECTION_SEND_ALL = 8;
    public static final int MANUAL_MEASURE_ENTRY = 9;
    public static final int CALIBRATE_ENTRY = 10;
        
    //Dialog
    private static final int MEASURE_RESULT_DIALOG = 1;
    private static final int PROGRESS_DIALOG = 2;
    private static final int ALERT_DIALOG = 3;
    private static final int CERT_PROBLEM_DIALOG = 4;
    private static final int CERT_INFO_DIALOG = 5;
	private static final int STATUS_DIALOG = 6;
	private static final int ALERT_DIALOG_WITH_SAVE = 7;
	private static final int LOGIN_DIALOG = 8;
	private static final int SIMPLE_DIALOG_WITH_CLOSE = 10;
	private static final int CONFIRM_PATIENT_DIALOG = 11;
	//private static final int DIALOG_TASKING = 12;
	private static final int LIST_OR_NEW_USER_DIALOG = 13;
	private static final int SIMPLE_DIALOG_WITHOUT_CLOSE = 14;
	private static final int PRECOMPILED_LOGIN_DIALOG = 16;
	private static final int PLATFORM_UNREACHABLE_DIALOG = 17;
	private static final int CONFIRM_CLOSE_DIALOG = 18;
	private static final int MEASURE_RESULT_DIALOG_SEND_ALL = 19;
    
    private static final String SEND_STATUS = "SEND_STATUS";
	private static final String SAVE_STATUS = "SAVE_STATUS";

	private static final int DISCOVERABLE_DURATION = 120;
	private static final int REQUEST_CODE_APN = 999;
	
	//Stato della calibrazione
	private enum TCalibrateState {
		EIdle,
		EMeasureOn_ScanOn,
		EMeasureOn_ScanOff,
		EMeasureOff_ScanOn,
		EMeasureOff_ScanOff
	}
	protected TCalibrateState calibrateState = TCalibrateState.EIdle;
    
    private static ProgressDialog progressDialog;
    
    private GWTextView titleTV;
    private EditText loginET;
    private EditText pwdET;

    private LinearLayout currentPatientLL;

    //private boolean customTitleSupported;
    private Menu mActionBarMenu;
	
    private boolean isPairing;
    private boolean isConfig;    
    private boolean isManualMeasure;    
    private boolean isAR;
    
    int sentMeasures;
    int receivedMeasures;
    
	private DeviceManager deviceManager;
	
	private String selectedMeasureType;
	private int selectedMeasurePosition;
	
	//For showSelectModelDialog
	private int selectedModelPosition = -1;

	private DeviceListAdapter listAdapter;

	private List<HashMap<String, String>> fillMaps;
	
	private MeasureManager measureManager;
    
    private DeviceManagerMessageHandler deviceManagerHandler;
	private static Bundle dataBundle;
    //Bundle che contiene l'ultima misura effettuata dall'utente
    private Bundle measureDataBundle;
  	//Bundle che contiene informazioni sui certificati esportati dal server
	private Bundle serverCertBundle;
	
	private Bundle viewMeasureBundle;
	private Bundle startMeasureBundle;
	private Bundle userDataBundle;
	
	//Bundle che contiene i certificati esportati dal server
	private X509Certificate[] serverCertificate;
	
	private HashMap<String, UserDevice> deviceMap;
	private List<String> measureList;
	private HashMap<String, List<UserDevice>> measureModelsMap;

	private UserManager userManager;
	private ConnectionManager connectionManager;
	private ServerCertificateManager scManager;
	
	private boolean runningConfig;
	private boolean runningConfigUpdate;
	
	private GWTextView patientNameTV;
	
	private String[] patients;
	private List<UserPatient> patientList;
		
	public static DeviceList ACTIVE_INSTANCE;
	
	ProgressDialog mLoadingDialog;
	
	private UIHandler mUIHandler = new UIHandler();
	
	private Bundle broadcastReceiverBundle;
	
	private MeasureDialogClickListener measureDialogListener;
	
	private boolean isForcedRelogin = false;
	
	private boolean fastRestart = false;
	
	//Gestione menu laterale sinistro
	private DrawerLayout mDrawerLayout;
    private ExpandableListView mMenuDrawerList;
    private ActionBarDrawerToggle mActionBarDrawerToggle;
  	
  	//Adapter
  	MainMenuListAdapter mMenuExpListAdapter;
  	
  	//Gestione Fragment con lista dispositivi
  	DeviceListFragment deviceListFragment;
  	DeviceGridFragment deviceGridFragment;
  	FragmentManager fragmentManager;
  	
  	//Gestione switch List/Grid
  	private boolean isGrid = false;
  	private boolean isFragmentCreatedInSwitch = false;
  	
  	//Spinner per attesa gestione dispositivi
  	private LinearLayout linlaHeaderProgress;
  	    
    //private List<HashMap<String, String>> mMenuFillMaps;
	//private final ArrayList<String> mMenuIconList = new ArrayList<String>();
	//private final ArrayList<String> mMenuLabelList = new ArrayList<String>();

	//Controlla se è stata attivata la versione offline
	private boolean isOffLineVersion = false;

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.setTheme(R.style.Theme_MyDoctorAtHome_Light);
		setContentView(R.layout.device_list_main_activity);

		Intent intent = getIntent();

		if (intent != null) {
			Log.d(TAG, "onCreate() intent=" + intent);
			Log.d(TAG, "onCreate() flags=" + intent.getFlags());
			Log.d(TAG, "onCreate() action=" + intent.getAction());
			Log.d(TAG, "onCreate() package=" + intent.getPackage());

			/*if (intent.getFlags() == Intent.FLAG_ACTIVITY_NEW_TASK) {
				fastRestart = true;

				Log.d(TAG, "onCreate() fastRestart=" + fastRestart);
				finish();

				return;
			}*/

			Bundle extra = intent.getExtras();
			if (extra != null) {
				Log.d(TAG, "onCreate() extra()");

				Set<String> keys = extra.keySet();
				for (String k : keys) {

					Log.d(TAG, "onCreate() extra.key=" + k + " value=" + intent.getExtras().getByte(k));
				}
			}
		}

		broadcastReceiverBundle = getIntent().getExtras();


		//La tastiera viene aperta solo quando viene selezionata una edittext
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		//Flag per mantenere attivo lo schermo finch� l'activity � in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		ActionBar customActionBar;
		//Inizializza l'ActionBAr
		customActionBar = this.getSupportActionBar();
		//Setta il gradiente di sfondo della action bar
		Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
		customActionBar.setBackgroundDrawable(cd);

		customActionBar.setDisplayShowCustomEnabled(true);
		customActionBar.setDisplayShowTitleEnabled(false);
		//Abilita l'icona dell'actionbar ad attivare il menu laterale
		customActionBar.setDisplayHomeAsUpEnabled(true);
		customActionBar.setHomeButtonEnabled(true);
		//customActionBar.setHomeAsUpIndicator(R.drawable.logo_icon);


		//Setta l'icon
		customActionBar.setIcon(R.drawable.icon_action_bar);

		//Ricava la TextView dell'ActionBar
		LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View titleView = inflator.inflate(R.layout.actionbar_title, null);
		titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		titleTV.setText(R.string.app_name);
		customActionBar.setCustomView(titleView);

		//Ottengo il riferimento agli elementi che compongono la view
		mDrawerLayout = (DrawerLayout) findViewById(R.id.device_list_drawer_layout);
		mMenuDrawerList = (ExpandableListView) findViewById(R.id.device_list_left_menu);

        // set a custom shadow that overlays the main content when the drawer opens
		// setta il background del menu laterale
        mDrawerLayout.setDrawerShadow(R.drawable.background_lateral_menu, GravityCompat.START);
        // prepara la lista del menu e setta il listener
        setupMenuListView();
        //mMenuDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // ActionBarDrawerToggle gestisce l'interazione con il menu per lo sliding
        // e le azioni con la action bar app icon
        mActionBarDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                //R.drawable.ic_lateral_menu,  /* nav drawer image to replace 'Up' caret */
                R.string.main_menu_drawer_open,  /* "open drawer" description for accessibility */
                R.string.main_menu_drawer_close  /* "close drawer" description for accessibility */
                )
        {

        	private boolean isClosed = true;

            public void onDrawerClosed(View view) {
            	//supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            	selectedMenuItemAction();
            }

            public void onDrawerOpened(View drawerView) {
            	supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            public void onDrawerStateChanged (int newState) {

            	switch (newState) {
            	case DrawerLayout.STATE_IDLE:
					isClosed = !isClosed;
            		break;
            	case DrawerLayout.STATE_DRAGGING:
            		break;
            	case DrawerLayout.STATE_SETTLING:
            		if(isClosed)
            			prepareMenuListView();
            		break;
            		default:
            			break;
            	}
            }
        };
        mDrawerLayout.setDrawerListener(mActionBarDrawerToggle);

        //Inizializza la ListFragment con l'elenco dei dispositivi
        setFragmentView();

		//////////////////// SPOSTARE in setFragmentView() //////////////////////////////
		/*currentPatientLL = (LinearLayout) findViewById(R.id.current_patient_relative_layout);

		if(patientNameTV == null){
			patientNameTV = (GWTextView)findViewById(R.id.patient_name_label);
			fitTextInPatientNameLabel(getText(R.string.selectPatient).toString());
			currentPatientLL.setOnClickListener(patientNameLabelClickListener);
		}*/
		////////////////////////////////////////////////////////////

        deviceManagerHandler = new DeviceManagerMessageHandler();
		deviceManager = MyDoctorApp.getDeviceManager();
		deviceManager.setHandler(deviceManagerHandler);

		measureManager = MeasureManager.getMeasureManager();

		userManager = UserManager.getUserManager();
		userManager.setHandler(userManagerHandler);

		connectionManager = ConnectionManager.getConnectionManager(getApplicationContext());
		connectionManager.setHandler(connectionManagerHandler);

		scManager = ServerCertificateManager.getScMananger();
		scManager.setHandler(serverCertificateManagerHandler);

		ACTIVE_INSTANCE = this;

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) ) {

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    ActivityCompat.requestPermissions(DeviceList.this,
                            new String[]{
                                    Manifest.permission.ACCESS_WIFI_STATE,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_NETWORK_STATE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.READ_PHONE_STATE
                            },
                            PERMISSIONS_REQUEST);
                }
            }, 500);

        }
    }

    private boolean isPermissionGranted(int[] grantResults) {

        Log.d(TAG, "grantResults.length=" + grantResults.length);

        for (int i=0; i<grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
		finish();
    }

	private class UIHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case LOGIN_DIALOG:
				showDialog(LIST_OR_NEW_USER_DIALOG);
				break;
			case PRECOMPILED_LOGIN_DIALOG:
				showDialog(PRECOMPILED_LOGIN_DIALOG);
				break;
			}
		}
	}

	private class InitTask extends AsyncTask<Void, Void, Void> {

		private boolean errorFound;

		protected void onPreExecute() {
			//ACTIVE_INSTANCE.showDialog(DIALOG_TASKING);
		}

		protected Void doInBackground(Void... unused) {

			try {
				errorFound = false;
				MyDoctorApp.getConfigurationManager().init();
				checkActiveUser();

			} catch (Exception e) {
				e.printStackTrace();
				Log.e(TAG, "ERROR on doInBackground: " + e.getMessage());
				dataBundle = new Bundle();
				dataBundle.putString(GWConst.MESSAGE, e.getMessage());
				errorFound = true;
			}
			return null;
		}

		protected void onPostExecute(Void unused) {
			//ACTIVE_INSTANCE.removeDialog(DIALOG_TASKING);

			if (mLoadingDialog != null) {

				try {
					mLoadingDialog.dismiss();
				}
				catch (Exception e) {
					Log.e(TAG, "mLoadingDialog.dismiss()", e);
				}
			}

			if(errorFound)
				showDialog(SIMPLE_DIALOG_WITH_CLOSE);

			if(broadcastReceiverBundle != null && broadcastReceiverBundle.getString(GWConst.MEASURE_TYPE) != null) {
				selectedMeasureType = broadcastReceiverBundle.getString(GWConst.MEASURE_TYPE);
				setCurrentDevice(3);
				doMeasure();
			}
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if( linlaHeaderProgress != null )
			linlaHeaderProgress.setVisibility(View.GONE);
	}

	@Override
	protected void onResume() {
		super.onResume();

		Log.i(TAG, "onResume()");

		boolean testOffLineVersion = Util.getRegistryValue(Util.KEY_DEMO_VERSION_SETTING, false);
		if( testOffLineVersion != isOffLineVersion ) {

			isOffLineVersion = testOffLineVersion;
		}

		try {
			deviceManager.setHandler(deviceManagerHandler);
			userManager.setHandler(userManagerHandler);
			connectionManager.setHandler(connectionManagerHandler);
			scManager.setHandler(serverCertificateManagerHandler);

		} catch (Exception e) {
			Log.e(TAG, "ERROR on onResume: " + e.getMessage());
		}


		if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false) && UserManager.getUserManager().getCurrentPatient() != null) {

    		DialogManager.showToastMessage(DeviceList.this, ResourceManager.getResource().getString("userBlocked"));
			Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);


			measureList = new ArrayList<String>();
			setupView();

			resetView();

			UserManager.getUserManager().setCurrentPatient(null);

			doLogout();

			//setupListView();
			//showDialog(LOGIN_DIALOG);
			//mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
    	}

	}

	@Override
	public void onDestroy(){
		super.onDestroy();

		Log.i(TAG, "onDestroy()");

		if (fastRestart) {
			Log.i(TAG, "fastRestart()");
			fastRestart = false;
			return;
		}

		ACTIVE_INSTANCE = null;
		//Per essere sicuro che venga ripristinato l'APN di default del dispositivo
		if (Util.getRegistryValue(Util.APN_RESET_KEY, false))
			ConnectionManager.getConnectionManager(this).resetDefaultConnection();

		Util.setRegistryValue(Util.APN_RESET_KEY, false);
		UserManager.getUserManager().setCurrentPatient(null);

		try {
			if (DbManager.getDbManager() != null) {
				User activeUser = DbManager.getDbManager().getActiveUser();
				if (activeUser != null) {
					Log.d(TAG, "auto=" + activeUser.getHasAutoLogin());
					Log.d(TAG, "exit=" + activeUser.getLogin());

					if( !activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) )
						Util.setRegistryValue(Util.KEY_LAST_USER, activeUser.getId());

					if (!activeUser.getHasAutoLogin()) {
						DbManager.getDbManager().removeAllActiveUsers();
					}
				}
			}
		}
		catch (Exception e) {
			Log.e(TAG, "onDestroy.removeAllActiveUsers", e);
		}

		//DbManager.getDbManager().removeAllActiveUsers();
		DbManager.getDbManager().close();
		ResourceManager.getResource().closeResource();
	}

	/**
	 * GESIONE NAVIGATION DRAWER
	 */
	private void setupMenuListView() {

		String[] fromGroup = new String[] { KEY_ICON, KEY_LABEL };
		int[] toGroup = new int[] { R.id.groupicon, R.id.groupname };

		String[] fromChildIt = new String[] { KEY_LABEL };
		int[] toChildIt = new int[] { R.id.childname };

		//Creare l'adapter
		mMenuExpListAdapter = new MainMenuListAdapter(this,
				fromGroup, toGroup,
				fromChildIt, toChildIt);

        prepareMenuListView();

		mMenuDrawerList.setAdapter(mMenuExpListAdapter);

		mMenuDrawerList.setTextFilterEnabled(false);
		mMenuDrawerList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		//settare i listener
		mMenuDrawerList.setOnChildClickListener(this);

		mMenuDrawerList.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

            @Override
            public boolean onGroupClick(ExpandableListView parent, View view,
                                        int position, long id) {
                TextView tv = (TextView) view.findViewById(R.id.groupname);

                //return false; il gruppo si apre
                if (tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.mi_user))) {
                    //Opzioni utente ha un sottomenu, espande solo questo elemento della lista
                    return false;
                } else {
					/*Toast.makeText(getApplicationContext(),
							"Group Item text " +tv.getText().toString(), Toast.LENGTH_SHORT).show();*/
                    selectItem(tv, position);
                    return true;
                }
            }

        });

	}

	private void prepareMenuListView() {
		User activeUser;
		synchronized (this) {
			activeUser = DbManager.getDbManager().getActiveUser();
		}

		ArrayList<String> iconGroupArray = new ArrayList<>();
		iconGroupArray.add(""+R.drawable.ic_menu_archive_dark);
		iconGroupArray.add(""+R.drawable.ic_menu_user_list);
		iconGroupArray.add(""+R.drawable.icon_menu_preferences);
		iconGroupArray.add(""+R.drawable.ic_menu_about_dark);
		iconGroupArray.add(""+R.drawable.ic_menu_logout_dark);
		iconGroupArray.add(""+R.drawable.ic_menu_power_off_dark);

		ArrayList<String> labelGroupArray = new ArrayList<>();
		labelGroupArray.add(getResources().getString(R.string.mi_measure));
		labelGroupArray.add(getResources().getString(R.string.mi_user));
		labelGroupArray.add(getResources().getString(R.string.mi_devices_man));
		labelGroupArray.add(getResources().getString(R.string.info));
		labelGroupArray.add(getResources().getString(R.string.mi_logout));
		labelGroupArray.add(getResources().getString(R.string.mi_exit));

		ArrayList<String> labelUserOptionsArray = new ArrayList<>();
		labelUserOptionsArray.add(getResources().getString(R.string.update_user));
		labelUserOptionsArray.add(getResources().getString(R.string.list_users));
		labelUserOptionsArray.add(getResources().getString(R.string.new_user));
		labelUserOptionsArray.add(getResources().getString(R.string.settings));

		ArrayList<ArrayList<String>> labelChildArray = new ArrayList<>();
		labelChildArray.add(new ArrayList<String>());
		labelChildArray.add(labelUserOptionsArray);
		labelChildArray.add(new ArrayList<String>());
		labelChildArray.add(new ArrayList<String>());
		labelChildArray.add(new ArrayList<String>());
		labelChildArray.add(new ArrayList<String>());

		if(activeUser == null || (activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID )) ){
			iconGroupArray.remove(4); //remove icona Logout*
			iconGroupArray.remove(0); //remove icona Misure

			labelGroupArray.remove(4); //remove etichetta Logout*
			labelGroupArray.remove(0); //remove etichetta Misure

			if(DbManager.getDbManager().getNotActiveUsers().size() != 0) {
				labelUserOptionsArray.remove(3); //remove etichetta Impostazioni*
				labelUserOptionsArray.remove(0); //remove etichetta Aggiorna
			} else {
				labelUserOptionsArray.remove(3); //remove etichetta Impostazioni*
				labelUserOptionsArray.remove(1); //remove etichetta Elenco
				labelUserOptionsArray.remove(0); //remove etichetta Aggiorna
			}

			labelChildArray.remove(4);
			labelChildArray.remove(0);
		} else {
			iconGroupArray.remove(2); //remove icona Gestione Dispositivi*
			labelGroupArray.remove(2); //remove etichetta Gestione Dispositivi*
			labelChildArray.remove(2);

			//La voce "Misure" viene abilitata solo se ci sono misure caricate nel DB
			String idUser = activeUser.getId();


			//Contollo se la lista paziente � stata attivata
			if( patientList != null ){
				//Controllo se il paziente � stato scelto
				if ( UserManager.getUserManager().getCurrentPatient() != null ) {
					String idPatient = UserManager.getUserManager().getCurrentPatient().getId();
					ArrayList<Measure> ml = measureManager.getMeasureData(idUser, null, null, null, idPatient, MeasureManager.BooleanFilter.not);
					if(ml == null) {
						//Non ci sono misure
						iconGroupArray.remove(0); //remove icona Misure
						labelGroupArray.remove(0); //remove etichetta Misure
						labelChildArray.remove(0);
					}
				} else {
					//Non ci sono pazienti attivi, quindi non si possono visualizzare misure
					iconGroupArray.remove(0); //remove icona Misure
					labelGroupArray.remove(0); //remove etichetta Misure
					labelChildArray.remove(0);
				}
			}

			if(DbManager.getDbManager().getNotActiveUsers().size() == 0) {
				labelUserOptionsArray.remove(1); //remove etichetta Elenco
			}
		}

		//Settare le liste iniziali
		List<? extends Map<String, ?>> fillMapsGroupItem = createGroupList(iconGroupArray, labelGroupArray);
		ArrayList< List<? extends Map<String, ?>> > fillMapsChildItem = createChildList(labelChildArray);

		mMenuExpListAdapter.setGroupList(fillMapsGroupItem);
		mMenuExpListAdapter.setChildItemList(fillMapsChildItem);
		mMenuExpListAdapter.notifyDataSetChanged();
	}

	@Override
	public void onListFragmentCreated() {
		if( isGrid ) {
			currentPatientLL = deviceGridFragment.getPatientLayout();
	        patientNameTV = deviceGridFragment.getPatientLabel();
	        linlaHeaderProgress = deviceGridFragment.getProgressSpinnerLayout();
		} else {
			currentPatientLL = deviceListFragment.getPatientLayout();
	        patientNameTV = deviceListFragment.getPatientLabel();
	        linlaHeaderProgress = deviceListFragment.getProgressSpinnerLayout();
		}

        fitTextInPatientNameLabel(getText(R.string.selectPatient).toString());
		currentPatientLL.setOnClickListener(patientNameLabelClickListener);

		if( isFragmentCreatedInSwitch ){
			isFragmentCreatedInSwitch = false;
			setupView();

			if( isGrid ) {
				registerForContextMenu(deviceGridFragment.getGridView());
			} else {
				registerForContextMenu(deviceListFragment.getListView());
			}

			Patient p = UserManager.getUserManager().getCurrentPatient();
			if( p != null )
				setCurrentUserLabel(p.getName() + " " + p.getSurname());
			else
				setCurrentUserLabel( "" );

			if (UserManager.getUserManager().getCurrentUser().getIsPatient()) {
				currentPatientLL.setVisibility(View.GONE);
			} else {
				currentPatientLL.setVisibility(View.VISIBLE);
			}
		} else {
			setCurrentUserLabel("");
		}
	}

	private void setFragmentView() {
		Log.d(TAG, "setFragmentView()");

		isGrid = Boolean.parseBoolean(Util.getRegistryValue(Util.KEY_GRID_LAYOUT, Boolean.toString(isGrid)));

		fragmentManager = getSupportFragmentManager();

		if( isGrid ) {
			deviceGridFragment = new DeviceGridFragment();
			deviceGridFragment.setDeviceListFragmentListener(this);
			fragmentManager.beginTransaction().replace(R.id.device_list_content_frame, deviceGridFragment).commit();
		} else {
			deviceListFragment = new DeviceListFragment();
			deviceListFragment.setDeviceListFragmentListener(this);
			fragmentManager.beginTransaction().replace(R.id.device_list_content_frame, deviceListFragment).commit();
		}
	}

	@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mActionBarDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mActionBarDrawerToggle.onConfigurationChanged(newConfig);

        if ( measureList != null )
        	setupView();
    }

	@Override
	public boolean onChildClick(ExpandableListView parent, View v,
			int groupPosition, int childPosition, long id) {
		/*Toast.makeText(this, "Clicked On Child" + v.getTag(),
				Toast.LENGTH_SHORT).show();*/

		TextView tv = (TextView)v.findViewById(R.id.childname);
		selectItem(tv, childPosition);
		return true;
	}

	private void selectItem(TextView tv, int position) {

    	mDrawerLayout.closeDrawer(mMenuDrawerList);

    	selectedItemBundle = new Bundle();

    	if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.mi_measure)) ) {
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_MEASURE);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.mi_logout)) ) {
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_LOGOUT);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.mi_exit)) ) {
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_EXIT);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.mi_devices_man)) ) {
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_DEVICES_MANAGEMENT);

    		linlaHeaderProgress.setVisibility(View.VISIBLE);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.info)) ) {
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_ABOUT);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.update_user)) ){
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_USER_UPDATES);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.list_users)) ){
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_USER_LIST);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.new_user)) ){
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_USER_NEW);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.settings)) ){
    		selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_USER_OPTIONS);
    	} else if( tv.getText().toString().equalsIgnoreCase(getResources().getString(R.string.shealth_settings)) ){
			selectedItemBundle.putInt(SELECTED_MENU_ITEM, ITEM_USER_SHEALTH_OPTIONS);
		}
	}

    private void selectedMenuItemAction() {
    	int menuItem = ITEM_EMPTY;

    	if (selectedItemBundle != null) {
    		menuItem = selectedItemBundle.getInt(SELECTED_MENU_ITEM);
    		selectedItemBundle.clear();
    	}

    	Intent intent;

    	switch( menuItem ) {
    	case ITEM_MEASURE:
    		if(patientNameTV.getText().toString().trim().equals(getText(R.string.selectPatient))) {

	    		if(patients == null || patients.length == 0) {
	    			dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
	    		}
	    		else {
	    			viewMeasureBundle = new Bundle();
		    		viewMeasureBundle.putBoolean(GWConst.VIEW_MEASURE, true);
		    		viewMeasureBundle.putInt(GWConst.POSITION, -1);
		    		startSelectPatientActivity();
	    		}
	    	}
	    	else {
	    		Log.i(TAG, "Mostrare le misure di " + patientNameTV.getText().toString());
	    		intent = new Intent(DeviceList.this, ShowMeasure.class);
	    		intent.putExtra(GWConst.SHOW_MEASURE_TITLE, "ALL");
	    		startActivity(intent);
	    	}
    		break;
    	case ITEM_USER_UPDATES:
    		runningConfig = true;
	    	runningConfigUpdate = true;
	    	//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
	    	dataBundle = new Bundle();
			dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgConf"));
			dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
			dataBundle.putBoolean(GWConst.IS_CONFIGURATION, true);
			loginET = null;
			pwdET = null;
			changeConnectionSettings(true);
    		break;
    	case ITEM_USER_LIST:
    		showUsers(USER_LIST);
    		break;
    	case ITEM_USER_NEW:
    		showDialog(LOGIN_DIALOG);
    		break;
    	case ITEM_USER_OPTIONS:
    		Intent intentSettingsUser = new Intent(DeviceList.this, ShowSettings.class);
			intentSettingsUser.putExtra("TYPE_SETTINGS", "USER");
	    	startActivity(intentSettingsUser);
    		break;
    	case ITEM_DEVICES_MANAGEMENT:
    		//Verifica che l'utente attivo sia quello di default
    		User activeUser = DbManager.getDbManager().getActiveUser();
    		if( activeUser == null ){
    			//Non ci sono utenti registrati, quindi crea l'utente di default
    			try {
					activeUser = DbManager.getDbManager().createDefaultUser();
				} catch (DbException e) {
					e.printStackTrace();
					showErrorDialog(ResourceManager.getResource().getString("errorDb"));
				}

    			intent = new Intent(DeviceList.this, DeviceSettingsActivity.class);
        		startActivity(intent);
    		} else if( activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) ) {
    			//� l'utente di default
    			intent = new Intent(DeviceList.this, DeviceSettingsActivity.class);
        		startActivity(intent);
    		}
    		break;
    	case ITEM_ABOUT:
    		showAboutDialog();
    		break;
    	case ITEM_LOGOUT:
    		doLogout();
    		break;
    	case ITEM_EXIT:
    		doRevertToDefaultConnectionSettings();
	    	finish();
    		break;

    		default:
    			break;
    	}
    }

    private List<? extends Map<String, ?>> createGroupList(ArrayList<String> iconGroup, ArrayList<String> labelGroup) {

		List<HashMap<String, String>> fillMaps = new ArrayList<>();
		for (int i=0; i<labelGroup.size(); i++) {
			HashMap<String, String> map = new HashMap<>();
			map.put(KEY_ICON, iconGroup.get(i));
			map.put(KEY_LABEL, labelGroup.get(i));
			fillMaps.add(map);
		}

		return fillMaps;
    }

	private ArrayList< List<? extends Map<String, ?>> > createChildList(ArrayList<ArrayList<String>> labelChild) {

		ArrayList<List<? extends Map<String, ?>>> fillMaps = new ArrayList<>();
		for( int i = 0 ; i < labelChild.size() ; ++i ) {

			List<HashMap<String, String>> itemChild = new ArrayList<>();

			for( int n = 0 ; n < labelChild.get(i).size() ; n++ ) {
				HashMap<String, String> map = new HashMap<>();
				map.put(KEY_LABEL, labelChild.get(i).get(n));
				itemChild.add(map);
			}

			fillMaps.add(itemChild);
		}

		return fillMaps;
	}
	/** FINE GESTIONE NAVIGATION DRAWER	 */

	private void initMeasureList() throws DbException {
		measureList = DbManager.getDbManager().getMeasureTypesForUser();
		Collections.sort(measureList, new Comparator<String>(){
			public int compare(String s1, String s2) {
				String measureText1 = ResourceManager.getResource().getString(
						"measureType." + s1);
				String measureText2 = ResourceManager.getResource().getString(
						"measureType." + s2);

				if (s1.equalsIgnoreCase(GWConst.KMsrLoc))
					return 1;

				return measureText1.compareTo(measureText2);
			}
		});
	}

	private void initDeviceMap() throws DbException {
		deviceMap = new HashMap<>();
		List<UserDevice> dList = DbManager.getDbManager().getCurrentUserDevices();
		for (UserDevice pDevice : dList) {
			UserDevice tmpDev = deviceMap.get(pDevice.getMeasure());
			if(tmpDev == null || pDevice.isActive()){
				deviceMap.put(pDevice.getMeasure(), pDevice);
			}
		}
	}

	private void initMeasureModelsMap(User currentUser) throws DbException {
		measureModelsMap = new HashMap<>();
		for (String measure : measureList) {
			List<UserDevice> modelList = DbManager.getDbManager().getModelsForMeasure(measure, currentUser.getId());
			measureModelsMap.put(measure, modelList);
		}
	}

	private void setupView() {
		if( isGrid ) {
			setupGridView();
		} else {
			setupListView();
		}
	}

	private void setupGridView() {
		// create the grid item mapping
		String[] from = new String[] { KEY_ICON, KEY_LABEL };
		int[] to = new int[] { R.id.grid_item_image, R.id.grid_item_label };

		fillMaps = new ArrayList<>();
		for (String measureType : measureList) {

			HashMap<String, String> map = setFieldsMap(measureType);
			fillMaps.add(map);
		}

		GridView gw = deviceGridFragment.getGridView();
		gw.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Log.i(TAG, "position: " + position);
                Log.i(TAG, "parent.getItemAtPosition: " + parent.getItemAtPosition(position));

                try {
                    initDeviceMap();
                } catch (DbException e) {
                }

                sentMeasures = 0;
                receivedMeasures = 0;
                if (!deviceManager.isOperationRunning() || measureList.get(position).equalsIgnoreCase(GWConst.KMsrAritm)) {
                    selectedMeasureType = measureList.get(position);
                    selectedMeasurePosition = position;
                    if (!deviceMap.get(selectedMeasureType).isActive()) {
                        showSelectModelDialog();
                    } else {
                        isAR = selectedMeasureType.equalsIgnoreCase(GWConst.KMsrAritm);
                        setCurrentDevice(position);
                        selectPatient();
                    }
                } else {
                    Log.i(TAG, "operation running: click ignored");
                    Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("KOperationRunning"), Toast.LENGTH_SHORT).show();
                }
            }
        });

		//Controlla la disposizione del display
		String orientation = getDisplayOrientation();
		if ( orientation.equalsIgnoreCase("portrait")) {
			//GRID PORTRAIT
			switch(measureList.size()) {
			case 1:
				gw.setNumColumns( 1 );
				break;
			case 2:
				gw.setNumColumns( 1 );
				break;
			case 3:
				gw.setNumColumns( 1 );
				break;
			default:
				gw.setNumColumns( 2 );
				break;
			}

			listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.device_grid_item, from, to, isGrid, true);
			gw.setAdapter( listAdapter );
		} else {
			//GRID LANDSCAPE
			switch(measureList.size()) {
			case 1:
				gw.setNumColumns( 1 );
			break;
			case 2:
				gw.setNumColumns( 2 );
				break;
			case 3:
				gw.setNumColumns( 3 );
				break;
			case 4:
				gw.setNumColumns( 2 );
				break;
			default:
				gw.setNumColumns( 3 );
			break;
			}

			listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.device_grid_item, from, to, isGrid, false);
			gw.setAdapter( listAdapter );
		}
	}

	private void setupListView() {
		// create the grid item mapping
		String[] from = new String[] { KEY_ICON, KEY_LABEL, KEY_MODEL };
		int[] to = new int[] { R.id.icon, R.id.label, R.id.model };

		fillMaps = new ArrayList<HashMap<String, String>>();
		for (String measureType : measureList) {
			HashMap<String, String> map = setFieldsMap(measureType);
			fillMaps.add(map);
		}

		//Controlla la disposizione del display
		String orientation = getDisplayOrientation();
		if ( orientation.equalsIgnoreCase("portrait"))
			listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.device_list_item, from, to, isGrid, true);
		else
			listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.device_list_item, from, to, isGrid, false);
		//setListAdapter(listAdapter);
		deviceListFragment.setListAdapter(listAdapter);

		ListView lv = deviceListFragment.getListView();
		lv.setDivider(null); //rimuove la linea di bordo
		lv.setCacheColorHint(Color.TRANSPARENT); //il background della lista non cambia colore durante lo scroll
		lv.setTextFilterEnabled(false);
		lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		lv.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.i(TAG, "position: "+position);
				Log.i(TAG, "parent.getItemAtPosition: "+parent.getItemAtPosition(position));

				try {
					initDeviceMap();
				} catch (DbException e) {
				}

				sentMeasures = 0;
				receivedMeasures = 0;
				if(!deviceManager.isOperationRunning() || measureList.get(position).equalsIgnoreCase(GWConst.KMsrAritm)){
					selectedMeasureType = measureList.get(position);
					selectedMeasurePosition = position;
					if(!deviceMap.get(selectedMeasureType).isActive()){
						showSelectModelDialog();
					} else {
						isAR = selectedMeasureType.equalsIgnoreCase(GWConst.KMsrAritm);
						setCurrentDevice(position);
						selectPatient();
					}
				} else {
					Log.i(TAG, "operation running: click ignored");
					Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("KOperationRunning"), Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	private void selectPatient() {
		User currentUser = UserManager.getUserManager().getCurrentUser();
		if(patientNameTV.getText().toString().trim().equals(getText(R.string.selectPatient))) {

			patientList = DbManager.getDbManager().getUserPatients(currentUser.getId());
			if (patientList != null && patientList.size() != 1) {
				patients = new String[patientList.size()];
				int counter = 0;
				for (UserPatient up : patientList) {
					Patient p;

					p = DbManager.getDbManager().getPatientData(up.getIdPatient());
					patients[counter++] = "[" + p.getId() + "] " + p.getSurname() + " " + p.getName();
				}
			}

			if (patients == null || patients.length == 0) {
				dataBundle = new Bundle();
				dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
				showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
			}
			else {
				startMeasureBundle = new Bundle();
				startMeasureBundle.putBoolean(GWConst.START_MEASURE, true);
				startSelectPatientActivity();
			}
		}
		else {
			if(measureEnabled(deviceMap.get(selectedMeasureType))){

				if(isAR){
					//Chiedo conferma per stop server
					AlertDialog.Builder builder = new AlertDialog.Builder(DeviceList.this);
					builder.setMessage(ResourceManager.getResource().getString("KConfirmStopStm"));
					builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							stopDeviceOperation(-1);
							refreshList();

							//Per essere sicuro che venga ripristinato l'APN di default del dispositivo
							ConnectionManager.getConnectionManager(DeviceList.this).resetDefaultConnection();

							isAR = false;
						}
					});
					builder.setNegativeButton(ResourceManager.getResource().getString("EGwnurseCancel"), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						}
					});
					builder.setTitle(ResourceManager.getResource().getString("KTitleStm"));
					builder.show();

				} else {

					deviceManager.checkIfAnotherSpirodocIsPaired(deviceManager.getCurrentDevice().getDevice().getModel());
					try {
						initDeviceMap();
					} catch (DbException e) {
					}
					doMeasure();
				}
			} else {

				if (deviceManager.getCurrentDevice().getDevice().getModel().equals(GWConst.KSpirodocOS) ||
						deviceManager.getCurrentDevice().getDevice().getModel().equals(GWConst.KSpirodocSP)) {

					if (deviceManager.checkIfAnotherSpirodocIsPaired(deviceManager.getCurrentDevice().getDevice().getModel())) {
						try {
							initDeviceMap();
						} catch (DbException e) {
						}
						doMeasure();
					}
					else {
						doScan();
					}

				}
				else {
					doScan();
				}
			}
		}
	}

	private HashMap<String, String> setFieldsMap(String measureType) {
		HashMap<String, String> map = new HashMap<String, String>();
		if(deviceManager.isOperationRunning() && !isConfig && !isPairing){
			map.put(KEY_ICON, "" + Util.getIconRunningId(measureType));
		} else {
			map.put(KEY_ICON, "" + Util.getIconId(measureType));
		}
		map.put(KEY_LABEL, setupFeedback(ResourceManager.getResource().getString("measureType." + measureType)));
		UserDevice pd = deviceMap.get(measureType);
		if(pd.isActive()){
			map.put(KEY_MODEL, pd.getDevice().getDescription());
		} else {
			map.put(KEY_MODEL, getString(R.string.selectDevice));
		}
		return map;
	}

	private String setupFeedback(String measureLabel) {
		String label = measureLabel;
		if(isAR && deviceManager.isOperationRunning() && !isConfig && !isPairing){
			label = measureLabel.concat(" (" + sentMeasures + "/" + receivedMeasures + ") ");
		}
		return label;
	}

	private void fitTextInPatientNameLabel(String text) {
		patientNameTV.setText("  " + text + "  ");
	}

	private void setCurrentUserLabel(String username) {
		if(username.equals("")) {
			fitTextInPatientNameLabel(ResourceManager.getResource().getString("selectPatient"));
		}
		else {
			fitTextInPatientNameLabel(username);
		}
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuInflater menuInflater = getMenuInflater();
		menuInflater.inflate(R.menu.device_list_menu, menu);

		mActionBarMenu = menu;

		User activeUser = DbManager.getDbManager().getActiveUser();

		if( activeUser == null || activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) ){
			MenuItemCompat.setShowAsAction(mActionBarMenu.findItem(R.id.action_bar_menu), MenuItemCompat.SHOW_AS_ACTION_NEVER);
			mActionBarMenu.findItem(R.id.action_bar_menu).setVisible(false);
		} else {
			mActionBarMenu.findItem(R.id.action_bar_menu).setVisible(true);

			if(isGrid){
				mActionBarMenu.findItem(R.id.action_bar_menu).setIcon(R.drawable.collections_view_as_list);
			} else {
				mActionBarMenu.findItem(R.id.action_bar_menu).setIcon(R.drawable.collections_view_as_grid);
			}
		}

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {

		if(deviceManager.isOperationRunning()){
			super.onCreateContextMenu(menu, v, menuInfo);
			return;
		}

		boolean isGlucoTelDevice = false;

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		selectedMeasureType = measureList.get(info.position);
		selectedMeasurePosition = info.position;
		try {
			initDeviceMap();
			UserDevice pd = deviceMap.get(selectedMeasureType);

			//MenuInflater inflater = getSupportMenuInflater();
			android.view.MenuInflater inflater = getMenuInflater();

			//Menu per l'inserimento manuale delle misure
			if (Util.isManualMeasure(pd.getDevice())) {
				//inflater.inflate(R.menu.context_menu_manual_insert, (Menu) menu);
				inflater.inflate(R.menu.context_menu_manual_insert, menu);
				menu.setHeaderTitle(ResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(selectedMeasureType, userManager.getCurrentUser().getId(), userManager.getCurrentPatient().getId());
					if (patientMeasures == null || patientMeasures.size() == 0) {
						//All'esame non � associata alcuna misura
						menu.setGroupVisible(R.id.show_measure_group, false);
					}
				}
				else
					menu.setGroupVisible(R.id.show_measure_group, false);

				if(measureModelsMap.get(selectedMeasureType).size() == 1){
					//Un solo modello di device disponibile
					menu.setGroupVisible(R.id.select_model_group, false);
				}
			}
			//Menu per i dispositivi che non richiedono una procedura di pairing
			//ECG, INR, OSSIMETRO NONIN
			else if (Util.isNoPairingDevice(pd.getDevice())) {
				//inflater.inflate(R.menu.context_menu_no_pair_device, (Menu) menu);
				inflater.inflate(R.menu.context_menu_no_pair_device, menu);
				menu.setHeaderTitle(ResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(selectedMeasureType, userManager.getCurrentUser().getId(), userManager.getCurrentPatient().getId());
					if (patientMeasures == null || patientMeasures.size() == 0) {
						//All'esame non � associata alcuna misura
						menu.setGroupVisible(R.id.show_measure_group, false);
					}
				}
				else
					menu.setGroupVisible(R.id.show_measure_group, false);

				if(measureModelsMap.get(selectedMeasureType).size() == 1){
					//Un solo modello di device disponibile
					menu.setGroupVisible(R.id.select_model_group, false);
				}

				if(measureEnabled(pd)){
					Log.i(TAG, "Gi� associato");
					menu.setGroupVisible(R.id.new_device_first_run_group, false);
				} else {
					Log.i(TAG, "Prima associazione");
					menu.setGroupVisible(R.id.new_device_group, false);
				}

				if (Util.isIEMECGDevice(pd.getDevice())) {
					menu.setGroupVisible(R.id.new_device_first_run_group, false);
					menu.setGroupVisible(R.id.new_device_group, false);
				}

				if (pd.getDevice().getModel().equals(GWConst.KPO3IHealth)
						|| pd.getDevice().getModel().equals(GWConst.KBP5IHealth)
						|| pd.getDevice().getModel().equals(GWConst.KHS4SIHealth)) {
					menu.setGroupVisible(R.id.new_device_first_run_group, true);
					menu.setGroupVisible(R.id.new_device_group, false);
				}

				if(!pd.isActive()){
					menu.setGroupVisible(R.id.new_device_group, false);
					menu.setGroupVisible(R.id.new_device_first_run_group, false);
				}
			}
			//Menu per i dispositivi MIR
			else if ((isGlucoTelDevice = Util.isGlucoTelDevice(pd.getDevice()))) {

				if (isGlucoTelDevice){
					//inflater.inflate(R.menu.context_menu_glucotel_device, (Menu) menu);
					inflater.inflate(R.menu.context_menu_glucotel_device, menu);
					String itemText = menu.findItem(R.id.calibrate).getTitle()
							.toString()
							+ " " + Util.getCurrentCalibrationCode();
					menu.findItem(R.id.calibrate).setTitle(itemText);
				} else {

					if ((pd.getDevice().getModel() != null) && (pd.getDevice().getModel().equals(GWConst.KSpirodocOS) ||
							pd.getDevice().getModel().equals(GWConst.KSpirodocSP))) {
						//inflater.inflate(R.menu.context_menu_mir_spirodoc_device, (Menu) menu);
						inflater.inflate(R.menu.context_menu_mir_spirodoc_device, menu);
					}
					else {
						//inflater.inflate(R.menu.context_menu_mir_device, (Menu) menu);
						inflater.inflate(R.menu.context_menu_mir_device, menu);
					}
				}

				menu.setHeaderTitle(ResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(selectedMeasureType, userManager.getCurrentUser().getId(), userManager.getCurrentPatient().getId());
					if (patientMeasures == null || patientMeasures.size() == 0) {
						//All'esame non � associata alcuna misura
						menu.setGroupVisible(R.id.show_measure_group, false);
					}
				}
				else
					menu.setGroupVisible(R.id.show_measure_group, false);

				if(measureModelsMap.get(selectedMeasureType).size() == 1){
					//Un solo modello di device disponibile
					menu.setGroupVisible(R.id.select_model_group, false);
				}

				if(!pd.isActive()){
					menu.setGroupVisible(R.id.config_group, false);
				}
			}
			else {
				//inflater.inflate(R.menu.context_menu_pair_device, (Menu) menu);
				inflater.inflate(R.menu.context_menu_pair_device, menu);
				menu.setHeaderTitle(ResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(selectedMeasureType, userManager.getCurrentUser().getId(), userManager.getCurrentPatient().getId());
					if (patientMeasures == null || patientMeasures.size() == 0) {
						//All'esame non � associata alcuna misura
						menu.setGroupVisible(R.id.show_measure_group, false);
					}
				}
				else
					menu.setGroupVisible(R.id.show_measure_group, false);

				if(measureModelsMap.get(selectedMeasureType).size() == 1){
					//Un solo modello di device disponibile
					menu.setGroupVisible(R.id.select_model_group, false);
				}

				if(measureEnabled(pd)){
					menu.setGroupVisible(R.id.pair_group, false);
				} else {
					menu.setGroupVisible(R.id.new_device_group, false);
				}

				if(!pd.isActive()){
					menu.setGroupVisible(R.id.pair_group, false);
					menu.setGroupVisible(R.id.new_device_group, false);
					menu.setGroupVisible(R.id.new_device_first_run_group, false);
				}

				if (pd.getDevice().getModel().equalsIgnoreCase(GWConst.KSTM) &&
						(pd.getBtAddress() != null)) {
					menu.setGroupVisible(R.id.config_group1, true);
					menu.setGroupVisible(R.id.config_group2, true);
					menu.setGroupVisible(R.id.new_device_group, false);
				}

				if ((pd.getDevice().getModel().equalsIgnoreCase(GWConst.KZEPHYR))&&
						(pd.getBtAddress() != null)) {
					menu.setGroupVisible(R.id.pair_group, true);
					menu.setGroupVisible(R.id.config_group3, true);
					menu.setGroupVisible(R.id.new_device_group, false);
				}
			}




			if (pd.getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA)) {
				menu.setGroupVisible(R.id.pair_group, false);
				menu.setGroupVisible(R.id.new_device_group, false);
				menu.setGroupVisible(R.id.new_device_first_run_group, false);
			}
		} catch (DbException e) {
			e.printStackTrace();
			showErrorDialog(ResourceManager.getResource().getString("errorDb"));
		}
	}

	@Override
	public void onBackPressed() {

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode == KeyEvent.KEYCODE_SEARCH || super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		// Opening submenu in action bar on Hardware menu button click
		if(event.getAction() == KeyEvent.ACTION_UP){
		    switch(keyCode) {
		    case KeyEvent.KEYCODE_MENU:
		    	//mActionBarMenu.performIdentifierAction(R.id.mi_action_bar_menu_overflow, 0);
		    	if ( this.mDrawerLayout.isDrawerOpen(this.mMenuDrawerList)) {
	                this.mDrawerLayout.closeDrawer(this.mMenuDrawerList);
	            }
	            else {
	                this.mDrawerLayout.openDrawer(this.mMenuDrawerList);
	            }

		        return true;
		    }
		}
		return super.onKeyUp(keyCode, event);
	}


	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		// Handle item selection
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		if( isGrid ) {
			//deviceGridFragment.getListView().setSelection(info.position);
			deviceGridFragment.getGridView().setSelection(info.position);
		} else {
			deviceListFragment.getListView().setSelection(info.position);
		}

    	setCurrentDevice(info.position);

	    switch (item.getItemId()) {
		    case R.id.pair:

		    	if(Util.isGlucoTelDevice(deviceMap.get(selectedMeasureType).getDevice()) && Util.glucoTelNotCalibrated()){
		    		showCalibrateActivity(false, true);
				}
		    	else {
		    		doScan();
		    	}
		    	return true;
		    case R.id.calibrate:
		    	showCalibrateActivity(false, false);
		    	return true;
		    case R.id.config:
		    	doConfig();
		    	return true;
		    case R.id.reliability_menu:
		    	doAlternativeConfig();
		    	return true;
		    case R.id.new_device_adv:
		    case R.id.new_device:
			case R.id.new_device_only_association:
		    	doNewDevice();
		    	return true;
		    case R.id.show_measure:
		    	if(patientNameTV.getText().toString().trim().equals(getText(R.string.selectPatient))) {

		    		if (patients == null || patients.length == 0) {
		    			dataBundle = new Bundle();
						dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
						showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
		    		}
		    		else {
		    			viewMeasureBundle = new Bundle();
			    		viewMeasureBundle.putBoolean(GWConst.VIEW_MEASURE, true);
			    		viewMeasureBundle.putInt(GWConst.POSITION, info.position);
			    		startSelectPatientActivity();
		    		}
		    	}
		    	else
		    		showMeasures(info.position);
		    	return true;
		    case R.id.select_model:
		    	showSelectModelDialog();
		    	return true;

		    case R.id.zephyr_config:

		    	/*
		    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
		    	builder.setTitle(ResourceManager.getResource().getString("MainGUI.configWarningTitle"));
				builder.setMessage(ResourceManager.getResource().getString("MainGUI.configWarningMsg"));
				builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"),
						new DialogInterface.OnClickListener(){
							public void onClick(DialogInterface arg0,
									int arg1) {

							}
				});
				builder.show();
				*/

		    	Intent intent = new Intent(DeviceList.this, ShowSettings.class);
				intent.putExtra("TYPE_SETTINGS", "ZEPHYR");
		    	startActivity(intent);

		    	return true;

		    case R.id.advance_options:
		    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
		    	builder.setTitle(ResourceManager.getResource().getString("MainGUI.configWarningTitle"));
				builder.setMessage(ResourceManager.getResource().getString("MainGUI.configWarningMsg"));
				builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"),
						new DialogInterface.OnClickListener(){
							public void onClick(DialogInterface arg0,
									int arg1) {
								Intent intent = new Intent(DeviceList.this, ShowSettings.class);
								intent.putExtra("TYPE_SETTINGS", "STM");
						    	startActivity(intent);
							}
				});
				builder.show();
		    	return true;

		    default:
		        return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
		if (mActionBarDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch(item.getItemId()) {

		case R.id.action_bar_menu:
			changeLayoutToFragmentListView();
			return true;

		default:
				return super.onOptionsItemSelected(item);
		}

	}

	/**
	 * Modifica il layout della FragmentList da list a grid o viceversa
	 */
	private void changeLayoutToFragmentListView() {
		FragmentTransaction transaction = fragmentManager.beginTransaction();

		if( isGrid ) {
			isGrid = false;

			//Modifica icona menu ActionBar
			mActionBarMenu.findItem(R.id.action_bar_menu).setIcon(R.drawable.collections_view_as_grid);
			//Cambiare il layout da grid a list
			deviceListFragment = new DeviceListFragment();
			deviceListFragment.setDeviceListFragmentListener(this);
			isFragmentCreatedInSwitch = true;

			// Rimpiazza il fragment
			transaction.replace(R.id.device_list_content_frame, deviceListFragment);

			// Effettua la transazione
			transaction.commit();

		} else {
			isGrid = true;

			//Modifica icona menu ActionBar
			mActionBarMenu.findItem(R.id.action_bar_menu).setIcon(R.drawable.collections_view_as_list);
			//Cambiare il layout da list a grid
			deviceGridFragment = new DeviceGridFragment();
			deviceGridFragment.setDeviceListFragmentListener(this);
			isFragmentCreatedInSwitch = true;

			// Rimpiazza il fragment
			transaction.replace(R.id.device_list_content_frame, deviceGridFragment);

			// Effettua la transazione
			transaction.commit();
		}

		Util.setRegistryValue(Util.KEY_GRID_LAYOUT, Boolean.toString(isGrid));
	}

	/**
	 * Verifica l'orientamento del dispositivo
	 */
	private String getDisplayOrientation() {
		WindowManager wm = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);
		// SDK_INT = 1;
		int widthPixels = metrics.widthPixels;
		int heightPixels = metrics.heightPixels;

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

		//LANDSCAPE PORTRAIT
		if( widthPixels > heightPixels ) {
			return "landscape";
		} else {
			return "portrait";
		}
	}

	private void doLogout() {

		Log.d(TAG, "doLogout()");

		try {
			//Nasconde l'elemento per la scelta del paziente
			currentPatientLL.setVisibility(View.GONE);
			//Svuota la lista
			listAdapter.clearData();
			listAdapter.notifyDataSetChanged();
			//Nasconde l'icona dell'Action Bar Menu
			mActionBarMenu.findItem(R.id.action_bar_menu).setVisible(false);
			MenuItemCompat.setShowAsAction(mActionBarMenu.findItem(R.id.action_bar_menu), MenuItemCompat.SHOW_AS_ACTION_NEVER);
			//Cambia titolo nella Action Bar
			titleTV.setText(R.string.app_name);
		}
		catch (Exception e) {
			Log.e(TAG, "doLogout()", e);
		}

		//Rimuove paziente e utenti
		UserManager.getUserManager().setCurrentPatient(null);
		User activeUser = DbManager.getDbManager().getActiveUser();
		if (activeUser != null) {
			if( !activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) )
				Util.setRegistryValue(Util.KEY_LAST_USER, activeUser.getId());

			/*if (!activeUser.getHasAutoLogin()) {
				DbManager.getDbManager().removeAllActiveUsers();
			}*/
			synchronized (this) {
				DbManager.getDbManager().removeAllActiveUsers();
			}

		}
	}

	private void doScan() {
		// If BT is not on, request that it be enabled.
		// startScan() will then be called during onActivityResult
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			isPairing = true;
			isConfig = false;
			isManualMeasure = false;
		    requestEnableBT();
		} else {
			startScan();
		}
	}

	private void doNewDevice() {
		// If BT is not on, request that it be enabled.
		// startNewDevice() will then be called during onActivityResult
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			isPairing = true;
			isConfig = false;
			isManualMeasure = false;
		    requestEnableBT();
		} else {
			startNewDevice();
		}
	}

	private void doMeasure() {
		UserDevice uDevice = deviceMap.get(selectedMeasureType);
		if(uDevice != null && measureEnabled(uDevice)){
			if(Util.isGlucoTelDevice(uDevice.getDevice()) && Util.glucoTelNotCalibrated()){
				showCalibrateActivity(true, false);
			} else if(Util.isManualMeasure(uDevice.getDevice())){
				doManualMeasure();
			} else {
				isPairing = false;
				isConfig = false;
				isManualMeasure = false;

				if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
					// If BT is not on, request that it be enabled.
					// startMeasure() will then be called during onActivityResult
					requestEnableBT();
				} else {

					if(Util.isGlucoTelDevice(uDevice.getDevice())) {

						if (uDevice.getBtAddress() == null)
							doScan();
						else
							startMeasure();
					}
					else {
						startMeasure();
					}
				}
			}
		}
	}

	private void doConfig() {
		isPairing = false;
		isConfig = true;
		isManualMeasure = false;
		//requestDiscoverability();
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			// If BT is not on, request that it be enabled.
			// startConfig() will then be called during onActivityResult
			requestEnableBT();
		} else {
			startConfig();
		}
	}

	private void doAlternativeConfig() {
		isPairing = false;
		isConfig = true;
		isManualMeasure = true;
		//requestDiscoverability();
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			// If BT is not on, request that it be enabled.
			// startConfig() will then be called during onActivityResult
			requestEnableBT();
		} else {
			startAlternativeConfig();
		}
	}

	private void startScan() {
		if (!Util.isGlucoTelDevice(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isC40(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isCamera(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isGearFitDevice(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isSHealthDevice(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isGoogleFitDevice(deviceManager.getCurrentDevice().getDevice())
				) {
			// Launch the DeviceScanActivity to see devices and do scan
			Intent serverIntent = new Intent(this, DeviceScanActivity.class);
			//startActivity(serverIntent);
			startActivityForResult(serverIntent, REQUEST_SCAN_DEVICES);
		} else {
			deviceManager.startDiscovery(null);
		}
	}

	private void startNewDevice() {
		//Occorre annullare il BT address per forzare lo scouting dei dispositivi
		UserDevice ud = deviceMap.get(selectedMeasureType);
		UserDevice tmpUd = (UserDevice) ud.clone();
		tmpUd.setBtAddress(null);
		deviceManager.setCurrentDevice(tmpUd);
		startScan();
	}

	private void showMeasures(int position) {
		Intent myIntent = new Intent(DeviceList.this, ShowMeasure.class);
		if(viewMeasureBundle != null && viewMeasureBundle.getInt(GWConst.POSITION) == -1)
			myIntent.putExtra(GWConst.SHOW_MEASURE_TITLE, "ALL");
		else {
			myIntent.putExtra(GWConst.SHOW_MEASURE_TITLE, ResourceManager.getResource().getString("measureType." + measureList.get(position)));
			myIntent.putExtra(GWConst.SELECTED_MEASURE, measureList.get(position));
		}
		startActivity(myIntent);
	}

	private void showUsers(int intentMode) {
		Intent myIntent = new Intent(this, UsersList.class);
		if( intentMode == USER_SELECTION ) {
			//Richiesta elenco utenti per nuova login
			//Disabilito la funzionalit� di cancellazione utente
			//nella activity UserList
			myIntent.putExtra(GWConst.ENABLE_DELETE_USER_ID, 0);
		} else {
			myIntent.putExtra(GWConst.ENABLE_DELETE_USER_ID, 1);
		}
		startActivityForResult(myIntent, intentMode);
	}

	public void showUserDialog(/*View button*/) {
		removeDialog(PRECOMPILED_LOGIN_DIALOG);
		removeDialog(LOGIN_DIALOG);
		showUsers(USER_SELECTION);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		isForcedRelogin = false;

		Log.d(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

    	switch(requestCode) {

    	case USER_LIST:

    		if(resultCode == RESULT_OK){
	    		if(data != null){
	    			Bundle extras = data.getExtras();
	    			User user = (User) extras.get(UsersList.SELECTED_USER);
	    			Log.i(TAG, "Login utente " + user.getName() + " da db");


	    			if(!user.getHasAutoLogin() ||  Util.getRegistryValue(Util.KEY_FORCE_LOGOUT + user.getId(), false)) {

	    				isForcedRelogin = true;
	    				userDataBundle = new Bundle();
	    				userDataBundle.putBoolean("CHANGEABLE", false);
	    				userDataBundle.putString("LOGIN", user.getLogin());

	    				showDialog(PRECOMPILED_LOGIN_DIALOG);
	    			}
	    			else
	    				userManager.selectUser(user);
	    		} else {
	    			showDialog(LOGIN_DIALOG);
	    		}
    		} else if(resultCode == UsersList.RESULT_DB_ERROR){
    			showErrorDialog(ResourceManager.getResource().getString("errorDb"));
    		}
    	    break;

    	case USER_SELECTION:
    		if(resultCode == RESULT_OK){
	    		if(data != null){
	    			Bundle extras = data.getExtras();
	    			User user = (User) extras.get(UsersList.SELECTED_USER);
					if (user != null) {
						Log.i(TAG, "Login utente " + user.getName() + " da db");

						if (!user.getHasAutoLogin() || Util.getRegistryValue(Util.KEY_FORCE_LOGOUT + user.getId(), false)) {

							isForcedRelogin = true;
							userDataBundle = new Bundle();
							userDataBundle.putBoolean("CHANGEABLE", false);
							userDataBundle.putString("LOGIN", user.getLogin());

							showDialog(PRECOMPILED_LOGIN_DIALOG);
						}
					}
	    			else
	    				userManager.selectUser(user);
	    		} else {
	    			showDialog(LOGIN_DIALOG);
	    		}
    		} else if(resultCode == UsersList.RESULT_DB_ERROR){
    			showErrorDialog(ResourceManager.getResource().getString("errorDb"));
    		} else {
    			showDialog(PRECOMPILED_LOGIN_DIALOG);
    		}
    		break;
    	case PATIENT_SELECTION:
    		if (resultCode == RESULT_OK) {
    			if (data != null) {
    				Bundle extras = data.getExtras();
    				String patientName = extras.getString(GWConst.PATIENT);
    				String patientID = extras.getString(GWConst.PATIENT_ID);
    				fitTextInPatientNameLabel(patientName);

					Patient p = DbManager.getDbManager().getPatientData(patientID);
					Log.i(TAG, "Selezionato il paziente " + p.getName() + " " + p.getSurname());
					UserManager.getUserManager().setCurrentPatient(p);
    				if(viewMeasureBundle != null && viewMeasureBundle.getBoolean(GWConst.VIEW_MEASURE, false)) {
						Log.d(TAG, "Visualizzo le misure di " + p.getName() + " " + p.getSurname());
						showMeasures(viewMeasureBundle.getInt(GWConst.POSITION));
					}
					else if (startMeasureBundle != null && startMeasureBundle.getBoolean(GWConst.START_MEASURE, false)) {
						Log.d(TAG, "Inizio la misura di " + p.getName() + " " + p.getSurname());
						if(measureEnabled(deviceMap.get(selectedMeasureType))){
							doMeasure();
						} else {
							doScan();
						}
					}
    				/*
					else {
						checkAutoUpdate();
					}
					*/
    			}
    		} /*
    		else {
    			checkAutoUpdate();
    		}*/
    		break;
    	case PATIENT_SELECTION_2:
    		if (resultCode == RESULT_OK) {
    			if (data != null) {
    				Bundle extras = data.getExtras();
    				String patientName = extras.getString(GWConst.PATIENT);
    				String patientID = extras.getString(GWConst.PATIENT_ID);
    				fitTextInPatientNameLabel(patientName);

					Patient p = DbManager.getDbManager().getPatientData(patientID);
					Log.i(TAG, "Selezionato il paziente " + p.getName() + " " + p.getSurname());
					UserManager.getUserManager().setCurrentPatient(p);
					XmlManager.getXmlManager().setIdUser(UserManager.getUserManager().getCurrentPatient().getId());
    			}
    		}
    		showDialog(MEASURE_RESULT_DIALOG);
    		break;

    	case PATIENT_SELECTION_SEND_ALL:
    		if (resultCode == RESULT_OK) {
    			if (data != null) {
    				Bundle extras = data.getExtras();
    				String patientName = extras.getString(GWConst.PATIENT);
    				String patientID = extras.getString(GWConst.PATIENT_ID);
    				fitTextInPatientNameLabel(patientName);

					Patient p = DbManager.getDbManager().getPatientData(patientID);
					Log.i(TAG, "Selezionato il paziente " + p.getName() + " " + p.getSurname() + " patientID=" + patientID + " p=" + p.getId());
					UserManager.getUserManager().setCurrentPatient(p);
					//XmlManager.getXmlManager().setIdUserPatientId(UserManager.getUserManager().getCurrentPatient().getId());
    			}
    		}
    		showDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
    		break;

    	case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
            	if(isPairing){
            		startScan();
            	} else if(isConfig){
            		startConfig();
            	} else if(isManualMeasure){
            		startManualMeasure();
            	} else {
            		startMeasure();
            	}
            } else {
            	// User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled, Toast.LENGTH_SHORT).show();
            }
            break;
    	case REQUEST_DISCOVERABLE:
    		if (resultCode == DISCOVERABLE_DURATION) {
    			if(!deviceManager.isOperationRunning()){
	            	if(isConfig){
	            		startConfig();
	            	} else {
	            		startMeasure();
	            	}
    			} else {
    				Log.i(TAG, "OPERATION ALREADY RUNNING");
    			}
            } else {
            	// User did not enable Bluetooth discoverability
                Log.i(TAG, "BT discoverability not enabled by user");
                if(deviceManager.isOperationRunning()){
                	Log.i(TAG, "Stop running device operation");
                	closeProgressDialog();
                	stopDeviceOperation(-1);
                }
            }
    		break;
    	case REQUEST_SCAN_DEVICES:
    		if (resultCode == Activity.RESULT_OK){
    			int position = data.getExtras().getInt(DeviceScanActivity.SELECTED_DEVICE_POSITION);
    			stopDeviceOperation(position);
    		} else {
    			stopDeviceOperation(-2);
    		}
    		break;

    	case MANUAL_MEASURE_ENTRY:
    		if(resultCode == RESULT_OK){

    	         String pkg = getPackageName();
    	         String measure = data.getExtras().getString( pkg + ".measure" );
    	         ArrayList<String> values = data.getStringArrayListExtra( pkg + ".values" );
    	         int battery = data.getExtras().getInt( pkg + ".battery" );

    	         sendManualMeasure(measure, values, battery);
    	     }
    	     if (resultCode == RESULT_CANCELED) {
    	    	 cancelManualMeasure();
    	     }
    		 break;

    	case CALIBRATE_ENTRY:
    		if(resultCode == RESULT_OK) {
    			switch( calibrateState ) {
    			case EMeasureOn_ScanOn:
    				doMeasure();
    				doScan();
    				break;
    			case EMeasureOn_ScanOff:
    				doMeasure();
    				break;
    			case EMeasureOff_ScanOn:
    				doScan();
    				break;
    			case EIdle:
    			case EMeasureOff_ScanOff:
    				break;
    			}
	   	     }
	   		 break;
    	}
	}

	private void resetView() {
		DbManager.getDbManager().removeAllActiveUsers();

		titleTV.setText(R.string.app_name);

		if (fillMaps != null)
			fillMaps.clear();
		fitTextInPatientNameLabel(ResourceManager.getResource().getString("selectPatient"));
		if (listAdapter != null)
			listAdapter.notifyDataSetChanged();
	}

	private Dialog createAlertDialog(Bundle data) {
    	String msg = data.getString(GWConst.MESSAGE);
		return createAlert(msg, null);
	}

    private AlertDialog createAlert(String msg, String title) {

    	Log.d(TAG, "createAlert() msg=" + msg);

    	AlertDialog.Builder builder = new AlertDialog.Builder(DeviceList.this);
		builder.setMessage(msg);
		builder.setPositiveButton("Ok", new AlertDialogClickListener());
		builder.setTitle(title);
		beep();
		return builder.create();
	}

    private void showSelectModelDialog() {
		final List<UserDevice> userDevices = measureModelsMap.get(selectedMeasureType);

		final HashMap<Integer, Integer> mapPosition = new HashMap<>();
 		List<String> nal = new ArrayList<String>();

 		int deviceSelectedIndex = -1;

 		int p = 0;
 		for (int i = 0; i < userDevices.size(); i++) {
			//Aggiunge l'elemento alla lista di scelta
			nal.add(userDevices.get(i).getDevice().getDescription());
			mapPosition.put(p, i);
			p++;

			//Individua il dispositivo scelto in precedenza
			if( userDevices.get(i).isActive() ) {
				deviceSelectedIndex = i;
			}
		}

		final CharSequence[] items = new CharSequence[nal.size()];
		for (int i = 0; i < items.length; i++) {
			items[i] = nal.get(i);
		}

		selectedModelPosition = -1;

		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle(R.string.select_model);
		builder.setIcon(Util.getSmallIconId(selectedMeasureType));

		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {

				if (selectedModelPosition != -1) {
					UserDevice selectedUserDevice = userDevices.get(selectedModelPosition);

			    	for (int i = 0; i < userDevices.size(); i++) {
			    		userDevices.get(i).setActive(false);
					}
			    	selectedUserDevice.setActive(true);
			    	deviceMap.put(selectedMeasureType, selectedUserDevice);
			    	//DbManager.getDbManager().updateUserDeviceModel(selectedMeasureType, selectedUserDevice.getDevice().getId());

			    	try {
			    		DbManager.getDbManager().updateUserDeviceModel(selectedMeasureType, selectedUserDevice.getDevice().getId());
					} catch (DbException e) {
					}

			    	refreshList();

					dialog.dismiss();
				}
			}
		});

		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});

		builder.setSingleChoiceItems(items, deviceSelectedIndex, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {

		    	selectedModelPosition = mapPosition.get(item).intValue();
		    	((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
		    }
		});

		final AlertDialog selectModelDialog = builder.create();
		selectModelDialog.show();
		if( deviceSelectedIndex == -1 ){
			Button b = selectModelDialog.getButton(DialogInterface.BUTTON_POSITIVE);
			b.setEnabled(false);
		}
	}

	private Dialog createProgressDialog(Bundle data) {
		progressDialog = new ProgressDialog(this);

		String msg = data.getString( GWConst.MESSAGE );
		Log.d(TAG, "createProgressDialog msg=" + msg);

		if( msg.startsWith(WebSocket.FILE_TRANSFER) || msg.equalsIgnoreCase(ResourceManager.getResource().getString("KMsgTrasfImg")) ) {
			int value = 0;

			try {
				value = Integer.parseInt(msg.substring(WebSocket.FILE_TRANSFER.length()));
			}
			catch (Exception e) {
				value = 0;
			}

			Log.d(TAG, "createProgressDialog setProgress=" + value);
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.setIndeterminate(false);
			progressDialog.setCancelable(false);
			progressDialog.setMax(100);
			progressDialog.setProgress(value);

			if (value == 0)
				progressDialog.setMessage(data.getString(GWConst.MESSAGE));
		} else {
			progressDialog.setIndeterminate(true);
		}

		progressDialog.setCancelable(false);

		progressDialog.setMessage(data.getString(GWConst.MESSAGE));
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, ResourceManager.getResource().getString("EGwnurseCancel"),  new ProgressDialogClickListener());

		if(deviceMap != null && selectedMeasureType != null &&
				(deviceMap.get(selectedMeasureType) != null && Util.isStmDevice(deviceMap.get(selectedMeasureType).getDevice())) &&
				!isPairing && isConfig && isManualMeasure) {

			progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, ResourceManager.getResource().getString("DeviceListView.configureBtn"),
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							stopDeviceOperation(-1);
							doConfig();
						}
					});
		}

		progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				return true;
			}
		});
    	return progressDialog;
	}

	private OnClickListener patientNameLabelClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {

			User currentUser = DbManager.getDbManager().getActiveUser();
			if(currentUser != null) {
				Log.d(TAG, "Utente corrente: " + currentUser.getName() + " " + currentUser.getSurname());
				patientList = DbManager.getDbManager().getUserPatients(currentUser.getId());
				if (patientList == null) {
					dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
				}
				else if (patientList.size() != 1) {
					patients = new String[patientList.size()];
					int counter = 0;
					for (UserPatient up : patientList) {
						Patient p;

						p = DbManager.getDbManager().getPatientData(up.getIdPatient());
						patients[counter++] = "[" + p.getId() + "] " + p.getSurname() + " " + p.getName();
					}

					viewMeasureBundle = null;
					startMeasureBundle = null;

					if(patients == null || patients.length == 0) {
						dataBundle = new Bundle();
						dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
						showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
					}
					else {
						startSelectPatientActivity();
					}
				}
			}
		}
	};

	private void startSelectPatientActivity() {

		Log.d(TAG, "startSelectPatientActivity()");

		User currentUser = DbManager.getDbManager().getActiveUser();
		Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
		selectPatientIntent.putExtra(GWConst.USER_ID, currentUser.getId());
		startActivityForResult(selectPatientIntent, PATIENT_SELECTION);
	}

	private String getMeasureMessage(Bundle data) {
		ArrayList<String> labels = data.getStringArrayList(DeviceManager.LABELS);
		ArrayList<String> values = data.getStringArrayList(DeviceManager.VALUES);
		String msg = "";

		if (labels != null && values !=  null)
		for (int i = 0; i < labels.size(); i++) {
			msg = msg.concat(labels.get(i) + " " + values.get(i)
					+ " " + Util.getMeasureUnit(labels.get(i)) + "\n");
		}
		return msg;
	}

    private class DeviceManagerMessageHandler extends Handler {
    	@Override
		public void handleMessage(Message msg) {
        	dataBundle = msg.getData();

        	Log.d(TAG, "DeviceManagerMessageHandler d=" + dataBundle);

            switch (msg.what) {
            case DeviceManager.MESSAGE_STATE:
            	dataBundle.putBoolean(GWConst.IS_MEASURE, true);
                showDialog(PROGRESS_DIALOG);
                break;
            case DeviceManager.MESSAGE_STATE_WAIT:
            	dataBundle.putBoolean(GWConst.IS_MEASURE, true);
                showDialog(PROGRESS_DIALOG);
                break;
            case DeviceManager.ASK_PREPOST_PRANDIAL_GLYCEMIA:
            	askPrePostPrandialGlycaemia(false);
            	break;
			case DeviceManager.SHEALTH_NOTIFICATION:
				showSHealthNotification();
				break;

            case DeviceManager.ASK_SOMETHING:
            	askSomething(
            			dataBundle.getString(GWConst.ASK_MESSAGE),
            			dataBundle.getString(GWConst.ASK_POSITIVE),
            			dataBundle.getString(GWConst.ASK_NEGATIVE));
            	break;

            case DeviceManager.REFRESH_LIST:
            	refreshList();

            	break;

            case DeviceManager.STOP_BACKGROUND:

	       		stopDeviceOperation(-1);
	       		refreshList();

	       		//Per essere sicuro che venga ripristinato l'APN di default del dispositivo
	       		ConnectionManager.getConnectionManager(DeviceList.this).resetDefaultConnection();

	       		isAR = false;
       			break;

            case DeviceManager.SEND_ALL:
            	receivedMeasures++;
            	refreshList();
                closeProgressDialog();
                measureDataBundle = msg.getData();
                measureDataBundle.putBoolean(GWConst.IS_SEND_MEASURE, true);
                measureDataBundle.putBoolean(GWConst.IS_SAVE_MEASURE, false);
            	//sendAllMeasures(dataBundle.getString(GWConst.SEND_MEASURE_TYPE));

            	if(DbManager.getDbManager().getAutoSendStatus(UserManager.getUserManager().getCurrentUser().getId())
                		|| deviceManager.getCurrentDevice().getMeasure().equalsIgnoreCase(GWConst.KMsrAritm)) {
                	String action = MeasureDialogClickListener.SEND_ALL_ACTION;
                	measureDialogListener = new MeasureDialogClickListener(action, dataBundle.getString(GWConst.SEND_MEASURE_TYPE));
                } else {
                	showDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
                }

            	break;

            case DeviceManager.MEASURE_RESULT:
            	receivedMeasures++;
            	refreshList();
                closeProgressDialog();
                measureDataBundle = msg.getData();
                measureDataBundle.putBoolean(GWConst.IS_SEND_MEASURE, true);
                measureDataBundle.putBoolean(GWConst.IS_SAVE_MEASURE, false);
//                if(deviceManager.getCurrentDevice().getMeasure().equalsIgnoreCase(GWConst.KMsrAritm)){
                	//TODO
                	//Ci assicuriamo che gli handler siano correttamente settati
//                	measureManager.setHandler(measureManagerHandler);
//            		connectionManager.setHandler(connectionManagerHandler);
//            		scManager.setHandler(serverCertificateManagerHandler);
//
//                	Log.i(TAG, "Salviamo la misura corrente");
//                	MeasureManager.getMeasureManager().saveMeasureData("n");
//                } else

                if(DbManager.getDbManager().getAutoSendStatus(UserManager.getUserManager().getCurrentUser().getId())
                		|| deviceManager.getCurrentDevice().getMeasure().equalsIgnoreCase(GWConst.KMsrAritm)) {
                	String action = MeasureDialogClickListener.AUTO_SEND_ACTION;
                	measureDialogListener = new MeasureDialogClickListener(action, null);
                } else {
                	showDialog(MEASURE_RESULT_DIALOG);
                }
                break;
	        case DeviceManager.ERROR_STATE:
	        	closeProgressDialog();
	        	showDialog(ALERT_DIALOG);
	            break;
	        case DeviceManager.CONFIG_READY:

	        	refreshList();

	        	closeProgressDialog();
	        	showDialog(ALERT_DIALOG);
	            break;
            }
        }
    }

	/**
     * Gestore dei messaggi in arrivo dalla classe ServerCertificateManager
     */
    private final Handler serverCertificateManagerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		switch (msg.what) {
    		case ServerCertificateManager.ADD_CERTIFICATE_FAILURE:
    			Log.i(TAG, "serverCertificateManagerHandler: Errore nella memorizzazione del certificato");
    			createAlert(ResourceManager.getResource().getString("saveServerCertFailure"), ResourceManager.getResource().getString("warningTitle"));
    			break;
    		}
    	}
    };

    /**
     * Gestore dei messaggi in arrivo dalla classe ConnectionManager
     */
    private final Handler connectionManagerHandler = new Handler() {
//    	boolean isSilentSend = false;

//    	public boolean isSilentSend() {
//			return isSilentSend;
//		}
//
//		public void setSilentSend(boolean isSilentSend) {
//			this.isSilentSend = isSilentSend;
//		}

		@Override
    	public void handleMessage(Message msg) {

			Log.d(TAG, "connectionManagerHandler.handleMessage msg=" + msg.what);

			showWarningMessage();

    		switch (msg.what) {
    		case ConnectionManager.CONNECTION_SUCCESS:
    			if(runningConfig) {
    				Log.i(TAG, "Connessione Ok --> aggiorno configurazione loginET=" + loginET);
    				try {
    					if(loginET == null && pwdET == null) {
    						if (UserManager.getUserManager().getCurrentUser() == null) {
    							Log.i(TAG, "DB getActiveUser:" +  DbManager.getDbManager().getActiveUser());
    							UserManager.getUserManager().selectUser(DbManager.getDbManager().getActiveUser(), true);
    						}
    						userManager.logInUser();
    					}
    					else {
    						userManager.logInUser(loginET.getText().toString(), pwdET.getText().toString());
    					}

    				} catch (Exception e) {
    					Log.e(TAG, "Error: " + e.toString());
    					showErrorDialog(e.getMessage());
    				}
    			}
    			else {
	    				Log.i(TAG, "Connessione Ok --> invio la misura");
	    				showDialog(PROGRESS_DIALOG);
	    				measureManager.sendMeasureData();
//    				}
    			}
    			break;
    		case ConnectionManager.CONNECTION_ERROR:
    			if (runningConfig) {
    				Log.i(TAG, "Connessione non ok --> impossibile aggiornare configurazione");
    				removeDialog(PROGRESS_DIALOG);
    				dataBundle = msg.getData();
    				if (runningConfigUpdate)
    					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showSettingsApnError") + "\n" + ResourceManager.getResource().getString("configUpdateFail"));
    				else
    					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showSettingsApnError"));
    				runningConfig = false;
    				runningConfigUpdate = false;
    				showDialog(PLATFORM_UNREACHABLE_DIALOG);
    			}
    			else {
    				Log.i(TAG, "Connessione non ok --> impossibile inviare la misura");
    				showErrorDialogWithSave(ResourceManager.getResource().getString("showSettingsApnError"));
    			}
    			revertToDefaultConnectionSettings();
    			closeProgressDialog();
    			break;
    		case ConnectionManager.APN_ERROR:
    			if (runningConfig) {
    				Log.i(TAG, "Errore APN --> impossibile aggiornare configurazione");
    				removeDialog(PROGRESS_DIALOG);
    				dataBundle = msg.getData();
    				if (runningConfigUpdate)
    					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("changeApnError") + "\n" + ResourceManager.getResource().getString("configUpdateFail"));
    				else
    					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("changeApnError"));
    				runningConfig = false;
    				runningConfigUpdate = false;
    				showDialog(PLATFORM_UNREACHABLE_DIALOG);
    			}
    			else {
    				Log.i(TAG, "Errore APN --> impossibile inviare la misura");
    				showErrorDialogWithSave(ResourceManager.getResource().getString("changeApnError"));
    			}
    			revertToDefaultConnectionSettings();
    			closeProgressDialog();
    			break;
    		case ConnectionManager.DATA_CONNECTION_ERROR:
    			if (runningConfig) {

    				Log.d(TAG, "DATA_CONNECTION_ERROR: isForcedRelogin=" + isForcedRelogin);
    				DialogManager.showToastMessage(DeviceList.this,
    				ResourceManager.getResource().getString("errorHttp") + "\n\n" +
    				ResourceManager.getResource().getString("offlineMsg"));

    				// if (isForcedRelogin || (loginET == null && pwdET != null && DbManager.getDbManager().getActiveUser() != null)) {

    					isForcedRelogin = false;
    					Log.i(TAG, "Errore Connessione dati --> connessione dati non disponibile --> logInUserFromDb");
	    				removeDialog(PROGRESS_DIALOG);

    					try {
    						User activeUser = DbManager.getDbManager().getActiveUser();

    						if ((loginET == null || pwdET == null) && (activeUser != null))
        						userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
    						else
    							UserManager.getUserManager().logInUserFromDb(loginET.getText().toString(), pwdET.getText().toString());

    					} catch (Exception e) {
    						e.printStackTrace();
    					}
    			/*	}
    				else {
    					isForcedRelogin = false;
	    				Log.i(TAG, "Errore Connessione dati --> connessione dati non disponibile");
	    				removeDialog(PROGRESS_DIALOG);
	    				dataBundle = msg.getData();
	    				dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("showSettingsConnError"));
	    				runningConfig = false;
	    				runningConfigUpdate = false;
	    				showDialog(ALERT_DIALOG);
    				} */
    			}
    			else {
    				Log.i(TAG, "Errore Connessione dati --> impossibile inviare la misura");
    				showErrorDialogWithSave(ResourceManager.getResource().getString("showSettingsConnError"));
    			}
    			closeProgressDialog();
    			break;
    		case ConnectionManager.OPERATION_CANCELLED:
    			closeProgressDialog();
    			break;
    		}
    	}
    };

	private final Handler measureManagerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			showWarningMessage();

			switch (msg.what) {
			case MeasureManager.MEASURE_SENT:
				Log.i(TAG, "measureManagerHandler: Invio misure completato");
            	sentMeasures++;
            	refreshList();
				dataBundle = new Bundle();

				if (deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
					dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureSuccessImg"));
				else
					dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureSuccess"));

				MeasureManager.getMeasureManager().saveMeasureData("s");
				revertToDefaultConnectionSettings();
	        	break;
			case MeasureManager.LOGIN_FAILED:
				Log.i(TAG, "measureManagerHandler: Login fallito");
				closeProgressDialog();
				dataBundle = new Bundle();
				dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("SEND_LOGIN_ERROR"));
				showDialog(ALERT_DIALOG);
				MeasureManager.getMeasureManager().saveMeasureData("n");
				revertToDefaultConnectionSettings();
				break;
            case MeasureManager.SENDING_ERROR:
            case MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED:

				dataBundle = new Bundle();
				if (msg.what == MeasureManager.SERVICECENTER_CAPACITY_EXCEEDED) {
                    Log.i(TAG, "measureManagerHandler: Errore nell'invio - Raggiunto numero massimo misure");
                    dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureFailureQuota"));
                } else {
                    Log.i(TAG, "measureManagerHandler: Errore nell'invio");
                    dataBundle.putString(SEND_STATUS, ResourceManager.getResource().getString("KMsgSendMeasureFailure"));
                }
				MeasureManager.getMeasureManager().saveMeasureData("n");
				revertToDefaultConnectionSettings();

				if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {

		    		DialogManager.showToastMessage(DeviceList.this, ResourceManager.getResource().getString("userBlocked"));
					Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);


					measureList = new ArrayList<String>();
					setupView();

					resetView();

					UserManager.getUserManager().setCurrentPatient(null);

					doLogout();

					//setupListView();
					//showDialog(LOGIN_DIALOG);
					//mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
		    	}

				break;
			case MeasureManager.ACCEPT_SERVER_CERT:
				Log.i(TAG, "measureManagerHandler: Accetta certificato server");
				closeProgressDialog();
				//Il server espone dei certificati non riconosciuti dal telefono. Viene chiesto all'utente se intende fidarsi e accettare il certificato
				serverCertificate = (X509Certificate[]) msg.obj;
				Bundle data = msg.getData();
				serverCertBundle = new Bundle();
				serverCertBundle.putString(GWConst.MESSAGE_LOWER, data.getString(GWConst.EXCEPTION_MSG));
				showDialog(CERT_PROBLEM_DIALOG);
				revertToDefaultConnectionSettings();
				break;
			case MeasureManager.SAVE_MEASURE_FAIL:
				Log.i(TAG, "measureManagerHandler: Salvataggio misura fallito");
				if(!isAR){
					setOperationCompleted();
					closeProgressDialog();
					dataBundle.putString(SAVE_STATUS, ResourceManager.getResource().getString("KMsgSaveMeasureError"));
					showDialog(STATUS_DIALOG);
				}
				break;
			case MeasureManager.TRANSFER_FILE:
				Log.i(TAG, "measureManagerHandler: TRANSFER_FILE");
				dataBundle.putString(GWConst.MESSAGE, ""+msg.obj);
				showDialog(PROGRESS_DIALOG);
				break;

			case MeasureManager.SAVE_MEASURE_SUCCESS:
				Log.i(TAG, "measureManagerHandler: Misura salvata correttamente");
//				if(!isAR){

				if (deviceManager != null &&
						deviceManager.getCurrentDevice() != null &&
						deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
					dataBundle.putString(SAVE_STATUS, ResourceManager.getResource().getString("KMsgSaveMeasureConfirmImg"));
				else
					dataBundle.putString(SAVE_STATUS, ResourceManager.getResource().getString("KMsgSaveMeasureConfirm"));

				setOperationCompleted();
				closeProgressDialog();

				showDialog(STATUS_DIALOG);
//				} else {
//					Log.i(TAG, "Invio delle misure di AR");
//    				measureManager.setSendedFromDB(true);
//    				try {
//    					changeConnectionSettings(false);
//    				} catch (Exception e) {
//    					Log.e(TAG, "Invio fallito --> si riprova pi� tardi:  "+ e.getMessage());
//    				}
//				}
				break;
			}
		}
	};

	private final Handler userManagerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			showWarningMessage();

			if(runningConfig)
				revertToDefaultConnectionSettings();

			switch (msg.what) {
			case UserManager.USER_CHANGED:

				Log.d(TAG, " UserManager.USER_CHANGED: runningConfig=" + runningConfig);

				resetView();
				UserManager.getUserManager().setCurrentPatient(null);

				/*if (UserManager.getUserManager().getCurrentUser().getIsPatient()) {
					currentPatientLL.setVisibility(View.GONE);

					MenuItem item = mActionBarMenu.findItem(R.id.mi_patients);
					item.setEnabled(false);
					item.setVisible(false);
				} else {
					currentPatientLL.setVisibility(View.VISIBLE);
				}*/
				Log.i(TAG, "userManangerHandler: user changed");

				RecoveryManager.getInstance(getApplicationContext()).restoreAllMeasures();

				Log.i(TAG, "userManangerHandler: user changed");
				//ACTIVE_INSTANCE.removeDialog(DIALOG_TASKING); // Rimuove la progressbar Task init()


				try {
					titleTV.setText(UserManager.getUserManager().getCurrentUser().getName() + "\n" + UserManager.getUserManager().getCurrentUser().getSurname());

					fitTextInPatientNameLabel(getString(R.string.selectPatient));
					removeDialog(PROGRESS_DIALOG);
					setupDeviceList();

					//Forzo la ricostruzione del menu
					supportInvalidateOptionsMenu();
				} catch (DbException e) {
					showErrorDialog(e.getMessage());
				}

				if (UserManager.getUserManager().getCurrentUser().getIsPatient()) {
					currentPatientLL.setVisibility(View.GONE);

					/**Modifica del 08.10.2013 tolta voce menu per scelta pazienti**/
					/*MenuItem item = mActionBarMenu.findItem(R.id.mi_patients);
					item.setEnabled(false);
					item.setVisible(false);*/

				} else {
					currentPatientLL.setVisibility(View.VISIBLE);
				}

				runningConfig = false;
				runningConfigUpdate = false;

				/*
				if (UserManager.getUserManager().getCurrentUser().getIsPatient()) {
					checkAutoUpdate();
				}
				*/

				break;

			case UserManager.ERROR_OCCURED:

				Log.d(TAG, "UserManager.ERROR_OCCURED:");

				if (runningConfig) {
					//connectionManagerHandler.sendEmptyMessage(ConnectionManager.CONNECTION_ERROR);

					if (!Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {
						try {
							User activeUser = DbManager.getDbManager().getActiveUser();

							if (activeUser == null && (loginET != null && pwdET != null && pwdET.getText().length() > 0)) {

								UserManager.getUserManager().logInUserFromDb(loginET.getText().toString(), pwdET.getText().toString());
								pwdET.setText("");
								break;
							}
							else {
								userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
							}

						} catch (Exception e) {
							Log.e(TAG, "logInUserFromDb: " + e);
						}
					}
				}

				Log.i(TAG, "userManangerHandler: error occurred");
				removeDialog(PROGRESS_DIALOG);
				dataBundle = msg.getData();
				dataBundle.putBoolean(GWConst.LOGIN_ERROR, false);
				showDialog(ALERT_DIALOG);
				runningConfig = false;
				runningConfigUpdate = false;

				if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {

					DialogManager.showToastMessage(DeviceList.this, ResourceManager.getResource().getString("userBlocked"));
					Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);


					measureList = new ArrayList<String>();
					setupView();

					resetView();

					UserManager.getUserManager().setCurrentPatient(null);
					setCurrentUserLabel("");
					currentPatientLL.setVisibility(View.GONE);

					doLogout();

					/**Modifica del 08.10.2013 tolta voce menu per scelta pazienti**/
					/*MenuItem item = mActionBarMenu.findItem(R.id.mi_patients);
					item.setEnabled(false);
					item.setVisible(false);*/
		    	}

				break;

			case UserManager.LOGIN_FAILED:
				Log.e(TAG, "userManagerHandler: login failed");
				removeDialog(PROGRESS_DIALOG);
				dataBundle = msg.getData();
				dataBundle.putBoolean(GWConst.LOGIN_ERROR, true);
				showDialog(ALERT_DIALOG);
				runningConfig = false;
				runningConfigUpdate = false;
				break;

			case UserManager.STOP_CONFIG_OK:
				Log.i(TAG, "userManagerHandler: stop config ok");
				removeDialog(PROGRESS_DIALOG);
				runningConfig = false;
				runningConfigUpdate = false;
				break;

			case UserManager.PLATFORM_UNREACHABLE:
				Log.i(TAG, "userManagerHandler: platform unreachable");
				removeDialog(PROGRESS_DIALOG);
				dataBundle = msg.getData();
				if (runningConfigUpdate)
					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("errorHttp") + "\n" + ResourceManager.getResource().getString("configUpdateFail"));
				else
					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("errorHttp") + "\n" + ResourceManager.getResource().getString("offlineMsg"));
				showDialog(PLATFORM_UNREACHABLE_DIALOG);
				runningConfig = false;
				runningConfigUpdate = false;
				break;
			case MeasureManager.ACCEPT_SERVER_CERT:
				Log.i(TAG, "userManagerHandler: accept server cert");
				closeProgressDialog();
				//Il server espone dei certificati non riconosciuti dal telefono. Viene chiesto all'utente se intende fidarsi e accettare il certificato
				serverCertificate = (X509Certificate[]) msg.obj;
				Bundle data = msg.getData();
				serverCertBundle = new Bundle();
				serverCertBundle.putString(GWConst.MESSAGE_LOWER, data.getString(GWConst.EXCEPTION_MSG));
				showDialog(CERT_PROBLEM_DIALOG);
				break;
			}
		}
	};

	private void setupDeviceList() throws DbException {
		User currentUser = UserManager.getUserManager().getCurrentUser();
		if(currentUser != null){
			//L'utente corrente diventa utente attivo
			currentUser.setActive(true);
			//Registra il suo ID
			Util.setRegistryValue(Util.KEY_LAST_USER, currentUser.getId());
			//Modifica il DB
			DbManager.getDbManager().setCurrentUser(currentUser);

			deviceManager.setCurrentUser(currentUser);

			initMeasureList();
			initDeviceMap();
			initMeasureModelsMap(currentUser);

			patientList = DbManager.getDbManager().getUserPatients(currentUser.getId());
			if (patientList == null || patientList.size() == 0) {
				dataBundle = new Bundle();
				dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
				showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
			}
			else if (patientList.size() != 1) {
				setCurrentUserLabel(ResourceManager.getResource().getString("selectPatient"));
				patients = new String[patientList.size()];
	        	int counter = 0;
	        	for (UserPatient up : patientList) {
	        		Patient p;

	        		p = DbManager.getDbManager().getPatientData(up.getIdPatient());
	        		patients[counter++] = "[" + p.getId() + "] " + p.getSurname() + " " + p.getName();
	        	}

	        	if (patients == null || patients.length == 0) {
	        		dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
	        	}
	        	else {
	        		startSelectPatientActivity();
	        	}
			}
			else {
				Patient p = DbManager.getDbManager().getPatientData(patientList.get(0).getIdPatient());
				setCurrentUserLabel(p.getName() + " " + p.getSurname());
				UserManager.getUserManager().setCurrentPatient(p);
			}

			setupView();
			if( isGrid ) {
				registerForContextMenu(deviceGridFragment.getGridView());
			} else {
				registerForContextMenu(deviceListFragment.getListView());
			}
		}
	}

	private void sendAllMeasures(String measureType) {

		Log.d(TAG, "sendAllMeasures(" + measureType + ")");

		Intent myIntent = new Intent(DeviceList.this, ShowMeasure.class);

		myIntent.putExtra(GWConst.SHOW_MEASURE_TITLE, ResourceManager.getResource().getString("measureType." + measureType));
		myIntent.putExtra(GWConst.SELECTED_MEASURE, measureType);
		myIntent.putExtra(GWConst.SEND_ALL, measureType);
		myIntent.putExtra(GWConst.SEND_MEASURE_TYPE, measureType);
		myIntent.putExtra(GWConst.MEASURE_MODE, GWConst.MEASURE_MODE_ON);

		startActivity(myIntent);
	}

    protected void showWarningMessage() {

    	if (Util.getRegistryValue(Util.KEY_WARNING_TIMESTAMP, false)) {

    		DialogManager.showToastMessage(DeviceList.this, ResourceManager.getResource().getString("warningDate"));
			Util.setRegistryValue(Util.KEY_WARNING_TIMESTAMP, false);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {

    	if(isAR && deviceManager.isOperationRunning()){
    		Log.i(TAG, "onCreateDialog bypassed");
    		if(dataBundle != null){
    			String msg = dataBundle.getString(SEND_STATUS);
    			if(Util.isEmptyString(msg)){
    				msg = dataBundle.getString(SAVE_STATUS);
    			}
    			if(Util.isEmptyString(msg)){
    				msg = dataBundle.getString(GWConst.MESSAGE);
    			}
				if(!Util.isEmptyString(msg) && ResourceManager.getResource().getString("KConnMsgZephyr").equalsIgnoreCase(msg)){
    				Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    				return null;
    			}
    		}

    	}

    	Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    	//AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	//LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
		CheckBox pwdCB;

		switch (id) {
		case LIST_OR_NEW_USER_DIALOG:
			builder.setTitle(R.string.new_user_title);
			if (DbManager.getDbManager().getNotActiveUsers().size() != 0)
				builder.setItems(new String[] {getString(R.string.newUser), getString(R.string.users_title)}, list_or_new_user_dialog_click_listener);
			else
				builder.setItems(new String[] {getString(R.string.newUser)}, list_or_new_user_dialog_click_listener);
			return builder.create();

		/*case DIALOG_TASKING:
            mLoadingDialog = new ProgressDialog(this);
            mLoadingDialog.setTitle(R.string.waitTitle);
            mLoadingDialog.setMessage(getText(R.string.readInfo));
            mLoadingDialog.setIndeterminate(true);
            mLoadingDialog.setCancelable(false);
            return mLoadingDialog;*/
		case MEASURE_RESULT_DIALOG:
			Log.i(TAG, "Visualizzo dialog MEASURE_RESULT_DIALOG");
            return createMeasureResultDialog(measureDataBundle);
		case MEASURE_RESULT_DIALOG_SEND_ALL:
			Log.i(TAG, "Visualizzo dialog MEASURE_RESULT_DIALOG_SEND_ALL");
            return createMeasureResultDialog(measureDataBundle, PATIENT_SELECTION_SEND_ALL);
        case PROGRESS_DIALOG:
        	return createProgressDialog(dataBundle);
        case PLATFORM_UNREACHABLE_DIALOG:
        	builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
        	builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
    		builder.setPositiveButton("Ok", platform_unreachable_dialog_click_listener);
    		beep();
        	return builder.create();
        case ALERT_DIALOG:
            return createAlertDialog(dataBundle);
        case ALERT_DIALOG_WITH_SAVE:
        	builder.setTitle(dataBundle.getString(GWConst.TITLE));
			builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
			builder.setNeutralButton(ResourceManager.getResource().getString("okButton"), alert_dialog_with_save_click_listener);
			beep();
			return builder.create();
        case PRECOMPILED_LOGIN_DIALOG:
        	builder.setTitle(R.string.authentication);
        	View login_dialog_v = inflater.inflate(R.layout.new_user, null);
        	loginET = (EditText) login_dialog_v.findViewById(R.id.login);
        	pwdET = (EditText) login_dialog_v.findViewById(R.id.password);

			if( userDataBundle != null ) {
				loginET.setText(userDataBundle.getString("LOGIN"));
				if (!userDataBundle.getBoolean("CHANGEABLE"))
					loginET.setEnabled(false);
				else
					loginET.setEnabled(true);
			} else {
				loginET.setText("");
			}

        	pwdCB = (CheckBox) login_dialog_v.findViewById(R.id.passwordCheckBox);
        	pwdCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					// Controlla se rendere visibile o meno la password
					if( isChecked ) {
						pwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
					} else {
						pwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
					}

					pwdET.setSelection(pwdET.getText().length());
				}
			});

        	//Controlla se ci sono altri utente registrati
        	if((DbManager.getDbManager().getNotActiveUsers()).size() != 0) {
        		//Abilita il button per la lista utenti
        		ImageButton iv = (ImageButton) login_dialog_v.findViewById(R.id.user_list_button);
        		iv.setVisibility(View.VISIBLE);

        		iv.setOnClickListener( new OnClickListener() {

					@Override
					public void onClick(View v) {
						showUserDialog();
					}
				});
        	}

        	builder.setView(login_dialog_v);
        	builder.setPositiveButton(R.string.okButton, login_dialog_click_listener);
        	builder.setNegativeButton(R.string.cancelButton, login_dialog_click_listener);
        	beep();
        	return builder.create();
        case LOGIN_DIALOG:
        	builder.setTitle(R.string.authentication);
        	View login_dialog_view = inflater.inflate(R.layout.new_user, null);
        	loginET = (EditText) login_dialog_view.findViewById(R.id.login);
        	pwdET = (EditText) login_dialog_view.findViewById(R.id.password);

        	pwdCB = (CheckBox) login_dialog_view.findViewById(R.id.passwordCheckBox);
        	pwdCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					// Controlla se rendere visibile o meno la password
					if( isChecked ) {
						pwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
					} else {
						pwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
					}

					pwdET.setSelection(pwdET.getText().length());
				}
			});

        	//Controlla se ci sono altri utente registrati
        	if((DbManager.getDbManager().getNotActiveUsers()).size() != 0) {
        		//Abilita il button per la lista utenti
        		ImageButton iv = (ImageButton) login_dialog_view.findViewById(R.id.user_list_button);
        		iv.setVisibility(View.VISIBLE);

        		iv.setOnClickListener( new OnClickListener() {

					@Override
					public void onClick(View v) {
						showUserDialog();
					}
				});
        	}

        	builder.setView(login_dialog_view);
        	builder.setPositiveButton(R.string.okButton, login_dialog_click_listener);
        	builder.setNegativeButton(R.string.cancelButton, login_dialog_click_listener);
        	beep();
        	return builder.create();
        case CONFIRM_CLOSE_DIALOG:
        	builder.setTitle(R.string.app_name);
        	builder.setMessage(ResourceManager.getResource().getString("MainGUI.menu.fileMenu.exitMsg"));
        	builder.setPositiveButton(ResourceManager.getResource().getString("okButton"), confirm_close_dialog_click_listener);
        	builder.setNegativeButton(ResourceManager.getResource().getString("cancelButton"), confirm_close_dialog_click_listener);
        	return builder.create();
        case CONFIRM_PATIENT_DIALOG:
        	return createConfirmPatientDialog();
        case SIMPLE_DIALOG_WITH_CLOSE:
        	builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
        	builder.setNeutralButton("Ok", simple_dialog_with_close_click_listener);
        	beep();
        	return builder.create();
        case SIMPLE_DIALOG_WITHOUT_CLOSE:
        	builder.setMessage(dataBundle.getString(GWConst.MESSAGE));
        	builder.setNeutralButton("Ok", simple_dialog_without_close_click_listener);
        	beep();
        	return builder.create();
        case STATUS_DIALOG:
        	if(dataBundle.getString(SEND_STATUS) != null){
        		builder.setMessage(dataBundle.getString(SEND_STATUS));
        	} else {
        		builder.setMessage(dataBundle.getString(SAVE_STATUS));
        	}
        	builder.setNeutralButton("Ok", status_dialog_click_listener);
        	beep();
        	return builder.create();
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

        	builder.setPositiveButton("Ok", cert_info_dialog_click_listener);
        	return builder.create();
        default:
            return null;
		}
	}

    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {

    	if(isAR && deviceManager.isOperationRunning()){
    		Log.i(TAG, "onPrepareDialog bypassed");
    		if(dataBundle != null){
    			String msg = dataBundle.getString(SEND_STATUS);
    			if(Util.isEmptyString(msg)){
    				msg = dataBundle.getString(SAVE_STATUS);
    			}
    			if(Util.isEmptyString(msg)){
    				msg = dataBundle.getString(GWConst.MESSAGE);
    			}
    			if(!Util.isEmptyString(msg) && ResourceManager.getResource().getString("KConnMsgZephyr").equalsIgnoreCase(msg)){
    				//Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    				DialogManager.showSimpleToastMessage(DeviceList.this, msg);

    				super.onPrepareDialog(id, dialog);
    				runOnUiThread(new Runnable() {
						@Override
						public void run() {
							new Handler().post(new Runnable() {
								@Override
								public void run() {
				    				closeProgressDialog();
								}
							});
						}
					});

    				return;
    			}
    		}

    	}

		switch(id) {
        case PROGRESS_DIALOG:



        	String msg = dataBundle.getString(GWConst.MESSAGE);
    		Log.d(TAG, "onPrepareDialog msg=" + msg);

        	if (msg.startsWith(WebSocket.FILE_TRANSFER)) {

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
        		((ProgressDialog)dialog).setMessage(dataBundle.getString(GWConst.MESSAGE));
        	}


        	if(deviceMap != null && selectedMeasureType != null && deviceMap.get(selectedMeasureType) != null &&
        			Util.isStmDevice(deviceMap.get(selectedMeasureType).getDevice()) && !isPairing && isConfig && isManualMeasure) {

    			progressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    		}

        	if (dataBundle.getString(GWConst.MESSAGE).startsWith(ResourceManager.getResource().getString("KReliabilityStm") + ":") &&
        			dataBundle.getString(GWConst.MESSAGE).endsWith("%")) {

	        	String rvalue = dataBundle.getString(GWConst.MESSAGE).split(":")[1].trim().split("%")[0].trim();
	        	Log.i(TAG, "Reliability Value=" + rvalue);

	        	int reliabilityValue = 0;
	        	try {
	        		reliabilityValue = Integer.parseInt(rvalue);
	        	}
	        	catch (Exception e) {
	        		reliabilityValue = 0;
				}

	        	String color = "yellow";

	        	((ProgressDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

	        	int traffic = 1;
	        	if (reliabilityValue<70) {
	        		traffic = 0;
	        		color = "red";
	        	}
	        	else {
	        		if (reliabilityValue>90) {
	        			traffic = 2;
	        			color = "#7BFF02";
	        			((ProgressDialog)dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
	        		}
	        	}

	        	((ProgressDialog)dialog).setMessage(Html.fromHtml(
	               "<img src=\"file:///android_res/drawable/traffic" + traffic + ".png\" /><h1>" + ResourceManager.getResource().getString("KReliabilityStm") +": <font color='" + color + "'>" +  rvalue + "%</font></h1>",
	               new ImageGetter() {

					@Override
					public Drawable getDrawable(String source) {

						Drawable d = DeviceList.this.getResources().getDrawable(R.drawable.traffic1);
						if (source.endsWith("0.png"))
							d = DeviceList.this.getResources().getDrawable(R.drawable.traffic0);
						else
							if (source.endsWith("2.png"))
								d = DeviceList.this.getResources().getDrawable(R.drawable.traffic2);

						d.setBounds(0,0,(int)(d.getIntrinsicWidth()*0.7f),(int)(d.getIntrinsicHeight()*0.7f));
						return d;
					}
				}, null));
        	}

        	if(dataBundle.getBoolean(GWConst.MESSAGE_CANCELLABLE)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(true);//Visibility(View.VISIBLE);
    		} else {
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(false);//setVisibility(View.INVISIBLE);
    		}
    		//Assegno un tag al button per poter gestire correttamente il click su Annulla
    		if(dataBundle.getBoolean(GWConst.IS_MEASURE)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setTag(GWConst.IS_MEASURE);
    		} else if(dataBundle.getBoolean(GWConst.IS_SEND_MEASURE)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setTag(GWConst.IS_SEND_MEASURE);
    		} else if(dataBundle.getBoolean(GWConst.IS_CONFIGURATION)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setTag(GWConst.IS_CONFIGURATION);
    		}
    		break;
        case ALERT_DIALOG:
            msg = dataBundle.getString(GWConst.MESSAGE);
            ((AlertDialog)dialog).setMessage(msg);
            break;
        default:
        	super.onPrepareDialog(id, dialog);
        }
	}

    private DialogInterface.OnClickListener platform_unreachable_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(PLATFORM_UNREACHABLE_DIALOG);
			try {
				UserManager.getUserManager().logInUserFromDb(loginET.getText().toString(), pwdET.getText().toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	};

    /**
     * Listener per i click sulla dialog LOGIN_DIALOG
     */
    private DialogInterface.OnClickListener login_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(PRECOMPILED_LOGIN_DIALOG);
			removeDialog(LOGIN_DIALOG);
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
//				resetView();
				dataBundle = new Bundle();
				dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgConf"));
				dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
				dataBundle.putBoolean(GWConst.IS_CONFIGURATION, true);
				runningConfig = true;
				changeConnectionSettings(true);
				break;
			}
		}
	};

	/**
	 * Listener per i click sulla dialog LIST_OR_NEW_USER_DIALOG
	 */
	private DialogInterface.OnClickListener list_or_new_user_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(LIST_OR_NEW_USER_DIALOG);
			switch(which) {
			case 1:
				//Premuto l'elemento "Elenco utenti"
				showUsers(USER_LIST);
				break;
			case 0:
				//Premuto l'elemento "Nuovo utente"
				showDialog(LOGIN_DIALOG);
				break;
			}
		}
	};

	/**
	 * Listener per i click sulla dialog SIMPLE_DIALOG_WITH_CLOSE
	 */
	private DialogInterface.OnClickListener simple_dialog_with_close_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(SIMPLE_DIALOG_WITH_CLOSE);
				doRevertToDefaultConnectionSettings();
				finish();
				break;
			}
		}
	};

	/**
	 * Listener per i click sulla dialog SIMPLE_DIALOG_WITHOUT_CLOSE
	 */
	private DialogInterface.OnClickListener simple_dialog_without_close_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
				break;
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
     * Listener per i click sulla dialog CERT_INFO_DIALOG
     */
    private DialogInterface.OnClickListener cert_info_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			//Premuto il pulsante "Ok"
			case DialogInterface.BUTTON_POSITIVE:
				removeDialog(CERT_INFO_DIALOG);
				showDialog(CERT_PROBLEM_DIALOG);
			}
		}
	};

	/**
	 * Listener per i click sulla dialog CONFIRM_CLOSE_DIALOG
	 */
	private DialogInterface.OnClickListener confirm_close_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale pulsante della dialog � stato premuto
			removeDialog(CONFIRM_CLOSE_DIALOG);
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				doRevertToDefaultConnectionSettings();
				finish();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				break;
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
				MeasureManager.getMeasureManager().saveMeasureData("n");
				Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("serverCertDialogCertRejected"), Toast.LENGTH_LONG).show();
				break;
			//Premuto il pulsante "Accetta certificato"
			case DialogInterface.BUTTON_POSITIVE:
				removeDialog(CERT_PROBLEM_DIALOG);
				Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("serverCertDialogCertAccepted"), Toast.LENGTH_LONG).show();

				scManager.addServerCertificate(UserManager.getUserManager().getCurrentUser().getId(), serverCertificate[0]);

				if(runningConfig) {
					//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
			    	dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgConf"));
					dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
					dataBundle.putBoolean(GWConst.IS_CONFIGURATION, true);
					showDialog(PROGRESS_DIALOG);
			    	userManager.logInUser();
				}
				else {
					//Il certificato � stato accettato. Chiedo all'utente se vuole riprovare l'invio
					measureDataBundle.putBoolean(GWConst.IS_SEND_MEASURE, true);
			    	measureDataBundle.putBoolean(GWConst.IS_SAVE_MEASURE, false);
	            	showDialog(MEASURE_RESULT_DIALOG);
				}
				break;
			//Premuto il pulsante "Vedi certificato"
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(CERT_PROBLEM_DIALOG);
				showDialog(CERT_INFO_DIALOG);
				break;
			}
		}
	};

	private Dialog createConfirmPatientDialog() {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

		/*LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);*/

        /*View select_patient_dialog_v = inflater.inflate(R.layout.show_measure_custom_dialog, null);
    	TextView measureDialogTV = (TextView) select_patient_dialog_v.findViewById(R.id.measure_dialog_tv);
    	TextView questionDialogTV = (TextView) select_patient_dialog_v.findViewById(R.id.question_dialog_tv);
    	questionDialogTV.setVisibility(View.GONE);
    	Button measureDialogButton = (Button) select_patient_dialog_v.findViewById(R.id.measure_dialog_button);
    	measureDialogButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				removeDialog(CONFIRM_PATIENT_DIALOG);
				Log.i(TAG, "Cambia paziente");

				if (patients == null || patients.length == 0) {
					dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
				}
				else {
					viewMeasureBundle = null;
					startMeasureBundle = new Bundle();
					startMeasureBundle.putBoolean(GWConst.START_MEASURE, true);
					startSelectPatientActivity();
				}
			}
		});
    	builder.setTitle(ResourceManager.getResource().getString("confirmPatient"));
    	Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
    	measureDialogTV.setText(ResourceManager.getResource().getString("confirmChangeQuestion") + " " + currentPatient.getName() + " " + currentPatient.getSurname() + "?");
    	builder.setView(select_patient_dialog_v);*/


    	builder.setTitle(ResourceManager.getResource().getString("confirmPatient"));

    	Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
    	String measureStr = ResourceManager.getResource().getString("confirmChangeQuestion") + " " + currentPatient.getName() + " " + currentPatient.getSurname() + "?";
		builder.setMessage(measureStr);

    	builder.setPositiveButton(ResourceManager.getResource().getString("yes"), confirm_patient_dialog_click_listener);
    	builder.setNegativeButton(ResourceManager.getResource().getString("no"), confirm_patient_dialog_click_listener);
    	builder.setNeutralButton(R.string.changePatientQuestion, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				removeDialog(CONFIRM_PATIENT_DIALOG);
				Log.i(TAG, "Cambia paziente");

				if (patients == null || patients.length == 0) {
					dataBundle = new Bundle();
					dataBundle.putString(GWConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
				}
				else {
					viewMeasureBundle = null;
					startMeasureBundle = new Bundle();
					startMeasureBundle.putBoolean(GWConst.START_MEASURE, true);
					startSelectPatientActivity();
				}
			}
		});
    	builder.setCancelable(false);
    	return builder.create();
	}

	private Dialog createMeasureResultDialog(Bundle data) {
		return createMeasureResultDialog(data, PATIENT_SELECTION_2);
	}

	private Dialog createMeasureResultDialog(Bundle data, final int id) {

		Log.d(TAG, "createMeasureResultDialog id=" + id);

		RecoveryManager.getInstance(getApplicationContext()).backupAllMeasures();

		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

        //LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		//LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
		//AlertDialog.Builder builder = new AlertDialog.Builder(this);
		/*builder.setTitle(data.getString(DeviceManager.RESULT_TYPE));

		View show_measure_dialog_view = inflater.inflate(R.layout.show_measure_custom_dialog, null);
		TextView measureTV = (TextView) show_measure_dialog_view.findViewById(R.id.measure_dialog_tv);
		TextView questionTV = (TextView) show_measure_dialog_view.findViewById(R.id.question_dialog_tv);
		Button measureButton = (Button) show_measure_dialog_view.findViewById(R.id.measure_dialog_button);
		measureButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				removeDialog(MEASURE_RESULT_DIALOG);
				removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
				Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
				selectPatientIntent.putExtra(GWConst.USER_ID, deviceManager.getCurrentUser().getId());
				startActivityForResult(selectPatientIntent, id);
			}
		});

		measureTV.setText(getMeasureMessage(data));

		String msg = "";
		String okBtnMsg = "";
		String action = "";
		if(data.getBoolean(GWConst.IS_SEND_MEASURE) == true){
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Patient patient = UserManager.getUserManager().getCurrentPatient();
			msg = String.format(txt, patient.getName(), patient.getSurname());
			okBtnMsg = ResourceManager.getResource().getString("MeasureResultDialog.sendBtn");

			if (id == PATIENT_SELECTION_SEND_ALL)
				action = MeasureDialogClickListener.SEND_ALL_ACTION;
			else
				action = MeasureDialogClickListener.SEND_ACTION;

		} else if(data.getBoolean(GWConst.IS_SAVE_MEASURE) == true){
			msg = ResourceManager.getResource().getString("MeasureResultDialog.saveMeasureMsg");
			okBtnMsg = ResourceManager.getResource().getString("MeasureResultDialog.saveBtn");

			if (id == PATIENT_SELECTION_SEND_ALL)
				action = MeasureDialogClickListener.SAVE_ALL_ACTION;
			else
				action = MeasureDialogClickListener.SAVE_ACTION;
		}


		questionTV.setText(msg);

		builder.setView(show_measure_dialog_view);
		measureDialogListener = new MeasureDialogClickListener(action, data.getString(GWConst.SEND_MEASURE_TYPE));
		builder.setPositiveButton(okBtnMsg, measureDialogListener);
		builder.setNegativeButton(ResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), measureDialogListener);

		if ((action.equals(MeasureDialogClickListener.SEND_ALL_ACTION) || action.equals(MeasureDialogClickListener.SEND_ACTION)) && !deviceManager.getCurrentUser().getIsPatient() && !deviceManager.getCurrentDevice().getMeasure().equals(GWConst.KMsrSpir)) {
			List<UserPatient> patients = DbManager.getDbManager().getUserPatients(UserManager.getUserManager().getCurrentUser().getId());
			if (patients != null && patients.size() != 1) {
				measureButton.setVisibility(View.VISIBLE);
			}
			else
				measureButton.setVisibility(View.GONE);
		}
		else
			measureButton.setVisibility(View.GONE);

		beep();
		builder.setCancelable(false);
		return builder.create();*/

        builder.setTitle(data.getString(DeviceManager.RESULT_TYPE));

		String msg = "";
		String okBtnMsg = "";
		String action = "";
		if(data.getBoolean(GWConst.IS_SEND_MEASURE)){
			String txt = ResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
			Patient patient = UserManager.getUserManager().getCurrentPatient();
			msg = String.format(txt, patient.getName(), patient.getSurname());
			okBtnMsg = ResourceManager.getResource().getString("MeasureResultDialog.sendBtn");

			if (id == PATIENT_SELECTION_SEND_ALL)
				action = MeasureDialogClickListener.SEND_ALL_ACTION;
			else
				action = MeasureDialogClickListener.SEND_ACTION;

		} else if(data.getBoolean(GWConst.IS_SAVE_MEASURE)){
			msg = ResourceManager.getResource().getString("MeasureResultDialog.saveMeasureMsg");
			okBtnMsg = ResourceManager.getResource().getString("MeasureResultDialog.saveBtn");

			if (id == PATIENT_SELECTION_SEND_ALL)
				action = MeasureDialogClickListener.SAVE_ALL_ACTION;
			else
				action = MeasureDialogClickListener.SAVE_ACTION;
		}


		String measureStr = getMeasureMessage(data);
		String questionStr = msg;
		builder.setMessage(measureStr + "\n" + questionStr);

		measureDialogListener = new MeasureDialogClickListener(action, data.getString(GWConst.SEND_MEASURE_TYPE));
		builder.setPositiveButton(okBtnMsg, measureDialogListener);
		builder.setNegativeButton(ResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), measureDialogListener);

		if ((action.equals(MeasureDialogClickListener.SEND_ALL_ACTION) || action.equals(MeasureDialogClickListener.SEND_ACTION)) && !deviceManager.getCurrentUser().getIsPatient() && !deviceManager.getCurrentDevice().getMeasure().equals(GWConst.KMsrSpir)) {
			List<UserPatient> patients = DbManager.getDbManager().getUserPatients(UserManager.getUserManager().getCurrentUser().getId());
			if (patients != null && patients.size() != 1) {
				builder.setNeutralButton(R.string.changePatientQuestion, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(MEASURE_RESULT_DIALOG);
						removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
						Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
						selectPatientIntent.putExtra(GWConst.USER_ID, deviceManager.getCurrentUser().getId());
						startActivityForResult(selectPatientIntent, id);
					}
				});

			}
		}

		beep();
		builder.setCancelable(false);
		return builder.create();
	}

	private void showAboutDialog() {
		startActivity(new Intent(getApplicationContext(), AboutScreen.class));
	}

	private void beep() {
		Thread t = new Thread(beepRunnable);
		t.start();
	}

	private Runnable beepRunnable = new Runnable() {

		@Override
		public void run() {
			try {
				Thread.sleep(400);
				ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME);
		    	tg.startTone(ToneGenerator.TONE_PROP_BEEP);
			} catch (InterruptedException e) {
				e.printStackTrace();
				Log.e(TAG, "ERROR on beepRunnable: " + e.getMessage());
			}
		}
	};

	private void closeProgressDialog() {
		if(progressDialog!= null && progressDialog.isShowing()){
			removeDialog(PROGRESS_DIALOG);
			progressDialog.dismiss();
			progressDialog = null;
		}
	}

	private void startMeasure() {
		User currentUser = UserManager.getUserManager().getCurrentUser();
		//La richiesta di conferma deve essere visualizzata se l'utente � un nurse, se il nurse ha pi� di un paziente e se la misura da effettuare � una spirometria
		if(!DbManager.getDbManager().getIsPatient(currentUser.getId()) && selectedMeasureType.equalsIgnoreCase(GWConst.KMsrSpir)) {
			List<UserPatient> userPatients = DbManager.getDbManager().getUserPatients(currentUser.getId());
			if (userPatients != null && userPatients.size() != 1){
				showDialog(CONFIRM_PATIENT_DIALOG);
			} else {
				makeMeasure();
			}
		} else {
			makeMeasure();
		}
	}

	private void makeMeasure() {
		if(selectedMeasureType.equalsIgnoreCase(GWConst.KMsrGlic) &&
				(!deviceManager.getCurrentDevice().getDevice().getModel().equals(GWConst.KMYGLUCOHEALTH)) &&
				(!deviceManager.getCurrentDevice().getDevice().getModel().equals(GWConst.KTDCC))
				){
			askPrePostPrandialGlycaemia();
		} else {
			deviceManager.startMeasure();
		}
	}

	private void startConfig() {
		deviceManager.startConfig();
	}

	private void startAlternativeConfig() {
		deviceManager.startAlternativeConfig();
	}

	private void setCurrentDevice(int position) {
		UserDevice ud = deviceMap.get(selectedMeasureType);
		if(ud.isActive()){
			deviceManager.setCurrentDevice(ud);
		} else {
			deviceManager.setCurrentDevice(null);
		}
	}

	private void setOperationCompleted(){
		//imposto il current device a null per segnalare al DeviceManager
		//che la misura corrente � terminata (eventualmente inviata e/o salvata su DB)
		if(deviceManager.getCurrentDevice() != null && !isAR) {
			Log.i(TAG, "setOperationCompleted: " + deviceManager.getCurrentDevice().getDevice().getDescription() + " ha terminato");
			deviceManager.setCurrentDevice(null);
		}
	}

	private void stopDeviceOperation(int position) {
		deviceManager.stopDeviceOperation(position);
		if(position < 0){
			removeDialog(PROGRESS_DIALOG);
		}

		refreshList();
	}

	private void stopConfiguration() {
		try {
			userManager.stopConfiguration();
		} catch (Exception e) {
			showErrorDialog(ResourceManager.getResource().getString("errorHttp"));
		}
	}

	private boolean measureEnabled(UserDevice device) {
		return (device.getBtAddress()!= null && device.getBtAddress().length() > 0)
				|| Util.isGlucoTelDevice(device.getDevice())
				|| Util.isManualMeasure(device.getDevice())
				|| Util.isIEMECGDevice(device.getDevice())
				|| Util.isStmDevice(device.getDevice());
	}

	private class ProgressDialogClickListener implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			Button btn = ((ProgressDialog)dialog).getButton(which);
			String tag = (String) btn.getTag();
			removeDialog(PROGRESS_DIALOG);
			if(GWConst.IS_SEND_MEASURE.equals(tag)){
				try {
					Log.i(TAG, "Annullo l'invio della misura");
					stopConnection();
					measureManager.stopSendMeasure();
					revertToDefaultConnectionSettings();
				} catch (Exception e) {
					showErrorDialog(ResourceManager.getResource().getString("errorHttp"));
				}
			} else if(GWConst.IS_MEASURE.equals(tag)){
				stopDeviceOperation(-1);
			} else if(GWConst.IS_CONFIGURATION.equals(tag)){
				Log.i(TAG, "Annullo l'update della configurazione");
				runningConfig = false;
		    	runningConfigUpdate = false;
				stopConnection();
				stopConfiguration();
				revertToDefaultConnectionSettings();
			}
		}
	}

	/**
	 * Listener per i click sulla alert CONFIRM_PATIENT_DIALOG
	 */
	private DialogInterface.OnClickListener confirm_patient_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(CONFIRM_PATIENT_DIALOG);
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				Log.i(TAG, "Conferma paziente");
				makeMeasure();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Log.i(TAG, "Annulla misura");
				setOperationCompleted();
				break;
			}
		}
	};

	/**
	 * Listener per i click sulla alert ALERT_DIALOG_WITH_SAVE
	 */
	private DialogInterface.OnClickListener alert_dialog_with_save_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(ALERT_DIALOG_WITH_SAVE);
			measureManager.saveMeasureData("n");
		}
	};

	private class AlertDialogClickListener implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			if(progressDialog != null && progressDialog.isShowing()){
				removeDialog(PROGRESS_DIALOG);
			}

			removeDialog(ALERT_DIALOG);
     	   	removeDialog(MEASURE_RESULT_DIALOG);

     	   	if (dataBundle.getBoolean(GWConst.LOGIN_ERROR)) {
     	   		userDataBundle = new Bundle();
     	   		userDataBundle.putBoolean("CHANGEABLE", true);

     	   		if (loginET != null && loginET.getText() != null)
     	   			userDataBundle.putString("LOGIN", loginET.getText().toString());
     	   		else
     	   			userDataBundle.putString("LOGIN", "");
     	   		showDialog(PRECOMPILED_LOGIN_DIALOG);
     	   	}
		}
	}

	private class MeasureDialogClickListener implements DialogInterface.OnClickListener {
    	public static final String SEND_ACTION = "SEND_ACTION";
    	public static final String SAVE_ACTION = "SAVE_ACTION";
    	public static final String AUTO_SEND_ACTION = "AUTO_SEND_ACTION";
    	public static final String SEND_ALL_ACTION = "SEND_ALL_ACTION";
    	public static final String SAVE_ALL_ACTION = "SAVE_ALL_ACTION";

    	private String action;
		private String measureType;

    	public MeasureDialogClickListener(String action, String measureType) {
			this.action = action;
			this.measureType = measureType;

			Log.d(TAG, "MeasureDialogClickListener action=" + action + " measureType=" + measureType);

			/*
			if(this.action.equals(SEND_ALL_ACTION) || this.action.equals(SAVE_ALL_ACTION)) {
				/*
				if(this.action.equals(AUTO_SEND_ACTION)) {
					removeDialog(PROGRESS_DIALOG);
					removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
					deviceManager.saveAllMeasure();
					RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
					sendAllMeasures(measureType);
				/*}
				else {
					if (deviceManager.getCurrentDevice().getMeasure().equalsIgnoreCase(GWConst.KIEMECG)) {
						deviceManager.saveAllMeasure();
					}
				}*
			}
			else {
			*/
				if(this.action.equals(AUTO_SEND_ACTION)) {

					deviceManager.saveAllMeasure();
					RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
					removeDialog(PROGRESS_DIALOG);
					removeDialog(MEASURE_RESULT_DIALOG);

					if (deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KIEMECG) ||
                            deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KGOOGLEFIT) ||
							deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KSHEALTH)) {
						sendAllMeasures(measureType);
					}
					else {
						dataBundle = new Bundle();

						if (deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
							dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgTrasfImg"));
						else
							dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgTrasf"));

						dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
						dataBundle.putBoolean(GWConst.IS_SEND_MEASURE, true);
						showDialog(PROGRESS_DIALOG);
						Log.i(TAG, "Invio delle misure in corso");
						measureManager.setSendedFromDB(false);
						try {
							changeConnectionSettings(false);
						} catch (Exception e) {
							setOperationCompleted();
							showErrorDialog(ResourceManager.getResource().getString("SEND_ERROR"));
						}
					}
				}
			//}
		}

    	public void onClick(DialogInterface dialog, int which) {

    		Log.d(TAG, "MeasureDialogClickListener.onClick() action=" + action + " measureType=" + measureType + " which=" + which);

    		if(this.action.equals(SEND_ALL_ACTION) || this.action.equals(SAVE_ALL_ACTION)) {

    			switch(which) {
				case DialogInterface.BUTTON_POSITIVE:
					removeDialog(PROGRESS_DIALOG);
					removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);

					if(action.equals(SEND_ALL_ACTION)){
						deviceManager.saveAllMeasure();
						RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
						sendAllMeasures(measureType);
					}
					else {
						if(action.equals(SAVE_ALL_ACTION)){
							deviceManager.saveAllMeasure();
							RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
						}
					}

					break;
				case DialogInterface.BUTTON_NEGATIVE:
					removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
					if(action.equals(SEND_ALL_ACTION)){
		                measureDataBundle.putBoolean(GWConst.IS_SEND_MEASURE, false);
		                measureDataBundle.putBoolean(GWConst.IS_SAVE_MEASURE, true);
		            	showDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
					} else if(action.equals(SAVE_ALL_ACTION)){

						RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
						setOperationCompleted();
					}
					break;
				case DialogInterface.BUTTON_NEUTRAL:
					removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
					Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
					selectPatientIntent.putExtra(GWConst.USER_ID, deviceManager.getCurrentUser().getId());
					startActivityForResult(selectPatientIntent, PATIENT_SELECTION_SEND_ALL);
					break;
    			}
			}
			else {
				//Identifico quale button � stato premuto dall'utente
				switch(which) {
				case DialogInterface.BUTTON_POSITIVE:
					removeDialog(PROGRESS_DIALOG);
					removeDialog(MEASURE_RESULT_DIALOG);
					if(action.equals(SEND_ACTION)){
						dataBundle = new Bundle();

						if (deviceManager.getCurrentDevice().getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA))
							dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgTrasfImg"));
						else
							dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgTrasf"));

						dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
						dataBundle.putBoolean(GWConst.IS_SEND_MEASURE, true);
						showDialog(PROGRESS_DIALOG);
						Log.i(TAG, "Invio delle misure in corso");
						measureManager.setSendedFromDB(false);
			        	try {
			        		changeConnectionSettings(false);
						} catch (Exception e) {
							setOperationCompleted();
							showErrorDialog(ResourceManager.getResource().getString("SEND_ERROR"));
						}
					} else if(action.equals(SAVE_ACTION)){
						measureManager.saveMeasureData("n");
						RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
					}
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					removeDialog(MEASURE_RESULT_DIALOG);
					if(action.equals(SEND_ACTION)){
		                measureDataBundle.putBoolean(GWConst.IS_SEND_MEASURE, false);
		                measureDataBundle.putBoolean(GWConst.IS_SAVE_MEASURE, true);
		            	showDialog(MEASURE_RESULT_DIALOG);
					} else if(action.equals(SAVE_ACTION)){

						RecoveryManager.getInstance(getApplicationContext()).deleteAllMeasures();
						setOperationCompleted();
					}
					break;
				case DialogInterface.BUTTON_NEUTRAL:
					removeDialog(MEASURE_RESULT_DIALOG);
					Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
					selectPatientIntent.putExtra(GWConst.USER_ID, deviceManager.getCurrentUser().getId());
					startActivityForResult(selectPatientIntent, PATIENT_SELECTION_2);
					break;
				}
			}
		}
	}

	private BroadcastReceiver bcReceiver4Discoverability = null;

	//BroadcastReceiver for Discoverability
    //-------------------------------------
    private class MyBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
            	int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                Log.i(TAG, "MyBroadcastReceiver: newState = " + newState);
                if (newState != -1 && newState != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                	Log.i(TAG, "Device not discoverable anymore");
                    onDiscoverabilityEnd();
                }
            }
        }
    }

	private void registerBroadcastReceiver(){
		//to be sure no broadcastreceiver is registred twice:
        unregisterReceiver();

	    // Register the BroadcastReceiver
	    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
	    bcReceiver4Discoverability = new MyBroadcastReceiver();
	    Log.i(TAG, "registerBroadcastReceiver: ACTION_SCAN_MODE_CHANGED");
	    MyDoctorApp.getContext().registerReceiver(bcReceiver4Discoverability, filter);
	}

	private void unregisterReceiver() {
		try{
			MyDoctorApp.getContext().unregisterReceiver(bcReceiver4Discoverability);
			Log.i(TAG, "Receiver Unregistered");
		} catch(Exception e){
			Log.w(TAG, e.getMessage());
		}
		bcReceiver4Discoverability = null;
	}

	private void requestDiscoverability() {
		Log.i(TAG, "requestDiscoverability");

		registerBroadcastReceiver();

		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_DURATION);
		startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
	}

	private void onDiscoverabilityEnd(){
			unregisterReceiver();
			Log.i(TAG, "onDiscoverabilityEnd: No need to be discoverable again");
	}

	private void requestEnableBT() {
		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
	}

	private void showErrorDialog(String msg){
		dataBundle = new Bundle();
		Log.i(TAG, "showErrorDialog: " + msg);
		dataBundle.putString(GWConst.MESSAGE, msg);
		showDialog(ALERT_DIALOG);
	}

	private void showErrorDialogWithSave(String msg) {
		if(isAR){
			//Salviamo la misura senza chiedere conferma con la dialog
			measureManager.saveMeasureData("n");
		} else {
			dataBundle = new Bundle();
			Log.i(TAG, "showErrorDialog: " + msg);
			dataBundle.putString(GWConst.MESSAGE, msg);
			showDialog(ALERT_DIALOG_WITH_SAVE);
		}
	}

	/*private void checkActiveUser() throws DbException{

		Log.d(TAG, "checkActiveUser()");

		try {
			User activeUser = DbManager.getDbManager().getActiveUser();

			if(activeUser == null || !activeUser.getHasAutoLogin()){
				mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
			} else {

				if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT + activeUser.getId(), false)) {
					mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
				}
				else {
					checkAutoUpdate();
				}
				//	userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
			}
		} catch (Exception e) {
			throw new DbException(e.getMessage());
		}
	}*/

	private void checkActiveUser() throws DbException{

		Log.d(TAG, "checkActiveUser()");

		try {
			String lastUserId = Util.getRegistryValue(Util.KEY_LAST_USER);
			User lastUser = null;
			if ( lastUserId.length() > 0 ) {
				lastUser = DbManager.getDbManager().getUser(lastUserId);
			}

			if( lastUser == null ){
				mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
			} else if ( !lastUser.getHasAutoLogin() ) {
				//L'utente non ha l'autologin quindi appare dialog di inserimento precompilata
				isForcedRelogin = true;
				userDataBundle = new Bundle();
				userDataBundle.putBoolean("CHANGEABLE", false);
				userDataBundle.putString("LOGIN", lastUser.getLogin());

				mUIHandler.sendEmptyMessage(PRECOMPILED_LOGIN_DIALOG);

			} else {
				if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT + lastUser.getId(), false)) {
					mUIHandler.sendEmptyMessage(LOGIN_DIALOG);
				} else {
					checkAutoUpdate();
				}
			}
		} catch (Exception e) {
			throw new DbException(e.getMessage());
		}
	}

	private void changeConnectionSettings(final boolean isConfiguration) {
		showDialog(PROGRESS_DIALOG);
		Thread t = new Thread() {
			@Override
			public void run() {
				ConnectionManager.getConnectionManager(getApplicationContext()).changeConnection(isConfiguration);
			}
		};
		t.start();
	}

	private void doRevertToDefaultConnectionSettings() {
		Log.i(TAG, "doRevertToDefaultConnectionSettings()");

		if (Util.getRegistryValue(Util.APN_RESET_KEY, false))
			ConnectionManager.getConnectionManager(getApplicationContext()).resetDefaultConnection();
		Util.setRegistryValue(Util.APN_RESET_KEY, false);
	}

	private void revertToDefaultConnectionSettings() {
		// ConnectionManager.getConnectionManager(getApplicationContext()).resetDefaultConnection();
		Log.i(TAG, "revertToDefaultConnectionSettings() ignorato!");
	}

	private void stopConnection() {
		ConnectionManager.getConnectionManager(getApplicationContext()).stopOperation();
	}

	private void askPrePostPrandialGlycaemia() {
		askPrePostPrandialGlycaemia(true);
	}

	private void askPrePostPrandialGlycaemia(final boolean startMeasure) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle("");
    	builder.setCancelable(false);
		builder.setMessage(ResourceManager.getResource().getString("KPrePostMsgCGE2Pro"));
		builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseMeasureGlyPREBtn"),
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface arg0, int arg1) {

						if (startMeasure)
							deviceManager.startMeasure();
						else
							deviceManager.finalizeMeasure();
					}
		});
		builder.setNegativeButton(ResourceManager.getResource().getString("EGwnurseMeasureGlyPOSTBtn"),
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface arg0, int arg1) {

						if (startMeasure)
							deviceManager.startMeasure();
						else
							deviceManager.finalizeMeasure();
					}
		});
		builder.show();
	}

	private void showSHealthNotification() {
		final Dialog infoSHealthDialog = new Dialog( this );
		infoSHealthDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		infoSHealthDialog.setContentView(R.layout.shealth_notification_disclaimer);
		infoSHealthDialog.setCancelable(false);

		Button connectionButton = (Button) infoSHealthDialog.findViewById( R.id.shealthConnectionButton );
		connectionButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				infoSHealthDialog.dismiss();
				deviceManager.confirmDialog();
			}
		});

		//infoSHealthDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		infoSHealthDialog.show();
	}

	
	private void askSomething(String message, String positive, String negative) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle("");
		builder.setMessage(message);
		builder.setPositiveButton(positive, 
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface arg0, int arg1) {		
						deviceManager.confirmDialog();
					}
		});
		builder.setNegativeButton(negative, 
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface arg0, int arg1) {		
						deviceManager.cancelDialog();
						removeDialog(PROGRESS_DIALOG);						
					}
		});
		builder.setCancelable(true);
		builder.show();		
	}
	
	public void doManualMeasure() {
		// If BT is not on, request that it be enabled.
		// startManualMeasure() will then be called during onActivityResult
		startManualMeasure();
		/*
		if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			isPairing = false;
			isConfig = false;
			isManualMeasure = true;
		    requestEnableBT();
		} else {
			startManualMeasure();
		}
		*/		
	}

	private void startManualMeasure() {		
		if (UserManager.getUserManager().getCurrentPatient() == null) {
			selectPatient();
		}
		showManualMeasureDialog();
	}
	
	private void showManualMeasureDialog() {		
		if(UserManager.getUserManager().getCurrentPatient() != null){
			UserDevice uDevice = deviceMap.get(selectedMeasureType);
			//We set the current device in device Manager 
			deviceManager.setCurrentDevice(uDevice);	
			if(uDevice.getMeasure().equalsIgnoreCase(GWConst.KMsrTemp)){					
				//showManualTemperatureDialog();
				showManualTemperatureActivity();
			}
		}
	}
	
	/*private void showCalibrateDialog(final boolean doMeasure, final boolean doScan) {
		CalibrateDialog calibrateDialog = new CalibrateDialog(this, new CalibrateDialogListener() {
			@Override
			public void onCalibrationConfirmed() {
				
				if (doMeasure)
					doMeasure();
				
				if (doScan)
					doScan();
			}
		});
		calibrateDialog.show();		
	}*/
	
	/*private void showManualTemperatureDialog(){
		ManualTemperatureDialog mtDialog = new ManualTemperatureDialog(this, new ManualMeasureDialogListener() {			
			@Override
			public void onOkClick(String measure, ArrayList<String> values, int battery) {							
				sendManualMeasure(measure, values, battery);
			}
			
			@Override
			public void onCancelClick() {
				cancelManualMeasure();
			}
		});
		mtDialog.show();		
	}*/
	
	private void showCalibrateActivity(boolean doMeasure, boolean doScan) {
		if( doMeasure && doScan ) {
			calibrateState = TCalibrateState.EMeasureOn_ScanOn;
		} else if( doMeasure && !doScan ) {
			calibrateState = TCalibrateState.EMeasureOn_ScanOff;
		} else if( !doMeasure && doScan ) {
			calibrateState = TCalibrateState.EMeasureOff_ScanOn;
		} else if( !doMeasure && !doScan ) {
			calibrateState = TCalibrateState.EMeasureOff_ScanOff;
		}		
		
		Intent intent = new Intent( DeviceList.this, CalibrateActivity.class );			
		startActivityForResult( intent, CALIBRATE_ENTRY );
	}
	
	private void showManualTemperatureActivity(){
		Intent intent = new Intent( DeviceList.this, ManualTemperatureActivity.class );			
		startActivityForResult( intent, MANUAL_MEASURE_ENTRY );
	}
		
	public void sendManualMeasure(String measure, ArrayList<String> values, int battery) {
		XmlManager xmlManager = XmlManager.getXmlManager();
		xmlManager.setFileContent(null);
		xmlManager.setFileType(null);
		xmlManager.setXmlCmd(getDeviceType(measure), BluetoothAdapter.getDefaultAdapter().getAddress(), 
				values, battery, ResourceManager.getResource().getString("KMsgManualMeasure"));
		ArrayList<String> tmpLab = new ArrayList<String>();
		tmpLab.add(ResourceManager.getResource().getString("EGwMeasureTemperature"));
		ArrayList<String> tmpVal = new ArrayList<String>();
		tmpVal.add(values.get(0));
		deviceManager.showMeasurementResults(ResourceManager.getResource().getString("EGwnurseDeviceLabelTemperature"), tmpLab, tmpVal);
	}

	private TDeviceType getDeviceType(String measure) {
		if(measure.equalsIgnoreCase(GWConst.KMsrTemp)){
			return XmlManager.TDeviceType.TEMPERATURE_DT;
		} else {
			return null;
		}
	}
	
	public void cancelManualMeasure() {
		//We set the current device in device Manager 
		deviceManager.setCurrentDevice(null);	
	}

	private void refreshList() {
		HashMap<String, String> map = setFieldsMap(measureList.get(selectedMeasurePosition));
		fillMaps.remove(selectedMeasurePosition);
		fillMaps.add(selectedMeasurePosition, map);
		listAdapter.notifyDataSetChanged();
	}
	
	private void checkAutoUpdate() throws Exception {
		
		Log.d(TAG, "checkAutoUpdate()");
		
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				final User activeUser = DbManager.getDbManager().getActiveUser();				
				if(activeUser != null && activeUser.getHasAutoLogin()) {
					
					boolean autoUpdate = Util.getRegistryValue(Util.KEY_AUTO_UPDATE + "_" + activeUser.getId(), true);		
					long autoUpdateDate = Util.getRegistryLongValue(Util.KEY_AUTO_UPDATE_DATE + "_" + activeUser.getId());
					
					if (autoUpdateDate == 0)
						Util.setRegistryValue(Util.KEY_AUTO_UPDATE_DATE + "_" + activeUser.getId(), Long.toString(new Date().getTime()));
					
					int diffHours = Util.getDiffHours(new Date().getTime(), autoUpdateDate);	
					
					Log.d(TAG, "diffHours=" + diffHours + " autoUpdate=" + autoUpdate + " autoUpdateDate=" + autoUpdateDate);

					if (diffHours > (24 * 7)) {
						
						if (autoUpdate) {
							runningConfig = true;
					    	runningConfigUpdate = true;
					    	//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
					    	dataBundle = new Bundle();
							dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgConf"));
							dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, false);
							dataBundle.putBoolean(GWConst.IS_CONFIGURATION, true);
							loginET = null;
							pwdET = null;
							
							isForcedRelogin = true;
							
							changeConnectionSettings(true);
						}
						else {
											
							AlertDialog dialog = new AlertDialog.Builder(DeviceList.this).create();
							dialog.setTitle(ResourceManager.getResource().getString("autoUpdateSetting"));
							SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
							
							dialog.setMessage(String.format(ResourceManager.getResource().getString("autoUpdate"), sdf.format(new Date(autoUpdateDate))));			
							dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
									ResourceManager.getResource().getString("EGwnurseCancel"),  
									new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {				
									
									try {
										userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
									} catch (Exception e) {
										Log.e(TAG, "logInUserFromDb: " + e);
									}
									/*
									if (!UserManager.getUserManager().getCurrentUser().getIsPatient()) {	
										startSelectPatientActivity();
									}
									*/							
								} });
							
							dialog.setButton(DialogInterface.BUTTON_POSITIVE, 
									ResourceManager.getResource().getString("BTDeviceScoutingDialog.updateBtn"), 
									new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									runningConfig = true;
							    	runningConfigUpdate = true;
							    	//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
							    	dataBundle = new Bundle();
									dataBundle.putString(GWConst.MESSAGE, ResourceManager.getResource().getString("KMsgConf"));
									dataBundle.putBoolean(GWConst.MESSAGE_CANCELLABLE, true);
									dataBundle.putBoolean(GWConst.IS_CONFIGURATION, true);
									loginET = null;
									pwdET = null;
									
									isForcedRelogin = true;
									//loginET.setText(activeUser.getLogin());
									//pwdET.setText(activeUser.getPassword());
									
									changeConnectionSettings(true);
								}
							});
							dialog.show();
						}
					}
					else {
						
						try {
							userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
						} catch (Exception e) {
							Log.e(TAG, "logInUserFromDb: " + e);
						}
					}
				}		
			}
		});
			
	}

}