package com.ti.app.mydoctor.util;

import java.text.DecimalFormat;

import android.util.Log;

public class PedometerCalculator {
	
	public static final float K_WOMAN_DISTANCE = 0.413F;
	public static final float K_MAN_DISTANCE = 0.415F;
	
	public static final float K_SPEED_COEF32 = 2.5f;
	public static final float K_SPEED = 3.2f;
	
	private static final String TAG = "PedometerCalculator";

	private int steps;
	private boolean isMan;
	private float heightMeter;
	private float weightKg;
	
	private DecimalFormat df = null;
	private int kcal;
	private long msTimeActivity;
	private float distanceMeter;

	public PedometerCalculator(int steps, boolean isMan, float heightMeter, float weightKg) {
		
		this.steps = steps;
		this.isMan = isMan;
		this.heightMeter = heightMeter;
		this.weightKg = weightKg;
		
		df = new DecimalFormat("#.##");
		
		calculate();
	}
	
	/*

	- Calcolo distanza percorsa  (in km)

	  UOMO:  (altezza in metri) x 0,415 x (numero passi) = d
	  DONNA: (altezza in metri) x 0,413 x (numero passi) = d
	
	- Calcolo calorie consumate (utilizzando velocità a 3.2 km/h):
	
	  Calorie bruciate = (peso in kg)x2,5x((distanza in km)/3.2)
	
	  Quindi se supponiamo che un uomo di 1,78m e 76kg di peso fa 4000 passi otteniamo:
	
	  d = 1,78x0,415x4000 = 2,954 km
	  
	  Calorie = 76x2,5x(2,954/3,2) = 175,39
	  
	 */

	private void calculate() {
		
		Log.d(TAG, "calculate()");
		
		distanceMeter = 0.0F;
		
		if (isMan)
			distanceMeter = K_MAN_DISTANCE * heightMeter * steps;
		else
			distanceMeter = K_WOMAN_DISTANCE * heightMeter * steps;
		
		float timeActivity = distanceMeter / 1000 / K_SPEED;
		kcal = (int)(weightKg * K_SPEED_COEF32 * timeActivity);
				
		msTimeActivity = (long)(timeActivity*60*60*1000);			
	}
	
	public String getTimeElapseLabel() {
		
		return TimeUtil.formatMillis(msTimeActivity);
	}

	public String getKcalLabel() {
		
		return Integer.toString(kcal);
	}

	public String getHeightMeterLabel() {

		return df.format(heightMeter);
	}
	
	public String getHeightCentimeterLabel() {

		return Integer.toString((int)(heightMeter*100));
	}

	public String getWeightKgLabel() {
		
		return df.format(weightKg);
	}

	public String getDistanceMeterLabel() {
		
		return df.format(distanceMeter);
	}
	
	public String getDistanceKmLabel() {
		
		return df.format(distanceMeter/1000);
	}

	public String getStepsLabel() {
		
		return Integer.toString(steps);
	}

	public int getKcal() {
		
		return kcal;
	}

	public int getDistanceMeter() {
		
		return (int)distanceMeter;
	}

	public int getDurationMs() {
		
		return (int)msTimeActivity;
	}

}
