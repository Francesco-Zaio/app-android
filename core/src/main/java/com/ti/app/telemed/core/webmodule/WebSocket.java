package com.ti.app.telemed.core.webmodule;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;

import android.util.Log;

import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.webmodule.WebManager.WebData;
import com.ti.app.telemed.core.webmodule.websocketevents.WebSocketTransactionEvent;
import com.ti.app.telemed.core.webmodule.websocketevents.WebSocketTransactionEventListener;
import com.ti.app.telemed.core.xmlmodule.ParsedResponseHandlerDataSet;
import com.ti.app.telemed.core.xmlmodule.ResponseBodyHandler;
import com.ti.app.telemed.core.xmlmodule.XmlManager;


//this is a singleton (in the future we can change this in a multiple instance class, but using
//getWebSocket to check the load balance for the web opeartion)
public class WebSocket implements Runnable {
	
	private enum Op {
        IDLE,
        SENDHTTPREQUEST,
        RECEIVEHTTPRESPONSE
    }
	
	private static final String TAG = "WebSocket";

	static final String REQUEST_METHOD_GET = "GET";
	static final String REQUEST_METHOD_POST = "POST";
	private static final int BUFFER_SIZE = 16384;

	private XmlManager.XmlErrorCode resultCode = XmlManager.XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC;

	private Vector<WebSocketTransactionEventListener> webSocketTransactionEventListeners = new Vector<>();
	private URL connUrl;
	private URLConnection urlConnection;
	private String currRequestMethod;
    private BufferedReader reader = null;
    private BufferedOutputStream writer = null;
    private String body;
    private byte[] attachments;
    private String attachmentsSwap;
    private String authorization;
	private String contentType;
	private int responseCode;
	private boolean loop = true;
    // switch about current state (which operation is
    // currently on, or idle if there isn't)
    private Op currentOpOn;
    private final Thread currT;
    private boolean receiveCompleted;
    private String responseBody;
    private boolean firstTime;

    
    // variable for singleton
	private static WebSocket webSocket;
	
	private Logger logger = Logger.getLogger(WebSocket.class.getName());
	
	private WebSocket() {
		reset();
		currT = new Thread(this);
    	currT.setName("websocket thread");
        currT.start();
	}
	
	static WebSocket getWebSocket() {
		if (webSocket == null) {
			webSocket = new WebSocket();
			System.setProperty("http.keepAlive", "false");
		}
		return webSocket;
	}
	
	private void reset() {
		Log.i(TAG, "reset()");
        currentOpOn = Op.IDLE;
        body = null;
        attachments = null;
        attachmentsSwap = null;
        contentType = null;
		urlConnection = null;
		receiveCompleted = false;
		responseBody = "";
		firstTime = true;
		writer = null;
		reader = null;
    }
	
	public void send(WebData info) throws Exception {
		synchronized (currT) {
            if (currentOpOn == Op.IDLE) {
            	System.setProperty("http.keepAlive", "false");
            	//Prima dell'invio faccio un reset della connessione
            	reset();
            	connUrl = info.getUrl();
        		currRequestMethod = info.getRequestMethod();
        		contentType = info.getContentType();
        		body = info.getBody();
        		attachments = info.getAttachments();
        		attachmentsSwap = info.getAttachmentsSwap();
        		authorization = info.getAuthorization();
                currentOpOn = Op.SENDHTTPREQUEST;
            }
            currT.notifyAll();
        }
	}
	
	private void sendDone() {
		currentOpOn = Op.RECEIVEHTTPRESPONSE;
	}
	
	private void receiveDone() {
		Log.i(TAG, "receiveDone()");
		currentOpOn = Op.IDLE;
		receiveCompleted = false;
		firstTime = true;
		fireTransactionSucceeded(responseCode, responseBody);
		Log.i(TAG, "ReceiveDone: Operazione terminata con successo");
		responseBody = "";
	}
	
	private void close() {
		Log.i(TAG, "close socket");
		
		try {
            if (writer != null) {
            	writer.close();
				writer = null;
            }
            if (reader != null) {
            	reader.close();
				reader = null;
            }                                        
        } catch (Exception e) {
        	logger.log(Level.INFO, "Exception closing input/output stream: "+e.getMessage());
        	Log.e(TAG, "close: Exception closing input/output stream: " + e.getMessage());
        } finally {
        	if(urlConnection != null){
        		logger.log(Level.INFO, "Calling disconnect on urlConnection");  
        		try {
        			if(urlConnection instanceof HttpsURLConnection){
        				((HttpsURLConnection)urlConnection).disconnect();
        			} else if(urlConnection instanceof HttpURLConnection){
        				((HttpURLConnection)urlConnection).disconnect();
        			}
				} catch (Exception e) {
					logger.log(Level.INFO, "Exception thrown Calling disconnect: "+e.getMessage()); 
				}        		
        	}
        	reset();
        }
	}

	
	// methods to add/remove event listeners

	synchronized void addWebSocketTransactionEventListener(WebSocketTransactionEventListener listener) {
        if (webSocketTransactionEventListeners.contains(listener)) {
            return;
        }
        webSocketTransactionEventListeners.addElement(listener);
    }
    
    public synchronized void removeWebSocketTransactionEventListener(WebSocketTransactionEventListener listener) {
    	webSocketTransactionEventListeners.removeElement(listener);
    }
    
    // methods to trigger events

    private void fireTransactionSucceeded(int responseCode, String responseBody) {
    	WebSocketTransactionEvent event = new WebSocketTransactionEvent(this);
        for (WebSocketTransactionEventListener listener : getWebSocketTransactionEventListeners()) {
        	listener.transactionSucceeded(event, responseCode, responseBody);
        }
    }
    
	private void fireTransactionFailed(int responseCode) {
    	WebSocketTransactionEvent event = new WebSocketTransactionEvent(this, resultCode);
        for (WebSocketTransactionEventListener listener : getWebSocketTransactionEventListeners()) {
        	listener.transactionFailed(event, responseCode);
        }
    }
	
	private Vector<WebSocketTransactionEventListener> getWebSocketTransactionEventListeners(){
		// we work on a copy of the vector, so if change we don't have problem
		Vector<WebSocketTransactionEventListener> copy;
        synchronized (this) {
            copy = new Vector<> (webSocketTransactionEventListeners);
        }
        return copy;
	}
	
	// methods of Runnable interface
	public void run() {
        synchronized (currT) {
            while (loop) {
                switch (currentOpOn) {
                    case IDLE:
                        try {
                            currT.wait();
                        } catch (InterruptedException e) {
							// TODO va notificato l'eventuale chiamante ??
                        	// fireStatusChanged(1, "Thread interrotto");
                        	logger.log(Level.SEVERE, "Thread interrotto " + e.getMessage());
                        }
                        break;
                    case SENDHTTPREQUEST:
                    	try {
                    		Log.i(TAG, "Piattaforma: " + connUrl.toString());
                    		
                    		if ((connUrl.getProtocol()).equals("https")) {
                    			SimpleX509TrustManager.allowAllSSL();
                    		}
                    		
                    		// we open the HTTP connection
                    		urlConnection = connUrl.openConnection();
                            urlConnection.setConnectTimeout(GWConst.CONNECTION_TIMEOUT);
                            urlConnection.setReadTimeout(GWConst.READ_TIMEOUT);

                			// we add capability to use input and output stream
                			urlConnection.setDoInput(true);
                			urlConnection.setDoOutput(true);
                			
                			if(urlConnection instanceof HttpsURLConnection){
                				((HttpsURLConnection)urlConnection).setRequestMethod(currRequestMethod);
                			} else if(urlConnection instanceof HttpURLConnection){
                				((HttpURLConnection)urlConnection).setRequestMethod(currRequestMethod);
                            }
                			
                			// we set the header fields in the HTTP request
                            // used user agent for requests
                            urlConnection.setRequestProperty("User-Agent", "TelemonitoraggioDesktop");
							// this client accepts all content types. (change to e.g. "text/plain" for plain text only)
                            urlConnection.setRequestProperty("Accept", "*/*");
                            urlConnection.setRequestProperty("Content-Type", contentType);    
                            
                            /*
                            if(attachmentsSwap != null){
                            	
                            	File fi = new File(attachmentsSwap);
                            	FileInputStream fis = new FileInputStream(fi);      
                            	long total = fi.length();
                            	
                            	urlConnection.setRequestProperty("Content-Length", ""+total);   
                            }
                            */
                            
                            if (authorization != null) {
                            	urlConnection.setRequestProperty("Authorization", "Basic " + authorization);
                            }
                            
                            urlConnection.setDefaultUseCaches(false);
                            urlConnection.setUseCaches(false);

                            File fi = null;
                            if(attachmentsSwap != null){
                            	fi = new File(attachmentsSwap);
                            	int total = body.getBytes().length + (int)fi.length() + WebMessageManager.getWebMessageManager().getContentSeparator().length;
                            	if(urlConnection instanceof HttpsURLConnection){
                    				((HttpsURLConnection)urlConnection).setFixedLengthStreamingMode(total);
                    			} else if(urlConnection instanceof HttpURLConnection){
                    				((HttpURLConnection)urlConnection).setFixedLengthStreamingMode(total);
                    			}
                            } else {
                                // Issue 163595: 	HttpsURLConnection silently retries on POST method request
                                if(urlConnection instanceof HttpsURLConnection){
                                    ((HttpsURLConnection)urlConnection).setChunkedStreamingMode(0);
                                } else if(urlConnection instanceof HttpURLConnection){
                                     ((HttpURLConnection)urlConnection).setChunkedStreamingMode(0);
                                }
                            }
                                                                                   
                            // we connect to the URL
                            urlConnection.connect();
                                                                                    
                            // we open input and output streams                            
                            writer = new BufferedOutputStream(urlConnection.getOutputStream(), BUFFER_SIZE);
                            logger.log(Level.INFO, "Message body : "+body);
                            // we send the HTTP request
                            writer.write(body.getBytes());
                            if(attachments != null){
                            	writer.write(attachments);
                            }
                            
                            if(fi != null){
                            	Log.d(TAG, "case SENDHTTPREQUEST: attachmentsSwap=" + attachmentsSwap);
                            	byte[] buffer = new byte[BUFFER_SIZE];
                            	int bytes_read;
                            	FileInputStream fis = new FileInputStream(fi);
                                while (((bytes_read = fis.read(buffer)) != -1) && (bytes_read > 0)) {
                                    writer.write(buffer, 0, bytes_read);
                                    writer.flush();
                                }
                                // Write the end of file multipart TAG
                                writer.write(WebMessageManager.getWebMessageManager().getContentSeparator());
                            }
                            
                            writer.flush();
                            sendDone();

                        } catch (java.net.SocketTimeoutException ex) {
                            Log.i(TAG, "Exception");
                            String msg = ex.getMessage();
                            logger.log(Level.SEVERE, "HTTP Connection Timeout error: " + msg);
                            fireTransactionFailed(408);
                            close();
                        }  catch (Exception ex) {
                        	Log.i(TAG, "Exception");
                        	String msg = ex.getMessage();
                            logger.log(Level.SEVERE, "httpengine send error " + ex.getClass().getName() + ": " + msg);
                            fireTransactionFailed(404);
                            close();
                        }
                        break;
                    case RECEIVEHTTPRESPONSE:
                        // the operation we must do is a read

                        // java.net.SocketTimeoutException
                        try {
                        	// get the response
                        	boolean rightResponseCode = true;
                        	if (firstTime && urlConnection!=null) {
	                        	// get HTTP status code from header (e.g. 200)
                        		String statusText = "";
                        		
                        		if(urlConnection instanceof HttpsURLConnection){
                        			responseCode = ((HttpsURLConnection)urlConnection).getResponseCode();
                        			statusText = ((HttpsURLConnection)urlConnection).getResponseMessage();
                    			} else if(urlConnection instanceof HttpURLConnection){
                    				responseCode = ((HttpURLConnection)urlConnection).getResponseCode();
                        			statusText = ((HttpURLConnection)urlConnection).getResponseMessage();
                    			}
	                        	
	                        	if (responseCode != 200) {
	                        		// 200 is http response ok, so we had a problem
	                        		Log.i(TAG, "ResponseCode: " + responseCode + " - Message: " + statusText);
									rightResponseCode = false;
                                    fireTransactionFailed(responseCode);
                                    close();
	                        	} else {
	                        		// in case of http response ok
		                        	reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		                        	firstTime = false;
	                        	}
                        	}
                        	if (rightResponseCode) {
	                            String line = reader.readLine();
	                            if (line != null) {
	                            	responseBody = responseBody + line + System.getProperty("line.separator");
	                            } else {
	                            	receiveCompleted = true;
	                            }
	                            if (receiveCompleted) {
                                    if(urlConnection != null){
                                        logger.log(Level.INFO, "Calling disconnect on urlConnection");
                                        try {
                                            if(urlConnection instanceof HttpsURLConnection){
                                                ((HttpsURLConnection)urlConnection).disconnect();
                                            } else if(urlConnection instanceof HttpURLConnection){
                                                ((HttpURLConnection)urlConnection).disconnect();
                                            }
                                        } catch (Exception e) {
                                            logger.log(Level.INFO, "Exception thrown Calling disconnect: "+e.getMessage());
                                        }
                                        urlConnection = null;
                                    }
                                    if(writer != null) {
                                        writer.close();
                                        writer = null;
                                    }
                                    if(reader != null) {
                                        reader.close();
                                        reader = null;
                                    }
	                                /* Create a new ContentHandler and apply it to the XML-Reader*/
	                                ResponseBodyHandler responseBodyHandler = new ResponseBodyHandler();
	                                /* Parse the xml-data */
	                                Log.d(TAG, "responseBody =" + responseBody);
	                                android.util.Xml.parse(responseBody, responseBodyHandler);
	                                /* Parsing has finished. */
	                                
	                                ParsedResponseHandlerDataSet parsedResponseHandlerDataSet = responseBodyHandler.getParsedData();
	                                Log.i(TAG, "ResponseCode: " + parsedResponseHandlerDataSet.getExtractedInt());
	                                Log.i(TAG, "ResponseMessage: " + parsedResponseHandlerDataSet.getExtractedString());
	                                
	                                if(parsedResponseHandlerDataSet.getExtractedInt() != 0) {
	                                	currentOpOn = Op.IDLE;
		                        		receiveCompleted = false;
		                        		firstTime = true;
                                        resultCode = XmlManager.XmlErrorCode.convertTo(parsedResponseHandlerDataSet.getExtractedInt());
                                        fireTransactionFailed(-1);
	                                }
	                                else {
	                                	// output the response	                            	
	                                	receiveDone();
	                                }
	                            }
                        	}
                        } catch (java.net.SocketTimeoutException ex) {
                            Log.i(TAG, "Exception");
                            String msg = ex.getMessage();
                            logger.log(Level.SEVERE, "HTTP Connection Timeout error: " + msg);
                            fireTransactionFailed(408);
                            close();
                        } catch (Exception ioe) {
                            logger.log(Level.SEVERE, "httpengine receive error " + ioe);
							fireTransactionFailed(-1);
                            close();
                        }
                        break;                    
                }
            }
        }
	}
}
