package com.ti.app.telemed.core.exceptions;

public class XmlException extends Exception {

	private static final long serialVersionUID = 1L;
	private XmlException() {
	}

	public XmlException(String message) {
		super(message);
	}
}
