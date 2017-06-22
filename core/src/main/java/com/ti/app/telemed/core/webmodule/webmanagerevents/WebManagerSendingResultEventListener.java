package com.ti.app.telemed.core.webmodule.webmanagerevents;

import java.util.EventListener;

import javax.security.cert.X509Certificate;

import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;


public interface WebManagerSendingResultEventListener extends EventListener {
	public void sendingMeasureSucceeded(WebManagerSendingResultEvent evt);
	public void webAuthenticationFailed(WebManagerSendingResultEvent evt);
	public void webOperationFailed(WebManagerSendingResultEvent evt, XmlErrorCode code);
}
