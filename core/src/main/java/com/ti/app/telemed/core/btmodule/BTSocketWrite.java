package com.ti.app.telemed.core.btmodule;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Vector;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BTSocketWrite implements Runnable {

	private enum Op {
		IDLE,
		OPEN,
		WRITE,
		CLOSE
	}
	
	//Stream Data
	private BluetoothSocket mmSocket;
	private OutputStream outStream;
	private byte[] bfrWrite;
	private boolean isClosed = true;
	
	//Thread management
	private final Thread currT;
	private boolean loop = true;
	private Op currentOpOn;
	
	//Listener management
	private Vector<BTSocketWriteEventListener> btSocketWriteEventListeners = new Vector<>();
	
	private static final String TAG = "BTSocket";
	 
	public BTSocketWrite() {		
		reset();
		currT = new Thread(this);
		currT.setName("BTSocketWrite Thread");
		currT.start();
	}
	
	public void open(BluetoothSocket bts) {
		synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
            	mmSocket = bts;
            	currentOpOn = Op.OPEN;                
            }
            currT.notifyAll();
        }
	}
	
	public void write(byte[] bfr) {
		synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                bfrWrite = bfr;
                currentOpOn = Op.WRITE;
            }
            currT.notifyAll();
        }
	}
	
	public void close() {
		try {
			isClosed = true;
			if(outStream!=null){
				outStream.close();   
			}
        	Log.i(TAG, "---------------STREAM CHIUSO: " +currentOpOn);
        } catch (IOException ioe) {
            reset();
            fireErrorThrown(4, "bluetooth output stream close error");
        } finally {
        	currentOpOn = Op.IDLE;        	
        }
	}
	
	private void reset() {
		currentOpOn = Op.IDLE;
	}
	
	// methods to add/remove event listeners
	
	public synchronized void addBTSocketWriteEventListener(BTSocketWriteEventListener listener) {
        if (btSocketWriteEventListeners.contains(listener)) {
            return;
        }
        btSocketWriteEventListeners.addElement(listener);
	}
	
	public synchronized void removeBTSocketWriteEventListener(BTSocketWriteEventListener listener) {
		btSocketWriteEventListeners.removeElement(listener);
	}
	
	// methods to trigger events
	
	private Vector<BTSocketWriteEventListener> getBTSocketWriteEventListeners(){
		// we work on a copy of the vector, so if change we don't have problem
		Vector<BTSocketWriteEventListener> copy;
        synchronized (this) {
            copy = (Vector<BTSocketWriteEventListener>) btSocketWriteEventListeners.clone();
        }
        return copy;
	}
	
    private void fireOpenDone() {
        for (BTSocketWriteEventListener listener : getBTSocketWriteEventListeners()) {        	
            listener.writeOpenDone();
        }
    }
    
    private void fireWriteDone() {
        for (BTSocketWriteEventListener listener : getBTSocketWriteEventListeners()) {
            listener.writeDone();
        }        
    }
    
    private void fireErrorThrown(int type, String description) {
        for (BTSocketWriteEventListener listener : getBTSocketWriteEventListeners()) {
            listener.writeErrorThrown(type, description);
        }
    }
	
	public void run() {
		synchronized (currT) {
			while (loop) {
				switch (currentOpOn) {
					case IDLE:
						try {
                        	currT.wait();                            
                        } catch (InterruptedException e) {
                        	reset();
                            fireErrorThrown(0, "thread interrupted");
                        }
						break;
					case OPEN:
						try {      
                        	outStream = mmSocket.getOutputStream();
                            currentOpOn = Op.IDLE;
                            isClosed = false;
                            fireOpenDone();                            
                        } catch (IOException ioe) {
                            reset();
                            fireErrorThrown(1, "bluetooth open output stream error");
                            close();
                        }
						break;
					case WRITE:
						try {
                        	outStream.write(bfrWrite);
                            outStream.flush();
                            currentOpOn = Op.IDLE;
                            fireWriteDone();                            
                        } catch (IOException ioe) {
                            reset();
                            if (!isClosed) {
                            	//L'errore viene comunicato nel caso i cui la write fallisca, ma lo stream
                            	//è ancora aperto, cioè non è stata forzata la sua chiusura dall'utente.
                            	fireErrorThrown(3, "bluetooth write error");                            
                            }
                        }
						break;
					case CLOSE:
						try {
                        	outStream.close();                            
                        } catch (IOException ioe) {
                            reset();
                            fireErrorThrown(4, "bluetooth close error");
                        } finally {
                        	currentOpOn = Op.IDLE;
                        }
						break;
				}
			}
		}
	}
}
