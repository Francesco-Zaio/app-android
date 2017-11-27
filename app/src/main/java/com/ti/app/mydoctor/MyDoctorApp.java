package com.ti.app.mydoctor;

import com.ti.app.mydoctor.devicemodule.DeviceOperations;
import com.ti.app.telemed.core.MyApp;

public class MyDoctorApp extends MyApp {
	
	private static DeviceOperations deviceOperations;

    public static DeviceOperations getDeviceOperations(){
    	if(deviceOperations == null){
    		deviceOperations = new DeviceOperations();
    	}
    	return deviceOperations;
    }
}
