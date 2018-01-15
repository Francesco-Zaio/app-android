package com.ti.app.mydoctor.gui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.adapter.GridViewAdapter;
import com.ti.app.mydoctor.util.AppConst;
import com.ti.app.mydoctor.util.AppUtil;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DocumentDetails extends AppCompatActivity {
    private static final String TAG = "DocumentDetails";

	private static final int ERROR_DIALOG = 0;
	private static final int DELETE_CONFIRM_DIALOG = 1;

    private static final int BITMAP_MAX_SIZE = 512;

	private File docBaseDir;
    private GridViewAdapter gridAdapter;
    private ArrayList<GridViewAdapter.ImageItem> imageItems = null;
    private ArrayList<File> fileList = new ArrayList<>();

    private Bundle deleteBundle = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.document_details_grid);

		Bundle extras = getIntent().getExtras();

		if (extras != null) {
			Measure currentMeasure = (Measure)extras.get(ShowMeasure.MEASURE_KEY);
			if (currentMeasure==null)
				return;
            try {
                String dir = new String(currentMeasure.getFile(), "UTF-8");
                docBaseDir = new File(dir);
                if (!docBaseDir.exists() || !docBaseDir.isDirectory())
                    return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

			//Ricava il paziente relativo a questa misura
			Patient p = UserManager.getUserManager().getPatientData(currentMeasure.getIdPatient());
			String patientName = p.getSurname() + " " + p.getName();
			
			//Setta l'icona
			ImageView measureIcon = findViewById(R.id.measureIcon);
			measureIcon.setImageResource(AppUtil.getIconId(currentMeasure.getMeasureType()));
						
			//Setta la stringa tipo misura
			final String title = AppResourceManager.getResource().getString("measureType." + currentMeasure.getMeasureType());
			TextView measureType = findViewById(R.id.measureLabel);
			measureType.setText(title);
						
			//Setta il nome del paziente
			TextView patientNameTV = findViewById(R.id.patientNameLabel);
			patientNameTV.setText(patientName);
			
			//Setta la data
			Date d = Util.parseTimestamp(currentMeasure.getTimestamp());
			String date = getDate(d);
			String hour = getHour(d);
			TextView dateText = findViewById(R.id.dataValue);
			dateText.setText(date + " " + hour);
			
			//Setta il listener per il buttone cancellazione misura
			ImageButton buttonCancel = findViewById(R.id.imageButtonCancel);
			buttonCancel.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					//Crea la dialog per la conferma della cancellazione della misura		
					deleteBundle = new Bundle();
					deleteBundle.putString(AppConst.TITLE, AppResourceManager.getResource().getString("warningTitle"));
					deleteBundle.putString(AppConst.MESSAGE, AppResourceManager.getResource().getString("deleteMeasureConfirm") + "?");
		    		showDialog(DELETE_CONFIRM_DIALOG);
				}
			});

            final GridView gridView = findViewById(R.id.gridView);
            imageItems = new ArrayList<>();
            gridAdapter = new GridViewAdapter(this, R.layout.document_grid_item, imageItems);
            gridView.setAdapter(gridAdapter);

            initImages();

            gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri photoURI = FileProvider.getUriForFile(DocumentDetails.this, getApplicationContext().getPackageName() + ".provider", fileList.get(position));
                    intent.setDataAndType(photoURI, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                }
            });
		}
	}

    private void initImages() {
        File[] files = docBaseDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jpg");
            }
        });

        imageItems.clear();
        fileList.clear();
        for (File file : files) {
            Bitmap bitmap = createBitmap(file.getAbsolutePath());
            imageItems.add(gridAdapter.new ImageItem(bitmap, "Image#"));
            fileList.add(file);
        }
        gridAdapter.notifyDataSetChanged();
    }

    private Bitmap createBitmap(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        int scale = Math.max(options.outWidth/BITMAP_MAX_SIZE,options.outHeight/BITMAP_MAX_SIZE)+1;
        options.inJustDecodeBounds = false;
        options.inSampleSize=scale;
        return BitmapFactory.decodeFile(path, options);
    }

	@Override
	public Dialog onCreateDialog(int id) {
		Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		
		switch (id) {
		case ERROR_DIALOG:
			builder.setTitle(AppResourceManager.getResource().getString("warningTitle"));
			builder.setMessage(AppResourceManager.getResource().getString("errorDbRead"));
			builder.setNeutralButton("Ok", error_dialog_click_listener);
			break;
		case DELETE_CONFIRM_DIALOG:
			builder.setTitle(deleteBundle.getString(AppConst.TITLE));
			builder.setMessage(deleteBundle.getString(AppConst.MESSAGE));
			builder.setPositiveButton(AppResourceManager.getResource().getString("confirmButton"), delete_confirm_dialog_click_listener);
			builder.setNegativeButton(AppResourceManager.getResource().getString("cancelButton"), delete_confirm_dialog_click_listener);
			break;
		}
		
		return builder.create();
	}
	
	/**
	 * Listener per i click sulla dialog ERROR_DIALOG
	 */
	private DialogInterface.OnClickListener error_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch(which) {
			case DialogInterface.BUTTON_NEUTRAL:
				removeDialog(ERROR_DIALOG);
				finish();
			}
		}
	};    
	
	/**
	 * Listener per i click sulla dialog DELETE_CONFIRM_DIALOG
	 */
	private DialogInterface.OnClickListener delete_confirm_dialog_click_listener = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			removeDialog(DELETE_CONFIRM_DIALOG);
			
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				//Viene eliminata la singola misura
				Intent returnIntent = new Intent();
				String result = "delete";
				returnIntent.putExtra("result",result);
				setResult(RESULT_OK, returnIntent);     
				finish();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				Toast.makeText(DocumentDetails.this, "Misura non eliminata", Toast.LENGTH_LONG).show();
				break;
			}			
		}
	};
	
	/**
	 * Metodo che restituisce la data in forma dd MMM yyyy
	 * @param data  variabile di tipo {@code Date} che contiene la data da convertire
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getDate(Date data) {
		if (data == null) return "";
		SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
		return sdf.format(data);
	}
	
	/**
	 * Metodo che restituisce l'orario in forma HH:mm"
	 * @param data variabile di tipo {@code Date} che contiene la data da convertire in ora
	 * @return variabile di tipo {@code String} che contiene la stringa convertita
	 */
	private String getHour(Date data) {
		if (data == null) return "";
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
		return sdf.format(data);
	}
}
