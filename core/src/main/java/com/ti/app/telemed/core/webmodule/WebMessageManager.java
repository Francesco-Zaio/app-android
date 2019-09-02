package com.ti.app.telemed.core.webmodule;

import java.io.File;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.util.Log;

import com.ti.app.telemed.core.xmlmodule.XmlManager;
import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;
import com.ti.app.telemed.core.exceptions.XmlException;

import static com.ti.app.telemed.core.xmlmodule.XmlManager.AECG_FILE_TYPE;
import static com.ti.app.telemed.core.xmlmodule.XmlManager.DOCUMENT_FILE_TYPE;
import static com.ti.app.telemed.core.xmlmodule.XmlManager.IMG_FILE_TYPE;


// this is a singleton
public class WebMessageManager {

    class WebResponse {
        WebMessageType type;
        Vector<Object> responseObjects;
    }

	public enum WebMessageType {
        LOGIN_RESPONSE,
        CONF_UPDATE_RESPONSE,
        SEND_MEASURE_RESPONSE,
        CHANGE_PASSWORD_RESPONSE,
        LOGIN_ERROR_RESPONSE,
        SEND_ERROR_RESPONSE
    }
	
	public enum WebMessageRequest {
        LOGIN_CONF_REQUEST,
        CHANGE_PASSWORD_REQUEST,
        SEND_MEASURE_REQUEST,
    }

	private static final String TAG = "WebMessageManager";
	
	private final String GENERICMESSAGEDEFINER = "M2MXMLPacket=";
    private final String CONFIGURATIONMESSAGEDEFINER = "M2MConfigurePacket=";
    private final String GENERICCONTENTTYPE = "application/x-www-form-urlencoded";
    
    final static String NEWLINE = "\r\n";
    final static String PREFIX = "--";
    // the Boudary String should be the same in all the message part (System.currentTimeMillis() must be the same!!)
    final static String BOUNDARY = "*****" + Long.toString(System.currentTimeMillis(), 16);

    private final String MULTIPARTCONTENTYPE = "multipart/form-data; boundary=";
	
	private XmlManager xmlManager;
	private String contentType;
	private String webMessage;
	private byte[] attachments;
	private String attachmentsSwap;
	private WebMessageRequest currentRequest;
	
	private static WebMessageManager webMessageManager;
	
	private XmlErrorCode xmlResultCode;
	
	private Logger logger = Logger.getLogger(WebMessageManager.class.getName());
	
	private WebMessageManager() {
		xmlManager = XmlManager.getXmlManager();
	}
	
	public static WebMessageManager getWebMessageManager() {
		if (webMessageManager == null) {
			webMessageManager = new WebMessageManager();
		}
		return webMessageManager;
	}

    public byte[] getContentSeparator() {
        String separator =
                NEWLINE
                + PREFIX
                + BOUNDARY
                + PREFIX
                + NEWLINE;
        return separator.getBytes();
    }
	// generation of configuration messages
	
	public void generateLogInWebMessage() throws SQLException {
		currentRequest = WebMessageRequest.LOGIN_CONF_REQUEST;
		contentType = GENERICCONTENTTYPE;
		// db doesn't exist (first time that the application is used)
		webMessage = CONFIGURATIONMESSAGEDEFINER + xmlManager.getConfigurationQuery();		
	}

	public void generateNewPasswordWebMessage(String password) {
		currentRequest = WebMessageRequest.CHANGE_PASSWORD_REQUEST;
		contentType = GENERICCONTENTTYPE;
		// db doesn't exist (first time that the application is used)
		webMessage = CONFIGURATIONMESSAGEDEFINER + xmlManager.getNewPassword(password);
	}

	/* Genera XML per invio orario schedulazione misure
    public void generateSetScheduledWebMessage(String schedule) {
        currentRequest = WebMessageRequest.SEND_CFG_REQUEST;
        contentType = GENERICCONTENTTYPE;
        // db doesn't exist (first time that the application is used)
        webMessage = CONFIGURATIONMESSAGEDEFINER + xmlManager.getSchedule(schedule);
    }
    */

    public void generateSendMeasureWebMessage(String xmlMessage) throws Exception {
		currentRequest = WebMessageRequest.SEND_MEASURE_REQUEST;
		contentType = GENERICCONTENTTYPE;
		if (xmlMessage.length() != 0) {
			webMessage = GENERICMESSAGEDEFINER + xmlMessage;
		} else {
			// at this moment the xml message must be there
			throw new Exception("Xml Message doesn't exist");
		}
		attachments = null;
		attachmentsSwap = null;
	}
	
	public void generateSendMeasureWebMessageMultipart(String xmlMessage, byte[] xmlFileContent, String fileType) throws Exception {	
		currentRequest = WebMessageRequest.SEND_MEASURE_REQUEST;

    	contentType = MULTIPARTCONTENTYPE + BOUNDARY;

		boolean isImageFile = false;
        String filePath = null;
		//Costruisce il multipart
    	String dataString = "";
    	dataString = dataString + PREFIX
    							+ BOUNDARY
    							+ NEWLINE;
    	dataString = dataString + "Content-Disposition: form-data; name=\"M2MXMLFile\"; filename=\"M2MXLMFile\""
    							+ NEWLINE;
    	dataString = dataString + "Content-Type: text/xml"
    							+ NEWLINE
    							+ NEWLINE;
    	dataString = dataString + xmlMessage
    							+ NEWLINE
    							+ PREFIX
    							+ BOUNDARY
    							+ NEWLINE;
		switch (fileType) {
			case IMG_FILE_TYPE:
			case DOCUMENT_FILE_TYPE:
			case AECG_FILE_TYPE:
                isImageFile = new File(new String(xmlFileContent,"UTF-8")).exists();
                filePath = new String(xmlFileContent,"UTF-8");
                String [] tokens  = filePath.split(File.separator);
				dataString = dataString + "Content-Disposition: form-data; name=\""+fileType+"\"; filename=\""+tokens[tokens.length-1]+"\""
						+ NEWLINE;
                break;
            default:
                dataString = dataString + "Content-Disposition: form-data; name=\""+fileType+"\"; filename=\"\""
                        + NEWLINE;
		}
    	dataString = dataString + "Content-Type: application/octet-stream"
								+ NEWLINE
								+ NEWLINE;

    	int capacity;
    	
    	Log.d(TAG, "isImageFile=" + isImageFile);
    	
    	ByteBuffer buffer = null;
    	attachmentsSwap = null;
    	
    	if (isImageFile) {
    		attachmentsSwap = filePath;
    		Log.d(TAG, "attachmentsSwap=" + attachmentsSwap);
    	}
    	else {
    		capacity = xmlFileContent.length + NEWLINE.length()
				+ PREFIX.length() + BOUNDARY.length() + PREFIX.length()
				+ NEWLINE.length();
    	
	    	buffer = ByteBuffer.allocate(capacity);
	    	buffer.put(xmlFileContent);
	    	buffer.put(NEWLINE.getBytes());
	    	buffer.put(PREFIX.getBytes());
	    	buffer.put(BOUNDARY.getBytes());
	    	buffer.put(PREFIX.getBytes());
	    	buffer.put(NEWLINE.getBytes());    	
    	}
    	
		if (dataString.length() != 0) {
			webMessage = dataString;
			logger.log(Level.INFO, "Message: " +webMessage);
		} else {
			// at this moment the xml message must be there
			throw new Exception("Xml Message doesn't exist");
		}
		
		if (isImageFile) {
			attachments = null;
		}
		else {
			if(buffer.capacity() > 0){
				attachments = buffer.array();
			} else {
				attachments = null;
			}
		}
	}
	
	// this method must evaluate a web message response and return the message type; the result of
	// evaluation are saved on db if there are
	WebResponse evaluateWebMessageResponse(String responseBody) throws XmlException {
        WebResponse response = new WebResponse();
        response.type  = WebMessageType.LOGIN_RESPONSE;
		xmlManager.parse(responseBody);
		logger.log(Level.INFO, "WebMessageManager evaluateWebMessageResponse ENTER");
		logger.log(Level.INFO, "responseTimestamp=" + xmlManager.getResponseTimestamp());

		xmlResultCode = xmlManager.getResponseResultCode();
		if (xmlResultCode == XmlManager.XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC) {		
			// a response to a login or a response to configuration or update
			response.responseObjects = xmlManager.getParsedData();
			if (response.responseObjects != null) {
                switch (currentRequest){
                    case SEND_MEASURE_REQUEST:
                        // it was a response to sending measure data and so we have to update the db
                        logger.log(Level.INFO, "WebMessageManager evaluateWebMessageResponse message: " +xmlManager.getResponseMessage());
                        response.type = WebMessageType.SEND_MEASURE_RESPONSE;
                        break;
                    case CHANGE_PASSWORD_REQUEST:
                        response.type = WebMessageType.CHANGE_PASSWORD_RESPONSE;
                        break;
                    case LOGIN_CONF_REQUEST:
                        // it was a response to configuration or update and so we have to update the db
                        response.type = WebMessageType.CONF_UPDATE_RESPONSE;
                        break;
                }
			}
			else {
                response.type = WebMessageType.LOGIN_ERROR_RESPONSE;
            }
			// if not enter in any if case it was a login response and so the default
			// return value;
			
		} else if (xmlResultCode == XmlManager.XmlErrorCode.BAD_ARGUMENTS) {
            response.type = WebMessageType.LOGIN_ERROR_RESPONSE;
		} else if (xmlResultCode == XmlManager.XmlErrorCode.UNRECOGNIZED_COMMAND) {
			logger.log(Level.INFO, "WebMessageManager evaluateWebMessageResponse UNRECOGNIZED_COMMAND");
            response.type = WebMessageType.LOGIN_ERROR_RESPONSE;
		} else if (xmlResultCode == XmlManager.XmlErrorCode.EXECUTION_FAILED){
            response.type = WebMessageType.SEND_ERROR_RESPONSE;
		}

		return response;
	}
	
	public String getBody() {
        return webMessage;
    }
	
	public byte[] getAttachments() {
        return attachments;
    }
	
	public String getAttachmentsSwap() {
        return attachmentsSwap;
    }
	
	public String getContentType() {
        return contentType;
    }
	
	public XmlErrorCode getXmlResultCode(){
		return xmlResultCode;
	}
	
}
