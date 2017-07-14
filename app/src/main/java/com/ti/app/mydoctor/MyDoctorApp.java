package com.ti.app.mydoctor;

import com.ti.app.mydoctor.devicemodule.DeviceManager;
import com.ti.app.telemed.core.MyApp;

public class MyDoctorApp extends MyApp {
	
	private static DeviceManager deviceManager;

    public static DeviceManager getDeviceManager(){
    	if(deviceManager == null){
    		deviceManager = new DeviceManager();
    	}
    	return deviceManager;
    }
}
