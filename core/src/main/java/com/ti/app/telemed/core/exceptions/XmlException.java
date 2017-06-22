package com.ti.app.telemed.core.exceptions;

import com.ti.app.telemed.core.ResourceManager;

public class XmlException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String msg;

	public XmlException() {
		super();
		this.msg = ResourceManager.getResource().getString("errorXml");
	}

	public XmlException(String message) {
		super(message);
	}

	@Override
	public String getMessage() {
		if (msg != null) {
			return msg;
		} else {
			return super.getMessage();
		}
	}

}
