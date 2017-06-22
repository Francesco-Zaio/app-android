package com.ti.app.telemed.core.common;

public class ServerCertificate {

	private String hostName;
	private byte[] publicKey;
	
	public String getHostName() {
		return this.hostName;
	}
	
	public void setHostname(String hostName) {
		this.hostName = hostName;
	}
	
	public byte[] getPublicKey() {
		return this.publicKey;
	}
	
	public void setPublicKey(byte[] publicKey) {
		this.publicKey = publicKey;
	}
}
