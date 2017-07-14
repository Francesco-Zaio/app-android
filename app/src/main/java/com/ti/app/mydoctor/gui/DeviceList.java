package com.ti.app.mydoctor.gui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
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
import android.util.SparseIntArray;
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
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.MeasureDetail;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.devicemodule.DeviceManager;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.DeviceListAdapter;
import com.ti.app.mydoctor.gui.listadapter.MainMenuListAdapter;
import com.ti.app.mydoctor.util.DialogManager;
import com.ti.app.mydoctor.util.Util;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;


@SuppressWarnings("deprecation")
public class DeviceList extends ActionBarActivity implements OnChildClickListener, DeviceListFragmentListener {
	private static final String TAG = "DeviceList";

    //Costants for Bundle
	private static final String VIEW_MEASURE = "VIEW_MEASURE";
	private static final String START_MEASURE = "START_MEASURE";
	private static final String POSITION = "POSITION";
	
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
	private static final int STATUS_DIALOG = 6;
	private static final int LOGIN_DIALOG = 8;
	private static final int SIMPLE_DIALOG_WITH_CLOSE = 10;
	private static final int CONFIRM_PATIENT_DIALOG = 11;
	private static final int LIST_OR_NEW_USER_DIALOG = 13;
	private static final int SIMPLE_DIALOG_WITHOUT_CLOSE = 14;
	private static final int PRECOMPILED_LOGIN_DIALOG = 16;
	private static final int CONFIRM_CLOSE_DIALOG = 18;
	private static final int MEASURE_RESULT_DIALOG_SEND_ALL = 19;
    
    private static final String SEND_STATUS = "SEND_STATUS";
	private static final String SAVE_STATUS = "SAVE_STATUS";

	private static final int DISCOVERABLE_DURATION = 120;

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
    private Menu mActionBarMenu;
	
    private boolean isPairing;
    private boolean isConfig;    
    private boolean isManualMeasure;    
    private boolean isAR;
    
    int sentMeasures;
    int receivedMeasures;

    private UserManager userManager;
    private MeasureManager measureManager;
	private DeviceManager deviceManager;

    private DeviceManagerMessageHandler deviceManagerHandler = new DeviceManagerMessageHandler(this);
    private UserManagerMessageHandler userManagerHandler = new UserManagerMessageHandler(this);
    private UIHandler mUIHandler = new UIHandler(this);

	private String selectedMeasureType;
	private int selectedMeasurePosition;
	
	private int selectedModelPosition = -1;

	private DeviceListAdapter listAdapter;

	private List<HashMap<String, String>> fillMaps;

    private static Bundle dataBundle;
    //Bundle che contiene l'ultima misura effettuata dall'utente
    private Measure measureData;
	
	private Bundle viewMeasureBundle;
	private Bundle startMeasureBundle;
	private Bundle userDataBundle;

	private HashMap<String, UserDevice> deviceMap;
	private List<String> measureList;
	private HashMap<String, List<UserDevice>> measureModelsMap;

	private boolean runningConfig;

	private GWTextView patientNameTV;
	
	private String[] patients;
	private List<UserPatient> patientList;
		
	public static DeviceList ACTIVE_INSTANCE;

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

			Bundle extra = intent.getExtras();
			if (extra != null) {
				Log.d(TAG, "onCreate() extra()");

				Set<String> keys = extra.keySet();
				for (String k : keys) {

					Log.d(TAG, "onCreate() extra.key=" + k + " value=" + intent.getExtras().getByte(k));
				}
			}
		}

		//La tastiera viene aperta solo quando viene selezionata una edittext
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		//Flag per mantenere attivo lo schermo finch� l'activity � in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Inizializza l'ActionBAr
		ActionBar customActionBar = getSupportActionBar();
        if (customActionBar != null) {
            //Setta il gradiente di sfondo della action bar
            Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
            customActionBar.setBackgroundDrawable(cd);
            customActionBar.setDisplayShowCustomEnabled(true);
            customActionBar.setDisplayShowTitleEnabled(false);
            //Abilita l'icona dell'actionbar ad attivare il menu laterale
            customActionBar.setDisplayHomeAsUpEnabled(true);
            customActionBar.setHomeButtonEnabled(true);
            //Setta l'icon
            customActionBar.setIcon(R.drawable.icon_action_bar);
            //Ricava la TextView dell'ActionBar
            LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View titleView = inflator.inflate(R.layout.actionbar_title, null);
            titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
            titleTV.setText(R.string.app_name);
            customActionBar.setCustomView(titleView);
        }

		//Ottengo il riferimento agli elementi che compongono la view
		mDrawerLayout = (DrawerLayout) findViewById(R.id.device_list_drawer_layout);
		mMenuDrawerList = (ExpandableListView) findViewById(R.id.device_list_left_menu);

        // set a custom shadow that overlays the main content when the drawer opens
		// setta il background del menu laterale
        mDrawerLayout.setDrawerShadow(R.drawable.background_lateral_menu, GravityCompat.START);
        // prepara la lista del menu e setta il listener
        setupMenuListView();

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

		deviceManager = MyDoctorApp.getDeviceManager();
		deviceManager.setHandler(deviceManagerHandler);

		measureManager = MeasureManager.getMeasureManager();

		userManager = UserManager.getUserManager();
		userManager.setHandler(userManagerHandler);

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

        } else {
            new InitTask().execute();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST:
                for (int result: grantResults)
                    if (result != PackageManager.PERMISSION_GRANTED)
                        finish();
                new InitTask().execute();
        }
    }

	private static class UIHandler extends Handler {
        private final WeakReference<DeviceList> mActivity;

        UIHandler(DeviceList activity) {
            mActivity = new WeakReference<>(activity);
        }

		@Override
		public void handleMessage(Message msg) {
            DeviceList activity = mActivity.get();
            if (activity != null) {
                switch(msg.what) {
                    case LOGIN_DIALOG:
                        activity.showDialog(LIST_OR_NEW_USER_DIALOG);
                        break;
                    case PRECOMPILED_LOGIN_DIALOG:
                        activity.showDialog(PRECOMPILED_LOGIN_DIALOG);
                        break;
                }
            }
		}
	}

    private class InitTask extends AsyncTask<Void, Void, Void> {

        private boolean errorFound;

        @Override
        protected Void doInBackground(Void... unused) {

            try {
                errorFound = false;
                MyDoctorApp.getConfigurationManager().init();
                checkActiveUser();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "ERROR on doInBackground: " + e.getMessage());
                dataBundle = new Bundle();
                dataBundle.putString(AppConst.MESSAGE, e.getMessage());
                errorFound = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            if(errorFound)
                showDialog(SIMPLE_DIALOG_WITH_CLOSE);
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

		try {
			deviceManager.setHandler(deviceManagerHandler);
			userManager.setHandler(userManagerHandler);
		} catch (Exception e) {
			Log.e(TAG, "ERROR on onResume: " + e.getMessage());
		}


		if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false) && UserManager.getUserManager().getCurrentPatient() != null) {

    		DialogManager.showToastMessage(DeviceList.this, AppResourceManager.getResource().getString("userBlocked"));
			Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);


			measureList = new ArrayList<>();
			setupView();

			resetView();

			UserManager.getUserManager().setCurrentPatient(null);

			doLogout();
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
		UserManager.getUserManager().setCurrentPatient(null);

		if (DbManager.getDbManager() != null) {
			User activeUser = DbManager.getDbManager().getActiveUser();
			if (activeUser != null) {
				Log.d(TAG, "auto=" + activeUser.getHasAutoLogin());
				Log.d(TAG, "exit=" + activeUser.getLogin());

				if( !activeUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) )
					Util.setRegistryValue(Util.KEY_LAST_USER, activeUser.getId());
			}
		}

		DbManager.getDbManager().close();
		AppResourceManager.getResource().closeResource();
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
                    selectItem(tv);
                    return true;
                }
            }

        });

	}

	private void prepareMenuListView() {
		User loggedUser;
		synchronized (this) {
            loggedUser = DbManager.getDbManager().getCurrentUser();
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

		if(loggedUser == null || (loggedUser.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID )) ){
			iconGroupArray.remove(4); //remove icona Logout*
			iconGroupArray.remove(0); //remove icona Misure

			labelGroupArray.remove(4); //remove etichetta Logout*
			labelGroupArray.remove(0); //remove etichetta Misure

			if(DbManager.getDbManager().getNotLoggedUsers().size() != 0) {
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
			String idUser = loggedUser.getId();


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

			if(DbManager.getDbManager().getNotLoggedUsers().size() == 0) {
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

		TextView tv = (TextView)v.findViewById(R.id.childname);
		selectItem(tv);
		return true;
	}

	private void selectItem(TextView tv) {

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
                        dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
                        showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
                    }
                    else {
                        viewMeasureBundle = new Bundle();
                        viewMeasureBundle.putBoolean(VIEW_MEASURE, true);
                        viewMeasureBundle.putInt(POSITION, -1);
                        startSelectPatientActivity();
                    }
                }
                else {
                    Log.i(TAG, "Mostrare le misure di " + patientNameTV.getText().toString());
                    intent = new Intent(DeviceList.this, ShowMeasure.class);
                    intent.putExtra(ShowMeasure.SHOW_MEASURE_KEY, ShowMeasure.ALL_MEASURES);
                    startActivity(intent);
                }
                break;
            case ITEM_USER_UPDATES:
                runningConfig = true;
                //l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
                dataBundle = new Bundle();
                dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("KMsgConf"));
                dataBundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, true);
                dataBundle.putBoolean(AppConst.IS_CONFIGURATION, true);
                loginET = null;
                pwdET = null;
                doLogin();
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
                User loggedUser = DbManager.getDbManager().getCurrentUser();
                if( loggedUser == null || GWConst.DEFAULT_USER_ID.equalsIgnoreCase(loggedUser.getId())){
                    //Non ci sono utenti registrati, quindi crea l'utente di default
                    try {
                        DbManager.getDbManager().createDefaultUser();
                    } catch (DbException e) {
                        e.printStackTrace();
                        showErrorDialog(AppResourceManager.getResource().getString("errorDb"));
                    }

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
                    Log.e(TAG, e.getMessage());
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
                        setCurrentDevice();
                        selectPatient();
                    }
                } else {
                    Log.i(TAG, "operation running: click ignored");
                    Toast.makeText(getApplicationContext(), AppResourceManager.getResource().getString("KOperationRunning"), Toast.LENGTH_SHORT).show();
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

		fillMaps = new ArrayList<>();
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
                    e.printStackTrace();
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
						setCurrentDevice();
						selectPatient();
					}
				} else {
					Log.i(TAG, "operation running: click ignored");
					Toast.makeText(getApplicationContext(), AppResourceManager.getResource().getString("KOperationRunning"), Toast.LENGTH_SHORT).show();
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
				dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
				showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
			}
			else {
				startMeasureBundle = new Bundle();
				startMeasureBundle.putBoolean(START_MEASURE, true);
				startSelectPatientActivity();
			}
		}
		else {
			if(measureEnabled(deviceMap.get(selectedMeasureType))){

				if(isAR){
					//Chiedo conferma per stop server
					AlertDialog.Builder builder = new AlertDialog.Builder(DeviceList.this);
					builder.setMessage(AppResourceManager.getResource().getString("KConfirmStopStm"));
					builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseOk"), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							stopDeviceOperation(-1);
							refreshList();
							isAR = false;
						}
					});
					builder.setNegativeButton(AppResourceManager.getResource().getString("EGwnurseCancel"), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						}
					});
					builder.setTitle(AppResourceManager.getResource().getString("KTitleStm"));
					builder.show();

				} else {

					deviceManager.checkIfAnotherSpirodocIsPaired(deviceManager.getCurrentDevice().getDevice().getModel());
					try {
						initDeviceMap();
					} catch (DbException e) {
						e.printStackTrace();
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
                            Log.e(TAG, e.getMessage());
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
		HashMap<String, String> map = new HashMap<>();
		if(deviceManager.isOperationRunning() && !isConfig && !isPairing){
			map.put(KEY_ICON, "" + Util.getIconRunningId(measureType));
		} else {
			map.put(KEY_ICON, "" + Util.getIconId(measureType));
		}
		map.put(KEY_LABEL, setupFeedback(AppResourceManager.getResource().getString("measureType." + measureType)));
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
			fitTextInPatientNameLabel(AppResourceManager.getResource().getString("selectPatient"));
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
				inflater.inflate(R.menu.context_menu_manual_insert, menu);
				menu.setHeaderTitle(AppResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(userManager.getCurrentUser().getId(), null, null, selectedMeasureType, userManager.getCurrentPatient().getId(), MeasureManager.BooleanFilter.ignore);
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
				inflater.inflate(R.menu.context_menu_no_pair_device, menu);
				menu.setHeaderTitle(AppResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(userManager.getCurrentUser().getId(), null, null, selectedMeasureType, userManager.getCurrentPatient().getId(), MeasureManager.BooleanFilter.ignore);
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
			} else {
				inflater.inflate(R.menu.context_menu_pair_device, menu);
				menu.setHeaderTitle(AppResourceManager.getResource().getString("measureType." + selectedMeasureType));
				//menu.setHeaderIcon(Util.getIconId(selectedMeasureType));
				menu.setHeaderIcon(Util.getSmallIconId(selectedMeasureType));

				if (userManager.getCurrentPatient() != null) {
					ArrayList<Measure> patientMeasures = measureManager.getMeasureData(userManager.getCurrentUser().getId(), null, null, selectedMeasureType, userManager.getCurrentPatient().getId(), MeasureManager.BooleanFilter.not);
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
			}

			if (pd.getDevice().getModel().equalsIgnoreCase(GWConst.KCAMERA)) {
				menu.setGroupVisible(R.id.pair_group, false);
				menu.setGroupVisible(R.id.new_device_group, false);
				menu.setGroupVisible(R.id.new_device_first_run_group, false);
			}
		} catch (DbException e) {
			e.printStackTrace();
			showErrorDialog(AppResourceManager.getResource().getString("errorDb"));
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

    	setCurrentDevice();

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
		    case R.id.new_device:
			case R.id.new_device_only_association:
		    	doNewDevice();
		    	return true;
		    case R.id.show_measure:
		    	if(patientNameTV.getText().toString().trim().equals(getText(R.string.selectPatient))) {

		    		if (patients == null || patients.length == 0) {
		    			dataBundle = new Bundle();
						dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
						showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
		    		}
		    		else {
		    			viewMeasureBundle = new Bundle();
			    		viewMeasureBundle.putBoolean(VIEW_MEASURE, true);
			    		viewMeasureBundle.putInt(POSITION, info.position);
			    		startSelectPatientActivity();
		    		}
		    	}
		    	else
		    		showMeasures(info.position);
		    	return true;
		    case R.id.select_model:
		    	showSelectModelDialog();
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
		}
		try {
            DbManager.getDbManager().setCurrentUser(null);
        } catch (DbException e) {
            Log.e(TAG, "DbManager.getDbManager().setCurrentUser(null): Error");
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

	private void startScan() {
		if (!Util.isGlucoTelDevice(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isC40(deviceManager.getCurrentDevice().getDevice())
				&&
				!Util.isCamera(deviceManager.getCurrentDevice().getDevice())
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
		if(viewMeasureBundle != null && viewMeasureBundle.getInt(POSITION) == -1)
			myIntent.putExtra(ShowMeasure.SHOW_MEASURE_KEY, ShowMeasure.ALL_MEASURES);
		else {
			myIntent.putExtra(ShowMeasure.SHOW_MEASURE_KEY, AppResourceManager.getResource().getString("measureType." + measureList.get(position)));
			myIntent.putExtra(ShowMeasure.SELECTED_MEASURE_KEY, measureList.get(position));
		}
		startActivity(myIntent);
	}

	private void showUsers(int intentMode) {
		Intent myIntent = new Intent(this, UsersList.class);
		if( intentMode == USER_SELECTION ) {
			//Richiesta elenco utenti per nuova login
			//Disabilito la funzionalit� di cancellazione utente
			//nella activity UserList
			myIntent.putExtra(UsersList.ENABLE_DELETE_USER_ID, 0);
		} else {
			myIntent.putExtra(UsersList.ENABLE_DELETE_USER_ID, 1);
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

		Log.d(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

    	switch(requestCode) {

    	case USER_LIST:
    		if(resultCode == RESULT_OK){
	    		if(data != null){
	    			Bundle extras = data.getExtras();
	    			User user = (User) extras.get(UsersList.SELECTED_USER);
                    if (user == null) {
                        break;
                    }
	    			Log.i(TAG, "Login utente " + user.getName() + " da db");
	    			if(!user.getHasAutoLogin() ||  Util.getRegistryValue(Util.KEY_FORCE_LOGOUT + user.getId(), false)) {
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
    			showErrorDialog(AppResourceManager.getResource().getString("errorDb"));
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
							userDataBundle = new Bundle();
							userDataBundle.putBoolean("CHANGEABLE", false);
							userDataBundle.putString("LOGIN", user.getLogin());
							showDialog(PRECOMPILED_LOGIN_DIALOG);
						}
					}
	    			else
	    				userManager.selectUser((User)null);
	    		} else {
	    			showDialog(LOGIN_DIALOG);
	    		}
    		} else if(resultCode == UsersList.RESULT_DB_ERROR){
    			showErrorDialog(AppResourceManager.getResource().getString("errorDb"));
    		} else {
    			showDialog(PRECOMPILED_LOGIN_DIALOG);
    		}
    		break;
    	case PATIENT_SELECTION:
    		if (resultCode == RESULT_OK) {
    			if (data != null) {
    				Bundle extras = data.getExtras();
    				String patientName = extras.getString(SelectPatient.PATIENT);
    				String patientID = extras.getString(SelectPatient.PATIENT_ID);
    				fitTextInPatientNameLabel(patientName);

					Patient p = DbManager.getDbManager().getPatientData(patientID);
					Log.i(TAG, "Selezionato il paziente " + p.getName() + " " + p.getSurname());
					UserManager.getUserManager().setCurrentPatient(p);
    				if(viewMeasureBundle != null && viewMeasureBundle.getBoolean(VIEW_MEASURE, false)) {
						Log.d(TAG, "Visualizzo le misure di " + p.getName() + " " + p.getSurname());
						showMeasures(viewMeasureBundle.getInt(POSITION));
					}
					else if (startMeasureBundle != null && startMeasureBundle.getBoolean(START_MEASURE, false)) {
						Log.d(TAG, "Inizio la misura di " + p.getName() + " " + p.getSurname());
						if(measureEnabled(deviceMap.get(selectedMeasureType))){
							doMeasure();
						} else {
							doScan();
						}
					}
    			}
    		}
    		break;
    	case PATIENT_SELECTION_2:
    		if (resultCode == RESULT_OK) {
    			if (data != null) {
    				Bundle extras = data.getExtras();
    				String patientName = extras.getString(SelectPatient.PATIENT);
    				String patientID = extras.getString(SelectPatient.PATIENT_ID);
    				fitTextInPatientNameLabel(patientName);

					Patient p = DbManager.getDbManager().getPatientData(patientID);
					Log.i(TAG, "Selezionato il paziente " + p.getName() + " " + p.getSurname());
					UserManager.getUserManager().setCurrentPatient(p);
    			}
    		}
    		showDialog(MEASURE_RESULT_DIALOG);
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

    	         sendManualMeasure(measure, values);
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
		titleTV.setText(R.string.app_name);

		if (fillMaps != null)
			fillMaps.clear();
		fitTextInPatientNameLabel(AppResourceManager.getResource().getString("selectPatient"));
		if (listAdapter != null)
			listAdapter.notifyDataSetChanged();
	}

	private Dialog createAlertDialog(Bundle data) {
    	String msg = data.getString(AppConst.MESSAGE);
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

		final SparseIntArray mapPosition = new SparseIntArray(userDevices.size());
 		List<String> nal = new ArrayList<>();

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
                        Log.e(TAG, e.getMessage());
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

		    	selectedModelPosition = mapPosition.get(item);
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

		String msg = data.getString( AppConst.MESSAGE );
		Log.d(TAG, "createProgressDialog msg=" + msg);

        progressDialog.setIndeterminate(true);
		progressDialog.setCancelable(false);

		progressDialog.setMessage(data.getString(AppConst.MESSAGE));
		progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, AppResourceManager.getResource().getString("EGwnurseCancel"),  new ProgressDialogClickListener());

		if(deviceMap != null && selectedMeasureType != null &&
				(deviceMap.get(selectedMeasureType) != null && Util.isStmDevice(deviceMap.get(selectedMeasureType).getDevice())) &&
				!isPairing && isConfig && isManualMeasure) {

			progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, AppResourceManager.getResource().getString("DeviceListView.configureBtn"),
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
					dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
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
						dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
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
		selectPatientIntent.putExtra(SelectPatient.USER_ID, currentUser.getId());
		startActivityForResult(selectPatientIntent, PATIENT_SELECTION);
	}

	private String getMeasureMessage(Measure data) {

        String msg = "";
        Vector<MeasureDetail> mdv = MeasureDetail.getMeasureDetails(data);
        for (MeasureDetail md: mdv) {
            msg += md.getName() + ": " + md.getValue() + " " + md.getUnit() + "\n";
        }
		return msg;
	}

    private static class DeviceManagerMessageHandler extends Handler {
        private final WeakReference<DeviceList> mActivity;

        DeviceManagerMessageHandler(DeviceList activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
		public void handleMessage(Message msg) {
            DeviceList activity = mActivity.get();
            if (activity == null)
                return;

        	dataBundle = msg.getData();

        	Log.d(TAG, "DeviceManagerMessageHandler d=" + dataBundle);

            switch (msg.what) {
                case DeviceManager.MESSAGE_STATE:
                    dataBundle.putBoolean(AppConst.IS_MEASURE, true);
                    activity.showDialog(PROGRESS_DIALOG);
                    break;
                case DeviceManager.MESSAGE_STATE_WAIT:
                    dataBundle.putBoolean(AppConst.IS_MEASURE, true);
                    activity.showDialog(PROGRESS_DIALOG);
                    break;
                case DeviceManager.ASK_SOMETHING:
                    activity.askSomething(
                            dataBundle.getString(AppConst.ASK_MESSAGE),
                            dataBundle.getString(AppConst.ASK_POSITIVE),
                            dataBundle.getString(AppConst.ASK_NEGATIVE));
                    break;

                case DeviceManager.REFRESH_LIST:
                    activity.refreshList();

                    break;
                case DeviceManager.MEASURE_RESULT:
                    activity.receivedMeasures++;
                    activity.refreshList();
                    activity.closeProgressDialog();
                    activity.measureData = (Measure) msg.getData().getSerializable(DeviceManager.MEASURE);// MeasureManager.getMeasureManager().saveMeasureData(measureData);
                    activity.showDialog(MEASURE_RESULT_DIALOG);
                    break;
                case DeviceManager.ERROR_STATE:
                    activity.closeProgressDialog();
                    activity.showDialog(ALERT_DIALOG);
                    break;
                case DeviceManager.CONFIG_READY:
                    activity.refreshList();
                    activity.closeProgressDialog();
                    activity.showDialog(ALERT_DIALOG);
                    break;
            }
        }
    }

	private static class UserManagerMessageHandler extends Handler  {

        private final WeakReference<DeviceList> mActivity;

        UserManagerMessageHandler(DeviceList activity) {
            mActivity = new WeakReference<>(activity);
        }

		@Override
		public void handleMessage(Message msg) {

            DeviceList activity = mActivity.get();
            if (activity == null)
                return;

            activity.showWarningMessage();

			switch (msg.what) {
			case UserManager.USER_CHANGED:
				Log.d(TAG, " UserManager.USER_CHANGED: runningConfig=" + activity.runningConfig);
                activity.resetView();
				UserManager.getUserManager().setCurrentPatient(null);
				Log.i(TAG, "userManangerHandler: user changed");

				try {
                    activity.titleTV.setText(UserManager.getUserManager().getCurrentUser().getName() + "\n" + UserManager.getUserManager().getCurrentUser().getSurname());
                    activity.fitTextInPatientNameLabel(activity.getString(R.string.selectPatient));
                    activity.removeDialog(PROGRESS_DIALOG);
                    activity.setupDeviceList();

					//Forzo la ricostruzione del menu
                    activity.supportInvalidateOptionsMenu();
				} catch (DbException e) {
                    activity.showErrorDialog(e.getMessage());
				}

				if (UserManager.getUserManager().getCurrentUser().getIsPatient()) {
                    activity.currentPatientLL.setVisibility(View.GONE);
				} else {
                    activity.currentPatientLL.setVisibility(View.VISIBLE);
				}

                activity.runningConfig = false;
				break;
			case UserManager.ERROR_OCCURED:
				Log.d(TAG, "UserManager.ERROR_OCCURED:");
				if (activity.runningConfig) {
					if (!Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {
						try {
							User activeUser = DbManager.getDbManager().getActiveUser();
							if (activeUser == null && (activity.loginET != null && activity.pwdET != null && activity.pwdET.getText().length() > 0)) {
								UserManager.getUserManager().logInUserFromDb(activity.loginET.getText().toString(), activity.pwdET.getText().toString());
                                activity.pwdET.setText("");
								break;
							}
							else if (activeUser != null){
                                activity.userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
							}
						} catch (Exception e) {
							Log.e(TAG, "logInUserFromDb: " + e);
						}
					}
				}
				Log.i(TAG, "userManangerHandler: error occurred");
                activity.removeDialog(PROGRESS_DIALOG);
				dataBundle = msg.getData();
				dataBundle.putBoolean(AppConst.LOGIN_ERROR, false);
                activity.showDialog(ALERT_DIALOG);
                activity.runningConfig = false;

				if (Util.getRegistryValue(Util.KEY_FORCE_LOGOUT, false)) {
					DialogManager.showToastMessage(activity, AppResourceManager.getResource().getString("userBlocked"));
					Util.setRegistryValue(Util.KEY_FORCE_LOGOUT, false);
                    activity.measureList = new ArrayList<>();
                    activity.setupView();
                    activity.resetView();
					UserManager.getUserManager().setCurrentPatient(null);
                    activity.setCurrentUserLabel("");
                    activity.currentPatientLL.setVisibility(View.GONE);
                    activity.doLogout();
		    	}
				break;
			case UserManager.LOGIN_FAILED:
				Log.e(TAG, "userManagerHandler: login failed");
                activity.removeDialog(PROGRESS_DIALOG);
				dataBundle = msg.getData();
				dataBundle.putBoolean(AppConst.LOGIN_ERROR, true);
                activity.showDialog(ALERT_DIALOG);
                activity.runningConfig = false;
				break;
            case UserManager.BAD_PASSWORD:
            case UserManager.USER_BLOCKED:
            case UserManager.USER_LOCKED:
                // TODO
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

            measureList = DbManager.getDbManager().getMeasureTypesForUser();
			initDeviceMap();
			initMeasureModelsMap(currentUser);

			patientList = DbManager.getDbManager().getUserPatients(currentUser.getId());
			if (patientList == null || patientList.size() == 0) {
				dataBundle = new Bundle();
				dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
				showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
			}
			else if (patientList.size() != 1) {
				setCurrentUserLabel(AppResourceManager.getResource().getString("selectPatient"));
				patients = new String[patientList.size()];
	        	int counter = 0;
	        	for (UserPatient up : patientList) {
	        		Patient p;

	        		p = DbManager.getDbManager().getPatientData(up.getIdPatient());
	        		patients[counter++] = "[" + p.getId() + "] " + p.getSurname() + " " + p.getName();
	        	}

	        	if (patients == null || patients.length == 0) {
	        		dataBundle = new Bundle();
					dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
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

    protected void showWarningMessage() {

    	if (Util.getRegistryValue(Util.KEY_WARNING_TIMESTAMP, false)) {

    		DialogManager.showToastMessage(DeviceList.this, AppResourceManager.getResource().getString("warningDate"));
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
    				msg = dataBundle.getString(AppConst.MESSAGE);
    			}
				if(!Util.isEmptyString(msg) && AppResourceManager.getResource().getString("KConnMsgZephyr").equalsIgnoreCase(msg)){
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
			if (DbManager.getDbManager().getNotLoggedUsers().size() != 0)
				builder.setItems(new String[] {getString(R.string.newUser), getString(R.string.users_title)}, list_or_new_user_dialog_click_listener);
			else
				builder.setItems(new String[] {getString(R.string.newUser)}, list_or_new_user_dialog_click_listener);
			return builder.create();
		case MEASURE_RESULT_DIALOG:
			Log.i(TAG, "Visualizzo dialog MEASURE_RESULT_DIALOG");
            return createMeasureResultDialog(measureData);
		case MEASURE_RESULT_DIALOG_SEND_ALL:
			Log.i(TAG, "Visualizzo dialog MEASURE_RESULT_DIALOG_SEND_ALL");
            return createMeasureResultDialog(measureData, PATIENT_SELECTION_SEND_ALL);
        case PROGRESS_DIALOG:
        	return createProgressDialog(dataBundle);
        case ALERT_DIALOG:
            return createAlertDialog(dataBundle);
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
        	if((DbManager.getDbManager().getNotLoggedUsers()).size() != 0) {
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
        	if((DbManager.getDbManager().getNotLoggedUsers()).size() != 0) {
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
        	builder.setMessage(AppResourceManager.getResource().getString("MainGUI.menu.fileMenu.exitMsg"));
        	builder.setPositiveButton(AppResourceManager.getResource().getString("okButton"), confirm_close_dialog_click_listener);
        	builder.setNegativeButton(AppResourceManager.getResource().getString("cancelButton"), confirm_close_dialog_click_listener);
        	return builder.create();
        case CONFIRM_PATIENT_DIALOG:
        	return createConfirmPatientDialog();
        case SIMPLE_DIALOG_WITH_CLOSE:
        	builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
        	builder.setNeutralButton("Ok", simple_dialog_with_close_click_listener);
        	beep();
        	return builder.create();
        case SIMPLE_DIALOG_WITHOUT_CLOSE:
        	builder.setMessage(dataBundle.getString(AppConst.MESSAGE));
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
        default:
            return null;
		}
	}

    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {
        if (dataBundle == null) {
            Log.e(TAG, "onPrepareDialog: dataBundle is null");
            return;
        }

    	if(isAR && deviceManager.isOperationRunning()){
    		Log.i(TAG, "onPrepareDialog bypassed");
            String msg = dataBundle.getString(SEND_STATUS);
            if(Util.isEmptyString(msg)){
                msg = dataBundle.getString(SAVE_STATUS);
            }
            if(Util.isEmptyString(msg)){
                msg = dataBundle.getString(AppConst.MESSAGE);
            }
            if(!Util.isEmptyString(msg) && AppResourceManager.getResource().getString("KConnMsgZephyr").equalsIgnoreCase(msg)){
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

		switch(id) {
        case PROGRESS_DIALOG:
            ((ProgressDialog)dialog).setMessage(dataBundle.getString(AppConst.MESSAGE));
        	if(deviceMap != null && selectedMeasureType != null && deviceMap.get(selectedMeasureType) != null &&
        			Util.isStmDevice(deviceMap.get(selectedMeasureType).getDevice()) && !isPairing && isConfig && isManualMeasure) {

    			progressDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    		}
        	if (dataBundle.getString(AppConst.MESSAGE).startsWith(AppResourceManager.getResource().getString("KReliabilityStm") + ":") &&
        			dataBundle.getString(AppConst.MESSAGE).endsWith("%")) {
	        	String rvalue = dataBundle.getString(AppConst.MESSAGE).split(":")[1].trim().split("%")[0].trim();
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
	               "<img src=\"file:///android_res/drawable/traffic" + traffic + ".png\" /><h1>" + AppResourceManager.getResource().getString("KReliabilityStm") +": <font color='" + color + "'>" +  rvalue + "%</font></h1>",
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

        	if(dataBundle.getBoolean(AppConst.MESSAGE_CANCELLABLE)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(true);//Visibility(View.VISIBLE);
    		} else {
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setEnabled(false);//setVisibility(View.INVISIBLE);
    		}
    		//Assegno un tag al button per poter gestire correttamente il click su Annulla
    		if(dataBundle.getBoolean(AppConst.IS_MEASURE)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setTag(AppConst.IS_MEASURE);
    		} else if(dataBundle.getBoolean(AppConst.IS_CONFIGURATION)){
    			((ProgressDialog)dialog).getButton(ProgressDialog.BUTTON_NEGATIVE).setTag(AppConst.IS_CONFIGURATION);
    		}
    		break;
        case ALERT_DIALOG:
            ((AlertDialog)dialog).setMessage(dataBundle.getString(AppConst.MESSAGE));
            break;
        default:
        	super.onPrepareDialog(id, dialog);
        }
	}

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
				dataBundle = new Bundle();
				dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("KMsgConf"));
				dataBundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, true);
				dataBundle.putBoolean(AppConst.IS_CONFIGURATION, true);
				runningConfig = true;
                doLogin();
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
	 * Listener per i click sulla dialog CONFIRM_CLOSE_DIALOG
	 */
	private DialogInterface.OnClickListener confirm_close_dialog_click_listener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale pulsante della dialog � stato premuto
			removeDialog(CONFIRM_CLOSE_DIALOG);
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				finish();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				break;
			}
		}
	};

	private void doLogin() {
        if(loginET == null || pwdET == null) {
            if (UserManager.getUserManager().getCurrentUser() == null) {
                Log.i(TAG, "DB getActiveUser:" + DbManager.getDbManager().getActiveUser());
                UserManager.getUserManager().selectUser(DbManager.getDbManager().getActiveUser(), true);
            }
            userManager.logInUser();
        }
        else
            userManager.logInUser(loginET.getText().toString(), pwdET.getText().toString());
    }

	private Dialog createConfirmPatientDialog() {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

    	builder.setTitle(AppResourceManager.getResource().getString("confirmPatient"));

    	Patient currentPatient = UserManager.getUserManager().getCurrentPatient();
    	String measureStr = AppResourceManager.getResource().getString("confirmChangeQuestion") + " " + currentPatient.getName() + " " + currentPatient.getSurname() + "?";
		builder.setMessage(measureStr);

    	builder.setPositiveButton(AppResourceManager.getResource().getString("yes"), confirm_patient_dialog_click_listener);
    	builder.setNegativeButton(AppResourceManager.getResource().getString("no"), confirm_patient_dialog_click_listener);
    	builder.setNeutralButton(R.string.changePatientQuestion, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				removeDialog(CONFIRM_PATIENT_DIALOG);
				Log.i(TAG, "Cambia paziente");

				if (patients == null || patients.length == 0) {
					dataBundle = new Bundle();
					dataBundle.putString(AppConst.MESSAGE, getString(R.string.noPatient));
					showDialog(SIMPLE_DIALOG_WITHOUT_CLOSE);
				}
				else {
					viewMeasureBundle = null;
					startMeasureBundle = new Bundle();
					startMeasureBundle.putBoolean(START_MEASURE, true);
					startSelectPatientActivity();
				}
			}
		});
    	builder.setCancelable(false);
    	return builder.create();
	}

	private Dialog createMeasureResultDialog(Measure data) {
		return createMeasureResultDialog(data, PATIENT_SELECTION_2);
	}

	private Dialog createMeasureResultDialog(Measure data, final int id) {
        measureData = data;

        Log.d(TAG, "createMeasureResultDialog id=" + id);

        Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);

        String keyMeasType = Util.KEY_MEASURE_TYPE.concat(data.getMeasureType());
        String title = AppResourceManager.getResource().getString(keyMeasType);
        builder.setTitle(title);

        String txt = AppResourceManager.getResource().getString("MeasureResultDialog.sendMeasureMsg");
        Patient patient = UserManager.getUserManager().getCurrentPatient();
        String msg = String.format(txt, patient.getName(), patient.getSurname());
        String okBtnMsg = AppResourceManager.getResource().getString("MeasureResultDialog.sendBtn");
        String action = MeasureDialogClickListener.SEND_ACTION;

        String measureStr = getMeasureMessage(data);
        builder.setMessage(measureStr + "\n" + msg);

        MeasureDialogClickListener measureDialogListener = new MeasureDialogClickListener(action, data.getMeasureType());
        builder.setPositiveButton(okBtnMsg, measureDialogListener);
        builder.setNegativeButton(AppResourceManager.getResource().getString("MeasureResultDialog.cancelBtn"), measureDialogListener);

        if ((action.equals(MeasureDialogClickListener.SEND_ALL_ACTION) || action.equals(MeasureDialogClickListener.SEND_ACTION)) && !deviceManager.getCurrentUser().getIsPatient() && !deviceManager.getCurrentDevice().getMeasure().equals(GWConst.KMsrSpir)) {
            List<UserPatient> patients = DbManager.getDbManager().getUserPatients(UserManager.getUserManager().getCurrentUser().getId());
            if (patients != null && patients.size() != 1) {
                builder.setNeutralButton(R.string.changePatientQuestion, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(MEASURE_RESULT_DIALOG);
                        removeDialog(MEASURE_RESULT_DIALOG_SEND_ALL);
                        Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
                        selectPatientIntent.putExtra(SelectPatient.USER_ID, deviceManager.getCurrentUser().getId());
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

	private void setCurrentDevice() {
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

	private boolean measureEnabled(UserDevice device) {
		return (device.getBtAddress()!= null && device.getBtAddress().length() > 0)
				|| Util.isGlucoTelDevice(device.getDevice())
				|| Util.isManualMeasure(device.getDevice())
				|| Util.isStmDevice(device.getDevice());
	}

	private class ProgressDialogClickListener implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			Button btn = ((ProgressDialog)dialog).getButton(which);
			String tag = (String) btn.getTag();
			removeDialog(PROGRESS_DIALOG);
			if(AppConst.IS_MEASURE.equals(tag)){
                // annullo l'acquisizione di una misura
				stopDeviceOperation(-1);
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

	private class AlertDialogClickListener implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			if(progressDialog != null && progressDialog.isShowing()){
				removeDialog(PROGRESS_DIALOG);
			}

			removeDialog(ALERT_DIALOG);
     	   	removeDialog(MEASURE_RESULT_DIALOG);

     	   	if (dataBundle.getBoolean(AppConst.LOGIN_ERROR)) {
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
        static final String SEND_ACTION = "SEND_ACTION";
        static final String SEND_ALL_ACTION = "SEND_ALL_ACTION";

        private String action;
        private String measureType;

        MeasureDialogClickListener(String action, String measureType) {
            this.action = action;
            this.measureType = measureType;
            Log.d(TAG, "MeasureDialogClickListener action=" + action + " measureType=" + measureType);
        }

        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "MeasureDialogClickListener.onClick() action=" + action + " measureType=" + measureType + " which=" + which);

            //Identifico quale button � stato premuto dall'utente
            switch(which) {
                case DialogInterface.BUTTON_POSITIVE:
                    removeDialog(PROGRESS_DIALOG);
                    removeDialog(MEASURE_RESULT_DIALOG);
                    if(action.equals(SEND_ACTION)){
                        Patient p = UserManager.getUserManager().getCurrentPatient();
                        if (p != null)
                            measureData.setIdPatient(p.getId());
                        measureManager.saveMeasureData(measureData);
                        setOperationCompleted();
                    }
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    removeDialog(MEASURE_RESULT_DIALOG);
                    if(action.equals(SEND_ACTION)){
                        // TODO chiedere conferma dello scarto della misura
                        setOperationCompleted();
                    }
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    removeDialog(MEASURE_RESULT_DIALOG);
                    Intent selectPatientIntent = new Intent(DeviceList.this, SelectPatient.class);
                    selectPatientIntent.putExtra(SelectPatient.USER_ID, deviceManager.getCurrentUser().getId());
                    startActivityForResult(selectPatientIntent, PATIENT_SELECTION_2);
                    break;
            }

        }
    }

	private void requestEnableBT() {
		Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
	}

	private void showErrorDialog(String msg){
		dataBundle = new Bundle();
		Log.i(TAG, "showErrorDialog: " + msg);
		dataBundle.putString(AppConst.MESSAGE, msg);
		showDialog(ALERT_DIALOG);
	}

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

	private void askPrePostPrandialGlycaemia() {
		askPrePostPrandialGlycaemia(true);
	}

	private void askPrePostPrandialGlycaemia(final boolean startMeasure) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle("");
    	builder.setCancelable(false);
		builder.setMessage(AppResourceManager.getResource().getString("KPrePostMsgCGE2Pro"));
		builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseMeasureGlyPREBtn"),
				new DialogInterface.OnClickListener(){
					public void onClick(DialogInterface arg0, int arg1) {
						if (startMeasure)
							deviceManager.startMeasure();
						else
							deviceManager.finalizeMeasure();
					}
		});
		builder.setNegativeButton(AppResourceManager.getResource().getString("EGwnurseMeasureGlyPOSTBtn"),
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
	
	private void showCalibrateActivity(boolean doMeasure, boolean doScan) {
        if (doMeasure)
            if (doScan)
                calibrateState = TCalibrateState.EMeasureOn_ScanOn;
            else
                calibrateState = TCalibrateState.EMeasureOn_ScanOff;
        else
            if (doScan)
                calibrateState = TCalibrateState.EMeasureOff_ScanOn;
            else
                calibrateState = TCalibrateState.EMeasureOff_ScanOff;
		Intent intent = new Intent( DeviceList.this, CalibrateActivity.class );			
		startActivityForResult( intent, CALIBRATE_ENTRY );
	}
	
	private void showManualTemperatureActivity(){
		Intent intent = new Intent( DeviceList.this, ManualTemperatureActivity.class );			
		startActivityForResult( intent, MANUAL_MEASURE_ENTRY );
	}
		
	public void sendManualMeasure(String measure, ArrayList<String> values) {
        Measure m = new Measure();
        m.setMeasureType(measure);
        m.setStandardProtocol(true);
		m.setDeviceType(XmlManager.TDeviceType.TEMPERATURE_DT);
        m.setDeviceDesc(AppResourceManager.getResource().getString("KMsgManualMeasure"));
        m.setTimestamp(XmlManager.getXmlManager().getTimestamp(null));
        m.setFile(null);
        m.setFileType(null);
        User u = UserManager.getUserManager().getCurrentUser();
        m.setIdUser(u.getId());
        if (u.getIsPatient())
            m.setIdPatient(u.getId());

        HashMap<String,String> tmpVal = new HashMap<>();
        tmpVal.put(com.ti.app.telemed.core.util.GWConst.EGwCode_0R, values.get(0));  // peso
        m.setMeasures(tmpVal);
        m.setFile(null);
        m.setFileType(null);
        m.setFailed(false);
		deviceManager.showMeasurementResults(m);
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
					    	//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
					    	dataBundle = new Bundle();
							dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("KMsgConf"));
							dataBundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, false);
							dataBundle.putBoolean(AppConst.IS_CONFIGURATION, true);
							loginET = null;
							pwdET = null;
                            doLogin();
						}
						else {
											
							AlertDialog dialog = new AlertDialog.Builder(DeviceList.this).create();
							dialog.setTitle(AppResourceManager.getResource().getString("autoUpdateSetting"));
							SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
							
							dialog.setMessage(String.format(AppResourceManager.getResource().getString("autoUpdate"), sdf.format(new Date(autoUpdateDate))));
							dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
									AppResourceManager.getResource().getString("EGwnurseCancel"),
									new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {				
									
									try {
										userManager.logInUserFromDb(activeUser.getLogin(), activeUser.getPassword());
									} catch (Exception e) {
										Log.e(TAG, "logInUserFromDb: " + e);
									}
								} });
							
							dialog.setButton(DialogInterface.BUTTON_POSITIVE, 
									AppResourceManager.getResource().getString("BTDeviceScoutingDialog.updateBtn"),
									new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									runningConfig = true;
							    	//l'update della configurazione equivale a rifare il login (senza per� chiedere user e pwd)
							    	dataBundle = new Bundle();
									dataBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("KMsgConf"));
									dataBundle.putBoolean(AppConst.MESSAGE_CANCELLABLE, true);
									dataBundle.putBoolean(AppConst.IS_CONFIGURATION, true);
									loginET = null;
									pwdET = null;
                                    doLogin();
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