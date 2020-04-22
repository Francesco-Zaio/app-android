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
import androidx.appcompat.app.ActionBar;
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

import com.ti.app.mydoctor.MyDoctorApp;
import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.telemed.core.common.User;
import com.ti.app.mydoctor.gui.customview.ActionBarListActivity;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.mydoctor.gui.adapter.UserListAdapter;
import com.ti.app.telemed.core.usermodule.UserManager;

public class UsersList extends ActionBarListActivity {
	
	private static final String TAG = "UsersList";

	public static final String ENABLE_DELETE_USER_ID = "ENABLE_DELETE_USER_ID";
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
	private UserListAdapter listAdapter;

	private AdapterContextMenuInfo mAdapterContextMenuInfo;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle b = getIntent().getExtras();
		int value = b.getInt(ENABLE_DELETE_USER_ID);
		enableDeleteUserMenu = value != 0;

		setContentView(R.layout.users_list);
		
		//Flag per mantenere attivo lo schermo finchè l'activity è in primo piano
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Inizializza l'ActionBAr
        ActionBar customActionBar = this.getSupportActionBar();
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
        GWTextView titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
		titleTV.setText(R.string.users_title);
		customActionBar.setCustomView(titleView);
		
		//L'icona dell'App diventa tasto per tornare nella Home
		customActionBar.setHomeButtonEnabled(true);
		customActionBar.setDisplayHomeAsUpEnabled(true);

        //Ottengo il riferimento agli elementi che compongono la view
        LinearLayout newUserSelectedLL = (LinearLayout) findViewById(R.id.new_user_linear_layout);
		newUserSelectedLL.setOnClickListener(newUserSelectedClickListener);

        users = UserManager.getUserManager().getAllUsers();

		if(users != null && users.size() > 0){
			// create the grid item mapping
			String[] from = new String[] { KEY_USER_SURNAME, KEY_USER_NAME, KEY_USER_CF };
			int[] to = new int[] { R.id.user_surname, R.id.user_name, R.id.user_cf };

			fillMaps = new ArrayList<>();
			for (User u : users) {
				HashMap<String, String> map = new HashMap<>();
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
			listAdapter = new UserListAdapter(this, fillMaps, R.layout.user_item, from, to);
			setListAdapter(listAdapter);
			registerForContextMenu(getListView());
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(AppResourceManager.getResource().getString("PatientSelectionDialog.noUser"));
			builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseOk"),
					new DialogInterface.OnClickListener(){
				public void onClick(DialogInterface arg0,
						int arg1) {		
					setResult(Activity.RESULT_CANCELED);
					finish();
				}
			});
			builder.show();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
            setResult(Activity.RESULT_CANCELED);
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
			menu.setHeaderTitle(MyDoctorApp.getContext().getResources().getString(R.string.user_title));
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
			builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseOk"),
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {
							removeDialog(DELETE_USER_DIALOG);
							deleteUser(userToDelete, userPwdET.getText().toString());
						}
			});
			builder.setNegativeButton(AppResourceManager.getResource().getString("EGwnurseCancel"),
					new DialogInterface.OnClickListener(){
						public void onClick(DialogInterface arg0,
								int arg1) {						
							removeDialog(DELETE_USER_DIALOG);
						}
			});
			
			return builder.create();
		case PASSWORD_ERROR_DIALOG:
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
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
                if (UserManager.getUserManager().deleteUser(userToDelete.getId())) {
                    users = UserManager.getUserManager().getAllUsers();
                    fillMaps.remove(mAdapterContextMenuInfo.position);
                    listAdapter.notifyDataSetChanged();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(AppResourceManager.getResource().getString("deleteUserFailure"));
                    builder.setCancelable(true);
                    builder.show();
                }
			}
			else
				showDialog(PASSWORD_ERROR_DIALOG);
		} catch (Exception e) {
			AlertDialog.Builder builder = new AlertDialog.Builder(UsersList.this);
			builder.setMessage(e.getMessage());
			builder.setPositiveButton(AppResourceManager.getResource().getString("EGwnurseOk"),
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