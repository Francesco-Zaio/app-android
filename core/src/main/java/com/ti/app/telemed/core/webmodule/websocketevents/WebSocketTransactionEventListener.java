package com.ti.app.telemed.core.webmodule.websocketevents;

import java.util.EventListener;

import javax.security.cert.X509Certificate;

public interface WebSocketTransactionEventListener extends EventListener {
	public void transactionSucceeded(WebSocketTransactionEvent evt, int responseCode, String responseBody);
	public void transactionFailed(WebSocketTransactionEvent evt,int responseCode);
}
