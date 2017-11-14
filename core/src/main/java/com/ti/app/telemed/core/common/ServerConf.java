package com.ti.app.telemed.core.common;

public class ServerConf {
	private String ip;
	private String port;
	private String targetCfg;
	private String targetSend;
	private String ipDef;
	private String portDef;
	private String targetCfgDef;
	private String targetSendDef;

	public ServerConf(String ip, String port, String targetCfg, String targetSend) {
		this.ipDef = ip;
		this.portDef = port;
		this.targetCfgDef = targetCfg;
		this.targetSendDef = targetSend;
	}

	public ServerConf() {
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getTargetCfg() {
		return targetCfg;
	}

	public void setTargetCfg(String targetCfg) {
		this.targetCfg = targetCfg;
	}

	public String getTargetSend() {
		return targetSend;
	}

	public void setTargetSend(String targetSend) {
		this.targetSend = targetSend;
	}

	public String getIpDef() {
		return ipDef;
	}

	public void setIpDef(String ipDef) {
		this.ipDef = ipDef;
	}

	public String getPortDef() {
		return portDef;
	}

	public void setPortDef(String portDef) {
		this.portDef = portDef;
	}

	public String getTargetCfgDef() {
		return targetCfgDef;
	}

	public void setTargetCfgDef(String targetCfgDef) {
		this.targetCfgDef = targetCfgDef;
	}

	public String getTargetSendDef() {
		return targetSendDef;
	}

	public void setTargetSendDef(String targetSendDef) {
		this.targetSendDef = targetSendDef;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof ServerConf){
			ServerConf sc = (ServerConf)obj;
			return sc.getIp().equalsIgnoreCase(this.ip)
					&& sc.getPort().equalsIgnoreCase(this.port)
					&& sc.getTargetCfg().equalsIgnoreCase(this.targetCfg)
					&& sc.getTargetSend().equalsIgnoreCase(this.targetSend);
		} else {
			return false;
		}
	}

}
