package com.ti.app.telemed.core.common;

public class MeasureProtocolCfg {
	private int updateInterval; //intervallo agg.to cfg in minuti
	private String silentStart; //inizio intervallo senza invio notifiche
	private String silentEnd; //fine intervallo senza invio notifiche
	private int lateRemindDelay;  //Ritardo in minuti dopo cui una misura non eseguita genera una notifica Android
	private int lateMaxReminds; //nr giorni max consecutivi di invio reminders
	private int standardProtocolTimeOut; // timeout in minuti a partire dallo schedule per cui è diponibile il protocollo standard
	private String standardProtocolReminds; // Lista timeout (in minuti) per invio reminds di esescuzione protocollo standard

	public int getUpdateInterval() {
		return updateInterval;
	}

	public void setUpdateInterval(int updateInterval) {
		this.updateInterval = updateInterval;
	}

	public String getSilentStart() {
		return silentStart;
	}

	public void setSilentStart(String silentStart) {
		this.silentStart = silentStart;
	}

	public String getSilentEnd() {
		return silentEnd;
	}

	public void setSilentEnd(String silentEnd) {
		this.silentEnd = silentEnd;
	}

	public int getLateRemindDelay() {
		return lateRemindDelay;
	}

	public void setLateRemindDelay(int lateRemindDelay) {
		this.lateRemindDelay = lateRemindDelay;
	}

	public int getLateMaxReminds() {
		return lateMaxReminds;
	}

	public void setLateMaxReminds(int lateMaxReminds) {
		this.lateMaxReminds = lateMaxReminds;
	}

	public int getStandardProtocolTimeOut() {
		return standardProtocolTimeOut;
	}

	public void setStandardProtocolTimeOut(int standardProtocolTimeOut) {
		this.standardProtocolTimeOut = standardProtocolTimeOut;
	}

	public String getStandardProtocolReminds() {
		return standardProtocolReminds;
	}

	public void setStandardProtocolReminds(String standardProtocolReminds) {
		this.standardProtocolReminds = standardProtocolReminds;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof MeasureProtocolCfg){
			MeasureProtocolCfg sc = (MeasureProtocolCfg)obj;
			return sc.getUpdateInterval() == (this.updateInterval)
					&& sc.getSilentStart().equalsIgnoreCase(this.silentStart)
					&& sc.getSilentEnd().equalsIgnoreCase(this.silentEnd)
					&& sc.getLateRemindDelay() == (this.lateRemindDelay)
					&& sc.getLateMaxReminds() == (this.lateMaxReminds)
					&& sc.getStandardProtocolTimeOut() == (this.standardProtocolTimeOut)
					&& sc.getStandardProtocolReminds().equalsIgnoreCase(this.standardProtocolReminds);
		} else {
			return false;
		}
	}
}
