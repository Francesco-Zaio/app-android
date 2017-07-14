package com.ti.app.telemed.core.common;

import java.io.Serializable;

public class User implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String id; // id utente interno che è anche primary key sul DB
	private String cf; // codice fiscale
	private String name;
	private String surname;
	private long timestamp;
	private String login;
	private String password;
	private boolean hasAutoLogin;
	private boolean isPatient; // Indica se l'utente è anche un paziente
	
	private boolean active;
	private boolean blocked;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSurname() {
		return surname;
	}

	public void setSurname(String surname) {
		this.surname = surname;
	}

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean getHasAutoLogin() {
		return this.hasAutoLogin;
	}

	public void setHasAutoLogin(boolean hasAutoLogin) {
		this.hasAutoLogin = hasAutoLogin;
	}
	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public boolean getIsPatient() {
		return this.isPatient;
	}
	
	public void setIsPatient(boolean isPatient) {
		this.isPatient = isPatient;
	}

	public void setCf(String cf) {
		this.cf = cf;
	}

	public String getCf() {
		return cf;
	}
}
