package com.ti.app.telemed.core.configuration;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.dbmodule.DbManager;

/**
 * Questa classe gestisce i parametri necessari per connettersi alla piattaforma
 */
public class ConfigurationManager {

	private static final String TAG = "ConfigurationManager";
	
	// constants
	private static final String INI_FILE_NAME = "Gateway.ini";
    private static final String DEVICES_FILE_it_IT = "devices-it.txt";
    private static final String DEVICES_FILE_en_GB = "devices-en.txt";
    private static final char COMMENT_CHAR = '#';
    private static final String CONFIGURATION_SERVER_DEFAULT_SECTION = "SERVER_CONF_DEFAULT";
    private static final String CONFIGURATION_SERVER_SECTION = "SERVER_CONF";
	private static final String IP = "IP";
	private static final String PORT = "port";
	private static final String TARGETCFG = "targetCfg";
	private static final String TARGETSEND = "targetSend";

    private ServerConf serverConf, defaultServerConf = null;

	private Logger logger = Logger.getLogger(ConfigurationManager.class.getName());

    /**
     * Questo metodo viene automaticamente invocato all'avvio dell'applicazione e non deve essere usato.
     */
	public void init() throws Exception{
		try {
			readConfigurations();
			checkDevices();
		} 
		catch (SecurityException e) {
			Log.w(TAG, "WARNING on init: " + e.getMessage());
		}
		catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "ERROR on init: " + e.getMessage());
			throw new Exception(e.getMessage());
		} 
	}
	
	private String getDeviceFilename() {
		
		Locale locale = Locale.getDefault();
		String language = locale.getLanguage();
		
		String filename = DEVICES_FILE_it_IT;
		if("it".equals(language)){ 
			filename = DEVICES_FILE_it_IT;
		} else if("en".equals(language)){
			filename = DEVICES_FILE_en_GB;
		}
		return filename;
	}

	private void checkDevices() throws IOException {
		try{
			List<Device> currentDev = new ArrayList<>();
			// open the file
			Resources resources = MyApp.getContext().getResources();
	    	AssetManager assetManager = resources.getAssets();
	    	InputStream inputStream = assetManager.open(getDeviceFilename());        	
	    	InputStreamReader in= new InputStreamReader(inputStream);
	        BufferedReader br= new BufferedReader(in);      	       
	        // read file line by line
	        String line;
	        while ((line = br.readLine()) != null) {
				if (line.startsWith("//"))
					continue;
	            if (line.length()>0) {
	            	StringTokenizer st = new StringTokenizer(line, ",");
	            	String measure = st.nextToken().trim();
	            	String model = st.nextToken().trim();
	            	String description = st.nextToken().trim();
					int devType = Integer.parseInt(st.nextToken().trim());
	            	Device tmpDev = new Device();
	            	tmpDev.setMeasure(measure);
	            	tmpDev.setModel(model);
	            	tmpDev.setDescription(description);
					tmpDev.setDevType(Device.DevType.fromInteger(devType));
	            	currentDev.add(tmpDev);
	            	
	            	Device d = DbManager.getDbManager().getDeviceWhereMeasureModel(measure, model);
	            	if(d == null){
	            		DbManager.getDbManager().insertDevice(tmpDev);
	            	} else {
	            		DbManager.getDbManager().updateDevice(tmpDev);
	            	}
	            }
	        }
	        
	        DbManager.getDbManager().deleteUnusedDevice(currentDev);
	        
		} catch (FileNotFoundException fnfe) {
            logger.log(Level.SEVERE,"FILE NON TROVATO: " + getDeviceFilename());
            throw fnfe;
        } catch (IOException ioe) {
        	logger.log(Level.SEVERE,"ERROR READING FILE "+ getDeviceFilename() + " " + ioe.getMessage());
            throw ioe;
        }
	}

    private void readConfigurations() throws  IOException {
        defaultServerConf = readIniFile();
        serverConf = DbManager.getDbManager().getServerConf();
        if (serverConf == null) {
            serverConf = defaultServerConf;
            DbManager.getDbManager().insertServerConf(serverConf);
        }
    }

    private ServerConf readIniFile() throws IOException {
		String fileBuffer;
        String currentSection, key, value;
        boolean inSection = false;
        key = null;
        value = null;
        ServerConf sc = new ServerConf();
        try {
        	// open the file
        	Resources resources = MyApp.getContext().getResources();
        	AssetManager assetManager = resources.getAssets();
        	InputStream inputStream = assetManager.open(INI_FILE_NAME);        	
        	InputStreamReader in = new InputStreamReader(inputStream);
            BufferedReader br = new BufferedReader(in);
            
            // read file line by line
            while ((fileBuffer = br.readLine()) != null) {
                if (fileBuffer.length()> 0) {
                    switch (fileBuffer.charAt(0)) {
                        case COMMENT_CHAR:
                            break;
                        case '[':
                            currentSection = fileBuffer.substring(1, fileBuffer.length() - 1);
                            if (currentSection.equalsIgnoreCase(CONFIGURATION_SERVER_SECTION)) {
                            	inSection = true;
                            } else if (currentSection.equalsIgnoreCase(CONFIGURATION_SERVER_DEFAULT_SECTION)) {
                                inSection = true;
                            }
                            else {
                            	throw new IOException();
                            }
                            break;
                        default:
                            if (inSection) {
                                // si tratta di una chiave
                                StringTokenizer st = new StringTokenizer(fileBuffer, "=");
                                boolean first = true;
                                boolean hadTokens = false;
                                while (st.hasMoreTokens()) {
                                    hadTokens = true;
                                    if (first) {
                                        key = ((st.nextToken()).trim()).toLowerCase();
                                        first = false;
                                    } else {
                                        value = (st.nextToken()).trim();
                                    }
                                }
                                if (hadTokens) {
                                	if (key.equalsIgnoreCase(IP)) {
                                        sc.setIp(value);
                                	} else if (key.equalsIgnoreCase(PORT)) {
                                        sc.setPort(value);
                                	} else if (key.equalsIgnoreCase(TARGETCFG)) {
                                        sc.setTargetCfg(value);
                                	} else if (key.equalsIgnoreCase(TARGETSEND)) {
                                        sc.setTargetSend(value);
                                    }
                                }
                            }
                            break;
                    }
                }
            }
            // close all opened reader
            in.close();
            br.close();
       } catch (FileNotFoundException fnfe) {
            logger.log(Level.SEVERE,"FILE NON ESISTENTE");
            throw fnfe;
        } catch (IOException ioe) {
        	logger.log(Level.SEVERE,"DEFAULT:" + ioe.getMessage());
        	logger.log(Level.SEVERE,"FILE NAME:" + INI_FILE_NAME);
            throw ioe;
        } catch (NumberFormatException  e) {
        	logger.log(Level.SEVERE,"ERROR PARSING INI FILE:" + e.getMessage());
            throw e;
		}
		return sc;
    }


    /**
     * Restituisce un oggetto {@link ServerConf} contenente i parametri di configurazone
     * @return      {@link ServerConf}
     */
    public ServerConf getConfiguration() {
        return new ServerConf(serverConf.getIp(),
                serverConf.getPort(),
                serverConf.getTargetCfg(),
                serverConf.getTargetSend());
    }

    /**
     * Aggiorna la configurazione sul DB.
     * @param conf      Configurzione da aggiornare.
     */
    public void updateConfiguration(ServerConf conf) {
        serverConf.setIp(conf.getIp());
        serverConf.setPort(conf.getPort());
        serverConf.setTargetCfg(conf.getTargetCfg());
        serverConf.setTargetSend(conf.getTargetSend());
        DbManager.getDbManager().updateServerConf(serverConf);
    }

    /**
     * Resetta la configurazione ai valori di default
     */
    public void resetConfiguration() {
        serverConf.setIp(defaultServerConf.getIp());
        serverConf.setPort(defaultServerConf.getPort());
        serverConf.setTargetCfg(defaultServerConf.getTargetCfg());
        serverConf.setTargetSend(defaultServerConf.getTargetSend());
        DbManager.getDbManager().updateServerConf(serverConf);
    }

    /**
     * Restituisce la URL a cui inviare le richieste dei dati di configurazione
     * @return      {@link URL} o null in caso di errore
     */
	public URL getConfigurationPlatformUrl() {
        try {
            URL url = new URL("https", serverConf.getIp(), Integer.parseInt(serverConf.getPort()), serverConf.getTargetCfg());
            logger.log(Level.INFO, "Configuration Platfom URL = " + url.toString());
            return url;
        } catch (MalformedURLException e) {
            return null;
        }
	}

    /**
     * Restituisce la URL a cui inviare le misure
     * @return      {@link URL} o null in caso di errore
     */
    public URL getSendPlatformUrl() {
        try {
            URL url = new URL("https", serverConf.getIp(), Integer.parseInt(serverConf.getPort()), serverConf.getTargetSend());
            logger.log(Level.INFO, "Configuration Platfom URL = " + url.toString());
            return url;
        }catch (MalformedURLException e) {
            return null;
        }
    }
}
