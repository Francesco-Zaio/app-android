package com.ti.app.telemed.core.common;

public class ServerConf {
	private String ip;
	private String port;
	private String targetCfg;
	private String targetSend;

	public ServerConf() {
	}

	public ServerConf(String ip, String port, String targetCfg, String targetSend) {
		this.ip = ip;
		this.port = port;
		this.targetCfg = targetCfg;
		this.targetSend = targetSend;
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
