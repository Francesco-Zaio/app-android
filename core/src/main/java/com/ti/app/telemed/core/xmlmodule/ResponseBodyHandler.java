package com.ti.app.telemed.core.xmlmodule;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ResponseBodyHandler extends DefaultHandler{
	
	private ParsedResponseHandlerDataSet myParsedResponseHandlerDataSet;
	private boolean in_mytag = false;

	// ===========================================================
	// Methods
	// ===========================================================

	@Override
	public void startDocument() throws SAXException {
		this.myParsedResponseHandlerDataSet = new ParsedResponseHandlerDataSet();
	}

	@Override
	public void endDocument() throws SAXException {
		// Nothing to do
	}

	/** Gets be called on opening tags like:
	 * <tag>
	 * Can provide attribute(s), when xml was like:
	 * <tag attribute="attributeValue">*/

	@Override
	public void startElement(String namespaceURI, String localName,
			String qName, Attributes atts) throws SAXException {

		if (localName.equals("Response")) {
			this.in_mytag = true;
			String resultCode = atts.getValue("resultCode");
			String resultMessage = atts.getValue("message");
			
			myParsedResponseHandlerDataSet.setExtractedInt(Integer.parseInt(resultCode));
			myParsedResponseHandlerDataSet.setExtractedString(resultMessage);
		}
	}

	/** Gets be called on closing tags like:
	 * </tag> */
	@Override
	public void endElement(String namespaceURI, String localName, String qName)
	throws SAXException {
		if (localName.equals("Response")) {
			this.in_mytag = false;
		}
	}

	/** Gets be called on the following structure:
	 * <tag>characters</tag> */
	@Override
	public void characters(char ch[], int start, int length) {
		if(this.in_mytag){
			myParsedResponseHandlerDataSet.setExtractedString(new String(ch, start, length));
		}
	}

	public ParsedResponseHandlerDataSet getParsedData() {
		return this.myParsedResponseHandlerDataSet;
	}
}
