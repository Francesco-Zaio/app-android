package com.ti.app.mydoctor.util;

public interface GWConst {

	public static final int MAX_DEVICE_NUM = 20;
	public static final int MAX_USER_NUM = 50;
	public static final char COMMENT_CHAR = '#';

	public static final String DEVICES_FILE = "devices.txt";
	public static final String DEVICES_FILE_it_IT = "devices-it.txt";
	public static final String DEVICES_FILE_pt_BR = "devices-pt.txt";
	public static final String DEVICES_FILE_en_GB = "devices-en.txt";
	public static final String DEVICES_FILE_es_ES = "devices-es.txt";
	
	public static enum TCmd {
		ECmdConnByAddr, ECmdConnByUser
	}

	public static final String KBalanceMD = "UC321PBT";
	public static final String KPressureMD = "UA767PBT";
	public static final String KEcgAR = "AR4100VIEW";
	public static final String KEcgAR1200 = "AR1200VIEWBT";
	public static final String KEcgMicro = "MICROTELBT";
	public static final String KEcgAR1200ADVRS = "AR1200ADVRS";
	public static final String KOximeterNon = "NONIN4100";
	public static final String KPO3IHealth = "PO3";
	public static final String KBP5IHealth = "BP5";
	public static final String KHS4SIHealth = "HS4S";
	public static final String KBP550BTIHealth = "BP550BT";
	public static final String KMirSpirometerSNA = "SNA23060";
	public static final String KMirOxySNA = "SNA23067";
	public static final String KCGBPPro = "CGBPPRO";
	public static final String KCGWSPro = "CGWSPRO";
	public static final String KCGHP = "CGHP";
	public static final String KTDCC = "CLEVERCHEK";
	public static final String KOBC = "OBC";
	public static final String KCcxsRoche = "CCXS";
	public static final String KIEMWS = "IEMWS";
	public static final String KIEMBP = "IEMBP";
	public static final String KFORATherm = "FORAIR21B";
	public static final String KForaWS = "FORAW310";
    public static final String KManualMeasure = "MANUALE";
    public static final String KBTGT = "GLUCOTEL";
    public static final String KSTM = "STM";
    public static final String KSpirodocSP = "SPIRODOCSP";
    public static final String KSpirodocOS = "SPIRODOCOS";
    
    public static final String KANDPS = "ANDPS";
    public static final String KANDPR = "ANDPR";
    public static final String KCAMERA = "CAMERA";
    public static final String KZEPHYR = "ZEPHYR";
    
    public static final String KAEROTEL = "AEROTEL";
    
    public static final String KMYGLUCOHEALTH = "MYGLUCOHEALTH";
        
	public static final String KMsrOss = "OS";
	public static final String KMsrSpir = "SP";
	public static final String KMsrEcg = "EC";
	public static final String KMsrPres = "PR";
	public static final String KMsrPeso = "PS";
	public static final String KMsrGlic = "GL";
	public static final String KMsrBodyFat = "MG";
	public static final String KMsrTemp = "TC";
	public static final String KMsrAritm = "AR";
	public static final String KMsrProtr = "PT";
	public static final String KMsrImg = "IM";
	public static final String KMsrAttMot = "AM";
	public static final String KMsrLoc = "PO";

	// labels for xml format e parse
	public static final String EGwCode_01 = "01";
	public static final String EGwCode_03 = "03";
	public static final String EGwCode_04 = "04";
	public static final String EGwCode_05 = "05";
	public static final String EGwCode_06 = "06";
	public static final String EGwCode_07 = "07";
	public static final String EGwCode_0F = "0F";
	public static final String EGwCode_1A = "1A";
	public static final String EGwCode_1B = "1B";
	public static final String EGwCode_1C = "1C";
	public static final String EGwCode_1D = "1D";
	public static final String EGwCode_1E = "1E";
	public static final String EGwCode_1F = "1F";
	public static final String EGwCode_1G = "1G";
	public static final String EGwCode_0N = "0N";
	public static final String EGwCode_1H = "1H";
	public static final String EGwCode_0G = "0G";
	public static final String EGwCode_0J = "0J";
	public static final String EGwCode_0O = "0O";
	public static final String EGwCode_0P = "0P";
	public static final String EGwCode_0E = "0E";
	public static final String EGwCode_0T = "0T";
	public static final String EGwCode_0R = "0R";
	public static final String EGwCode_0U = "0U";
	public static final String EGwCode_0Q = "0Q";
	public static final String EGwCode_0S = "0S";
	public static final String EGwCode_08 = "08";
	public static final String EGwCode_B8 = "B8";
	public static final String EGwCode_09 = "09";
	public static final String EGwCode_B9 = "B9";
	public static final String EGwCode_0A = "0A";
	public static final String EGwCode_BA = "BA";
	public static final String EGwCode_0B = "0B";
	public static final String EGwCode_0C = "0C";
	public static final String EGwCode_BC = "BC";
	public static final String EGwCode_0D = "0D";
	public static final String EGwCode_BD = "BD";
	public static final String EGwCode_0L = "0L";
	public static final String EGwCode_BL = "BL";
	public static final String EGwCode_0M = "0M";
	public static final String EGwCode_0V = "0V";
	public static final String EGwCode_0Z = "0Z";
	public static final String EGwCode_0X = "0X";
	public static final String EGwCode_11 = "11";
	public static final String EGwCode_12 = "12";
	public static final String EGwCode_13 = "13";
	public static final String EGwCode_14 = "14";
	public static final String EGwCode_15 = "15";
	public static final String EGwCode_M1 = "M1";
	public static final String EGwCode_0I = "0I";
	public static final Object EGwCode_2A = "2A";
	public static final Object EGwCode_2B = "2B";
	public static final Object EGwCode_2C = "2C";	
	public static final String EGwCode_F1 = "F1";
	public static final String EGwCode_F2 = "F2";
	public static final String EGwCode_1L = "1L";
	public static final String EGwCode_1I = "1I";
	public static final String EGwCode_1M = "1M";
	public static final String EGwCode_XM = "XM";
	public static final String EGwCode_17 = "17";
	public static final String EGwCode_18 = "18";
	public static final String EGwCode_19 = "19";
	public static final String EGwCode_31 = "31";

	//Constants for Message Bundles
	public static final String MESSAGE = "MESSAGE";
	public static final String ASK_MESSAGE = "ASK_MESSAGE";
	public static final String ASK_POSITIVE = "ASK_POSITIVE";
	public static final String ASK_NEGATIVE = "ASK_NEGATIVE";
	public static final String MESSAGE_LOWER = "MESSAGE_LOWER";
	public static final String TITLE = "TITLE";
	public static final String MESSAGE_CANCELLABLE = "MESSAGE_CANCELLABLE";
	public static final String IS_SEND_MEASURE = "IS_SEND_MEASURE";
	public static final String IS_SAVE_MEASURE = "IS_SAVE_MEASURE";
	public static final String IS_MEASURE = "IS_MEASURE";
	public static final String IS_CONFIGURATION = "IS_CONFIGURATION";
	public static final String LOGIN_ERROR = "LOGIN_ERROR";
	
	//Constants for Shared Preferences
	public static final String SERVER_CERT = "SERVER_CERT";
	public static final String SERVER_CERT_HOSTNAME = "SERVER_CERT_HOSTNAME";
	public static final String SERVER_CERT_PUBLIC_KEY = "SERVER_CERT_PK";
	
	//Costants for Bundle
	public static final String EXCEPTION_MSG = "EXCEPTION_MSG";
	public static final String VIEW_MEASURE = "VIEW_MEASURE";
	public static final String START_MEASURE = "START_MEASURE";
	public static final String SELECTED_MEASURE = "SELECTED_MEASURE";
    public static final String SHOW_MEASURE_TITLE = "SHOW_MEASURE_TITLE";
    public static final String POSITION = "POSITION";
    public static final String MEASURE_TYPE = "MEASURE_TYPE";
    public static final String MEASURE_MODE = "MEASURE_MODE";
    public static final String MEASURE_MODE_ON = "ON";
    
    public static final String USER_ID = "USER_ID";
    
    public static final String PATIENT = "PATIENT";
    public static final String PATIENT_ID = "PATIENT_ID";
    
    public static final String ENABLE_DELETE_USER_ID = "ENABLE_DELETE_USER_ID";
    
    public static final float MIN_TEMPERATURE = 30;
    public static final float MAX_TEMPERATURE = 45;
    public static final char DOT = '.';
	public static final char COMMA = ',';
	
	public static final int MIN_STRIP_CODE = 500;
	public static final int MAX_STRIP_CODE = 947;
	
	public static final double MIN_VOLTAGE = 2.3;
	public static final double MAX_VOLTAGE = 3.0;
	
	public static final byte AR_TIMEOUT_DEFAULT = 10;
	public static final byte ZEPHYR_TIMEOUT_DEFAULT = 60;
	public static final byte AR_TIMEOUT_MIN = 5;
	public static final byte AR_TIMEOUT_MAX = 90;
	
	public static final int ZEPHYR_TIMEOUT_MIN = 10;
	public static final int ZEPHYR_TIMEOUT_MAX = 999;
	
	public static final String SEND_ALL = "SEND_ALL";
	public static final String SEND_MEASURE_TYPE = "SEND_MEASURE_TYPE";
	
	
	//ID for default user
	public static final String DEFAULT_USER_ID = "-1";
}
