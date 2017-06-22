package com.ti.app.telemed.core.webmodule.webmanagerevents;

import java.util.EventListener;

import javax.security.cert.X509Certificate;

import com.ti.app.telemed.core.xmlmodule.XmlManager.XmlErrorCode;


public interface WebManagerResultEventListener extends EventListener {
	public void webAuthenticationSucceeded(WebManagerResultEvent evt);
	public void webAuthenticationFailed(WebManagerResultEvent evt);
	public void webChangePasswordSucceded(WebManagerResultEvent evt);
	public void webOperationFailed(WebManagerResultEvent evt, XmlErrorCode code);
}
