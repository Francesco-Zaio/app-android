package com.ti.app.telemed.core.util;

public interface GWConst {
    /**
     * Timeout attesa su tentativo connessione http (millisec)
     */
	int HTTP_CONNECTION_TIMEOUT = 15000;
    /**
     * Timeout attesa su risposta http (millisec)
     */
	int HTTP_READ_TIMEOUT = 40000;
    /**
     * numero massimo di tentativi invio misura
     */
	int MEASURE_SEND_RETRY = 10;
    /**
     * Timeout minimo fra tentativi successivi di invio misura
     */
	int MEASURE_SEND_TIMEOUT = 300;

    /**
     * Stringa che identifica un device fittizio per l'acquisizione manuale di una misura
     */
	String DEVICE_MANUAL = "MANUALE";
    /**
     * Stringa che identifica il device per l'acquisizione di immagini
     */
    String DEVICE_CAMERA = "CAMERA";

    /*
    String KPO3IHealth = "PO3";
    String KBP5IHealth = "BP5";
    String KHS4SIHealth = "HS4S";
    String KBP550BTIHealth = "BP550BT";
	String KEcgMicro = "MICROTELBT";
	String KOximeterNon = "NONIN4100";
	String KTDCC = "CLEVERCHEK";
	String KCcxsRoche = "CCXS";
	String KIEMWS = "IEMWS";
	String KIEMBP = "IEMBP";
	String KFORATherm = "FORAIR21B";
	String KForaWS = "FORAW310";
    String KSpirodoc = "SPIRODOC";
    String KANDPS = "ANDPS";
    String KANDPR = "ANDPR";
    String KAEROTEL = "AEROTEL";
    String KMYGLUCOHEALTH = "MYGLUCOHEALTH";
	String K8000GW = "8000GW";
	String KPC300SpotCheck = "PC300";
	*/

	/**
	 * Misura di Ossimetria.
	 */
	String KMsrOss = "OS";
	/**
	 * Misura di spirometria.
	 */
	String KMsrSpir = "SP";
	/**
	 * Elettrocardiogramma
	 */
	String KMsrEcg = "EC";
	/**
	 * Misura di Pressione
	 */
	String KMsrPres = "PR";
	/**
	 * Misura di Peso
	 */
	String KMsrPeso = "PS";
	/**
	 * Misura di Glicemia
	 */
	String KMsrGlic = "GL";
	/**
	 * Misura di Temperatura
	 */
	String KMsrTemp = "TC";
	/**
	 * Misura di Protrombina
	 */
	String KMsrProtr = "PT";
	/**
	 * Immagine
	 */
	String KMsrImg = "IM";
	/**
	 * Stato di salute
	 */
	String KMsr_Healt = "Q0";
	/**
	 * Livello di riposo
	 */
	String KMsr_Sleep = "Q1";
    /**
     * Livello di dolore
     */
	String KMsr_Pain = "Q2";
	/**
	 * Lettera di dimissione
	 */
	String KMsr_Disch = "D0";
	/**
	 * Documento di collaudo
	 */
	String KMsr_Accep = "D1";


    /**
     * Misura di peso
     */
	String EGwCode_01 = "01";
    /**
     * Pressione arteriosa minima
     */
	String EGwCode_03 = "03";
    /**
     * Pressione arteriosa massima
     */
	String EGwCode_04 = "04";
    /**
     * Pressione arteriosa media
     */
    String EGwCode_05 = "05";
    /**
     * Frequenza cardiaca
     */
	String EGwCode_06 = "06";
    /**
     * Percentuale di ossigen media
     */
	String EGwCode_07 = "07";
    /**
     * Frequenza cardiaca media
     */
	String EGwCode_0F = "0F";
    /**
     * Frequenza cardiaca minima
     */
	String EGwCode_1A = "1A";
    /**
     * Percentuale di ossigen minima
     */
    String EGwCode_1B = "1B";
    /**
     * Frequenza cardiaca massima
     */
    String EGwCode_1C = "1C";
    /**
     * Percentuale di ossigen massima
     */
    String EGwCode_1D = "1D";
    /**
     * Frequenza cardiaca basale
     */
	String EGwCode_1E = "1E";
    /**
     * Percentuale di ossigeno basale
     */
	String EGwCode_1F = "1F";
    /**
     * Durata del test in secondi
     */
	String EGwCode_1G = "1G";
    /**
     * Identificativo del file ossimetria
     */
	String EGwCode_1H = "1H";
    /**
     * Identificativo del file ECG di tipo scp
     */
	String EGwCode_0G = "0G";
    /**
     * Concentrazione glicemica pre-prandiale
     */
	String EGwCode_0E = "0E";
    /**
     * Concentrazione glicemica post-prandiale
     */
	String EGwCode_0T = "0T";
    /**
     * Temperatura corporea
     */
	String EGwCode_0R = "0R";
    /**
     * Temperatura ambientale
     */
	String EGwCode_0U = "0U";
    /**
     * Spirometria, PEF (litri/sec)
     */
	String EGwCode_08 = "08";
    /**
     * Spirometria, TPEF (valore teorico del PEF - litri/sec)
     */
	String EGwCode_B8 = "B8";
    /**
     * Spirometria, FEV1 (litri)
     */
	String EGwCode_09 = "09";
    /**
     * Spirometria, TFEV1 (valore teorico del FEV1 - litri)
     */
	String EGwCode_B9 = "B9";
    /**
     * Spirometria, FVC (litri)
     */
	String EGwCode_0A = "0A";
    /**
     * Spirometria, TFVC (valore teorico del FVC - litri)
     */
	String EGwCode_BA = "BA";
    /**
     * Spirometria, FEV1% (%)
     */
	String EGwCode_0C = "0C";
    /**
     * Spirometria, TFEV1% (valore teorico del FEV1% - %)
     */
	String EGwCode_BC = "BC";
    /**
     * Spirometria, FEF25-75 (litri/sec)
     */
	String EGwCode_0D = "0D";
    /**
     * Spirometria, TFEF25-75 (valore teorico del FEF25-75 - litri/sec)
     */
	String EGwCode_BD = "BD";
    /**
     * Spirometria, FET (centesimi di secondo)
     */
	String EGwCode_0L = "0L";
    /**
     * Spirometria, TFET (valore teorico del FET - centesimi di secondo)
     */
	String EGwCode_BL = "BL";
    /**
     * Identificativo del file di spirometria standard completo
     */
	String EGwCode_0M = "0M";
    /**
     * Tempo di protrombina (in secondi)
     */
	String EGwCode_0V = "0V";
    /**
     * Indice INR
     */
	String EGwCode_0Z = "0Z";
    /**
     * Percentuale di protrombina (%)
     */
	String EGwCode_0X = "0X";
    /**
     * Massa grassa (%)
     */
	String EGwCode_F1 = "F1";
    /**
     * Massa liquida (%)
     */
	String EGwCode_F2 = "F2";
    /**
     * Identificativo del file ossimetria con accelerometro
     */
	String EGwCode_1L = "1L";
    /**
     * Questionario prima domanda 'Come ti senti oggi?' valori possibili 0 - 5
     */
	String EGwCode_Q0 = "Q0";
    /**
     * Questionario seconda domanda 'Hai dormito bene ?' valori possibili 0 - 5
     */
	String EGwCode_Q1 = "Q1";
    /**
     * Questionario terza domanda 'Senti dolore oggi?' valori possibili 0 - 5
     */
	String EGwCode_Q2 = "Q2";
    /**
     * Lettera di dimissione
     */
	String EGwCode_D0 = "D0";
    /**
     * Documento di collaudo
     */
	String EGwCode_D1 = "D1";
    /**
     * Misura di BMI (Body mass Index)
     */
	String EGwCode_S0 = "S0";
    /**
     * Livello della batteria (%)
     */
	String EGwCode_BATTERY = "A0";
    /*
	String EGwCode_0Q = "0Q";
	String EGwCode_0S = "0S";
	String EGwCode_0B = "0B";
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
	String EGwCode_EA = "EA"; // Risultato analisi ECG
	String EGwCode_1I = "1I";
	String EGwCode_1M = "1M";
	String EGwCode_XM = "XM";
	String EGwCode_17 = "17";
	String EGwCode_18 = "18";
	String EGwCode_19 = "19";
	String EGwCode_31 = "31";
	String EGwCode_0N = "0N";
	String EGwCode_0J = "0J";
	String EGwCode_0O = "0O";
	String EGwCode_0P = "0P";
	*/
}
