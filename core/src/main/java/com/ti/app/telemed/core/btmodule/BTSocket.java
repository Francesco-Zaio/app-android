/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.ti.app.telemed.core.btmodule;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Vector;

import com.ti.app.telemed.core.btmodule.events.BTSocketEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketReadEventListener;
import com.ti.app.telemed.core.btmodule.events.BTSocketWriteEventListener;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BTSocket implements BTSocketReadEventListener, BTSocketWriteEventListener {
    
    private Vector<BTSocketEventListener> btSocketEventListeners = new Vector<>();
    
    private BluetoothDevice btDevice;
	private BluetoothSocket mmSocket;
    private boolean available = true;
    
    // variable for singleton
	private static BTSocket btSocket;
	
	private BTSocketRead btReader;
	private BTSocketWrite btWriter;
	
	private static final String TAG = "BTSocket";
	
	private BTSocket() {
		btReader = new BTSocketRead();
		btWriter = new BTSocketWrite();        
	}
	
	public static BTSocket getBTSocket() {
		if (btSocket == null) {
			btSocket = new BTSocket();
		}
		return btSocket;
	}
	
	public boolean isAvailable() {
		return available;
	}
	
	private Runnable connectRunnable = new Runnable() {
		
		@Override
		public void run() {
			try {      
	    		//mmSocket = btDevice.createRfcommSocketToServiceRecord(SERIALPORT);							
				Method m = btDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
				mmSocket = (BluetoothSocket) m.invoke(btDevice, 1);							
				mmSocket.connect();
	            //Prima fase viene aperto lo stream in lettura        
	            openReaderStream();            
	        } catch (Exception ioe) {
	            fireErrorThrown(1, "bluetooth open error: " + ioe.getMessage());
	            close();
	        }
		}
	};
	
	private Runnable connectInsecureRunnableUUID = new Runnable() {
		
		@Override
		public void run() {
			try {      
				UUID serviceRecordId = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
				
				Log.d(TAG, "connectInsecureRunnableUUID uuid=" + serviceRecordId);
				
	    		//mmSocket = btDevice.createRfcommSocketToServiceRecord(SERIALPORT);
				Method m = btDevice.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] {UUID.class});
				mmSocket = (BluetoothSocket) m.invoke(btDevice, serviceRecordId);							
				mmSocket.connect();
	            //Prima fase viene aperto lo stream in lettura        
	            openReaderStream();            
	        } catch (Exception ioe) {
	            fireErrorThrown(1, "bluetooth open error: " + ioe.getMessage());
	            close();
	        }
		}
	};
	
	private Runnable connectInsecureRunnable = new Runnable() {
		
		@Override
		public void run() {
			try {      
	    		//mmSocket = btDevice.createRfcommSocketToServiceRecord(SERIALPORT);
				Method m = btDevice.getClass().getMethod("createInsecureRfcommSocket", new Class[] {int.class});
				mmSocket = (BluetoothSocket) m.invoke(btDevice, 1);							
				mmSocket.connect();
	            //Prima fase viene aperto lo stream in lettura        
	            openReaderStream();            
	        } catch (Exception ioe) {
	            fireErrorThrown(1, "bluetooth open error: " + ioe.getMessage());
	            close();
	        }
		}
	};
	
	//Operazioni di apertura stream
    public void connect(BluetoothDevice device) {
		Log.i(TAG, "connect");
    	btDevice = device;
    	Thread t = new Thread(connectRunnable);
    	t.start();
    }
    
    public void connectInsecure(BluetoothDevice device) {
		Log.i(TAG, "connectInsecure");
    	btDevice = device;
    	Thread t = new Thread(connectInsecureRunnable);
    	t.start();
    }
    
    public void connectInsecureUUID(BluetoothDevice device) {
		Log.i(TAG, "connectInsecureUUID");
    	btDevice = device;
    	Thread t = new Thread(connectInsecureRunnableUUID);
    	t.start();
    }
    
    //Prima fase
    private void openReaderStream() {
    	btReader.addBTSocketReadEventListener(this);
    	btReader.open(mmSocket);    	
    }
    
    //Seconda fase
    private void openWriterStream() {
    	btWriter.addBTSocketWriteEventListener(this);
    	btWriter.open(mmSocket);
    }   
    
    
    /**
     * Read on the connection stream.
     * @param bfr
     */
    public void read(byte[] bfr) {
    	btReader.read(bfr, false);    	        
    }
    
    public void read(byte[] bfr, boolean notifyEOF) {
    	btReader.read(bfr, notifyEOF);    	        
    }
    
    /**
     * Write on the connection stream. 
     * @param bfr
     */
    public void write(byte[] bfr) {
    	btWriter.write(bfr);    	        
    }
    
    public void close() {
    	btReader.close();
    	btWriter.close();
    	try {
    		if(mmSocket!=null){
    			mmSocket.close();  
    		}
        } catch (IOException ioe) {            
            fireErrorThrown(4, "bluetooth close error");
        } finally {
        	available = true;        	
        }
    }
    
    // methods to add/remove event listeners
	
	public synchronized void addBTSocketEventListener(BTSocketEventListener listener) {
        if (btSocketEventListeners.contains(listener)) {
            return;
        }
        btSocketEventListeners.addElement(listener);
    }
    
    public synchronized void removeBTSocketEventListener(BTSocketEventListener listener) {
    	btSocketEventListeners.removeElement(listener);
    }
    
    // methods to trigger events
    
    private Vector<BTSocketEventListener> getBTSocketEventListeners(){
		// we work on a copy of the vector, so if change we don't have problem
		Vector<BTSocketEventListener> copy;
        synchronized (this) {
            copy = (Vector<BTSocketEventListener>) btSocketEventListeners.clone();
        }
        return copy;
	}
	
    private void fireOpenDone() {
        for (BTSocketEventListener listener : getBTSocketEventListeners()) {
            listener.openDone();
        }
    }
    
    private void fireReadDone() {
        for (BTSocketEventListener listener : getBTSocketEventListeners()) {
            listener.readDone();
        }        
    }
    
    private void fireWriteDone() {
        for (BTSocketEventListener listener : getBTSocketEventListeners()) {
            listener.writeDone();
        }        
    }
    
    private void fireErrorThrown(int type, String description) {
        for (BTSocketEventListener listener : getBTSocketEventListeners()) {
            listener.errorThrown(type, description);
        }
    }
    
 // methods of btSocketReadEventListeners interface
	@Override
    public void readOpenDone() {
    	openWriterStream();
    }

	@Override
	public void readDone() {
		fireReadDone();
	}

	@Override
	public void readErrorThrown(int type, String description) {
		fireErrorThrown(type, description);
	}
    
    // methods of btSocketWriteEventListeners interface

	@Override
	public void writeOpenDone() {
		available = false;
		fireOpenDone();
	}
	@Override
	public void writeDone() {
		fireWriteDone();
	}

	@Override
	public void writeErrorThrown(int type, String description) {
		fireErrorThrown(type, description);
	}

	public void resetStream() {
		
		try {
			btReader.resetStream();    	
		}
		catch (Exception e) {
			e.printStackTrace();
		}    	
	}
	
}
