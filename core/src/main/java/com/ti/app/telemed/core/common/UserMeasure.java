package com.ti.app.telemed.core.common;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.ti.app.telemed.core.util.CrontabManager;

public class UserMeasure implements Cloneable {

	private Integer id;
	private String idUser;
	private String measure;

	private String schedule;        // stringa in formato cron UNIX che indica lo scheduling della misura
    private Map<String,String> thresholds = new HashMap<>(); // elenco delle soglie da applicare alle diverse misure

	private boolean outOfRange;	    // flag di stato Out of Range
	private Date lastDay;		    // Data dell'ultima misura effettuata
	private int nrLastDay;		    // contatore misure effettuate nel 'lastDay'

	public UserMeasure() {
		outOfRange = false;
		lastDay = new Date(0);
		nrLastDay = 0;
        schedule = "";
        measure = "";
        idUser = "";
	}

	public Integer getId() {
		return id;
	}
	public void setId(Integer id) {
		this.id = id;
	}

	public String getIdUser() {
		return idUser;
	}
	public void setIdUser(String idUser) {
		this.idUser = idUser;
	}

	public String getMeasure() {
		return measure;
	}
	public void setMeasure(String measure) {
		this.measure = measure;
	}

	public boolean isOutOfRange() {
		return outOfRange;
	}
	public void setOutOfRange(boolean outOfRange) {
		this.outOfRange = outOfRange;
	}

	public Date getLastDay() {
		return lastDay;
	}
	public void setLastDay(Date lastDay) {
		this.lastDay = lastDay;
	}

	public int getNrLastDay() {
		return nrLastDay;
	}
	public void setNrLastDay(int nrLastDay) {
		this.nrLastDay = nrLastDay;
	}

	public String getSchedule() {
		return schedule;
	}
	public void setSchedule(String schedule) {
		this.schedule = schedule;
	}

    public Map<String,String> getThresholds() {
        return thresholds;
    }
    public void setThresholds(Map<String,String> thresholds) {
        this.thresholds = thresholds;
    }

    public  List<Date> getTodaySchedule ()  {
        if (!schedule.isEmpty()) {
            CrontabManager.parse(schedule);
            return CrontabManager.getTodayEvents();
        } else
            return new Vector<>();
    }

    public  List<Date> getScheduleOfDay (Calendar cal)  {
        if (!schedule.isEmpty()) {
            CrontabManager.parse(schedule);
            return CrontabManager.getEventsOfDay(cal);
        } else
            return new Vector<>();
    }

	@Override
	public Object clone() {
		try {
			super.clone();
			UserMeasure newDevice = new UserMeasure();
			newDevice.setId(this.id);
			newDevice.setMeasure(this.measure);
			newDevice.setIdUser(this.idUser);
			newDevice.setSchedule(this.schedule);
			newDevice.setThresholds(this.thresholds);
			newDevice.setOutOfRange(this.outOfRange);
			newDevice.setLastDay(this.lastDay);
			newDevice.setNrLastDay(this.nrLastDay);
			return newDevice;
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String toString() {
		return idUser + " - " + measure;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UserMeasure && equalsMeasureModel(obj);
	}

	private boolean equalsMeasureModel(Object obj) {
		if (obj instanceof UserMeasure) {
			UserMeasure pd = (UserMeasure) obj;
			return pd.getMeasure() != null && pd.getIdUser() != null && pd.getMeasure().equalsIgnoreCase(measure)
						&& pd.getIdUser().equalsIgnoreCase(idUser);
		} else {
			return false;
		}
	}
}
