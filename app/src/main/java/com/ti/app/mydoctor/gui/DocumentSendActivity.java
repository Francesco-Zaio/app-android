package com.ti.app.mydoctor.gui;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.Toast;

import com.ti.app.mydoctor.AppResourceManager;
import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.adapter.GridViewAdapter;
import com.ti.app.mydoctor.gui.customview.GWTextView;
import com.ti.app.telemed.core.measuremodule.MeasureManager;
import com.ti.app.telemed.core.usermodule.UserManager;
import com.ti.app.telemed.core.util.Util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DocumentSendActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "DocumentSendActivity";

    private static final String STATE_FILE = "STATE_FILE";
    private static final int BITMAP_MAX_SIZE = 512;

    private static final int MAX_IMAGES = 5;
    private GridViewAdapter gridAdapter;
    private GridView gridView;
    private ArrayList<GridViewAdapter.ImageItem> imageItems = null;
    private MeasureManager.DocumentType docType;
    private Button okButton;
    private FloatingActionButton cameraButton, galleryButton;

    public static final String DOCUMENT_KEY = "DOCUMENT_KEY";
    private static final int CAMERA_REQUEST=1;
    private static final int GALLERY_REQUEST=2;

    private ArrayList<File> fileList = new ArrayList<>();
    private File currentFile;
    File docBaseDir, sendDir;
    ProgressDialog progressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // Restore value of members from saved state
            currentFile = (File)savedInstanceState.getSerializable(STATE_FILE);
        }

        MeasureManager.getMeasureManager().setHandler(handler);
        Intent i = getIntent();
        docType = MeasureManager.DocumentType.fromString(i.getStringExtra(DOCUMENT_KEY));

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

        //Settare il font e il titolo della Activity
        LayoutInflater inflator = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View titleView = inflator.inflate(R.layout.actionbar_title, null);
        customActionBar.setCustomView(titleView);

        //L'icona dell'App diventa tasto per tornare nella Home
        customActionBar.setHomeButtonEnabled(true);
        customActionBar.setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.activity_document_send);

        String title = AppResourceManager.getResource().getString("measureType." + docType.toString());
        GWTextView titleTV = titleView.findViewById(R.id.actionbar_title_label);
        titleTV.setText(title);

        gridView = findViewById(R.id.gridView);
        imageItems = new ArrayList<>();
        gridAdapter = new GridViewAdapter(this, R.layout.document_grid_item, imageItems);
        gridView.setAdapter(gridAdapter);
        gridView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        //columnWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DP_COLUMN_WIDTH, getResources().getDisplayMetrics());
        //gridView.setColumnWidth(columnWidth);

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri photoURI = FileProvider.getUriForFile(DocumentSendActivity.this, getApplicationContext().getPackageName() + ".provider", fileList.get(position));
                intent.setDataAndType(photoURI, "image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }
        });

        gridView.setMultiChoiceModeListener(new MultiChoiceModeListener());

        okButton = findViewById(R.id.confirm_button);
        okButton.setOnClickListener(this);
        cameraButton = findViewById(R.id.camera);
        cameraButton.setOnClickListener(this);
        galleryButton = findViewById(R.id.gallery);
        galleryButton.setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);

        String id = UserManager.getUserManager().getCurrentPatient().getId();
        docBaseDir = Util.getDocumentDir(docType, id);

        initImages();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy");
        super.onDestroy();
        MeasureManager.getMeasureManager().setHandler(null);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putSerializable(STATE_FILE, currentFile);
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.camera:
                if (imageItems.size() >= MAX_IMAGES) {
                    Toast.makeText(DocumentSendActivity.this, "Massimo " + MAX_IMAGES + " immagini", Toast.LENGTH_SHORT).show();
                    return true;
                }
                startCamera();
                break;
            case R.id.gallery:
                if (imageItems.size() >= MAX_IMAGES) {
                    Toast.makeText(DocumentSendActivity.this, "Massimo " + MAX_IMAGES + " immagini", Toast.LENGTH_SHORT).show();
                    return true;
                }
                startGallery();
                break;
            case android.R.id.home: //Ritorna alla Home quando si clicca sull'icona della App
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void initImages() {
        if (docBaseDir != null) {
            File[] files = docBaseDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return (!file.isDirectory());
                }
            });

            imageItems.clear();
            fileList.clear();
            for (File file : files) {
                Bitmap bitmap;
                if (file.getName().endsWith(".pdf")) {
                    bitmap = createBitmap(null, true);
                } else
                    bitmap = createBitmap(file.getAbsolutePath(), false);
                imageItems.add(gridAdapter.new ImageItem(bitmap, file.getName()));
                fileList.add(file);
            }
            gridAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, AppResourceManager.getResource().getString("errorDb"), Toast.LENGTH_SHORT).show();
        }
        updateButtons(false);
    }

    private Bitmap createBitmap(String path, boolean isPdf) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        if (isPdf) {
            return BitmapFactory.decodeResource(getResources(), R.drawable.pdf);
        } else {
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int scale = Math.max(options.outWidth/BITMAP_MAX_SIZE,options.outHeight/BITMAP_MAX_SIZE)+1;
            options.inJustDecodeBounds = false;
            options.inSampleSize=scale;
            return BitmapFactory.decodeFile(path, options);
        }
    }

    private void updateButtons(boolean forceDisable) {
        if ((imageItems.size() < MAX_IMAGES) && (docBaseDir!=null)) {
            cameraButton.setEnabled(true);
            galleryButton.setEnabled(true);
        } else {
            cameraButton.setEnabled(false);
            galleryButton.setEnabled(false);
        }
        okButton.setEnabled((imageItems.size() > 0) && !forceDisable);
    }

    @Override
    public void onClick (View v) {
        switch(v.getId()) {
            case R.id.confirm_button:
                sendDocuments();
                break;
            case R.id.cancel_button:
                finish();
                break;
            case R.id.camera:
                if (imageItems.size() >= MAX_IMAGES) {
                    Toast.makeText(DocumentSendActivity.this, "Massimo " + MAX_IMAGES + " immagini", Toast.LENGTH_SHORT).show();
                    return;
                }
                startCamera();
                break;
            case R.id.gallery:
                if (imageItems.size() >= MAX_IMAGES) {
                    Toast.makeText(DocumentSendActivity.this, "Massimo " + MAX_IMAGES + " immagini", Toast.LENGTH_SHORT).show();
                    return;
                }
                startGallery();
                break;
        }
    }

    private File getImageFile(String extension) {
        return new File(docBaseDir, new SimpleDateFormat("yyyyMMddhhmmss", Locale.ITALY).format(new Date())+"."+extension);
    }

    private void sendDocuments() {
        String dirPath = Util.getTimestamp(null);
        sendDir =  new File(docBaseDir, dirPath);
        boolean success = false;
        try {
            if (sendDir.mkdir()) {
                for (File f:fileList)
                    copyFile(f,sendDir);
                if(MeasureManager.getMeasureManager().saveDocument(sendDir.getAbsolutePath(),docType)) {
                    progressDialog = new ProgressDialog(this);
                    progressDialog.setIndeterminate(true);
                    progressDialog.setCancelable(false);
                    progressDialog.setMessage(AppResourceManager.getResource().getString("KMsgZipDocumentStart"));
                    progressDialog.show();
                    success = true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG,"Errore creazione directory");
        }
        if (!success)
            showSimpleDialog(AppResourceManager.getResource().getString("warningTitle"), AppResourceManager.getResource().getString("ErrZipDocument"));
    }

    private void showSimpleDialog(String title, String message) {
        Context ctx = this;
        ctx.setTheme(R.style.Theme_MyDoctorAtHome_Light);
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        if (title!=null)
            builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.okButton, null);
        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void startCamera() {
        currentFile = getImageFile("jpg");
        Uri outputFileUri = Uri.fromFile(currentFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        } else {
            File file = new File(outputFileUri.getPath());
            Uri photoUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", file);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(getApplicationContext().getPackageManager()) != null) {
            try {
                startActivityForResult(intent, CAMERA_REQUEST);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
            }
        }
    }

    private Intent getFileChooserIntent() {
        String[] mimeTypes = {"image/*", "application/pdf"};
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*|application/pdf").putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }

    private void startGallery() {
        // Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Intent galleryIntent = getFileChooserIntent();
        startActivityForResult(galleryIntent, GALLERY_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == GALLERY_REQUEST) {
            // nel caso in cui sia cambiato l'orientamento dello schermo l'activity è
            // stata ricreata e devo attendere che il valore di columnWidth sia stato calcolato
            // altrimenti la createBitmap non conosce la dimensione delle bitmap da creare
            if (data != null) {
                Uri contentURI = data.getData();
                if (contentURI != null) {
                    String mimeType = getContentResolver().getType(contentURI);
                    MimeTypeMap mime = MimeTypeMap.getSingleton(); // return String like "image/jpeg" or "application/pdf"
                    String type = mime.getExtensionFromMimeType(mimeType); // return String like "jpg" or "pdf"

                    saveImage(contentURI, type);
                    gridView.post(new Runnable() {
                        @Override
                        public void run() {
                            initImages();
                        }
                    });
                }
            }
        } else if (requestCode == CAMERA_REQUEST) {
            // nel caso in cui sia cmbiato l'orientamento dello schermo l'activity è
            // stata ricreata e devo attendere che il valore di columnWidth sia stato calcolato
            // altrimenti la createBitmap non conosce la dimensione delle bitmap da creare
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    initImages();
                }
            });
        }
    }

    private final MyHandler handler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private final WeakReference<DocumentSendActivity> mOuter;

        private MyHandler(DocumentSendActivity outer) {
            mOuter = new WeakReference<>(outer);
        }

        @Override
        public void handleMessage(Message msg) {
            DocumentSendActivity outer = mOuter.get();
            super.handleMessage(msg);
            switch (msg.what) {
                case MeasureManager.OPERATION_COMPLETED:
                    for (File f:outer.fileList)
                        f.delete();
                    if (outer.progressDialog!=null)
                        outer.progressDialog.dismiss();
                    outer.setResult(RESULT_OK);
                    outer.finish();
                    break;
                case MeasureManager.ERROR_OCCURED:
                    Util.deleteTree(outer.sendDir);
                    if (outer.progressDialog!=null)
                        outer.progressDialog.dismiss();
                    outer.initImages();
                    outer.showSimpleDialog(AppResourceManager.getResource().getString("warningTitle"), AppResourceManager.getResource().getString("ErrZipDocument"));
                    break;
            }
        }
    }

    private File saveImage(Uri sourceuri, String extension)
    {
        File outputFile = getImageFile(extension);
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(sourceuri);
            fileOutputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream!=null) inputStream.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return outputFile;
    }

    public class MultiChoiceModeListener implements
            GridView.MultiChoiceModeListener {

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                                              int position, long id, boolean checked) {
            gridAdapter.toggleSelection(position);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    // Calls getSelectedIds method from ListViewAdapter Class
                    SparseBooleanArray selected = gridAdapter.getSelectedIds();
                    // Captures all selected ids with a loop
                    for (int i = (selected.size() - 1); i >= 0; i--) {
                        if (selected.valueAt(i)) {
                            GridViewAdapter.ImageItem selecteditem = gridAdapter
                                    .getItem(selected.keyAt(i));
                            // Remove selected items following the ids
                            gridAdapter.remove(selecteditem);
                            fileList.get(i).delete();
                            fileList.remove(i);
                        }
                    }
                    // Close CAB
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.document_send, menu);
            updateButtons(true);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            gridAdapter.removeSelection();
            updateButtons(false);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    }

    public static void copyFile(File sourceFile, File destDir) throws IOException {
        if (!destDir.exists())
            destDir.mkdirs();
        File destFile = new File(destDir, sourceFile.getName());
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }
}
