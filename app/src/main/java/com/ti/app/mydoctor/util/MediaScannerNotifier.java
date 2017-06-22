package com.ti.app.mydoctor.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.util.Log;

public class MediaScannerNotifier implements MediaScannerConnectionClient {

    private static final String TAG = "MediaScannerNotifier";
	private MediaScannerConnection mConnection;
    private String mPath;
    private boolean mDelete = false;
	private Context mContext;

    public void add(Context context, String path) {

    	mDelete = false;
    	initialize(context, path);
    }
    
    public void delete(Context context, String path) {
    	
    	mDelete = true;
    	initialize(context, path);
    }
    
    private void initialize(Context context, String path) {

        mPath = path;
        
        mContext = context;
        mConnection = new MediaScannerConnection(context, this);
        mConnection.connect();
        
        Log.v(TAG, "MediaScannerNotifier connect");
    }

    public void onMediaScannerConnected() {
    	
    	Log.v(TAG, "MediaScannerNotifier connected");
        mConnection.scanFile(mPath, null);	        
    }

    public void onScanCompleted(String path, Uri uri) {
       
    	Log.v(TAG, "MediaScannerNotifier scan completed " + uri);
    	
    	try {
    		if (uri != null) {
    			
    			if (mDelete)
    				mContext.getContentResolver().delete(uri, null, null);
    		}
    		
	    	if (mConnection != null) {
	    		 mConnection.disconnect();
	    	}
    	}
    	catch (Exception e) {
    		Log.e(TAG, "onScanCompleted", e);
		}
    	
    }
    
    
}