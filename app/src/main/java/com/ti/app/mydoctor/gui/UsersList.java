package com.ti.app.mydoctor.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.ti.app.mydoctor.R;
import com.ti.app.telemed.core.ResourceManager;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.dbmodule.DbManager;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.listadapter.UserListAdapter;
import com.ti.app.mydoctor.util.GWConst;
import com.ti.app.mydoctor.util.Util;

public class UsersList extends ActionBarListActivity {
	
	private static final String TAG = "UsersList";
	
	public static final String SELECTED_USER = "USER";
	public static final int RESULT_DB_ERROR = Activity.RESULT_FIRST_USER;
	
	private static final int DELETE_USER_DIALOG = 0;
	private static final int PASSWORD_ERROR_DIALOG = 1;
	
	private static final String KEY_USER_SURNAME = "user_surname";
	private static final String KEY_USER_NAME = "user_name";
	private static final String KEY_USER_CF = "user_cf";
	
	private boolean enableDeleteUserMenu = false;
		
	private List<User> users;
	private List<HashMap<String, String>> fillMaps;
	//private DeviceListAdapter listAdapter;	
	private UserListAdapter listAdapter;
	
	private ActionBar customActionBar;
	private GWTextView titleTV;
	
	private LinearLayout newUserSelectedLL;
	
	private AdapterContextMenuInfo mAdapterContextMenuInfo;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle b = getIntent().getExtras();
		int value = b.getInt(GWConst.ENABLE_DELETE_USER_ID);
		enableDeleteUserMenu = value != 0;
		
		//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		
		setContentView(R.layout.users_list);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
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
		
		User cu = DbManager.getDbManager().getCurrentUser();
		if( cu != null) {
			if( cu.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ))
				titleTV.setText(R.string.users_title);
			else
				titleTV.setText(cu.getName() + "\n" + cu.getSurname());
		} else {
			titleTV.setText(R.string.users_title);
		}				
		customActionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		customActionBar.setHomeButtonEnabled(true);
		customActionBar.setDisplayHomeAsUpEnabled(true);
		
		//Ottengo il riferimento agli elementi che compongono la view
		newUserSelectedLL = (LinearLayout) findViewById(R.id.new_user_linear_layout);
		newUserSelectedLL.setOnClickListener(newUserSelectedClickListener);	
		

		users = DbManager.getDbManager().getNotActiveUsers();
		
		if(users != null && users.size() > 0){		
			// create the grid item mapping
			String[] from = new String[] { KEY_USER_SURNAME, KEY_USER_NAME, KEY_USER_CF };
			int[] to = new int[] { R.id.user_surname, R.id.user_name, R.id.user_cf };

			fillMaps = new ArrayList<HashMap<String, String>>();
			for (User u : users) {			
				HashMap<String, String> map = new HashMap<String, String>();
				
					if( !u.getId().equalsIgnoreCase( GWConst.DEFAULT_USER_ID ) ) {
					
					String cfValue = u.getCf();
					if (cfValue == null || cfValue.equalsIgnoreCase("null")) {
						cfValue = "";	
					} else {
						cfValue = "[" + cfValue + "]";
					}
					
					map.put(KEY_USER_SURNAME, u.getSurname());
					map.put(KEY_USER_NAME, u.getName());
					map.put(KEY_USER_CF, cfValue);
					
					fillMaps.add(map);
				}
			}
			

			ListView lv = getListView();
			lv.setTextFilterEnabled(false);
			lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					try {
						User u = users.get(position);	
						Bundle bundle = new Bundle();
						bundle.putSerializable(SELECTED_USER, u);
						Intent mIntent = new Intent();
						mIntent.putExtras(bundle);
						setResult(RESULT_OK, mIntent);
						finish();
					}
					catch (IndexOutOfBoundsException iobe) {
						Log.e(TAG, "Selezionato l'utente corrente. Non faccio nulla");
					}
				}
			});
			
			/*User activeUser = DbManager.getDbManager().getActiveUser();
			if (activeUser != null) {		
				View v = getLayoutInflater().inflate(R.layout.current_user_layout, null);
				Log.d(TAG, "Utente attualmente loggato: " + activeUser.getName() + " " + activeUser.getSurname());
				LinearLayout users_list_current_user_ll = (LinearLayout) v.findViewById(R.id.users_list_current_user);
				TextView currentUser = (TextView) users_list_current_user_ll.findViewById(R.id.current_user);
				currentUser.setText(activeUser.getName() + " " + activeUser.getSurname());
				lv.addFooterView(v);
				
				Log.d(TAG, "Utente attualmente loggato: " + activeUser.getName() + " " + activeUser.getSurname());
				//setta l'elemento indicante l'utente attivo
				GWTextView activeUserLabel = (GWTextView)this.findViewById(R.id.active_user_name_label_ll);
				activeUserLabel.setText(activeUser.getName() + " " + activeUser.getSurname());
			}
			else {
				Log.d(TAG, "Nessun utente attualmente loggato");				
			}*/

			//listAdapter = new DeviceListAdapter(this, fillMaps, R.layout.user_item, from, to);
			listAdapter = new UserListAdapter(this, fillMaps, R.layout.user_item, from, to);
			setListAdapter(listAdapter);
			registerForContextMenu(getListView());
		} else {
			//setta l'elemento indicante l'utente attivo
			/*User activeUser = DbManager.getDbManager().getActiveUser();
			if(activeUser!=null) {
				GWTextView activeUserLabel = (GWTextView)this.findViewById(R.id.active_user_name_label_ll);
				activeUserLabel.setText(activeUser.getName() + " " + activeUser.getSurname());
			}*/
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(ResourceManager.getResource().getString("PatientSelectionDialog.noUser"));
			builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), 
					new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface arg0,
						int arg1) {		
					setResult(Activity.RESULT_CANCELED);
					finish();
				}
			});
			builder.show();
		}

		//getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
		//titleTV = (TextView) findViewById(R.id.title);
		//titleTV.setText(R.string.users_title);

	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            finish();
            return true;  
            
		default:
            return super.onOptionsItemSelected(item);
		}
	}



	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if ( (info.position != users.size()) && enableDeleteUserMenu ) {
			android.view.MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.users_context_menu, menu);
			menu.setHeaderTitle(Util.getString(R.string.user_title));
		}
		else
			Log.e(TAG, "Selezionato utente corrente. Non faccio nulla");
	}
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();    	
	    switch (item.getItemId()) {
		    case R.id.delete_user:		    	
		    	mAdapterContextMenuInfo = info;
		    	showDialog(DELETE_USER_DIALOG);
		    	return true;
		    default:
		        return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
		/*AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);*/
		
		switch (id) {
		case DELETE_USER_DIALOG:
			
			final User userToDelete = users.get(mAdapterContextMenuInfo.position);
			
			builder.setTitle(R.string.confirmDelete);
			builder.setMessage(getString(R.string.insertUserPassword) + " " + userToDelete.getName() + " " + userToDelete.getSurname());
			
			View delete_user_view = inflater.inflate(R.layout.new_user, null);
			EditText userLoginET = (EditText) delete_user_view.findViewById(R.id.login);
			final EditText userPwdET = (EditText) delete_user_view.findViewById(R.id.password);
			userLoginET.setText(userToDelete.getLogin());
			userLoginET.setEnabled(false);
			
			CheckBox userPwdCB = (CheckBox) delete_user_view.findViewById(R.id.passwordCheckBox);
			userPwdCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					// Controlla se rendere visibile o meno la password
					if( isChecked ) {
						userPwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
					} else {
						userPwdET.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
					}
					
					userPwdET.setSelection(userPwdET.getText().length());
				}
			});
			
			builder.setView(delete_user_view);
			
			builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), 
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {
							removeDialog(DELETE_USER_DIALOG);
							deleteUser(userToDelete, userPwdET.getText().toString());
						}
			});
			builder.setNegativeButton(ResourceManager.getResource().getString("EGwnurseCancel"), 
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {						
							removeDialog(DELETE_USER_DIALOG);
						}
			});
			
			return builder.create();
		case PASSWORD_ERROR_DIALOG:
			
			builder.setTitle(ResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(R.string.passwordError);
			builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					removeDialog(PASSWORD_ERROR_DIALOG);
				}
			});
			
			return builder.create();
		default:
			return null;
		}
	}
	
	private void deleteUser(User userToDelete, String insertedPwd) {
		try {
			if (userToDelete.getPassword().equals(insertedPwd)) {
				DbManager.getDbManager().deleteUser(userToDelete.getId());
				users = DbManager.getDbManager().getUsers();
				fillMaps.remove(mAdapterContextMenuInfo.position);
				listAdapter.notifyDataSetChanged();	
			}
			else
				showDialog(PASSWORD_ERROR_DIALOG);
		} catch (DbException e) {
			AlertDialog.Builder builder = new AlertDialog.Builder(UsersList.this);
			builder.setMessage(e.getMessage());
			builder.setPositiveButton(ResourceManager.getResource().getString("EGwnurseOk"), 
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {									
						}
			});
			builder.show();
		}
	}
	
	private OnClickListener newUserSelectedClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			setResult(RESULT_OK, null);
			finish();			
		}
	};

}