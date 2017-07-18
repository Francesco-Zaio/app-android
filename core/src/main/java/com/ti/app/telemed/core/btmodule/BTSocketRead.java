package com.ti.app.telemed.core.btmodule;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import com.ti.app.telemed.core.btmodule.events.BTSocketReadEvent;
import com.ti.app.telemed.core.btmodule.events.BTSocketReadEventListener;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BTSocketRead implements Runnable {

	private enum Op {
		IDLE,
		OPEN,
		READ,
		CLOSE
	}
	
	//Stream data
	private BluetoothSocket mmSocket;
	private InputStream inStream;
	private byte[] bfrRead;
	private int readBytes;
	private int offset;
	private boolean isClosed = true;
	private boolean notifyEOF = false;
	
	//Thread management
	private Thread currT;
	private boolean loop = true;
	private Op currentOpOn;
	
	//Listener management
	private Vector<BTSocketReadEventListener> btSocketReadEventListeners = new Vector<BTSocketReadEventListener>();
	
	private static final String TAG = "BTSocket";
	
	public BTSocketRead() {
		reset();
		currT = new Thread(this);
		currT.setName("BTSocketRead thread");
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
	
	public void read(byte[] bfr, boolean notifyEOF) {
		synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
                bfrRead = bfr;
                this.notifyEOF = notifyEOF;
                currentOpOn = Op.READ;
            }
            currT.notifyAll();
        }
	}
	
	public void close() {
		try {
			isClosed = true;
			if(inStream!=null){
				inStream.close();   
			}
        } catch (IOException ioe) {
            reset();
            fireErrorThrown(4, "bluetooth input stream close error");
        } finally {
        	currentOpOn = Op.IDLE;        	
        }
	}
	
	private void reset() {
		currentOpOn = Op.IDLE;
		readBytes = 0;
		offset = 0;		
		
		notifyEOF = false;
	}
	
	// methods to add/remove event listeners
	public synchronized void addBTSocketReadEventListener(BTSocketReadEventListener listener) {
        if (btSocketReadEventListeners.contains(listener)) {
            return;
        }
        btSocketReadEventListeners.addElement(listener);
	}
	
	public synchronized void removeBTSocketReadEventListener(BTSocketReadEventListener listener) {
		btSocketReadEventListeners.removeElement(listener);
	}
	
	// methods to trigger events    
	
	private Vector<BTSocketReadEventListener> getBTSocketReadEventListeners(){
		// we work on a copy of the vector, so if change we don't have problem
		Vector<BTSocketReadEventListener> copy = null;
        synchronized (this) {
            copy = (Vector<BTSocketReadEventListener>) btSocketReadEventListeners.clone();
        }
        return copy;
	}
	
    private void fireOpenDone() {
        BTSocketReadEvent event = new BTSocketReadEvent(this);
        for (BTSocketReadEventListener listener : getBTSocketReadEventListeners()) {        	
            listener.openDone(event);
        }
    }
    
    private void fireReadDone() {
    	BTSocketReadEvent event = new BTSocketReadEvent(this);
        for (BTSocketReadEventListener listener : getBTSocketReadEventListeners()) {
            listener.readDone(event);
        }        
    }
    
    private void fireErrorThrown(int type, String description) {
    	BTSocketReadEvent event = new BTSocketReadEvent(this);
        for (BTSocketReadEventListener listener : getBTSocketReadEventListeners()) {
            listener.errorThrown(event, type, description);
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
                        	inStream = mmSocket.getInputStream();;
                            currentOpOn = Op.IDLE;
                            isClosed = false;
                            fireOpenDone();                            
                        } catch (IOException ioe) {
                            reset();
                            fireErrorThrown(1, "bluetooth open input stream error");
                            close();
                        }
						break;
					case READ:
						try {
                    		//BufferedInputStream bis = new BufferedInputStream(inStream);
                    		//readBytes = bis.read(bfrRead, offset, (bfrRead.length - offset));							
							readBytes = inStream.read(bfrRead, offset, (bfrRead.length - offset));
                    		if ((bfrRead.length - offset) == readBytes) {
                                // only when we have read all data we need, we can consider the read complete
                                reset();
                                fireReadDone();
                            } else {
                            	if(readBytes>0){
	                                // we haven't finished to read, so we have to update
	                                // the offset to read what lacks at the end of previous
	                                // data in the buffer
	                                offset = offset + readBytes;
                            	} else {
                            		// In questo ramo ci capita quando siamo in attesa di nuovo sul primo byte
                            		// di trasmissione ma si ha la scadenza del timeout, in questo caso la read ritorna -1
                            		// e occorre chiamara reset per ripristinare i contatori.
                            		if(notifyEOF){
                            			Log.i(TAG, "READ returned -1: stream is at end of file");
                            			fireReadDone();
                            		}
                            		reset();                            		
                            	}
                            }                            
                        } catch (IOException ioe) {
                            reset();
                            if (!isClosed) {
                            	//L'errore viene comunicato nel caso i cui la read fallisca, ma lo stream
                            	//è ancora aperto, cioè non è stata forzata la sua chiusura dall'utente.
                            	fireErrorThrown(2, "bluetooth read error IO");                            
                            }
                        } catch (IndexOutOfBoundsException iobe) {
                        	if(bfrRead != null){
	                        	Log.i(TAG, iobe.getMessage()
									+ " read parameters: bfrRead.length = "
									+ bfrRead.length + " offset = " + offset
									+ " readBytes = " + readBytes);
	                        	String bytes = "";
	                        	for (int i = 0; i < bfrRead.length; i++) {
	                        		bytes = bytes + " " + bfrRead[i];
								}
	                        	Log.i(TAG, "bfrRead = "+ bytes );
                        	}
                        	iobe.printStackTrace();
                        	reset();
                            if (!isClosed) {
                            	//L'errore viene comunicato nel caso i cui la read fallisca, ma lo stream
                            	//è ancora aperto, cioè non è stata forzata la sua chiusura dall'utente.
                            	fireErrorThrown(2, "bluetooth read error IOOB");                            
                            }
                        } catch (NullPointerException npe) {
                        	reset();
                            if (!isClosed) {
                            	//L'errore viene comunicato nel caso i cui la read fallisca, ma lo stream
                            	//� ancora aperto, cio� non � stata forzata la sua chiusura dall'utente.
                            	fireErrorThrown(2, "bluetooth read error NULL");                            
                            }
                        }
						break;
					case CLOSE:
						try {
                        	inStream.close();                            
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

	public void resetStream() {
		try {
			if(inStream!=null){
				inStream.close();
				inStream = mmSocket.getInputStream();
			}
        } catch (IOException ioe) {
            reset();
            fireErrorThrown(4, "bluetooth input stream close error");
        } 
	}
}
