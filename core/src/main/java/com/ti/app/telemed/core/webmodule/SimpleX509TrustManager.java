package com.ti.app.telemed.core.webmodule;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class SimpleX509TrustManager implements X509TrustManager{

	private static TrustManager[] trustManagers;
	private static final X509Certificate[] _AcceptedIssuers = new
	X509Certificate[] {};

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String
			authType) throws CertificateException {
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String
			authType) throws CertificateException {
	}

	public boolean isClientTrusted(X509Certificate[] chain) {
		return true;
	}

	public boolean isServerTrusted(X509Certificate[] chain) {
		return true;
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return _AcceptedIssuers;
	}
	
	public static final String INSTALL_ACTION = "android.credentials.INSTALL";

	public static void allowAllSSL() {
		
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier()
		{
			@Override
			public boolean verify(String hostname, SSLSession session) {
				//Tutti i certificati vengono abilitati
				return true;
			}
		});

		SSLContext context = null;
		if (trustManagers == null) {
			trustManagers = new TrustManager[] { new SimpleX509TrustManager() };
		}

		try {
			context = SSLContext.getInstance("TLS");
			context.init(null, trustManagers, new SecureRandom());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}

		HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
	}

	public static void stopAllowAllSSL() {
		
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier()
		{
			@Override
			public boolean verify(String hostname, SSLSession session) {
				//Disabilito tutti i certificati
				return false;
			}
		});

		SSLContext context = null;
		if (trustManagers == null) {
			trustManagers = new TrustManager[] { new SimpleX509TrustManager() };
		}

		try {
			context = SSLContext.getInstance("TLS");
			context.init(null, trustManagers, new SecureRandom());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}

		HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
	}
	
	/**
	 * Metodo che effettua il confronto byte per byte delle chiavi pubbliche
	 * @param array1 
	 * @param array2
	 * @return
	 */
	private static boolean confronta(byte[] array1, byte[] array2) {
		if(array1.length != array2.length)
			return false;
		
		for(int i = 0; i < array1.length; i++) {
			if(array1[i] != array2[i]) {
				return false;
			}
		}
		
		return true;
	}
} 