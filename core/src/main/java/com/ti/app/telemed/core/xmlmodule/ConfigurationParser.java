package com.ti.app.telemed.core.xmlmodule;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.common.MeasureProtocolCfg;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.util.GWConst;

import android.util.Log;


public class ConfigurationParser {

	private static final String TAG = "ConfigurationParser";

	private static final String SERVER_SERV_SECTION = "SERVER_SERV";
	private static final String DEVICE_SECTION = "DEVICE";
	private static final String USER_DATA_SECTION = "USER_DATA";
	private static final String PATIENT_DATA_SECTION = "PATIENT_DATA";

	// constant for keys in section of server serv
	private static final String SERVERSERV_UPDATEINTERVAL_KEY = "UpdateInterval";
	private static final String SERVERSERV_SILENTSTART_KEY = "SilentStart";
	private static final String SERVERSERV_SILENTEND_KEY = "SilentEnd";
	private static final String SERVERSERV_LATEREMINDDELAY_KEY = "LateRemindDelay";
	private static final String SERVERSERV_LATEMAXREMINDS_KEY = "LateMaxReminds";
	private static final String SERVERSERV_STDTIMEOUT_KEY = "StandardProtocolTimeOut";
	private static final String SERVERSERV_STDREMINDS_KEY = "StandardProtocolReminds";


	// constant for keys in section of patient
	private static final String USER_DATA_NAME_KEY = "NAME";
	private static final String USER_DATA_SURNAME_KEY = "SURNAME";
	private static final String USER_DATA_SEX_KEY = "SEX";
	private static final String USER_DATA_HEIGHT_KEY = "HEIGHT";
	private static final String USER_DATA_WEIGHT_KEY = "WEIGHT";
	private static final String USER_DATA_BIRTHDAYDATE_KEY = "BIRTH_DATE";
	private static final String USER_DATA_ETHNIC_KEY = "ETNIA";
	private static final String USER_DATA_ADDITIONALDATA_KEY = "AUTORE";
	private static final String USER_DATA_ID_KEY = "ID";
	private static final String USER_DATA_CF_KEY = "CF";
	private static final String USER_DATA_SYMPTOMS_KEY = "SYMPTOMS";
	private static final String USER_DATA_QUESTIONS_KEY = "QUESTIONS";

	// constant for keys in section of devices
	private static final String DEVICE_MEASURE_KEY = "Measure";
	private static final String DEVICE_SCHEDULE_KEY = "Schedule";
	private static final String DEVICE_THRESHOLD_KEY = "Tresholds";
	private static final String DEVICE_FAMILY_KEY = "Family";


	private Vector<Object> dataContainer;
	
	private Logger logger = Logger.getLogger(ConfigurationParser.class.getName());
	
	public ConfigurationParser() {		
		dataContainer = new Vector<>();
	}

	public Vector<Object> parse(String confString) {
		String fileBuffer;
        String currentSection, key, value;
        boolean inSection = false;
        currentSection = null;
        key = null;
        value = null;
        
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(confString.getBytes());
			InputStreamReader in= new InputStreamReader(bis);
            BufferedReader br= new BufferedReader(in);   			
			User user = new User();
			MeasureProtocolCfg mpcfg = new MeasureProtocolCfg();
			UserMeasure userMeasure = null;
			Patient patient = null;
			Vector<UserMeasure> umList = new Vector<>();
			Map<String,String> thresholds = new HashMap<>();
			Vector<Patient> patList = new Vector<>();
            // read file line by line
            while ((fileBuffer = br.readLine()) != null) {
                if (fileBuffer.length()> 0) {
                    switch (fileBuffer.charAt(0)) {
                        case GWConst.COMMENT_CHAR:
                            break;
                        case '[':
                            currentSection = fileBuffer.substring(1, fileBuffer.length() - 1);
                            if (currentSection.equalsIgnoreCase(SERVER_SERV_SECTION)) {
                            	inSection = true;                            	
                            } else if (currentSection.startsWith((DEVICE_SECTION))) {
                            	inSection = true;
                            	if(userMeasure != null){
									userMeasure.setIdUser(user.getId());
									userMeasure.setThresholds(thresholds);
                            		umList.add(userMeasure);
                            	}
                            	userMeasure = new UserMeasure();
								thresholds = new HashMap<>();
                            } else if (currentSection.startsWith(PATIENT_DATA_SECTION)) {
                            	inSection = true;
                            	if(patient != null){
                            		patList.add(patient);
                            	}
                            	patient = new Patient();
                            } else if (currentSection.equalsIgnoreCase(USER_DATA_SECTION)) {
                            	inSection = true;
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
                                if (hadTokens && value != null) {
                                	if (key.equalsIgnoreCase(SERVERSERV_UPDATEINTERVAL_KEY)) {
										mpcfg.setUpdateInterval(Integer.parseInt(value));
									} else if (key.equalsIgnoreCase(SERVERSERV_SILENTSTART_KEY)) {
										mpcfg.setSilentStart(value);
									} else if (key.equalsIgnoreCase(SERVERSERV_SILENTEND_KEY)) {
										mpcfg.setSilentEnd(value);
									} else if (key.equalsIgnoreCase(SERVERSERV_LATEREMINDDELAY_KEY)) {
										mpcfg.setLateRemindDelay(Integer.parseInt(value));
									} else if (key.equalsIgnoreCase(SERVERSERV_LATEMAXREMINDS_KEY)) {
										mpcfg.setLateMaxReminds(Integer.parseInt(value));
									} else if (key.equalsIgnoreCase(SERVERSERV_STDTIMEOUT_KEY)) {
										mpcfg.setStandardProtocolTimeOut(Integer.parseInt(value));
									} else if (key.equalsIgnoreCase(SERVERSERV_STDREMINDS_KEY)) {
										mpcfg.setStandardProtocolReminds(value);
									} else if (key.equalsIgnoreCase(DEVICE_MEASURE_KEY)) {
                                		if  (userMeasure != null) userMeasure.setMeasure(value);
                                	} else if (key.equalsIgnoreCase(DEVICE_SCHEDULE_KEY)) {
										if  (userMeasure != null) userMeasure.setSchedule(value);
									} else if (key.equalsIgnoreCase(DEVICE_FAMILY_KEY)) {
										if  (userMeasure != null) userMeasure.setFamily(UserMeasure.MeasureFamily.get(Integer.parseInt(value)));
									} else if (key.toUpperCase().startsWith(DEVICE_THRESHOLD_KEY.toUpperCase())) {
										thresholds.put(key.substring(DEVICE_THRESHOLD_KEY.length()),value);
									} else if (currentSection.equalsIgnoreCase(USER_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_NAME_KEY)) {
                                		user.setName(value);
                                	} else if (currentSection.equalsIgnoreCase(USER_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_SURNAME_KEY)) {
                                		user.setSurname(value);
                                	} else if (currentSection.equalsIgnoreCase(USER_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_ID_KEY)) {
                                		user.setId(value);
                                	} else if (currentSection.equalsIgnoreCase(USER_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_CF_KEY)) {
                                		user.setCf(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_CF_KEY)) {
                                		if (patient != null) patient.setCf(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_NAME_KEY)) {
										if (patient != null) patient.setName(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_SURNAME_KEY)) {
										if (patient != null) patient.setSurname(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_SEX_KEY)) {
										if (patient != null) patient.setSex(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_HEIGHT_KEY)) {
										if (patient != null) patient.setHeight(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_WEIGHT_KEY)) {
										if (patient != null) patient.setWeight(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_BIRTHDAYDATE_KEY)) {
										if (patient != null) patient.setBirthdayDate(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_ETHNIC_KEY)) {
										if (patient != null) patient.setEthnic(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_ADDITIONALDATA_KEY)) {
										if (patient != null) patient.setAdditionalData(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_QUESTIONS_KEY)) {
										if (patient != null) patient.setQuestions(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_SYMPTOMS_KEY)) {
										if (patient != null) patient.setSymptoms(value);
                                	} else if (currentSection.startsWith(PATIENT_DATA_SECTION) && key.equalsIgnoreCase(USER_DATA_ID_KEY)) {
										if (patient != null) patient.setId(value);
                                	}
                                }
                            }
                            break;
                    }
                }
            }
            
            if(userMeasure != null){
				userMeasure.setIdUser(user.getId());
				userMeasure.setThresholds(thresholds);
        		umList.add(userMeasure);
        	}
            if(patient != null){
        		patList.add(patient);
        	}
                        
            if(patList.size() == 1) {
            	if(patList.get(0).getId().equals(user.getId())) {
            		//Lo user è un paziente
            		user.setIsPatient(true);
            	}
            	else {
            		//Lo user è un nurse
            		user.setIsPatient(false);
            	}
            }
            else {
            	//Siccome lo user ha associati dei pazienti è sicuramente un nurse
            	user.setIsPatient(false);
            }
            dataContainer.add(user);
			dataContainer.add(mpcfg);
            dataContainer.addAll(umList);
            dataContainer.addAll(patList);

		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.SEVERE, "Error parsing Configuration");
			Log.e(TAG, "Error parsing Configuration");
		}
		
		return dataContainer;
	}
}
