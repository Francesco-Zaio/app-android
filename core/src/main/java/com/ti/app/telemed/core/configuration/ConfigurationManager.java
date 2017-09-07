package com.ti.app.telemed.core.configuration;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;

public class ConfigurationManager {

	private static final String TAG = "ConfigurationManager";
	
	// constants
	private static final String INI_FILE_NAME = "Gateway.ini";
    private static final String CONFIGURATION_SERVER_DEFAULT_SECTION = "SERVER_CONF_DEFAULT";
    private static final String CONFIGURATION_SERVER_SECTION = "SERVER_CONF";
	private static final String IP = "IP";
	private static final String PORT = "port";
	private static final String TARGETCFG = "targetCfg";
	private static final String TARGETSEND = "targetSend";
	private static final String PROTOCOL = "protocol";
    private static final String IP_DEF = "IP_def";
    private static final String PORT_DEF = "port_def";
    private static final String TARGETCFG_DEF = "targetCfg_def";
	private static final String TARGETSEND_DEF = "targetSend_def";
	private static final String PROTOCOL_DEF = "protocol_def";

	private String ipValue;
	private String portValue;
	private String targetCfgValue;
    private String targetSendValue;
	private String httpModeValue;

	private String ipDefaultValue;
	private String portDefaultValue;
	private String targetCfgDefaultValue;
    private String targetSendDefaultValue;
	private String httpModeDefaultValue;

	private Logger logger = Logger.getLogger(ConfigurationManager.class.getName());

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
		
		String filename = GWConst.DEVICES_FILE_en_GB;
		if("it".equals(language)){ 
			filename = GWConst.DEVICES_FILE_it_IT;
		} else if("en".equals(language)){
			filename = GWConst.DEVICES_FILE_en_GB;
		}
		return filename;
	}

	private void checkDevices() throws IOException, DbException{
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
	            if (line.length()>0) {
	            	StringTokenizer st = new StringTokenizer(line, ",");
	            	String measure = st.nextToken().trim();
	            	String model = st.nextToken().trim();
	            	String description = st.nextToken().trim();
					int needCfg = Integer.parseInt(st.nextToken().trim());
					String className = st.nextToken().trim();
	            	Device tmpDev = new Device();
	            	tmpDev.setMeasure(measure);
	            	tmpDev.setModel(model);
	            	tmpDev.setDescription(description);
					tmpDev.setIsBTDevice(needCfg == 1);
					tmpDev.setClassName(className);
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
        } catch (DbException e) {
        	logger.log(Level.SEVERE,"ERROR checking devices "+ e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

    private void readConfigurations() throws  IOException, DbException {
        ServerConf dbServerConf = DbManager.getDbManager().getServerConf();
        if (dbServerConf != null) {
            setDbServerConf(dbServerConf);
        } else {
            readIniFile();
            insertDbServerConf();
        }
    }

    private void readIniFile() throws IOException, DbException {
		String fileBuffer;
        String currentSection, key, value;
        boolean inSection = false;
        key = null;
        value = null;
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
                        case GWConst.COMMENT_CHAR:
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
                                			ipValue = value;
                                	} else if (key.equalsIgnoreCase(PORT)) {
                                			portValue = value;
                                	} else if (key.equalsIgnoreCase(TARGETCFG)) {
                                			targetCfgValue = value;
                                	} else if (key.equalsIgnoreCase(TARGETSEND)) {
                                        targetSendValue = value;
                                    } else if (key.equalsIgnoreCase(PROTOCOL)) {
                                			httpModeValue = value;
                                	} else if (key.equalsIgnoreCase(IP_DEF)) {
                                        ipDefaultValue = value;
                                    } else if (key.equalsIgnoreCase(PORT_DEF)) {
                                        portDefaultValue = value;
                                    } else if (key.equalsIgnoreCase(TARGETCFG_DEF)) {
                                        targetCfgDefaultValue = value;
                                    } else if (key.equalsIgnoreCase(TARGETSEND_DEF)) {
                                        targetSendDefaultValue = value;
                                    } else if (key.equalsIgnoreCase(PROTOCOL_DEF)) {
                                        httpModeDefaultValue = value;
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
    }

	private void insertDbServerConf() throws DbException {
		ServerConf sc = new ServerConf();
		sc.setIp(ipValue);
		sc.setProtocol(httpModeValue);
		sc.setPort(portValue);
		sc.setTargetCfg(targetCfgValue);
        sc.setTargetSend(targetSendValue);
		sc.setIpDef(ipDefaultValue);
		sc.setProtocolDef(httpModeDefaultValue);
		sc.setPortDef(portDefaultValue);
        sc.setTargetCfgDef(targetCfgDefaultValue);
        sc.setTargetSendDef(targetSendDefaultValue);
		DbManager.getDbManager().insertServerConf(sc);
		logger.log(Level.INFO, "Configuration inserted into DB");
	}

	private void setDbServerConf(ServerConf dbServerConf) {
		// Sovrascrivo tutti i valori che ho letto dal file ini con quelli del DB

		ipValue = dbServerConf.getIp();
		httpModeValue = dbServerConf.getProtocol();
		portValue = dbServerConf.getPort();
		targetCfgValue = dbServerConf.getTargetCfg();
		targetSendValue = dbServerConf.getTargetSend();

		ipDefaultValue = dbServerConf.getIpDef();
		httpModeDefaultValue = dbServerConf.getProtocolDef();
		portDefaultValue = dbServerConf.getPortDef();
		targetCfgDefaultValue = dbServerConf.getTargetCfgDef();
		targetSendDefaultValue = dbServerConf.getTargetSendDef();

		logger.log(Level.INFO, "Configuration loaded from DB");
	}


    public void updateConfiguration(ServerConf conf) throws DbException {
        ipValue = conf.getIp();
        httpModeValue = conf.getProtocol();
        portValue = conf.getPort();
        targetCfgValue = conf.getTargetCfg();
        DbManager.getDbManager().updateServerConf(conf);
        logger.log(Level.INFO, "Configuration updated");
    }

	public String getIpValue() {
		return ipValue;
	}

	public String getPortValue() {
		return portValue;
	}

	public String getTargetCfgValue() {
		return targetCfgValue;
	}

    public String getTargetSendValue() {
        return targetSendValue;
    }

    public String getHttpModeValue() {
		return httpModeValue;
	}

	public URL getConfigurationPlatformUrl() throws Exception {
		String configurationPlatfomStr = getHttpModeValue() + "://"
				+ getIpValue() + ":" + getPortValue() + "/" + getTargetCfgValue();
		URL url = (new URI(configurationPlatfomStr)).toURL();
		logger.log(Level.INFO, "Configuration Platfom URL = "
				+ configurationPlatfomStr);
		return url;
	}

    public URL getSendPlatformUrl() throws Exception {
        String configurationPlatfomStr = getHttpModeValue() + "://"
                + getIpValue() + ":" + getPortValue() + "/" + getTargetSendValue();
        URL url = (new URI(configurationPlatfomStr)).toURL();
        logger.log(Level.INFO, "Configuration Platfom URL = "
                + configurationPlatfomStr);
        return url;
    }
}
