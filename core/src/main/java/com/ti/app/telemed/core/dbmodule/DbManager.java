package com.ti.app.telemed.core.dbmodule;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ti.app.telemed.core.MyApp;
import com.ti.app.telemed.core.common.MeasureProtocolCfg;
import com.ti.app.telemed.core.common.Device;
import com.ti.app.telemed.core.common.Measure;
import com.ti.app.telemed.core.common.Patient;
import com.ti.app.telemed.core.common.ServerCertificate;
import com.ti.app.telemed.core.common.ServerConf;
import com.ti.app.telemed.core.common.User;
import com.ti.app.telemed.core.common.UserDevice;
import com.ti.app.telemed.core.common.UserMeasure;
import com.ti.app.telemed.core.common.UserPatient;
import com.ti.app.telemed.core.exceptions.DbException;
import com.ti.app.telemed.core.util.GWConst;
import com.ti.app.telemed.core.util.Util;
import com.ti.app.telemed.core.xmlmodule.XmlManager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONObject;

public class DbManager {
	
	private Logger logger = Logger.getLogger(DbManager.class.getName());
	
	private static final String TAG = "DbManager";
	    
    private static final String DATABASE_NAME = "TelemonitoraggioDB";

    // Versione del DB: incrementare il nr se vi sono modifiche allo schema ed inserire le modifice
    // nel metoto onUpgrade
    private static final int DATABASE_VERSION = 12;
    // versione DB minima richiesta per cui è possibile effettuare un upgrade del DB senza
    // dover droppare e ricreare tutte le tabelle (vedi metodo onUpgrade)
    private static final int MIN_OLD_VERSION = 10;

    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    private Context mCtx;

	private static DbManager dbManager;

    private static final String CREATE_SERVER_CONF_TBL = "CREATE table SERVER_CONF ("
            + "IP text, "
            + "PROTOCOL text, "
            + "PORT text, "
            + "TARGETCFG text, "
            + "TARGETSEND text, "
            + "IP_DEF text, "
            + "PROTOCOL_DEF text, "
            + "PORT_DEF text, "
            + "TARGETCFG_DEF text, "
            + "TARGETSEND_DEF text )";

    private static final String CREATE_MEASURE_PROTOCOL_CFG_TBL = "CREATE table MEASURE_PROTOCOL_CFG ("
            + "UPDATE_INTERVAL integer, "
            + "SILENT_START text, "
            + "SILENT_END text, "
            + "LATE_REMIND_DELAY integer, "
            + "LATE_MAX_REMINDS integer, "
            + "STD_TIMEOUT integer,"
            + "STD_REMINDS text )";

    private static final String CREATE_USER_TBL = "CREATE table USER ("
		+ "ID text NOT NULL PRIMARY KEY, "
		+ "CF text, "
		+ "NAME text, "
		+ "SURNAME text, "
		+ "IS_PATIENT integer, "
		+ "LOGIN text, "
		+ "PASSWORD text, "
        + "TIMESTAMP integer, "
		+ "ACTIVE integer DEFAULT 0, "
        + "BLOCKED integer DEFAULT 0)";
    
    private static final String CREATE_PATIENT_TBL = "CREATE table PATIENT ("
		+ "ID text NOT NULL PRIMARY KEY, "
		+ "CF text, "
		+ "NAME text, "
		+ "SURNAME text, " 
		+ "SEX text, "
		+ "HEIGHT text, " 
		+ "WEIGHT text, "
		+ "BIRTHDATE text, " 
		+ "ETHNIC text, "
		+ "ADDITIONALDATA text, " 
		+ "SYMPTOMS text, "		
		+ "QUESTIONS text )";
    
    private static final String CREATE_USER_PATIENT_TBL = "CREATE table USER_PATIENT ("
		+ "ID integer primary key autoincrement, "
		+ "ID_USER text, "
		+ "ID_PATIENT text, "
		+ "FOREIGN KEY (ID_USER) REFERENCES USER (ID) ON DELETE CASCADE,"
		+ "FOREIGN KEY (ID_PATIENT) REFERENCES PATIENT (ID) )";

    private static final String CREATE_DEVICE_TBL = "CREATE table DEVICE ("
		+ "ID integer primary key autoincrement,"
		+ "MEASURE text, " 
		+ "MODEL text, "
		+ "DESCRIPTION text )";
    
    private static final String CREATE_USER_DEVICE_TBL = "CREATE table USER_DEVICE ("
            + "ID integer primary key autoincrement, "
            + "ID_USER text, "
            + "MEASURE text, "
            + "ID_DEVICE text, "
            + "BTADDRESS text, "
            + "ACTIVE text DEFAULT 'N', "
            + "FOREIGN KEY (ID_USER) REFERENCES USER (ID) ON DELETE CASCADE,"
            + "FOREIGN KEY (ID_DEVICE) REFERENCES DEVICE (ID) ON DELETE CASCADE )";

    private static final String CREATE_CURRENT_DEVICE_TBL = "CREATE table CURRENT_DEVICE ("
            + "MEASURE text, "
            + "ID_DEVICE text, "
            + "BTADDRESS text )";

	private static final String CREATE_USER_MEASURE_TBL = "CREATE table USER_MEASURE ("
			+ "ID integer primary key autoincrement, "
			+ "ID_USER text, "
			+ "MEASURE text, "
            + "FAMILY integer, "
			+ "SCHEDULE text, "
			+ "OUT_OF_RANGE integer, "
			+ "LAST_DAY int, "
			+ "NR_LAST_DAY integer, "
            + "THRESHOLDS text, "
			+ "FOREIGN KEY (ID_USER) REFERENCES USER (ID) ON DELETE CASCADE )";

    private static final String CREATE_MEASURE_TBL = "CREATE table MEASURE ("
		+ "TIMESTAMP text NOT NULL, "
		+ "MEASURE_TYPE text, "
        + "STANDARD integer, "
        + "DEVICE_TYPE integer, "
        + "DEVICE_DESC text, "
        + "BTADDRESS text, "
        + "MEASURES text, "
        + "THRESHOLDS text, "
		+ "SENT integer, "
		+ "FILE BLOB, " 
		+ "FILE_TYPE text, " 
		+ "ID_USER text, "
		+ "ID_PATIENT text, "
		+ "FAILED integer, "
		+ "FAILURE_CODE text, "
		+ "FAILURE_MESSAGE text, "
		+ "SEND_FAIL_COUNT integer, "
		+ "SEND_FAIL_REASON text, "
        + "SEND_FAIL_TIMESTAMP integer DEFAULT 0, "
        + "PRIMARY KEY (TIMESTAMP, MEASURE_TYPE), "
		+ "FOREIGN KEY (ID_PATIENT) REFERENCES PATIENT (ID) ON DELETE CASCADE, "
		+ "FOREIGN KEY (ID_USER) REFERENCES USER (ID) ON DELETE CASCADE )";
    
    private static final String CREATE_CERTIFICATES_TBL = "CREATE TABLE CERTIFICATES ("
    	+ "ID integer primary key autoincrement, "
    	+ "ID_USER text, "
    	+ "HOSTNAME text, "
    	+ "PUBLIC_KEY BLOB, "
    	+ "FOREIGN KEY (ID_USER) REFERENCES USER (ID) ON DELETE CASCADE )";
    
    private final String selectDeviceWhereModelMeasure = "select * from DEVICE where MEASURE = ? and MODEL = ? ";
    private final String selectDeviceBtAddressWhereIdUserIdDevice = "select * from USER_DEVICE where ID_USER = ? and ID_DEVICE = ?";
    private final String selectUserDeviceData = "SELECT ud.* FROM USER_DEVICE ud, DEVICE d WHERE ud.ID_USER = ? AND ud.ID_DEVICE = d.ID AND d.MEASURE = ? AND d.MODEL = ? ";
	private final String selectDistinctMeasures = "SELECT DISTINCT MEASURE FROM USER_DEVICE WHERE ID_USER = ?";
    private final String selectCurrentDeviceWhereMeasure = "select * from CURRENT_DEVICE where MEASURE = ? ";

	private User currentUser;      
    
	private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	db.execSQL(CREATE_USER_TBL);
            db.execSQL(CREATE_PATIENT_TBL);
            db.execSQL(CREATE_USER_PATIENT_TBL);
            db.execSQL(CREATE_DEVICE_TBL);
            db.execSQL(CREATE_USER_DEVICE_TBL);
			db.execSQL(CREATE_USER_MEASURE_TBL);
            db.execSQL(CREATE_MEASURE_TBL);
            db.execSQL(CREATE_CURRENT_DEVICE_TBL);
            db.execSQL(CREATE_SERVER_CONF_TBL);
            db.execSQL(CREATE_MEASURE_PROTOCOL_CFG_TBL);
            db.execSQL(CREATE_CERTIFICATES_TBL);
            Log.i(TAG, "Database created");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < MIN_OLD_VERSION) {
                Log.w(TAG, "Database upgrade from version " + oldVersion + " not possible. Recreating DB ...");
                db.execSQL("DROP TABLE IF EXISTS USER");
                db.execSQL("DROP TABLE IF EXISTS PATIENT");
                db.execSQL("DROP TABLE IF EXISTS USER_PATIENT");
                db.execSQL("DROP TABLE IF EXISTS DEVICE");
                db.execSQL("DROP TABLE IF EXISTS USER_DEVICE");
                db.execSQL("DROP TABLE IF EXISTS USER_MEASURE");
                db.execSQL("DROP TABLE IF EXISTS CURRENT_DEVICE");
                db.execSQL("DROP TABLE IF EXISTS MEASURE");
                db.execSQL("DROP TABLE IF EXISTS SERVER_CONF");
                db.execSQL("DROP TABLE IF EXISTS MEASURE_PROTOCOL_CFG");
                db.execSQL("DROP TABLE IF EXISTS CERTIFICATES");
                onCreate(db);
            } else {
                Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                        + newVersion);
                switch (oldVersion) {
                    case 10:
                        db.execSQL("ALTER TABLE USER ADD COLUMN TIMESTAMP integer");
                    case 11:
                        db.execSQL("DROP TABLE IF EXISTS CURRENT_DEVICE");
                        db.execSQL(CREATE_CURRENT_DEVICE_TBL);
                }
            }
        }
    }
	
    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     * 
     * @param ctx the Context within which to work
     */
    private DbManager(Context ctx) {
        this.mCtx = ctx;
    }
	
    /**
     * Open the database. If it cannot be opened, try to create a new
     * instance of the database. If it cannot be created, throw an exception to
     * signal the failure
     * 
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    private DbManager open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getWritableDatabase();

        if (!mDb.isReadOnly()){
        	// Enable foreign key constraints
        	mDb.execSQL("PRAGMA foreign_keys=ON;");
        }

        return this;
    }
    
	public void close(){
    	if(mDb != null){
    		mDb.close();
    		mDb = null;
    	}
    	if(mDbHelper !=  null){
    		mDbHelper.close();
    		mDbHelper = null;
    	}
    	dbManager = null;
    }

    public static DbManager getDbManager(){
        if(dbManager == null){
            dbManager = new  DbManager(MyApp.getContext());
            dbManager.open();
        }
        return dbManager;
    }

    // Device methods
	public Device getDeviceWhereMeasureModel(String measure, String model) throws DbException {
        synchronized (this) {
            Device ret = null;
            Cursor c = null;
            try {
                c = mDb.rawQuery(selectDeviceWhereModelMeasure, new String[]{measure, model});
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = getDeviceObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}

	public void insertDevice(Device d) throws DbException {
        synchronized (this) {
            try{
                ContentValues initialValues = new ContentValues();
                initialValues.put("MEASURE", d.getMeasure());
                initialValues.put("MODEL", d.getModel());
                initialValues.put("DESCRIPTION", d.getDescription());
                mDb.insert("DEVICE", null, initialValues);
                logger.log(Level.INFO, "Device inserted: "+ d.toString());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	public void updateDevice(Device d) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("DESCRIPTION", d.getDescription());
                String[] args = new String[]{d.getMeasure(), d.getModel()};
                mDb.update("DEVICE", values, "MEASURE = ? AND MODEL =  ? ", args);
                logger.log(Level.INFO, "Device updated: "+ d.toString());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	public void deleteUnusedDevice(List<Device> newDeviceList) throws DbException {
        synchronized (this) {
            try{
                List<Device> currentDevices = getAllDevices();
                for(Device d : currentDevices){
                    if(!newDeviceList.contains(d)){
                        int rows = mDb.delete("DEVICE", "MEASURE = ? AND MODEL = ?", new String[]{d.getMeasure(), d.getModel()});
                        logger.log(Level.INFO, "deleteUnusedDevice deleted rows = "+rows);
                        checkSingleModelForMeasure(d.getMeasure());
                    }
                }
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	public Vector<Device> getAllDevices() throws DbException {
        synchronized (this) {
            Vector<Device> ret = new Vector<>();
            Device tmpDv;
            Cursor c = null;
            try {
                c = mDb.query("DEVICE", null, null, null, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        tmpDv = getDeviceObject(c);
                        ret.addElement(tmpDv);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}

	///////////////////
    private Device getDeviceObject(Cursor c) {
        synchronized (this) {
            Device ret = new Device();
            ret.setId(c.getInt(c.getColumnIndex("ID")));
            ret.setMeasure(c.getString(c.getColumnIndex("MEASURE")));
            ret.setModel(c.getString(c.getColumnIndex("MODEL")));
            ret.setDescription(c.getString(c.getColumnIndex("DESCRIPTION")));
            return ret;
        }
    }

    private void insertCurrentDevice(String measure, String idDevice, String btAddress) throws DbException {
        synchronized (this) {
            try{
                ContentValues initialValues = new ContentValues();
                initialValues.put("MEASURE", measure);
                initialValues.put("ID_DEVICE", idDevice);
                initialValues.put("BTADDRESS", btAddress);
                mDb.insert("CURRENT_DEVICE", null, initialValues);
                logger.log(Level.INFO, "new record inserted in CURRENT_DEVICE: "
                        + measure + " - " + idDevice + " - " + btAddress);
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
    }

    private void updateCurrentDevice(String measure, String idDevice, String btAddress) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("ID_DEVICE", idDevice);
                values.put("BTADDRESS", btAddress);
                String[] args = new String[]{measure};
                mDb.update("CURRENT_DEVICE", values, "MEASURE = ? ", args);
                logger.log(Level.INFO, "record updated in CURRENT_DEVICE: "
                        + measure + " - " + idDevice + " - " + btAddress);
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
    }

    private String getCurrentDeviceBt(String idUser, String idDevice) throws DbException {
        synchronized (this) {
            String btAddress = "";
            Cursor c = null;

            try {
                c = mDb.rawQuery(selectDeviceBtAddressWhereIdUserIdDevice, new String[]{idUser, idDevice});
                if (c != null) {
                    if (c.moveToFirst()) {
                        int nameColumnIndex = c.getColumnIndexOrThrow("BTADDRESS");
                        btAddress = c.getString(nameColumnIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }

            return btAddress;
        }
    }

    public void storeCurrentDeviceData(String measure, String idDevice, String btAddress) throws DbException {
        synchronized (this) {
            Cursor c = null;

            try {
                c = mDb.rawQuery(selectCurrentDeviceWhereMeasure, new String[]{measure});
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something --> update
                        updateCurrentDevice(measure, idDevice, btAddress);
                    } else {
                        // insert
                        insertCurrentDevice(measure, idDevice, btAddress);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
        }
    }


    private void checkSingleModelForMeasure(String measure) {
        synchronized (this) {
            List<Device> dList = getDeviceByMeasure(measure);
            if (dList.size() > 0) {
                // Se la size è 0 significa che non c'è nessun modello gestito per quel tipo di misura
                // quindi non faccio nulla
                if (dList.size() == 1) {
                    //Se c'è un solo modello per quel tipo di misura
                    //associo a tutti i pazienti quel preciso strumento ponendo il flag ACTIVE = 'S'
                    ContentValues values = new ContentValues();
                    values.put("ACTIVE", "S");
                    mDb.update("USER_DEVICE", values, "MEASURE = ? ", new String[]{measure});
                }
            }
        }
	}
	
	private List<Device> getDeviceByMeasure(String measure) {
        synchronized (this) {
            Cursor c = null;
            List<Device> devs = new ArrayList<>();
            try {
                c = mDb.query("DEVICE", null, "MEASURE = ?", new String[]{measure}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        Device d = getDeviceObject(c);
                        devs.add(d);
                    }
                }
            } finally {
                if (c != null)
                    c.close();
            }
            return devs;
        }
	}

    private Device getDevice(Integer idDevice) {
        synchronized (this) {
            Device ret = null;
            Cursor c = null;
            try {
                c = mDb.query("DEVICE", null, "ID  = ? ", new String[]{"" + idDevice}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getDeviceObject(c);
                    }
                }
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
    }


    // ServerConf methods
    public ServerConf getServerConf() throws DbException {
        synchronized (this) {
            ServerConf ret = null;
            Cursor c = null;
            try {
                c = mDb.query("SERVER_CONF", null, null, null, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = getServerConfObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}		
	
	public void insertServerConf(ServerConf sc) throws DbException {
        synchronized (this) {
            try {
                ContentValues values = new ContentValues();
                values.put("IP", sc.getIp());
                values.put("PROTOCOL",  sc.getProtocol());
                values.put("PORT", sc.getPort());
                values.put("TARGETCFG", sc.getTargetCfg());
                values.put("TARGETSEND", sc.getTargetSend());
                values.put("IP_DEF", sc.getIpDef());
                values.put("PROTOCOL_DEF", sc.getProtocolDef());
                values.put("PORT_DEF", sc.getPortDef());
                values.put("TARGETCFG_DEF", sc.getTargetCfgDef());
                values.put("TARGETSEND_DEF", sc.getTargetSendDef());
                mDb.insert("SERVER_CONF", null, values);
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	public void updateServerConf(ServerConf sc) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("IP", sc.getIp());
                values.put("PROTOCOL",  sc.getProtocol());
                values.put("PORT", sc.getPort());
                values.put("TARGETCFG", sc.getTargetCfg());
                values.put("TARGETSEND", sc.getTargetSend());
                mDb.update("SERVER_CONF", values, null, null);
                logger.log(Level.INFO, "ServerConf updated");
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	/**
	 * Metodo che permette di ottenere le impostazioni di default della configurazione del server
	 * @return ServerConf
	 * @throws DbException
	 */
	public ServerConf getDefaultServerConf() throws DbException {
        synchronized (this) {
            ServerConf sc = null;
            Cursor c = null;
            try {
                c = mDb.query("SERVER_CONF", new String[]{"IP_DEF", "PROTOCOL_DEF", "PORT_DEF", "TARGETCFG_DEF", "TARGETSEND_DEF"}, null, null, null, null, null);
                if (c.moveToFirst()) {
                    int ipColumnIndex = c.getColumnIndexOrThrow("IP_DEF");
                    int protocolColumnIndex = c.getColumnIndexOrThrow("PROTOCOL_DEF");
                    int portColumnIndex = c.getColumnIndexOrThrow("PORT_DEF");
                    int targetCfgColumnIndex = c.getColumnIndexOrThrow("TARGETCFG_DEF");
                    int targetSendColumnIndex = c.getColumnIndexOrThrow("TARGETSEND_DEF");

                    String ip = c.getString(ipColumnIndex);
                    String protocol = c.getString(protocolColumnIndex);
                    String port = c.getString(portColumnIndex);
                    String targetCfg = c.getString(targetCfgColumnIndex);
                    String targetSend = c.getString(targetSendColumnIndex);

                    sc = new ServerConf(ip, protocol, port, targetCfg, targetSend);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage());
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return sc;
        }
	}
	
	/**
	 * Metodo che permette di ripristinare le impostazioni del server ai valori iniziali
	 */
	public void resetServerConf() throws DbException {
        synchronized (this) {
            try {
                ServerConf defaultSC = getDefaultServerConf();
                ContentValues values = new ContentValues();
                values.put("IP", defaultSC.getIpDef());
                values.put("PORT", defaultSC.getPortDef());
                values.put("PROTOCOL", defaultSC.getProtocolDef());
                values.put("TARGETCFG", defaultSC.getTargetCfgDef());
                values.put("TARGETSEND", defaultSC.getTargetSendDef());
                mDb.update("SERVER_CONF", values, null, null);
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage());
                e.printStackTrace();
                throw new DbException();
            }
        }
	}

    ///////////
    private ServerConf getServerConfObject(Cursor c) {
        synchronized (this) {
            ServerConf ret = new ServerConf();
            ret.setIp(c.getString(c.getColumnIndex("IP")));
            ret.setProtocol(c.getString(c.getColumnIndex("PROTOCOL")));
            ret.setPort(c.getString(c.getColumnIndex("PORT")));
            ret.setTargetCfg(c.getString(c.getColumnIndex("TARGETCFG")));
            ret.setTargetSend(c.getString(c.getColumnIndex("TARGETSEND")));
            ret.setIpDef(c.getString(c.getColumnIndex("IP_DEF")));
            ret.setProtocolDef(c.getString(c.getColumnIndex("PROTOCOL_DEF")));
            ret.setPortDef(c.getString(c.getColumnIndex("PORT_DEF")));
            ret.setTargetCfgDef(c.getString(c.getColumnIndex("TARGETCFG_DEF")));
            ret.setTargetSendDef(c.getString(c.getColumnIndex("TARGETSEND_DEF")));
            return ret;
        }
    }


    // public MeasureProtocolCfg methods
    public MeasureProtocolCfg getMeasureProtocolCfg() throws DbException {
        synchronized (this) {
            MeasureProtocolCfg ret = null;
            Cursor c = null;
            try {
                c = mDb.query("MEASURE_PROTOCOL_CFG", null, null, null, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = getMeasureProtocolCfgObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
    }

    public void insertMeasureProtocolCfg(MeasureProtocolCfg sc) throws DbException {
        synchronized (this) {
            try {
                ContentValues values = new ContentValues();
                values.put("UPDATE_INTERVAL", sc.getUpdateInterval());
                values.put("SILENT_START",  sc.getSilentStart());
                values.put("SILENT_END", sc.getSilentEnd());
                values.put("LATE_REMIND_DELAY", sc.getLateRemindDelay());
                values.put("LATE_MAX_REMINDS", sc.getLateMaxReminds());
                values.put("STD_TIMEOUT", sc.getStandardProtocolTimeOut());
                values.put("STD_REMINDS", sc.getStandardProtocolReminds());
                mDb.insert("MEASURE_PROTOCOL_CFG", null, values);
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            }
        }
    }

    public void updateMeasureProtocolCfg(MeasureProtocolCfg sc) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("UPDATE_INTERVAL", sc.getUpdateInterval());
                values.put("SILENT_START",  sc.getSilentStart());
                values.put("SILENT_END", sc.getSilentEnd());
                values.put("LATE_REMIND_DELAY", sc.getLateRemindDelay());
                values.put("LATE_MAX_REMINDS", sc.getLateMaxReminds());
                values.put("STD_TIMEOUT", sc.getStandardProtocolTimeOut());
                values.put("STD_REMINDS", sc.getStandardProtocolReminds());
                mDb.update("MEASURE_PROTOCOL_CFG", values, null, null);
                logger.log(Level.INFO, "MeasureProtocolCfg updated");
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
    }

    ////////////
    private MeasureProtocolCfg getMeasureProtocolCfgObject(Cursor c) {
        synchronized (this) {
            MeasureProtocolCfg ret = new MeasureProtocolCfg();
            ret.setUpdateInterval(c.getInt(c.getColumnIndex("UPDATE_INTERVAL")));
            ret.setSilentStart(c.getString(c.getColumnIndex("SILENT_START")));
            ret.setSilentEnd(c.getString(c.getColumnIndex("SILENT_END")));
            ret.setLateRemindDelay(c.getInt(c.getColumnIndex("LATE_REMIND_DELAY")));
            ret.setLateMaxReminds(c.getInt(c.getColumnIndex("LATE_MAX_REMINDS")));
            ret.setStandardProtocolTimeOut(c.getInt(c.getColumnIndex("STD_TIMEOUT")));
            ret.setStandardProtocolReminds(c.getString(c.getColumnIndex("STD_REMINDS")));
            return ret;
        }
    }


    /**
	 * Ottengo i certificato di sicurezza del server dalla tabella CERTIFICATES
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @return array di oggetti di tipo {@code ServerCertificate} che contengono la chiave pubblica associata al certificato accettato
	 * @throws DbException
	 */
	public ArrayList<ServerCertificate> getServerCertificate(String idUser) throws DbException {
        synchronized (this) {
            ArrayList<ServerCertificate> certList = null;
            ServerCertificate cert;
            Cursor c = null;

            try {
                c = mDb.query("CERTIFICATES", null, "ID_USER = ?", new String[]{idUser}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        do {
                            cert = getServerCertificateObject(c);
                            if (certList == null)
                                certList = new ArrayList<>();
                            certList.add(cert);
                        } while (c.moveToNext());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }

            return certList;
        }
	}
	
	/**
	 * Metodo che rimuove il certificato memorizzato all'interno della tabella {@code CERTIFICATES}
	 * @throws DbException
	 */
	public void removeServerCertificates() throws DbException {
		//Rimuovo tutte le entry dalla tabella dei certificati del server
        synchronized (this) {
            mDb.delete("CERTIFICATES", null, null);
        }
	}

	private ServerCertificate getServerCertificateObject(Cursor c) {
        synchronized (this) {
            ServerCertificate sc = new ServerCertificate();
            int hostNameColumnIndex = c.getColumnIndexOrThrow("HOSTNAME");
            int pkColumnIndex = c.getColumnIndexOrThrow("PUBLIC_KEY");
            sc.setHostname(c.getString(hostNameColumnIndex));
            sc.setPublicKey(c.getBlob(pkColumnIndex));
            return sc;
        }
	}

    public void  updateConfiguration(Vector<Object> dataContainer) throws DbException  {
        synchronized (this) {
            try {
                Enumeration<Object> en = dataContainer.elements();
                Object tmp;
                List<String> associatedMeasures = new ArrayList<>();
                while (en.hasMoreElements()) {
                    tmp = en.nextElement();
                    if (tmp instanceof User) {
                        setUser((User) tmp);
                        setCurrentUser((User) tmp);
                        deleteUserPatientByIdUser(getCurrentUser().getId());
                    } else if (tmp instanceof Patient) {
                        Patient p = (Patient) tmp;
                        setPatient(p);
                        setUserPatient(p);
                    } else if (tmp instanceof MeasureProtocolCfg) {
                        if (getMeasureProtocolCfg() == null)
                            insertMeasureProtocolCfg((MeasureProtocolCfg)tmp);
                        else
                            updateMeasureProtocolCfg((MeasureProtocolCfg)tmp);
                    }
                    else if (tmp instanceof UserMeasure) {
                        UserMeasure um = (UserMeasure) tmp;
                        setUserMeasure(um);
                        List<Device> dList = getDeviceByMeasure(um.getMeasure());

                        if(dList.size() > 0){
                            // Se la size è 0 significa che non c'è nessun modello gestito per quel tipo di misura
                            // quindi non faccio nulla
                            if(dList.size() == 1){
                                //Se c'è un solo modello per quel tipo di misura
                                //associo all'utente quel preciso strumento ponendo il flag ACTIVE = true
                                Device dev = dList.get(0);
                                setUserDevice(dev, true);
                            } else {
                                for (Device dev : dList) {
                                    //Se ci sono più modelli per quel tipo di misura
                                    //inserisco quelli che non sono già presenti ma ponendo il flag ACTIVE = false
                                    setUserDevice(dev, false);
                                }
                            }
                            associatedMeasures.add(um.getMeasure());
                        }
                    }
                }
                deleteOldUserDeviceData(associatedMeasures);
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
    
    public User getCurrentUser() {
        synchronized (this) {
            return currentUser;
        }
	}

	public void setCurrentUser(User currentUser) throws DbException {
        synchronized (this) {
            this.currentUser = currentUser;
            updateActiveUser(currentUser);
        }
	}
    
	public void setUser(User n) throws DbException {
        synchronized (this) {
            if (getUser(n.getId()) == null) {
                insertUser(n);
            } else {
                updateUser(n);
            }
        }
	}

    // USER methods

    public User getActiveUser() {
        synchronized (this) {
            User ret = null;
            Cursor c;

            if (mDb != null) {
                c = mDb.query("USER", null, "ACTIVE = ?", new String[]{String.valueOf(1)}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getUserObject(c);
                    }
                    c.close();
                }
            }
            return ret;
        }
    }

    public User getUser(String login, String password) throws DbException {
        synchronized (this) {
            User ret = null;
            Cursor c = null;
            try {
                c = mDb.query("USER", null, "LOGIN = ? AND PASSWORD = ?", new String[]{login, password}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getUserObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
    }

    public void updateUserCredentials(String login, String password) {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("LOGIN", login);
            values.put("PASSWORD", password);
            values.put("BLOCKED", 0);
            String[] args = new String[]{"" + getCurrentUser().getId()};
            mDb.update("USER", values, "ID = ? ", args);
        }
    }

    public void resetActiveUser(String userId) {
        synchronized (this) {
            try {
                int count;
                ContentValues values = new ContentValues();
                values.put("ACTIVE", 0);
                count = mDb.update("USER", values, "ID = ? ", new String[]{userId});
                logger.log(Level.INFO, count + "active user reset");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void updateActiveUser(User u) throws DbException {
        synchronized (this) {
            int count;
            ContentValues values = new ContentValues();
            values.put("ACTIVE", 1);
            mDb.update("USER", values, "ID = ? ", new String[]{u.getId()});
            logger.log(Level.INFO, "Active user updated: " + u.getId());

            values = new ContentValues();
            values.put("ACTIVE", 0);
            count = mDb.update("USER", values, "ID <> ? ", new String[]{u.getId()});
            logger.log(Level.INFO, "Inactive users updated count: " + count);
            alignActiveUserToCurrentDevices();
        }
    }

    public void updateUserBlocked(String userId, boolean blocked) {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("BLOCKED", blocked ?  1 : 0);
            mDb.update("USER", values, "ID = ? ", new String[]{userId});
        }
    }

    public List<User> getNotActiveUsers() {
        synchronized (this) {
            List<User> ret = new ArrayList<>();
            Cursor c;
            c = mDb.query("USER", null, "ACTIVE = ?", new String[]{String.valueOf(0)}, null, null, null);

            if (c != null) {
                while (c.moveToNext()) {
                    User u = getUserObject(c);
                    ret.add(u);
                }
                c.close();
            }
            return ret;
        }
    }

    public List<User> getAllUsers() throws DbException{
        synchronized (this) {
            List<User> ret = new ArrayList<>();
            Cursor c = null;
            try {
                c = mDb.query("USER", null, null, null, null, null, null);

                if (c != null) {
                    while (c.moveToNext()) {
                        User u = getUserObject(c);
                        ret.add(u);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
    }

    public void deleteUser(String idUser) throws DbException {
        synchronized (this) {
            try {
                int rows = mDb.delete("USER", "ID = ?", new String[] {idUser});
                Log.i(TAG, "Eliminato " + rows + " utente dal db: "+idUser);
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            }
        }
    }

    public User getUser(String id) throws DbException {
        synchronized (this) {
            User ret = null;
            Cursor c = null;
            try {
                c = mDb.query("USER", null, "ID = ?", new String[]{id}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getUserObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	private void insertUser(User u) throws SQLException {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("ID", u.getId());
            values.put("CF", u.getCf());
            values.put("NAME", u.getName());
            values.put("SURNAME", u.getSurname());
            values.put("TIMESTAMP", System.currentTimeMillis());
            values.put("IS_PATIENT",u.getIsPatient()? 1:0);
            mDb.insert("USER", null, values);
            logger.log(Level.INFO, "User inserted");
        }
	}
	
	private void updateUser(User u) throws SQLException {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("CF", u.getCf());
            values.put("NAME", u.getName());
            values.put("SURNAME", u.getSurname());
            values.put("TIMESTAMP", System.currentTimeMillis());
            mDb.update("USER", values, " ID = ? ", new String[]{u.getId()});
            logger.log(Level.INFO, "User updated");
        }
	}

	private User getUserObject(Cursor c) {
        synchronized (this) {
            User ret;
            ret = new User();
            ret.setId(c.getString(c.getColumnIndex("ID")));
            ret.setCf(c.getString(c.getColumnIndex("CF")));
            ret.setName(c.getString(c.getColumnIndex("NAME")));
            ret.setSurname(c.getString(c.getColumnIndex("SURNAME")));
            ret.setTimestamp(c.getLong(c.getColumnIndex("TIMESTAMP")));
            ret.setLogin(c.getString(c.getColumnIndex("LOGIN")));
            ret.setPassword(c.getString(c.getColumnIndex("PASSWORD")));
            ret.setActive(c.getInt(c.getColumnIndex("ACTIVE")) == 1);
            ret.setBlocked(c.getInt(c.getColumnIndex("BLOCKED")) == 1);
            ret.setIsPatient(c.getInt(c.getColumnIndex("IS_PATIENT")) == 1);

            return ret;
        }
	}
	
	private void setPatient(Patient pa) throws DbException {
        synchronized (this) {
            if (getPatient(pa.getId()) == null) {
                insertPatient(pa);
            } else {
                updatePatient(pa);
            }
        }
	}
	
	private Patient getPatient(String id) throws DbException {
        synchronized (this) {
            Patient ret = null;
            Cursor c = null;
            try {
                c = mDb.query("PATIENT", null, "ID = ?", new String[]{id}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getPatientObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	private Patient getPatientObject(Cursor c) throws SQLException {
        synchronized (this) {
            Patient tempPat = new Patient();
            tempPat.setId(c.getString(c.getColumnIndex("ID")));
            tempPat.setCf(c.getString(c.getColumnIndex("CF")));
            tempPat.setName(c.getString(c.getColumnIndex("NAME")));
            tempPat.setSurname(c.getString(c.getColumnIndex("SURNAME")));
            tempPat.setSex(c.getString(c.getColumnIndex("SEX")));
            tempPat.setHeight(c.getString(c.getColumnIndex("HEIGHT")));
            tempPat.setWeight(c.getString(c.getColumnIndex("WEIGHT")));
            tempPat.setBirthdayDate(c.getString(c.getColumnIndex("BIRTHDATE")));
            tempPat.setEthnic(c.getString(c.getColumnIndex("ETHNIC")));
            tempPat.setAdditionalData(c.getString(c.getColumnIndex("ADDITIONALDATA")));
            tempPat.setSymptoms(c.getString(c.getColumnIndex("SYMPTOMS")));
            tempPat.setQuestions(c.getString(c.getColumnIndex("QUESTIONS")));
            return tempPat;
        }
	}
	
	private void insertPatient(Patient p) throws SQLException {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("ID", p.getId());
            values.put("CF", p.getCf());
            values.put("NAME", p.getName());
            values.put("SURNAME", p.getSurname());
            values.put("SEX", p.getSex());
            values.put("HEIGHT", p.getHeight());
            values.put("WEIGHT", p.getWeight());
            values.put("BIRTHDATE", p.getBirthdayDate());
            values.put("ETHNIC", p.getEthnic());
            values.put("ADDITIONALDATA", p.getAdditionalData());
            values.put("SYMPTOMS", p.getSymptoms());
            values.put("QUESTIONS", p.getQuestions());

            mDb.insert("PATIENT", null, values);
            logger.log(Level.INFO, "Patient " + p.getId() + " inserted");
        }
	}
	
	private void updatePatient(Patient p) throws SQLException {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("CF", p.getCf());
            values.put("NAME", p.getName());
            values.put("SURNAME", p.getSurname());
            values.put("SEX", p.getSex());
            values.put("HEIGHT", p.getHeight());
            values.put("WEIGHT", p.getWeight());
            values.put("BIRTHDATE", p.getBirthdayDate());
            values.put("ETHNIC", p.getEthnic());
            values.put("ADDITIONALDATA", p.getAdditionalData());
            values.put("SYMPTOMS", p.getSymptoms());
            values.put("QUESTIONS", p.getQuestions());

            mDb.update("PATIENT", values, " ID = ? ", new String[]{p.getId()});
            logger.log(Level.INFO, "Patient " + p.getId() + " updated");
        }
	}
	
	private void setUserPatient(Patient patient) throws DbException {
        synchronized (this) {

            UserPatient up = getUserPatient(getCurrentUser().getId(), patient.getId());
            if (up == null) {
                insertUserPatientData(patient);
            }
        }
	}

	private void insertUserPatientData(Patient p) {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("ID_USER", getCurrentUser().getId());
            values.put("ID_PATIENT", p.getId());
            mDb.insert("USER_PATIENT", null, values);
        }
	}

	private UserPatient getUserPatient(String idUser, String idPatient) throws DbException {
        synchronized (this) {
            UserPatient ret = null;
            Cursor c = null;
            try {
                c = mDb.query("USER_PATIENT", null, "ID_USER = ? AND ID_PATIENT = ? ", new String[]{idUser, idPatient}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        // the query returns something
                        ret = getUserPatientObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	/**
	 * Metodo che restituisce tutti i pazienti associati ad un utente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'operatore
	 * @return Listaoggetti di tipo {@code UserPatient} riferiti all'idUser passato
	 */
	public List<UserPatient> getUserPatients(String idUser) {
        synchronized (this) {
            List<UserPatient> patients = null;
            UserPatient p;
            Cursor c;

            c = mDb.query("USER_PATIENT", null, "ID_USER = ?", new String[]{idUser}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        p = getUserPatientObject(c);
                        if (patients == null)
                            patients = new ArrayList<>();
                        patients.add(p);
                    } while (c.moveToNext());
                }
                c.close();
            }

            return patients;
        }
	}
	
	private UserPatient getUserPatientObject(Cursor c) {
        synchronized (this) {
            UserPatient up = new UserPatient();
            up.setId(c.getInt(c.getColumnIndex("ID")));
            up.setIdUser(c.getString(c.getColumnIndex("ID_USER")));
            up.setIdPatient(c.getString(c.getColumnIndex("ID_PATIENT")));
            return up;
        }
	}
	
	private void deleteUserPatientByIdUser(String idUser) {
        synchronized (this) {
            int rows = mDb.delete("USER_PATIENT", "ID_USER = ? ", new String[]{idUser});
            logger.log(Level.INFO, "deleteUserPatientByIdUser " + idUser + " deleted " + rows + " rows");
        }
	}

	public List<UserMeasure> getUserMeasures (String userId) throws DbException {
        synchronized (this) {
            Vector<UserMeasure> ret = new Vector<>();
            Cursor c = null;
            try {
                c = mDb.query("USER_MEASURE", null, "ID_USER  = ? ", new String[]{userId}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        ret.add(getUserMeasureObject(c));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
    public List<UserMeasure> getBiometricUserMeasures (String userId) throws DbException {
        synchronized (this) {
            Vector<UserMeasure> ret = new Vector<>();
            Cursor c = null;
            try {
                c = mDb.query("USER_MEASURE", null, "ID_USER  = ? AND FAMILY = ?", new String[]{userId, Integer.toString(UserMeasure.MeasureFamily.BIOMETRICA.getValue())}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        ret.add(getUserMeasureObject(c));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
    }

	public UserMeasure getUserMeasure (String userId, String measure) throws DbException {
        synchronized (this) {
            UserMeasure ret = null;
            Cursor c = null;
            try {
                c = mDb.query("USER_MEASURE", null, "MEASURE = ? AND ID_USER  = ? ", new String[]{measure, userId}, null, null, null);
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = getUserMeasureObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}

	private void insertUserMeasureData(UserMeasure um) throws DbException {
        synchronized (this) {
            try {
                ContentValues values = new ContentValues();
                values.put("ID_USER", getCurrentUser().getId());
                values.put("MEASURE", um.getMeasure());
                // Configuration values
                values.put("FAMILY", um.getFamily().getValue());
                values.put("SCHEDULE", um.getSchedule());
                values.put("THRESHOLDS", new JSONObject(um.getThresholds()).toString());
                // Status Values
                values.put("OUT_OF_RANGE", um.isOutOfRange() ? 1 : 0);
                values.put("LAST_DAY", um.getLastDay().getTime());
                values.put("NR_LAST_DAY", um.getNrLastDay());
                mDb.insert("USER_MEASURE", null, values);
                logger.log(Level.INFO, "UserMeasure inserted: " + um.toString());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                Log.i(TAG, "Exception insertUserMeasure: " + sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}

	private void updateUserMeasureCfg(UserMeasure um) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("SCHEDULE", um.getSchedule());
                values.put("THRESHOLDS", new JSONObject(um.getThresholds()).toString());
                values.put("FAMILY", um.getFamily().getValue());
                String[] args = new String[]{ um.getIdUser(), um.getMeasure() };
                mDb.update("USER_MEASURE", values, " ID_USER = ? AND MEASURE = ? ", args);
                logger.log(Level.INFO, "UserMeasure cfg updated: "+ um.toString());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}

	public void updateUserMeasureStatus(UserMeasure um) throws DbException {
        synchronized (this) {
            try{
                ContentValues values = new ContentValues();
                values.put("OUT_OF_RANGE", um.isOutOfRange()?1:0);
                values.put("LAST_DAY", um.getLastDay().getTime());
                values.put("NR_LAST_DAY", um.getNrLastDay());
                String[] args = new String[]{ um.getIdUser(), um.getMeasure() };
                mDb.update("USER_MEASURE", values, " ID_USER = ? AND MEASURE = ? ", args);
                logger.log(Level.INFO, "UserMeasure status updated: "+ um.toString());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}

	// UserDevice methods
	private void setUserMeasure(UserMeasure um) throws DbException {
        synchronized (this) {
            UserMeasure dbud = getUserMeasure(um.getIdUser(), um.getMeasure());
            if (dbud == null)
                insertUserMeasureData(um);
            else
                updateUserMeasureCfg(um);
        }
	}

	private int getUserMeasureId (String idUser, String measure) throws DbException {
        synchronized (this) {
            Cursor c = null;
            int ret = -1;
            try {
                c = mDb.rawQuery("SELECT ID FROM USER_MEASURE um WHERE um.ID_USER = ? AND um.MEASURE = ?", new String[]{idUser, measure});
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = c.getInt(c.getColumnIndex("ID"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}

	private UserMeasure getUserMeasureObject(Cursor c) {
        synchronized (this) {
            UserMeasure ud = new UserMeasure();
            ud.setId(c.getInt(c.getColumnIndex("ID")));
            ud.setIdUser(c.getString(c.getColumnIndex("ID_USER")));
            ud.setMeasure(c.getString(c.getColumnIndex("MEASURE")));
            ud.setFamily(UserMeasure.MeasureFamily.get((c.getInt(c.getColumnIndex("FAMILY")))));
            ud.setSchedule(c.getString(c.getColumnIndex("SCHEDULE")));
            ud.setOutOfRange(c.getInt(c.getColumnIndex("OUT_OF_RANGE")) == 1);
            ud.setLastDay(new Date(c.getInt(c.getColumnIndex("LAST_DAY"))));
            ud.setNrLastDay(c.getInt(c.getColumnIndex("NR_LAST_DAY")));
            ud.setThresholds(Util.jsonToStringMap(c.getString(c.getColumnIndex("THRESHOLDS"))));
            return ud;
        }
	}

    // UserDevice methods
	private void setUserDevice(Device device, boolean active) throws DbException {
        synchronized (this) {
            UserDevice ud = getUserDevice(getCurrentUser().getId(), device.getMeasure(), device.getModel());
            if (ud == null) {
                ud = new UserDevice();
                ud.setIdUser(getCurrentUser().getId());
                ud.setMeasure(device.getMeasure());
                ud.setDevice(device);
                ud.setActive(active);
                insertUserDeviceData(ud);
            }
        }
	}

	private void insertUserDeviceData(UserDevice ud) throws DbException {
        synchronized (this) {
            ContentValues values = new ContentValues();
            values.put("ID_USER", getCurrentUser().getId());
            values.put("MEASURE", ud.getMeasure());
            values.put("BTADDRESS", ud.getBtAddress());
            values.put("ID_DEVICE", ud.getDevice().getId());
            values.put("ACTIVE", ud.isActive() ? "S" : "N");

            mDb.insert("USER_DEVICE", null, values);
            logger.log(Level.INFO, "UserDevice inserted: " + ud.toString());
        }
	}

    public UserDevice getUserDevice(String idUser, String measure, String model) throws DbException {
        synchronized (this) {
            UserDevice ret = null;
            Cursor c = null;
            try {
                c = mDb.rawQuery(selectUserDeviceData, new String[]{idUser, measure, model});
                if (c != null) {
                    if (c.moveToFirst()) {
                        ret = getUserDeviceObject(c);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	private UserDevice getUserDeviceObject(Cursor c) {
        synchronized (this) {
            UserDevice ud = new UserDevice();
            ud.setId(c.getInt(c.getColumnIndex("ID")));
            ud.setIdUser(c.getString(c.getColumnIndex("ID_USER")));
            ud.setMeasure(c.getString(c.getColumnIndex("MEASURE")));
            ud.setBtAddress(c.getString(c.getColumnIndex("BTADDRESS")));
            ud.setActive(c.getString(c.getColumnIndex("ACTIVE")).equalsIgnoreCase("S"));
            Integer idDevice = c.getInt(c.getColumnIndex("ID_DEVICE"));
            ud.setDevice(getDevice(idDevice));
            return ud;
        }
	}

	private void deleteOldUserDeviceData(List<String> associatedMeasures) throws SQLException {
        synchronized (this) {
            if (associatedMeasures != null && associatedMeasures.size() > 0) {
                String measureList = "";
                for (String measure : associatedMeasures)
                    measureList = measureList + "'" + measure + "',";

                if (measureList.length() > 0) {
                    measureList = measureList.substring(0, measureList.length() - 1);
                }

                String whereClause = "ID_USER = ? AND MEASURE NOT IN (" + measureList + ")";

                int rows = mDb.delete("USER_DEVICE", whereClause, new String[]{getCurrentUser().getId()});
                logger.log(Level.INFO, "deleteOldUserDeviceData rows deleted: " + rows);
                rows = mDb.delete("USER_MEASURE", whereClause, new String[]{getCurrentUser().getId()});
                logger.log(Level.INFO, "deleteOldUserMeasureData rows deleted: " + rows);
            }
        }
	}

    //////////////////////////////////////////////
    private void alignActiveUserToCurrentDevices() throws DbException {
        synchronized (this) {
            Cursor c = null;
            try {
                c = mDb.query("CURRENT_DEVICE", null, null, null, null, null, null);

                if (c != null) {
                    while(c.moveToNext()){
                        int measureColumnIndex = c.getColumnIndexOrThrow("MEASURE");
                        int idDeviceColumnIndex = c.getColumnIndexOrThrow("ID_DEVICE");
                        int idBtAddressColumnIndex = c.getColumnIndexOrThrow("BTADDRESS");

                        String measure = c.getString(measureColumnIndex);
                        String idDevice = c.getString(idDeviceColumnIndex);
                        String btAddress = c.getString(idBtAddressColumnIndex);

                        //Prima metto tutti i flag a N
                        ContentValues values = new ContentValues();
                        values.put("ACTIVE", "N");
                        mDb.update("USER_DEVICE", values, "MEASURE = ? AND ID_USER = ?", new String[]{measure, ""+currentUser.getId()});

                        //Poi metto il flag a 's' e valorizzo il btAddress per il device corrente
                        values = new ContentValues();
                        values.put("ACTIVE", "S");
                        values.put("BTADDRESS", btAddress);
                        String[] args = new String[]{measure, ""+currentUser.getId(), ""+idDevice};
                        mDb.update("USER_DEVICE", values, "MEASURE = ? AND ID_USER = ? AND ID_DEVICE = ? ", args);
                    }
                }
                logger.log(Level.INFO, "User devices aligned to current devices");
            } catch(Exception e){
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
        }
    }
    /////////////////////////////////////////////

    public List<UserDevice> getCurrentUserDevices() throws DbException {
        synchronized (this) {
            List<UserDevice> ret = new ArrayList<>();
            Cursor c = null;
            try {
                c = mDb.query("USER_DEVICE", null, "ID_USER  = ? ", new String[]{"" + currentUser.getId()}, null, null, "MEASURE");
                if (c != null) {
                    while (c.moveToNext()) {
                        ret.add(getUserDeviceObject(c));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	public List<UserDevice> getCurrentUserDevicesActives(String userId) throws DbException {
        synchronized (this) {
            List<UserDevice> ret = new ArrayList<>();
            Cursor c = null;
            try {
                c = mDb.query("USER_DEVICE", null, "ID_USER  = ? ", new String[]{userId}, null, null, "MEASURE");
                if (c != null) {
                    while (c.moveToNext()) {
                        UserDevice ud = getUserDeviceObject(c);
                        if (ud.isActive())
                            ret.add(ud);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	public List<UserDevice> getModelsForMeasure(String measure, String userId) throws DbException {
        synchronized (this) {//SELECT * from USER_DEVICE where MEASURE = ? AND ID_USER = ?
            List<UserDevice> ret = new ArrayList<>();
            Cursor c = null;
            try {
                c = mDb.query("USER_DEVICE", null, "MEASURE = ? AND ID_USER  = ? ", new String[]{measure, "" + userId}, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        ret.add(getUserDeviceObject(c));
                    }
                }

            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	public void updateUserDeviceModel(String measure, Integer idDevice) throws DbException {
        synchronized (this) {
            //Prima metto tutti i flag a N
            ContentValues values = new ContentValues();
            values.put("ACTIVE", "N");
            mDb.update("USER_DEVICE", values, "MEASURE = ? AND ID_USER = ?", new String[]{measure, "" + currentUser.getId()});

            //Poi se idDevice non è nullo metto il falg a s per il device selezionato
            if (idDevice != null) {
                values = new ContentValues();
                values.put("ACTIVE", "S");
                String[] args = new String[]{measure, "" + currentUser.getId(), "" + idDevice};
                mDb.update("USER_DEVICE", values, "MEASURE = ? AND ID_USER = ? AND ID_DEVICE = ? ", args);
            }

            //In fine aggiorno l'idDevice per la misura sulla tabella current device
            try {
                String btAddress;
                if (idDevice != null) {
                    btAddress = getCurrentDeviceBt(getCurrentUser().getId(), "" + idDevice);
                    storeCurrentDeviceData(measure, "" + idDevice, btAddress);
                }
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}
	
	public List<String> getMeasureTypesForUser() throws DbException {
        synchronized (this) {
            //select distinct measure from user_device where id_user= ?
            List<String> ret = new ArrayList<>();
            Cursor c = null;
            try {
                c = mDb.rawQuery(selectDistinctMeasures, new String[]{"" + currentUser.getId()});
                if (c != null) {
                    while (c.moveToNext()) {
                        ret.add(c.getString(c.getColumnIndex("MEASURE")));
                    }
                }

            } finally {
                if (c != null)
                    c.close();
            }
            return ret;
        }
	}
	
	public void updateBtAddressDevice(UserDevice ud) {
        synchronized (this) {
            Log.i(TAG, "updateBtAddressDevice");
            ContentValues values = new ContentValues();
            values.put("BTADDRESS", ud.getBtAddress());
            String[] args = new String[]{"" + ud.getId()};
            mDb.update("USER_DEVICE", values, "ID = ? ", args);

            // Aggiorno i dati sulla tabella current_device
            try {
                storeCurrentDeviceData(ud.getMeasure(), "" + ud.getDevice().getId(), ud.getBtAddress());
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                sqle.printStackTrace();
            }
        }
	}
	
	public void cleanBtAddressDevice(UserDevice ud) {
        synchronized (this) {
            try {
                Log.i(TAG, "cleanBtAddressDevice");
                ContentValues values = new ContentValues();
                values.putNull("BTADDRESS");
                String[] args = new String[]{"" + ud.getId()};
                mDb.update("USER_DEVICE", values, "ID = ? ", args);
                mDb.delete("CURRENT_DEVICE", "MEASURE = ?", new String[] {ud.getMeasure()});
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
            }
        }
	}

    // MEASURE Methods

    private Measure getMeasureObject(Cursor c) {
        synchronized (this) {
            Measure m = new Measure();

            m.setTimestamp(c.getString(c.getColumnIndex("TIMESTAMP")));
            m.setMeasureType(c.getString(c.getColumnIndex("MEASURE_TYPE")));
            m.setStandardProtocol(c.getInt(c.getColumnIndex("STANDARD")) == 1);
            m.setIdUser(c.getString(c.getColumnIndex("ID_USER")));
            m.setIdPatient(c.getString(c.getColumnIndex("ID_PATIENT")));
            m.setDeviceType(XmlManager.TDeviceType.convertTo(c.getInt(c.getColumnIndex("DEVICE_TYPE"))));
            m.setDeviceDesc(c.getString(c.getColumnIndex("DEVICE_DESC")));
            m.setBtAddress(c.getString(c.getColumnIndex("BTADDRESS")));
            m.setMeasures(Util.jsonToStringMap(c.getString(c.getColumnIndex("MEASURES"))));
            m.setThresholds(Util.jsonToStringMap(c.getString(c.getColumnIndex("THRESHOLDS"))));
            m.setSent(c.getInt(c.getColumnIndex("SENT")) == 1);
            m.setFile(c.getBlob(c.getColumnIndex("FILE")));
            m.setFileType(c.getString(c.getColumnIndex("FILE_TYPE")));
            m.setFailed(c.getInt(c.getColumnIndex("FAILED")) == 1);
            m.setFailureCode(c.getString(c.getColumnIndex("FAILURE_CODE")));
            m.setFailureMessage(c.getString(c.getColumnIndex("FAILURE_MESSAGE")));
            m.setSendFailReason(c.getString(c.getColumnIndex("SEND_FAIL_REASON")));
            m.setSendFailCount(c.getInt(c.getColumnIndex("SEND_FAIL_COUNT")));
            return m;
        }
    }

	/**
	 * Metodo che permette di ottenere le misure associate all'utente in base alla data
	 * @param idUser {@code String} che contiene l'id dell'utente che effettua la ricerca
     * @param dateFrom {@code String} che contiene la data iniziale della ricerca
     * @param dateTo {@code String} che contiene la data finale della ricerca
     * @param measureType {@code String} che contiene il tipo di misura richiesto dall'utente. Null o '' se non utilizzata
	 * @param idPatient {@code String} che contiene l'id del paziente da ricercare. Null o '' se non utilizzata
     * @param failed {@code int} 0 solo misure valide, 1 solo misure fallite, qualsiasi altro valore per non filtrare
	 * @return oggetto di tipo {@code List<Measure>} che contiene la lista delle misure associate all'utente
	 * @throws DbException
	 */
	public List<Measure> getMeasureData(String idUser, String dateFrom, String dateTo, String measureType, String idPatient, int failed) throws DbException {
        synchronized (this) {
            ArrayList<Measure> listaMisure = new ArrayList<>();
            Cursor c = null;

            int n = 1
                    + ((dateFrom == null) || dateFrom.isEmpty()? 0 : 1)
                    + ((dateTo == null) || dateTo.isEmpty()? 0 : 1)
                    + ((idPatient == null) || idPatient.isEmpty()? 0 : 1)
                    + ((measureType == null) || measureType.isEmpty()? 0 : 1);
            String[] values = new String[n];

            String where = "ID_USER = ?";
            values[0] = idUser;

            n = 1;
            if ((dateFrom != null) && !dateFrom.isEmpty()) {
                where = where + " AND TIMESTAMP >= ?";
                values[n++] = dateFrom;
            }
            if ((dateTo != null) && !dateTo.isEmpty()) {
                where = where + " AND TIMESTAMP <= ?";
                values[n++] = dateTo;
            }
            if ((measureType != null) && !measureType.isEmpty()) {
                where = where + " AND MEASURE_TYPE = ?";
                values[n++] = measureType;
            }
            if ((idPatient != null) && !idPatient.isEmpty()) {
                where = where + " AND ID_PATIENT = ?";
                values [n] = idPatient;
            }
            if (failed == 0)
                where = where + " AND FAILED = 0";
            else if (failed == 1)
                where = where + " AND FAILED = 1";

            try {
                c = mDb.query("MEASURE", null, where, values, null, null, "TIMESTAMP");
                if (c != null) {
                    while (c.moveToNext()) {
                        listaMisure.add(getMeasureObject(c));
                    }
                }
            }catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }

            return listaMisure;
        }
	}
	
	public void insertMeasure(Measure measure) throws DbException {
        synchronized (this) {
            try {
                ContentValues values = new ContentValues();

                values.put("TIMESTAMP", measure.getTimestamp());
                values.put("MEASURE_TYPE", measure.getMeasureType());
                values.put("STANDARD", measure.getStandardProtocol()? 1:0);
                values.put("DEVICE_TYPE", XmlManager.TDeviceType.convertFrom(measure.getDeviceType()));
                values.put("DEVICE_DESC", measure.getDeviceDesc());
                values.put("BTADDRESS", measure.getBtAddress());
                values.put("SENT", measure.getSent()? 1:0);
                values.put("FILE", measure.getFile());
                values.put("FILE_TYPE", measure.getFileType());
                values.put("ID_USER", measure.getIdUser());
                values.put("ID_PATIENT", measure.getIdPatient());
                values.put("FAILED", measure.getFailed()? 1:0);
                values.put("FAILURE_CODE", measure.getFailureCode());
                values.put("FAILURE_MESSAGE", measure.getFailureMessage());
                values.put("SEND_FAIL_COUNT", measure.getSendFailCount());
                values.put("SEND_FAIL_REASON", measure.getSendFailReason());
                // if there are measures, add also the corresponding actual thresholds
                HashMap<String,String> map = new HashMap<>();
                if (measure.getMeasures() != null) {
                    if (measure.getThresholds() == null) {
                        UserMeasure um = getUserMeasure(measure.getIdUser(), measure.getMeasureType());
                        if (um != null) {
                            for (Map.Entry<String, String> entry : measure.getMeasures().entrySet())
                                if (um.getThresholds().containsKey(entry.getKey()))
                                    map.put(entry.getKey(), um.getThresholds().get(entry.getKey()));
                        }
                        measure.setThresholds(map);
                    }
                    values.put("MEASURES", new JSONObject(measure.getMeasures()).toString());
                    values.put("THRESHOLDS", new JSONObject(measure.getThresholds()).toString());
                } else{
                    // map is empty
                    values.put("MEASURES", new JSONObject(map).toString());
                    values.put("THRESHOLDS", new JSONObject(map).toString());
                }

                long num = mDb.insert("MEASURE", null, values);
                Log.i(TAG, "Inserite: " + num + " misure");
            } catch (Exception sqle) {
                logger.log(Level.SEVERE, sqle.getMessage());
                Log.i(TAG, "Exception insertMeasure: " + sqle.getMessage());
                sqle.printStackTrace();
                throw new DbException();
            }
        }
	}

    /**
     * Metodo che permette di rimuovere una misura dal database
     * @param idUser l'identificativo dell'utente attuale
     * @param timestamp il timestamp della misura da eliminare
     * @param measureType il tipo di misura della misura da eliminare
     */
    public void deleteMeasure(String idUser, String timestamp, String measureType) throws DbException {
        synchronized (this) {
            try {
                int rows = mDb.delete("MEASURE", "ID_USER = ? AND TIMESTAMP = ? AND MEASURE_TYPE = ?", new String[] {idUser, timestamp, measureType});
                Log.i(TAG, "Eliminate " + rows + " misure dal db");
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            }
        }
    }

	/**
	 * Metodo che permette di rimuovere le misure dal database
	 * @param idUser l'identificativo dell'utente attuale
	 * @param idPatient l'identificativo del paziente. null o "" per non inidcarlo
	 * @param measureType quali tipi di misure devono essere cancellate. null o "" per non indicarla
	 * @throws DbException
	 */
	public void deleteMeasures(String idUser, String idPatient, String measureType) throws DbException {
        synchronized (this) {
            try {
                int n = 1
                        + ((idPatient == null) || idPatient.isEmpty()? 0 : 1)
                        + ((measureType == null) || measureType.isEmpty()? 0 : 1);
                String[] values = new String[n];

                String where = "ID_USER = ?";
                values[0] = idUser;

                n = 1;
                if ((idPatient != null) && !idPatient.isEmpty()) {
                    where = where + " AND ID_PATIENT = ?";
                    values [n++] = idPatient;
                }
                if ((measureType != null) && !measureType.isEmpty()) {
                    where = where + " AND MEASURE_TYPE = ?";
                    values[n] = measureType;
                }
                int rows = mDb.delete("MEASURE", where, values);
                Log.i(TAG, "Eliminate " + rows + " misure dal db");
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            }
        }
	}

	/**
	 * Metodo che permette di aggiornare i valori di una misura all'interno del db
	 * @param m variabile di tipo {@code Measure} che contiene i valori della misura da aggiornare
	 * @throws DbException
	 */
	public void updateSentMeasure(Measure m) throws DbException{
        synchronized (this) {
            try {
                ContentValues values = new ContentValues();
                if (m.getSent()) {
                    values.put("SENT", 1);
                } else {
                    values.put("SENT", 0);
                    values.put("SEND_FAIL_TIMESTAMP", System.currentTimeMillis()/1000);
                    values.put("SEND_FAIL_COUNT", m.getSendFailCount());
                    values.put("SEND_FAIL_REASON", m.getSendFailReason());
                }
                mDb.update("MEASURE", values, "ID_USER = ? AND TIMESTAMP = ? AND MEASURE_TYPE = ?", new String[]{m.getIdUser(), m.getTimestamp(), m.getMeasureType()});
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            }
        }
	}

	/**
	 * Metodo che permette di ottenere le misure che non sono ancora state inviate alla piattaforma
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @return ArrayList<Measure>
	 */
	public ArrayList<Measure> getNotSentMeasures(String idUser) throws DbException{
        synchronized (this) {
            ArrayList<Measure> listaMisure = new ArrayList<>();
            Cursor c = null;
            try {
                //Devono essere restituite tutte le misure non ancora inviate ordinate per TIMESTAMP crescente
                c = mDb.query("MEASURE", null, "ID_USER = ? AND SENT = 0 AND SEND_FAIL_COUNT < ? AND SEND_FAIL_TIMESTAMP < ?",
                        new String[]{idUser,
                                String.valueOf(GWConst.MAX_SEND_RETRY),
                                String.valueOf(System.currentTimeMillis()/1000-GWConst.SEND_RETRY_TIMEOUT)
                        },
                        null, null, "TIMESTAMP");
                if (c != null) {
                    while (c.moveToNext()) {
                        listaMisure.add(getMeasureObject(c));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                throw new DbException();
            } finally {
                if (c != null)
                    c.close();
            }
            return listaMisure;
        }
	}

    public Patient getPatientData(String idPatient) {
        synchronized (this) {
            Patient ret = null;
            Cursor c;

            c = mDb.query("PATIENT", null, "ID = ?", new String[]{idPatient}, null, null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    ret = getPatientObject(c);
                }
                c.close();
            }

            return ret;
        }
    }
	
	/**
	 * Metodo che permette di inserire un certificato all'interno della tabella {@code CERTIFICATES}
	 * @param sc oggetto di tipo {@code ServerCertificate} che contiene il certificato del server da accettare
	 * @throws DbException
	 */
	public void insertServerCertificate(String idUser, ServerCertificate sc) throws DbException {
		//Inserisco il certificato del server nella tabella
        synchronized (this) {
            try {
                Log.i(TAG, "insertServerCertificate: aggiungo certificato " + idUser);
                ContentValues values = new ContentValues();
                values.put("ID_USER", idUser);
                values.put("HOSTNAME", " ");
                values.put("PUBLIC_KEY", sc.getPublicKey());
                long columnid = mDb.insert("CERTIFICATES", null, values);
                logger.log(Level.INFO, "Inserimento del certificato in posizione " + columnid);
                Log.i(TAG, "Certificato inserito in posizione " + columnid);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "insertServerCertificate: " + e.getMessage());
                throw new DbException();
            }
        }
	}
	
	/**
	 * Metodo che permette di eliminare tutti i certificati associati ad un utente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @throws DbException
	 */
	public void deleteAllServerCerts(String idUser) throws DbException {
        synchronized (this) {
            Log.i(TAG, "Elimino tutti i certificati di " + idUser);
            try {
                int rows = mDb.delete("CERTIFICATES", "ID_USER = ?", new String[] {idUser});
                Log.i(TAG, "Numero di certificati di " + idUser + " eliminati: "  + rows);
            } catch (Exception e) {
                e.printStackTrace();
                logger.log(Level.SEVERE, e.getMessage());
                Log.e(TAG, "deleteAllServerCerts: " + e.getMessage());
                throw new DbException();
            }
        }
	}
	
	/**
	 * Metodo che permette di verificare se l'utente corrente è un paziente
	 * @param idUser variabile di tipo {@code String} che contiene l'identificatore dell'utente
	 * @return variabile di tipo {@code boolean} che indica se l'utente è un paziente o meno
	 */
	public boolean getIsPatient(String idUser) {
        synchronized (this) {
            boolean status;
            Cursor c = mDb.query("USER", new String[]{"IS_PATIENT"}, "ID = ?", new String[]{idUser}, null, null, null);
            c.moveToFirst();
            status = c.getInt(c.getColumnIndex("IS_PATIENT")) == 1;
            c.close();
            return status;
        }
	}
}