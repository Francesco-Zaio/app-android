package com.ti.app.telemed.core.exceptions;

public class DbException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String msg = "DBError";

	public DbException() {
		super();
	}

	public DbException(String message) {
		super(message);
		this.msg = message;
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
