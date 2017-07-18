package com.ti.app.telemed.core.util;

public interface GWConst {
	char COMMENT_CHAR = '#';

	String DEVICES_FILE_it_IT = "devices-it.txt";
	String DEVICES_FILE_en_GB = "devices-en.txt";

	int CONNECTION_TIMEOUT = 15000; // timeout attesa connessione http (millisec)
	int READ_TIMEOUT = 40000;       // timeout attesa risposta http (millisec)
	int MAX_SEND_RETRY = 10;        // numero massimo di retry invio misure
	int SEND_RETRY_TIMEOUT = 300;   // timeout in sec per retry invio misure

	String KBalanceMD = "UC321PBT";
	String KPressureMD = "UA767PBT";
	String KEcgMicro = "MICROTELBT";
	String KOximeterNon = "NONIN4100";
	String KPO3IHealth = "PO3";
	String KBP5IHealth = "BP5";
	String KHS4SIHealth = "HS4S";
	String KBP550BTHealth = "BP550BT";
	String KMirSpirometerSNA = "SNA23060";
	String KMirOxySNA = "SNA23067";
	String KCGBPPro = "CGBPPRO";
	String KCGWSPro = "CGWSPRO";
	String KCGHP = "CGHP";
	String KTDCC = "CLEVERCHEK";
	String KOBC = "OBC";
	String KCcxsRoche = "CCXS";
	String KIEMWS = "IEMWS";
	String KIEMBP = "IEMBP";
	String KFORATherm = "FORAIR21B";
	String KForaWS = "FORAW310";
    String KManualMeasure = "MANUALE";
    String KBTGT = "GLUCOTEL";
    String KSTM = "STM";
    String KSpirodocSP = "SPIRODOCSP";
    String KSpirodocOS = "SPIRODOCOS";
    String KANDPS = "ANDPS";
    String KANDPR = "ANDPR";
    String KCAMERA = "CAMERA";
    String KAEROTEL = "AEROTEL";
    String KMYGLUCOHEALTH = "MYGLUCOHEALTH";

        
	String KMsrOss = "OS";
	String KMsrSpir = "SP";
	String KMsrEcg = "EC";
	String KMsrPres = "PR";
	String KMsrPeso = "PS";
	String KMsrGlic = "GL";
	String KMsrBodyFat = "MG";
	String KMsrTemp = "TC";
	String KMsrAritm = "AR";
	String KMsrProtr = "PT";
	String KMsrImg = "IM";
	String KMsrLoc = "PO";
	String KMsr_Healt = "Q0";
	String KMsr_Sleep = "Q1";
	String KMsr_Pain = "Q2";
	String KMsr_Disch = "D0";
	String KMsr_Accep = "D1";

	// labels for xml format e parse
	String EGwCode_01 = "01";
	String EGwCode_03 = "03";
	String EGwCode_04 = "04";
	String EGwCode_05 = "05";
	String EGwCode_06 = "06";
	String EGwCode_07 = "07";
	String EGwCode_0F = "0F";
	String EGwCode_1A = "1A";
	String EGwCode_1B = "1B";
	String EGwCode_1C = "1C";
	String EGwCode_1D = "1D";
	String EGwCode_1E = "1E";
	String EGwCode_1F = "1F";
	String EGwCode_1G = "1G";
	String EGwCode_0N = "0N";
	String EGwCode_1H = "1H";
	String EGwCode_0G = "0G";
	String EGwCode_0J = "0J";
	String EGwCode_0O = "0O";
	String EGwCode_0P = "0P";
	String EGwCode_0E = "0E";
	String EGwCode_0T = "0T";
	String EGwCode_0R = "0R";
	String EGwCode_0U = "0U";
	String EGwCode_0Q = "0Q";
	String EGwCode_0S = "0S";
	String EGwCode_08 = "08";
	String EGwCode_B8 = "B8";
	String EGwCode_09 = "09";
	String EGwCode_B9 = "B9";
	String EGwCode_0A = "0A";
	String EGwCode_BA = "BA";
	String EGwCode_0B = "0B";
	String EGwCode_0C = "0C";
	String EGwCode_BC = "BC";
	String EGwCode_0D = "0D";
	String EGwCode_BD = "BD";
	String EGwCode_0L = "0L";
	String EGwCode_BL = "BL";
	String EGwCode_0M = "0M";
	String EGwCode_0V = "0V";
	String EGwCode_0Z = "0Z";
	String EGwCode_0X = "0X";
	String EGwCode_11 = "11";
	String EGwCode_12 = "12";
	String EGwCode_13 = "13";
	String EGwCode_14 = "14";
	String EGwCode_15 = "15";
	String EGwCode_M1 = "M1";
	String EGwCode_0I = "0I";
	String EGwCode_2A = "2A";
	String EGwCode_2B = "2B";
	String EGwCode_2C = "2C";
	String EGwCode_F1 = "F1";
	String EGwCode_F2 = "F2";
	String EGwCode_1L = "1L";
	String EGwCode_1I = "1I";
	String EGwCode_1M = "1M";
	String EGwCode_XM = "XM";
	String EGwCode_17 = "17";
	String EGwCode_18 = "18";
	String EGwCode_19 = "19";
	String EGwCode_31 = "31";
	String EGwCode_Q0 = "Q0";
	String EGwCode_Q1 = "Q1";
	String EGwCode_Q2 = "Q2";
	String EGwCode_D0 = "D0";
	String EGwCode_D1 = "D1";
	String EGwCode_BATTERY = "A0";

	//Constants for Message Bundles
	String MESSAGE = "MESSAGE";

	//ID for default user
	String DEFAULT_USER_ID = "-1";
}
