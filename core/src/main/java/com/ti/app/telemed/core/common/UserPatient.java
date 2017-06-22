package com.ti.app.telemed.core.common;

public class UserPatient implements Cloneable {
	private Integer id;
	private String idUser;
	private String idPatient;
	
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getIdUser() {
		return idUser;
	}

	public void setIdUser(String idUser) {
		this.idUser = idUser;
	}

	public String getIdPatient() {
		return idPatient;
	}

	public void setIdPatient(String idPatient) {
		this.idPatient = idPatient;
	}

}
