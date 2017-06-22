package com.ti.app.telemed.core.webmodule;

import android.util.Log;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.webmodule.WebMessageManager.WebMessageType;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerResultEventListener;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEvent;
import com.ti.app.telemed.core.webmodule.webmanagerevents.WebManagerSendingResultEventListener;
import com.ti.app.telemed.core.webmodule.websocketevents.WebSocketTransactionEvent;
import com.ti.app.telemed.core.webmodule.websocketevents.WebSocketTransactionEventListener;
import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.exceptions.XmlException;
import com.ti.app.telemed.core.util.Base64;


// this is a singleton
public class WebManager implements Runnable, WebSocketTransactionEventListener {
	
	class WebData {
		private String authorization;
		private URL url;
		private String body;
		private String contentType;
		private byte[] attachments;
		private String attachmentsSwap;
        String requestMethod;
		private WebManagerResultEventListener webManagerResultEventListener = null;
		private WebManagerSendingResultEventListener webManagerSendingResultEventListener = null;
		
		String getAuthorization() {
			return authorization;
		}
		
		void setAuthorization(String authorization) {
			this.authorization = authorization;
		}
		
		URL getUrl() {
			return url;
		}
		
		void setUrl(URL url) {
			this.url = url;
		}
		
		String getBody() {
			return body;
		}

		void setBody(String body) {
			this.body = body;
		}

		String getContentType() {
			return contentType;
		}

		void setContentType(String contentType) {
			this.contentType = contentType;
		}

		byte[] getAttachments() {
			return attachments;
		}

		void setAttachments(byte[] attachments) {
			this.attachments = attachments;
		}
		
		String getAttachmentsSwap() {
			return attachmentsSwap;
		}

		void setAttachmentsSwap(String attachmentsSwap) {
			this.attachmentsSwap = attachmentsSwap;
		}

        String getRequestMethod() {
            return requestMethod;
        }

        void setRequestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
        }

		WebManagerResultEventListener getWebManagerResultEventListener() {
			return webManagerResultEventListener;
		}

		void setWebManagerResultEventListener(WebManagerResultEventListener webManagerResultEventListener) {
			this.webManagerResultEventListener = webManagerResultEventListener;
		}

		WebManagerSendingResultEventListener getWebManagerSendingResultEventListener() {
			return webManagerSendingResultEventListener;
		}

		void setWebManagerSendingResultEventListener(WebManagerSendingResultEventListener webManagerSendingResultEventListener) {
			this.webManagerSendingResultEventListener = webManagerSendingResultEventListener;
		}
	}

    // FIFO queue of web requests to send
	private List<WebData> list = Collections.synchronizedList(new LinkedList<WebData>());
    // if != null contains the current Web request waiting for response
    private WebData currServed = null;

	private static final String TAG = "WebManager";

	private WebSocket webSocket;
	
	private WebMessageManager webMessageManager;
	
	private String responseBody;
    private int responseCode;
    private XmlManager.XmlErrorCode resultCode = XmlManager.XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC;

    private final Thread currT;
	
    // variable for singleton
	private static WebManager webManager;
	
	private Logger logger = Logger.getLogger(WebManager.class.getName());

    // variables to manage the state of the responses from the server
    private boolean receiveError;
    private boolean responseReceived = false;

	private WebManager() {
        receiveError = false;
        webSocket = WebSocket.getWebSocket();
		// the web manager has to listen to the events of web socket
		webSocket.addWebSocketTransactionEventListener(this);
		webMessageManager = WebMessageManager.getWebMessageManager();
		currT = new Thread(this);
    	currT.setName("webmanager thread");
        currT.start();
	}
	
	public static WebManager getWebManager() {
		if (webManager == null) {
			webManager = new WebManager();
		}
		return webManager;
	}
		
	public void askOperatorData(String login, String password, WebManagerResultEventListener listener, boolean interactive) throws Exception {
		synchronized (currT) {
            Log.d(TAG, "askOperatorData enqueued");
            WebData currServed = new WebData();
            String tmp = String.format("%s:%s", login, password);
            String encoded = Base64.encodeBytes(tmp.getBytes());
            currServed.setAuthorization(encoded);
            currServed.setUrl(MyApp.getConfigurationManager().getConfigurationPlatformUrl());
            webMessageManager.generateLogInWebMessage();
            currServed.setContentType(webMessageManager.getContentType());
            currServed.setBody(webMessageManager.getBody());
            currServed.setRequestMethod(WebSocket.REQUEST_METHOD_POST);
            currServed.setWebManagerResultEventListener(listener);
            if (interactive)
                // add the request at the top of the List (maximum priority priority)
                list.add(0,currServed);
            else
                list.add(currServed);
            Log.d(TAG, "askOperatorData enqueued");
            currT.notifyAll();
        }
	}

	public void changePassword(String login, String password, String newPassword, WebManagerResultEventListener listener) throws Exception {
        synchronized (currT) {
            Log.d(TAG, "changePassword enqueued");
            WebData currServed = new WebData();
            String tmp = String.format("%s:%s", login, password);
            String encoded = Base64.encodeBytes(tmp.getBytes());
            currServed.setAuthorization(encoded);
            currServed.setUrl(MyApp.getConfigurationManager().getConfigurationPlatformUrl());
            webMessageManager.generateNewPasswordWebMessage(newPassword);
            currServed.setContentType(webMessageManager.getContentType());
            currServed.setBody(webMessageManager.getBody());
            currServed.setRequestMethod(WebSocket.REQUEST_METHOD_POST);
            currServed.setWebManagerResultEventListener(listener);
            // this kind of request is always interactive so add
            // the request at the top of the List (maximum priority priority)
            list.add(0,currServed);
            Log.d(TAG, "changePassword enqueued");
            currT.notifyAll();
        }
    }

    /* Invio messaggio con orario schedulazione misure scelto dal paziente
    public void sendCfg(String login, String password, String schedule, WebManagerResultEventListener listener) throws Exception {
        synchronized (currT) {
            WebData currServed = new WebData();
            String tmp = String.format("%s:%s", login, password);
            String encoded = Base64.encodeBytes(tmp.getBytes());
            currServed.setAuthorization(encoded);
            currServed.setUrl(MyApp.getConfigurationManager().getConfigurationPlatformUrl());
            webMessageManager.generateSetScheduledWebMessage(schedule);
            currServed.setContentType(webMessageManager.getContentType());
            currServed.setBody(webMessageManager.getBody());
            currServed.setRequestMethod(WebSocket.REQUEST_METHOD_POST);
            currServed.setWebManagerResultEventListener(listener);
            // this kind of request is always interactive so add
            // the request at the top of the List (maximum priority)
            list.add(0,currServed);
            Log.d(TAG, "sendCfg enqueued");
            currT.notifyAll();
        }
    }
    */

	public void sendMeasureData(String login, String password, String xmlMessage, byte[] xmlFileContent, String fileType, WebManagerSendingResultEventListener listener) throws Exception {
		synchronized (currT) {
            WebData currServed = new WebData();
            String tmp = String.format("%s:%s", login, password);
            String encoded = Base64.encodeBytes(tmp.getBytes());
            currServed.setAuthorization(encoded);
            currServed.setUrl(MyApp.getConfigurationManager().getSendPlatformUrl());

            if(xmlFileContent == null || xmlFileContent.length == 0){
                webMessageManager.generateSendMeasureWebMessage(xmlMessage);
            } else {
                webMessageManager.generateSendMeasureWebMessageMultipart(xmlMessage, xmlFileContent, fileType);
            }
            currServed.setContentType(webMessageManager.getContentType());
            currServed.setBody(webMessageManager.getBody());

            if(webMessageManager.getAttachments()!=null
                    && webMessageManager.getAttachments().length>0){
                currServed.setAttachments(webMessageManager.getAttachments());
            }

            currServed.setAttachmentsSwap(null);
            if(webMessageManager.getAttachmentsSwap()!=null){
                currServed.setAttachmentsSwap(webMessageManager.getAttachmentsSwap());
            }

            currServed.setRequestMethod(WebSocket.REQUEST_METHOD_GET);
            currServed.setWebManagerSendingResultEventListener(listener);
            // measures are sent in background, add the request at the
            // end of the List (lower priority)
            list.add(currServed);

            Log.d(TAG, "sendMeasureData enqueued");
            currT.notifyAll();
		}
	}
    
    // methods to trigger events
    
	private void fireWebAuthenticationSucceeded() {
    	WebManagerResultEvent event = new WebManagerResultEvent(this);
        WebManagerResultEventListener listener = currServed.getWebManagerResultEventListener();
        if (listener != null)
            listener.webAuthenticationSucceeded(event);
    }

    private void fireChangePasswordSucceeded() {
        WebManagerResultEvent event = new WebManagerResultEvent(this);
        WebManagerResultEventListener listener = currServed.getWebManagerResultEventListener();
        if (listener != null)
            listener.webChangePasswordSucceded(event);
    }

    private void fireWebAuthenticationFailed() {
        WebManagerResultEvent event = new WebManagerResultEvent(this);
        WebManagerResultEventListener listener = currServed.getWebManagerResultEventListener();
        if (listener != null)
            listener.webAuthenticationFailed(event);
    }
	
	private void fireWebOperationFailed(XmlErrorCode code) {
        WebManagerResultEvent event = new WebManagerResultEvent(this);
        event.setResultCode(responseCode);
        WebManagerResultEventListener listener = currServed.getWebManagerResultEventListener();
        if (listener != null)
            listener.webOperationFailed(event, code);
    }
	
	private void fireWebAuthenticationFailedSending() {
        WebManagerSendingResultEvent event = new WebManagerSendingResultEvent(this);
        WebManagerSendingResultEventListener listener = currServed.getWebManagerSendingResultEventListener();
        if (listener != null)
            listener.webAuthenticationFailed(event);
    }
	
	private void fireWebOperationFailedSending(XmlErrorCode code) {
    	WebManagerSendingResultEvent event = new WebManagerSendingResultEvent(this, resultCode);
        WebManagerSendingResultEventListener listener = currServed.getWebManagerSendingResultEventListener();
        if (listener != null)
            listener.webOperationFailed(event, code);
    }
	
	private void fireSendingMeasureSucceededSending() {
		WebManagerSendingResultEvent event = new WebManagerSendingResultEvent(this);
        WebManagerSendingResultEventListener listener = currServed.getWebManagerSendingResultEventListener();
        if (listener != null)
            listener.sendingMeasureSucceeded(event);
	}

	
	// methods of WebSocketTransactionEventListener interface
    @Override
	public void transactionSucceeded(WebSocketTransactionEvent evt, int responseCode, String responseBody) {
		// this is the case of login
		synchronized (currT) {
			if (currServed != null) {
				this.responseCode = responseCode;
				this.responseBody = responseBody;
				receiveError = false;
                responseReceived = true;
                currT.notifyAll();
	        }
		}
	}

    @Override
	public void transactionFailed(WebSocketTransactionEvent evt,int responseCode) {
		// this is the case of login
		synchronized (currT) {
			if (currServed != null) {
				this.resultCode = evt.resultCode();
				this.responseCode = responseCode;
                receiveError = true;
                responseReceived = true;
                currT.notifyAll();
	        }
		}
	}
	
	// methods of Runnable interface
    @Override
	public void run() {
        synchronized (currT) {
            while (true) {
                try {
                    if ((currServed != null) || (list.isEmpty())) {
                        Log.d(TAG, "Wait ....");
                        currT.wait();
                    }
                    if ((responseReceived) && (currServed != null)) {
                        Log.d(TAG, "Awake, response arrived");
                        manageResponse();
                    }
                    if ((currServed == null) && (!list.isEmpty())) {
                        // Send the next web request
                        Log.d(TAG, "Sending the next request.");
                        sendData();
                    }
                } catch (InterruptedException ie) {
                    logger.log(Level.SEVERE, "Thread interrotto");
                }
            }
        }
    }

    private void sendData() {
        if (!list.isEmpty()) {
            // get the first element
            currServed = list.get(0);
            list.remove(0);
            receiveError = false;
            responseReceived = false;
            try {
                webSocket.send(currServed);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "web socket error ASK_OPERATOR");
            }
        }
    }

    private void manageResponse() {
        try {
            if (!receiveError) {
                WebMessageManager.WebMessageType webMsgType = webMessageManager
                        .evaluateWebMessageResponse(responseBody);
                //logger.log(Level.INFO,responseBody);
                logger.log(Level.INFO, webMsgType.toString());

                if (webMsgType == WebMessageType.LOGIN_RESPONSE) {
                    // this response doesn't need other web operations
                    fireWebAuthenticationSucceeded();
                } else if (webMsgType == WebMessageType.CHANGE_PASSWORD_RESPONSE) {
                    // this response needs to deal newly with platform
                    fireChangePasswordSucceeded();
                } else if (webMsgType == WebMessageType.CONF_UPDATE_RESPONSE) {
                    // this response needs to deal newly with platform
                    fireWebAuthenticationSucceeded();
                } else if (webMsgType == WebMessageType.SEND_MEASURE_RESPONSE) {
                    // this response doesn't need other web operations
                    fireSendingMeasureSucceededSending();
                } else if (webMsgType == WebMessageType.LOGIN_ERROR_RESPONSE) {
                    // this response needs to deal newly with platform
                    receiveError = true;
                } else if (webMsgType == WebMessageType.SEND_ERROR_RESPONSE) {
                    receiveError = true;
                }
            }
            if (receiveError) {
                switch (responseCode) {
                    case 401:
                        // this is the case in which the authentication fails
                        fireWebAuthenticationFailed();
                        fireWebAuthenticationFailedSending();
                        break;
                    case 423:
                        fireWebOperationFailed(XmlErrorCode.PASSWORD_WRONG_TOO_MANY_TIMES);
                        fireWebOperationFailedSending(XmlErrorCode.PASSWORD_WRONG_TOO_MANY_TIMES);
                        break;
                    case 403:
                        fireWebOperationFailed(XmlErrorCode.USER_BLOCKED);
                        fireWebOperationFailedSending(XmlErrorCode.USER_BLOCKED);
                        break;
                    default:
                        // these are all other cases of http errors
                        fireWebOperationFailed(resultCode);
                        fireWebOperationFailedSending(resultCode);
                        break;
                }
            }
        } catch (XmlException e) {
            logger.log(Level.SEVERE, "web socket error MANAGE_SUCCESS "+e.getMessage());
            fireWebOperationFailed(XmlErrorCode.PLATFORM_ERROR);
            fireWebOperationFailedSending(XmlErrorCode.PLATFORM_ERROR);
        } catch (DbException e) {
            logger.log(Level.SEVERE, "web socket error MANAGE_SUCCESS "+e.getMessage());
            fireWebOperationFailed(XmlErrorCode.PLATFORM_ERROR);
        } finally {
            currServed = null;
        }
    }
}
