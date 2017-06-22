package com.ti.app.telemed.core.common;

public class ServerConf {
	private String ip;
	private String protocol;
	private String port;
	private String targetCfg;
	private String targetSend;
	private String ipDef;
	private String protocolDef;
	private String portDef;
	private String targetCfgDef;
	private String targetSendDef;

	public ServerConf(String ip, String protocol, String port, String targetCfg, String targetSend) {
		this.ipDef = ip;
		this.protocolDef = protocol;
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

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
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

	public String getProtocolDef() {
		return protocolDef;
	}

	public void setProtocolDef(String protocolDef) {
		this.protocolDef = protocolDef;
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
					&& sc.getProtocol().equalsIgnoreCase(this.protocol)
					&& sc.getPort().equalsIgnoreCase(this.port)
					&& sc.getTargetCfg().equalsIgnoreCase(this.targetCfg)
					&& sc.getTargetSend().equalsIgnoreCase(this.targetSend);
		} else {
			return false;
		}
	}

}
