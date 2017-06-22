package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.scmodule.ServerCertificateManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.gui.utilitytab.ConnectionSettingsFragment;
import com.ti.app.mydoctor.gui.utilitytab.DeviceSettingsFragment;

public class ShowUtilitySettingsTabs extends ActionBarActivity {
	
	private static final String TAG = "ShowUtilitySettingsTabs";
	
	//Dialog
	public static final int ERROR_DIALOG = 0;
	public static final int CONNECTING_CONFIRM_DIALOG = 1;
	public static final int ALERT_DIALOG = 3;
	
	private Bundle dataBundle;
	private static final String ID = "ID";
	private static final String TITLE = "TITLE";
	private static final String MESSAGE = "MESSAGE";
	
	private ActionBar mActionBar;
			
	private ViewPager mPager;
	private int mPageSelectedIndex;
	
	private FragmentManager mFragmentManager;
	
	//Indica quali impostazioni devono essere visualizzate
	private String mTabSelectedIndex;
	
	private ResourceManager rManager;
	private ServerCertificateManager scManager;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.show_utility_settings_tabs_layout);
		
		//ACTION BAR
		mActionBar = getSupportActionBar();
		mActionBar.setNavigationMode( ActionBar.NAVIGATION_MODE_TABS );
        
        //Setta il gradiente di sfondo della action bar
		Drawable cd = this.getResources().getDrawable(R.drawable.action_bar_background_color);
		mActionBar.setBackgroundDrawable(cd);
		
		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setDisplayShowTitleEnabled(false);
		
		//Setta l'icon
		mActionBar.setIcon(R.drawable.icon_action_bar);

		//Settare il font e il titolo della Activity
		LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View titleView = inflator.inflate(R.layout.actionbar_title, null);
		//titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		//titleTV.setText(this.getResources().getString(R.string.connectionSettings));
		mActionBar.setCustomView(titleView);
				
		//L'icona dell'App diventa tasto per tornare nella Home
		mActionBar.setHomeButtonEnabled(true);
		mActionBar.setDisplayHomeAsUpEnabled(true);
		
		//Per la gestione delle TABS
		mPager = (ViewPager) findViewById(R.id.pager);
		
		//Ricava il FragmentManager
		mFragmentManager = getSupportFragmentManager();
        
        /** Defining a listener for pageChange */
        ViewPager.SimpleOnPageChangeListener pageChangeListener = new ViewPager.SimpleOnPageChangeListener(){
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                mActionBar.setSelectedNavigationItem(position);
            }
        };
 
        /** Setting the pageChange listener to the viewPager */
        mPager.setOnPageChangeListener(pageChangeListener);
 
        /** Creating an instance of FragmentPagerAdapter */
        UtilitySettingsPagerAdapter fragmentPagerAdapter = new UtilitySettingsPagerAdapter(mFragmentManager);
 
        /** Setting the FragmentPagerAdapter object to the viewPager object */
        mPager.setAdapter(fragmentPagerAdapter);
 
        mActionBar.setDisplayShowTitleEnabled(true);
 
        /** Defining tab listener */
        ActionBar.TabListener tabListener = new ActionBar.TabListener() {
 
            @Override
            public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            }
 
            @Override
            public void onTabSelected(Tab tab, FragmentTransaction ft) {
            	mPageSelectedIndex = tab.getPosition();
                mPager.setCurrentItem(tab.getPosition());
            }
 
            @Override
            public void onTabReselected(Tab tab, FragmentTransaction ft) {
            }
        };
 
        //Legge TAB selezionato
		Bundle data = getIntent().getExtras();
		mTabSelectedIndex = data.getString("TYPE_SETTINGS");
		
		if(mTabSelectedIndex.equals("DEVICES")) {
			mPageSelectedIndex = 0;       
		} else if(mTabSelectedIndex.equals("CONNECTION")) {
			mPageSelectedIndex = 1;	                
		}
		
        /** Creating devices fragment Tab */
        Tab tab = mActionBar.newTab()
            .setText(R.string.show_settings_devices)
            .setTabListener(tabListener);
        if( mPageSelectedIndex == 0 ) {
        	mActionBar.addTab(tab, true);
        } else {
        	mActionBar.addTab(tab, false);
        } 
 
        /** Creating connections fragment Tab */
        tab = mActionBar.newTab()
            .setText(R.string.show_settings_connections)
            .setTabListener(tabListener);
        if( mPageSelectedIndex == 1 ) {
        	mActionBar.addTab(tab, true);
        } else {
        	mActionBar.addTab(tab, false);
        }
	    
		rManager = ResourceManager.getResource();
		
		scManager = ServerCertificateManager.getScMananger();
		scManager.setHandler(scManagerHandler);	
	}
	
	/*@Override
	public void onBackPressed() {
		
		if ( mPageSelectedIndex == 0 ) {
			Log.i(TAG, "Premuto il tasto back");
			
			DeviceSettingsFragment currentFragment = (DeviceSettingsFragment)mFragmentManager.findFragmentById(mPageSelectedIndex);
			currentFragment.onBackPressed();
		}		
	}*/
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
			//Creo l'oggetto Intent necessario per tale operazione
			Intent intent = new Intent();
			//Associo il risultato dell'operazione all'intent
			setResult(RESULT_CANCELED, intent);
			//L'activity ha terminato la sua funzione e viene chiusa
			//passando il controllo all'activity che l'ha chiamata
			finish();
            return true;  
		case R.id.reset_settings:
			
			if ( mPageSelectedIndex == 1 ) {
				Toast.makeText(this, "Profilo Utente", Toast.LENGTH_SHORT).show();
				prepareBundle(R.id.reset_settings, rManager.getString("showSettingsRevertToDefaultTitle"), rManager.getString("showSettingsRevertToDefaultMessage"));
				showDialog(CONNECTING_CONFIRM_DIALOG);				
			}			
			return true;
            
            default:
            	return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		//Identifico la dialog che deve essere visualizzata
		switch(id) {
		case ERROR_DIALOG:
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(ResourceManager.getResource().getString("errorDb"));
			builder.setNeutralButton(R.string.okButton, error_dialog_click_listener);
			break;
		case CONNECTING_CONFIRM_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setPositiveButton(R.string.okButton, connecting_confirm_dialog_click_listener);
			builder.setNegativeButton(R.string.cancelButton, connecting_confirm_dialog_click_listener);
			break;		
		case ALERT_DIALOG:
			builder.setTitle(dataBundle.getString(TITLE));
			builder.setMessage(dataBundle.getString(MESSAGE));
			builder.setNeutralButton(R.string.okButton, alert_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	/**
	 * Listener per la gestione degli eventi sulla dialog ERROR_DIALOG
	 */
	private DialogInterface.OnClickListener error_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ERROR_DIALOG);
				finish();
				break;
			}
		}
	};
	
	/**
	 * Listener per la gestione degli eventi sulla dialog CONFIRM_DIALOG per il ripristino dei dati di connessione
	 */
	private DialogInterface.OnClickListener connecting_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_POSITIVE:
				if(dataBundle.getInt(ID) == R.id.reset_settings) {
					DbManager dbManager = DbManager.getDbManager();
					try {
						dbManager.resetServerConf();
						ServerConf defaultSC = dbManager.getDefaultServerConf();
						defaultSC.setIp(defaultSC.getIpDef());
						defaultSC.setPort(defaultSC.getPortDef());
						defaultSC.setProtocol(defaultSC.getProtocolDef());
						defaultSC.setTarget(defaultSC.getTargetDef());
						
						MyDoctorApp.getConfigurationManager().updateConfiguration(defaultSC);
						finish();
						Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("showSettingsOperationSuccessfull"), Toast.LENGTH_LONG).show();
					} catch(DbException e) {
						showDialog(ERROR_DIALOG);
					}
				}
				else {
					scManager.deleteAllServerCerts(UserManager.getUserManager().getCurrentUser().getId());
				}
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(getApplicationContext(), ResourceManager.getResource().getString("showSettingsOperationCancelled"), Toast.LENGTH_LONG).show();
				break;
			}
			removeDialog(CONNECTING_CONFIRM_DIALOG);
		}
	};
	
	/**
	 * Listener per la gestione degli eventi sulla dialog ALERT_DIALOG
	 */
	private DialogInterface.OnClickListener alert_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			//Identifico quale elemento è stato premuto
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ALERT_DIALOG);
			}
		}
	};
	
	private void prepareBundle(int id, String title, String message) {
		dataBundle = new Bundle();
		dataBundle.putInt(ID, id);
		dataBundle.putString(TITLE, title);
		dataBundle.putString(MESSAGE, message);
	}


	private final Handler scManagerHandler = new Handler() {
		
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case ServerCertificateManager.DELETE_FAILURE:
				Log.i(TAG, "scManagerHandler: errore nella cancellazione dei certificati");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertFailure"));
				showDialog(ShowUtilitySettings.ALERT_DIALOG);
				break;
			case ServerCertificateManager.DELETE_SUCCESS:
				Log.i(TAG, "scManagerHandler: certificati cancellati con successo");
				prepareBundle(0, rManager.getString("warningTitle"), rManager.getString("deleteServerCertSuccess"));
				showDialog(ShowUtilitySettings.ALERT_DIALOG);
				break;
			}
		}
		
	};
	
	public class UtilitySettingsPagerAdapter extends FragmentPagerAdapter {
		
		final int PAGE_COUNT = 2;
		
		/** Constructor of the class */
	    public UtilitySettingsPagerAdapter(FragmentManager fm) {
			super(fm);			
		}	   
	 
	    /** This method will be invoked when a page is requested to create */
	    @Override
	    public Fragment getItem(int arg0) {
	        Bundle data = new Bundle();
	        switch(arg0){
	 
	            /** Devices is selected */
	            case 0:
	            	DeviceSettingsFragment deviceFragment = new DeviceSettingsFragment();
	                return deviceFragment;
	 
	            /** Connections is selected */
	            case 1:
	            	ConnectionSettingsFragment connectionFragment = new ConnectionSettingsFragment();
	                return connectionFragment;
	        }
	        return null;
	    }
	 
	    /** Returns the number of pages */
	    @Override
	    public int getCount() {
	        return PAGE_COUNT;
	    }
	}

}
