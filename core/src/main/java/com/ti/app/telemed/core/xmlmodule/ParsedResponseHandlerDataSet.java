package com.ti.app.telemed.core.xmlmodule;

public class ParsedResponseHandlerDataSet {

	private String extractedString = null;
	private int extractedInt = 0;

	public String getExtractedString() {
		return extractedString;
	}
	public void setExtractedString(String extractedString) {
		this.extractedString = extractedString;
	}

	public int getExtractedInt() {
		return extractedInt;
	}
	public void setExtractedInt(int extractedInt) {
		this.extractedInt = extractedInt;
	}

	@Override
	public String toString(){
		return "ExtractedString = " + this.extractedString
		+ "nExtractedInt = " + this.extractedInt;
	}

}
