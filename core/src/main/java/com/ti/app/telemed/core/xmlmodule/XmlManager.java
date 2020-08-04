package com.ti.app.telemed.core.xmlmodule;

import android.util.Log;

import com.ti.app.telemed.core.BuildConfig;
import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.exceptions.XmlException;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.nio.CharBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

//this is a singleton
public class XmlManager extends DefaultHandler {
	
	private static final String TAG = "XmlManager";

    public enum XmlErrorCode {
        COMMAND_SUCCESSFULLY_EXEC (0),
		MESSAGE_RECEIVED (1),
		MESSAGE_SUBMITTED (2),
		DELIVERY_FAILED (3),
		EXECUTION_FAILED (4),
		UNRECOGNIZED_COMMAND (5),
		BAD_ARGUMENTS (6),
		EXECUTED_WARNING (7),
		BAD_TD_VALUE (21),
		UNRECOGNIZED_TD (22),
		TD_ALREADY_USED (23),
		UNEXPECTED_ADDRESS_VALUE (24),
		SERVICECENTER_CAPACITY_EXCEEDED (34),
        BAD_PASSWORD (35),
		//---- Not contained in the message	
		BAD_SEQUENCE_NUMBER (33),
		RESPONSE_FORMAT_ERROR (55),        
        PLATFORM_ERROR (99),
        CONNECTION_ERROR (100),
        PASSWORD_WRONG_TOO_MANY_TIMES (423),
        USER_BLOCKED (403);

        private final int val;

        XmlErrorCode(int val) {
            this.val = val;
        }
        
        public static XmlErrorCode convertTo(int valToConvert) {
        	switch (valToConvert) {
        		case 0:
        			return COMMAND_SUCCESSFULLY_EXEC;
        		case 1:
        			return MESSAGE_RECEIVED;
        		case 2:
        			return MESSAGE_SUBMITTED;
        		case 3:
        			return DELIVERY_FAILED;
        		case 4:
        			return EXECUTION_FAILED;
        		case 5:
        			return UNRECOGNIZED_COMMAND;
        		case 6:
        			return BAD_ARGUMENTS;
        		case 7:
        			return EXECUTED_WARNING;
        		case 21:
        			return BAD_TD_VALUE;
        		case 22:
        			return UNRECOGNIZED_TD;
        		case 23:
        			return TD_ALREADY_USED;
        		case 24:
        			return UNEXPECTED_ADDRESS_VALUE;
        		case 33:
					return BAD_SEQUENCE_NUMBER;
				case 34:
					return SERVICECENTER_CAPACITY_EXCEEDED;
                case 35:
                    return BAD_PASSWORD;
                case 55:
        			return RESPONSE_FORMAT_ERROR;
				case 99:
					return PLATFORM_ERROR;
                case 100:
                    return CONNECTION_ERROR;
				case 423:
					return PASSWORD_WRONG_TOO_MANY_TIMES;
				case 403:
					return USER_BLOCKED;
        		default:
        			return COMMAND_SUCCESSFULLY_EXEC;
        	}
        }
        
        public static int convertFrom(XmlErrorCode valToConvert) {
            return valToConvert.val;
        }
    }
 
	final static private String M2MXML_VERSION = "1.1";
    final static private String CONFIGURATION_PROPERTY_NAME = "parametri_gateway_unificato";
    final static private String ID_USER_PERCEPTTYPE = "ID_USER";
    final static private String MODEL_PERCEPTTYPE = "MODEL";
    final static private String STANDARD_PERCEPTTYPE = "STANDARD";
    final static private String PRIORITY_PERCEPTTYPE = "PRIORITY";
    final static private String RESULT_PERCEPTTYPE = "RESULT";
    final static private String MEASURE_TYPE_PERCEPTTYPE = "MEASURE_TYPE";
	final static private String NEW_PASSWORD_PROPERTY = "PWD";
	final static private String SCHEDULE_PROPERTY = "SCHEDULE";

    final static private String CONFIGURATION_XMLQUERY = "<M2MXML ver=\"1.1\"><Command name=\"queryConfiguration\" seq=\"%d\"><Property name=\"%s\"/></Command></M2MXML>";
	final static private String PASSWORD_CHANGE_XML = "<M2MXML ver=\"1.1\"><Command name=\"changePassword\" seq=\"%d\"><Property name=\"%s\" value=\"%s\"/></Command></M2MXML>";
	final static private String CONFIGURATION_XMLREPORT = "<M2MXML ver=\"1.1\"><Command name=\"reportConfig\" seq=\"%d\"><Property name=\"%s\" value=\"%s\"/></Command></M2MXML>";
    final static private String ROOT_XML_SEND_MEASURE = "<M2MXML ver=\"1.1\" td=\"%s\">";
    final static private String EXCEPTION_XML = "<Exception exceptionCode=\"%s\" message=\"%s\"/>";
    final static private String COMMAND_XML_SEND_MEASURE = "<Command name=\"sendingPercept\" seq =\"%s\">";
    final static private String PROPERTY_XML_SEND_MEASURE = "<Property name=\"%s\" value=\"%s\"/>";
    final static private String PERCEPTBUNDLE_XML_SEND_MEASURE = "<PerceptBundle timestamp=\"%s\">";
    final static private String PERCEPT_XML_SEND_MEASURE = "<Percept address=\"%s\" value=\"%s\" />";
	final static private String PERCEPT_XML_SEND_MEASURE_WITH_TH = "<Percept address=\"%s\" value=\"%s\" thresholds=\"%s\"/>";
    final static private String PERCEPTBUNDLE_END_XML_SEND_MEASURE = "</PerceptBundle>";
    final static private String COMMAND_END_XML_SEND_MEASURE = "</Command>";
    final static private String ROOT_END_XML_SEND_MEASURE = "</M2MXML>";
    
    // constant tag names
    final static private String M2MXML_TAGNAME = "M2MXML";
    final static private String COMMAND_TAGNAME = "Command";
    final static private String RESPONSE_TAGNAME = "Response";
    final static private String PROPERTY_TAGNAME = "Property";

    // constant attribute names
    final static private String VERSION_ATTRIBUTENAME = "ver";
    final static private String SEQUENCE_ATTRIBUTENAME = "seq";
    final static private String RESULTCODE_ATTRIBUTENAME = "resultCode";
    final static private String MESSAGE_ATTRIBUTENAME = "message";
    final static private String TIMESTAMP_ATTRIBUTENAME = "timestamp";
    final static private String NAME_ATTRIBUTENAME = "name";

    // File Type
    public static final String AECG_FILE_TYPE = "AECGFile";
    public static final String ECG_FILE_TYPE = "ECGFile";
    public static final String PDF_FILE_TYPE = "PDFFile";
    public static final String MIR_SPIRO_FILE_TYPE = "MirSPIROFile";
    public static final String MIR_OXY_FILE_TYPE = "MirOXYFile";
    public static final String IMG_FILE_TYPE = "ImgFile";
    public static final String DOCUMENT_FILE_TYPE = "DocumentFile";

    private SAXParser parser; 

    // sequence number
    private int sequenceNumber;

    // variables for sax management
    private Vector<Object> dataContainer;
    private boolean rightM2mXml;
    private int responseSeqNumber;
    private String responseTimestamp;
    private XmlErrorCode responseResultCode;
	private String responseMessage;
	private boolean rightConfProperty;

	private StringBuilder propertyDataBuffer;
	
    private static XmlManager xmlManager;
	
	private XmlManager() {
		sequenceNumber = 0;
	}
	
	public static XmlManager getXmlManager() {
		if (xmlManager == null) {
			xmlManager = new XmlManager();
		}
		return xmlManager;
	}

    private void createParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        parser = factory.newSAXParser();
    }
    
    public String getConfigurationQuery() {
    	return String.format(Locale.ITALY, CONFIGURATION_XMLQUERY, sequenceNumber, CONFIGURATION_PROPERTY_NAME);
    }

	public String getNewPassword(String value) {
		return String.format(Locale.ITALY, PASSWORD_CHANGE_XML, sequenceNumber, NEW_PASSWORD_PROPERTY, value);
	}

	public String getSchedule(String value) {
		return String.format(Locale.ITALY, CONFIGURATION_XMLREPORT, sequenceNumber, SCHEDULE_PROPERTY, value);
	}

	public void parse(String xmlMessage) throws XmlException {
		try {
			Log.i(TAG, "Parse starting...");
			dataContainer = new Vector<>();
			rightM2mXml = false;
			responseSeqNumber = 0;
			responseTimestamp = null;
			responseResultCode = XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC;
			responseMessage = "";
			rightConfProperty = false;
			propertyDataBuffer = new StringBuilder();
			createParser();
			// we startOperation the parsing
			
			parser.parse(new ByteArrayInputStream(xmlMessage.getBytes()), this);
			Log.i(TAG, "Parse end");
		} catch (Exception e) {			
			e.printStackTrace();
			throw new XmlException(e.getMessage());
		} 
	}
    
    public Vector<Object> getParsedData() {
    	return dataContainer;
    }

    // Create XML message for failed measure
    public String getFailedMeasureXml(Measure m){
        String tmpCmd;

        tmpCmd = String.format(ROOT_XML_SEND_MEASURE, "N.A.");
        tmpCmd = tmpCmd + String.format(COMMAND_XML_SEND_MEASURE, sequenceNumber);
        //<Property name="ID_USER" value="id_user" />
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, ID_USER_PERCEPTTYPE, m.getIdPatient());
        //<Property name="MEASURE_TYPE" value="measureType"/>
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, MEASURE_TYPE_PERCEPTTYPE, m.getMeasureType());
		//<Property name="MODEL" value="modello+produttore" />
		tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, MODEL_PERCEPTTYPE, m.getDeviceDesc());
        //<PerceptBundle timestamp="(20080905174500)">
        tmpCmd = tmpCmd + String.format(PERCEPTBUNDLE_XML_SEND_MEASURE, m.getTimestamp());
        //we close the tag perceptBundle
        tmpCmd = tmpCmd + PERCEPTBUNDLE_END_XML_SEND_MEASURE;
        //we close the tag Command
        tmpCmd = tmpCmd + COMMAND_END_XML_SEND_MEASURE;
        // <Exception exceptionCode="GTW01" message="Testo indicante il tipo di errore" />
        tmpCmd = tmpCmd + String.format(EXCEPTION_XML, m.getFailureCode(), m.getFailureMessage());
        //we close the tag root
        tmpCmd = tmpCmd + ROOT_END_XML_SEND_MEASURE;

        return tmpCmd;
    }

    public String getMeasureXml(Measure m){
    	String tmpCmd;
    	
    	/*<M2MXML ver="1.1" td="(000B0D30BEC7)">
    	 <Command name="sendingPercept" seq ="(12345)"> */
    	String tmp = m.getBtAddress().replace(":", "") + " (by GW android v" + MyApp.getAppVersion() + ")";
        tmpCmd = String.format(ROOT_XML_SEND_MEASURE, tmp)
        		+String.format(COMMAND_XML_SEND_MEASURE, sequenceNumber);
    	
        //<Property name="ID_USER" value="id_user" />
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, ID_USER_PERCEPTTYPE, m.getIdPatient());
        //<Property name="MEASURE_TYPE" value="measureType"/>
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, MEASURE_TYPE_PERCEPTTYPE, m.getMeasureType());
         //<Property name="MODEL" value="modello+produttore" />
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, MODEL_PERCEPTTYPE, m.getDeviceDesc());
        //<Property name="STANDARD" value="true/false" />
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, STANDARD_PERCEPTTYPE,
                (m.getStandardProtocol()? "true":"false"));
        //<Property name="PRIORITY" value="true/false" />
        tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, PRIORITY_PERCEPTTYPE,
                (m.getUrgent()? "true":"false"));
        //<Property name="RESULT" value="G|Y|O|R" />
        if (!m.getResult().equals(Measure.RESULT_NONE))
            tmpCmd = tmpCmd + String.format(PROPERTY_XML_SEND_MEASURE, RESULT_PERCEPTTYPE, m.getResult());
     	//<PerceptBundle timestamp="(20080905174500)">
        tmpCmd = tmpCmd + String.format(PERCEPTBUNDLE_XML_SEND_MEASURE,  m.getTimestamp());
    	
		//we make the tag PerceptAddress
		Map<String,String> thresholds = m.getThresholds();
        for (Map.Entry<String, String> entry : m.getMeasures().entrySet())
        {
			if (thresholds.containsKey(entry.getKey()))
				tmpCmd = tmpCmd + String.format(PERCEPT_XML_SEND_MEASURE_WITH_TH, entry.getKey(), entry.getValue(), thresholds.get(entry.getKey()));
			else
				tmpCmd = tmpCmd + String.format(PERCEPT_XML_SEND_MEASURE, entry.getKey(), entry.getValue());
        }
    	
    	//we close the tag perceptbundle
    	tmpCmd = tmpCmd + PERCEPTBUNDLE_END_XML_SEND_MEASURE;
				
		//we close the tag command
    	tmpCmd = tmpCmd + COMMAND_END_XML_SEND_MEASURE;
				
		//we close the tag root
    	tmpCmd = tmpCmd + ROOT_END_XML_SEND_MEASURE;

        return tmpCmd;
    }

    public String getResponseTimestamp() {
    	return responseTimestamp;
    }
    
    public XmlErrorCode getResponseResultCode() {
    	Log.d(TAG, "responseResultCode = " +XmlErrorCode.convertFrom(responseResultCode));
    	return responseResultCode;
    }
    
    public String getResponseMessage() {
    	return responseMessage;
    }
    
	// sax management
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
    	//Log.d(TAG, "startElement (localName): " + localName);
        if (localName.equals(M2MXML_TAGNAME)) {
        	if (attributes.getValue(VERSION_ATTRIBUTENAME) != null) {
        		if (attributes.getValue(VERSION_ATTRIBUTENAME).equals(M2MXML_VERSION)) {
            		rightM2mXml = true;
            	}
        	}
        } else {
        	if (rightM2mXml) {
        		// we do something only if the document is right
                switch (localName) {
                    case RESPONSE_TAGNAME:
                        if (attributes.getValue(SEQUENCE_ATTRIBUTENAME) != null) {
                            responseSeqNumber = Integer.parseInt(attributes.getValue(SEQUENCE_ATTRIBUTENAME));
                        }
                        if (attributes.getValue(TIMESTAMP_ATTRIBUTENAME) != null) {
                            responseTimestamp = attributes.getValue(TIMESTAMP_ATTRIBUTENAME);
                        }
                        if (attributes.getValue(RESULTCODE_ATTRIBUTENAME) != null) {
                            responseResultCode = XmlErrorCode.convertTo(Integer.parseInt(attributes.getValue(RESULTCODE_ATTRIBUTENAME)));
                        }
                        if (attributes.getValue(MESSAGE_ATTRIBUTENAME) != null) {
                            responseMessage = attributes.getValue(MESSAGE_ATTRIBUTENAME);
                        }
                        break;
                    case PROPERTY_TAGNAME:
                        if (attributes.getValue(NAME_ATTRIBUTENAME) != null) {
                            rightConfProperty = attributes.getValue(NAME_ATTRIBUTENAME).equals(CONFIGURATION_PROPERTY_NAME);
                        }
                        break;
                    case COMMAND_TAGNAME:
                        if (attributes.getValue(SEQUENCE_ATTRIBUTENAME) != null) {
                            responseSeqNumber = Integer.parseInt(attributes.getValue(SEQUENCE_ATTRIBUTENAME));
                            sequenceNumber = responseSeqNumber-1;
                        }
                        break;
                }
        	}
        }
    }

    @Override
	public void endElement(String uri, String localName, String qName) {
		//Log.d(TAG, "endElement (localName): " + localName);
    	if (localName.equals(M2MXML_TAGNAME)) {
    		// when this tag finishes we don't do anything else
            rightM2mXml = false;
        } else {
        	if (rightM2mXml) {
        		if (rightConfProperty && localName.equals(PROPERTY_TAGNAME)) {
        			// when this tag finishes we don't take more info about patient and devices
        			ConfigurationParser parser = new ConfigurationParser();
    				dataContainer.addAll(parser.parse(propertyDataBuffer.toString()));
    				rightConfProperty = false;
                }
        	}
        }
    }

    @Override
    public void characters(char[] cbuf, int start, int len) {
        CharBuffer buf = CharBuffer.wrap(cbuf, start, len);
        char[] dataBuffer = new char[len];
        buf.get(dataBuffer);
        propertyDataBuffer.append(dataBuffer);
    }
}
