package com.ti.app.mydoctor.gui.utilitytab;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.core.MyDoctorApp;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.gui.ShowUtilitySettingsTabs;
import com.ti.app.mydoctor.util.Util;

public class ConnectionSettingsFragment extends Fragment {
	
	private static final String TAG = "ConnectionSettingsFragment";
	
	//Elementi che compongono la GUI
	private EditText hostEt;
	private TextView hostSendTitle;
	private EditText hostSendValue;
	private EditText portEt;
	private Spinner protocolSpinner;
	private EditText quizEt;
	private Button okButton;
	private Button cancelButton;
			
	private String[] portDefaultArray = new String[] {"443", "80"};
	
	private ArrayAdapter<String> protocolSpinnerAdapter;
	private String[] protocolArray = new String[] {"https", "http"};
	
	//Serve per controllare il listener su protocolSpinner
	//in modo che non venga eseguito sulla onCreate
	private boolean isProtocolSpinnerTouched = false;
		
	private User activeUser;
	
	//private ResourceManager rManager;
	//private ServerCertificateManager scManager;
	
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		View v = inflater.inflate(R.layout.show_connection_settings_layout, container, false);
		
		//Ottengo il riferimento degli elementi che compongono la GUI
		hostEt = (EditText) v.findViewById(R.id.host_et);
		hostSendTitle = (TextView) v.findViewById(R.id.host_send_title_tv);
		hostSendValue = (EditText) v.findViewById(R.id.host_send_value_tv);
		hostSendValue.setEnabled(false);
		portEt = (EditText) v.findViewById(R.id.port_et);
		
		protocolSpinner = (Spinner) v.findViewById(R.id.protocol_spinner);
		protocolSpinnerAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, protocolArray);
		protocolSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		protocolSpinner.setAdapter(protocolSpinnerAdapter);
				
		quizEt = (EditText) v.findViewById(R.id.quiz_et);
		
		
		activeUser = DbManager.getDbManager().getActiveUser();
		if( activeUser==null ) {
			hostSendTitle.setVisibility(View.GONE);
			hostSendValue.setVisibility(View.GONE);
		}
		
		okButton = (Button) v.findViewById(R.id.confirm_button);
		cancelButton = (Button) v.findViewById(R.id.cancel_button);
		//Imposto il listener per i click sui button
		okButton.setOnClickListener(connection_button_click_listener);
		cancelButton.setOnClickListener(connection_button_click_listener);
		
		populateActivity();

		return v;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	public static Bundle createBundle() {
        Bundle bundle = new Bundle();
        return bundle;
    }
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.show_settings_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	/**
	 * Metodo che permette di popolare gli elementi che compongono la GUI con le informazioni lette da db
	 */
	private void populateActivity() {
		DbManager dbManager = DbManager.getDbManager();
		try {
			ServerConf sc = dbManager.getServerConf();
						
			hostEt.setText(sc.getIp());
			if( activeUser!=null ) {
				hostSendValue.setText( activeUser.getIp() );
			}
			portEt.setText(sc.getPort());
					
			String protocol = sc.getProtocol();
			for(int i = 0; i < protocolArray.length; i++) {
				if(protocolArray[i].equals(protocol))
					protocolSpinner.setSelection(i);
			}		
			
			protocolSpinner.setOnTouchListener(new View.OnTouchListener() {			
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					isProtocolSpinnerTouched = true;
					return false;
				}
			});
			
			protocolSpinner.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
							
					if(isProtocolSpinnerTouched) {
						portEt.setText(portDefaultArray[pos]);
					}
				}

				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
					// Do nothing				
				}
			});	
			
			quizEt.setText(Util.getRegistryValue(Util.KEY_URL_QUIZ, Util.URL_QUIZ_DEFAULT));
			
		} catch (DbException e) {
			getActivity().showDialog(ShowUtilitySettingsTabs.ERROR_DIALOG);
		}
	}
	
	/**
	 * Listener per i click sui button che compongono l'activity
	 */
	private View.OnClickListener connection_button_click_listener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Identifico quale button è stato selezionato
			switch(v.getId()) {
			case R.id.confirm_button:
				//Aggiorno il contenuto del db in base agli input dell'utente
				DbManager dbManager = DbManager.getDbManager();
				try {
										
					ServerConf sc = new ServerConf();
					sc.setIp(hostEt.getText().toString());
					sc.setPort(portEt.getText().toString());
					sc.setTarget(dbManager.getDefaultServerConf().getTargetDef());
					sc.setProtocol(protocolArray[protocolSpinner.getSelectedItemPosition()]);
					Util.setRegistryValue(Util.KEY_URL_QUIZ, quizEt.getText().toString());
										
					MyDoctorApp.getConfigurationManager().updateConfiguration(sc);
					
					getActivity().finish();
				} catch (DbException e) {
					e.printStackTrace();
					Log.e(TAG, "ERROR on button_click_listener: " + e.getMessage());
					getActivity().showDialog(ShowUtilitySettingsTabs.ERROR_DIALOG);
				}
				
				break;
			case R.id.cancel_button:
				getActivity().finish();
				break;
			}
		}
	};

}
