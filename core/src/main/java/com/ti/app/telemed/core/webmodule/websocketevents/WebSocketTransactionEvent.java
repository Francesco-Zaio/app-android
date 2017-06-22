package com.ti.app.telemed.core.webmodule.websocketevents;

import java.util.EventObject;

import com.ti.app.telemed.core.webmodule.WebSocket;
import com.ti.app.telemed.core.xmlmodule.XmlManager;


public class WebSocketTransactionEvent extends EventObject {
	private XmlManager.XmlErrorCode _resultCode;

	public WebSocketTransactionEvent(WebSocket source) {
		super(source);
        _resultCode = XmlManager.XmlErrorCode.COMMAND_SUCCESSFULLY_EXEC;
	}

	public WebSocketTransactionEvent(WebSocket source, XmlManager.XmlErrorCode resultCode) {
		super(source);
		_resultCode = resultCode;
	}

	public XmlManager.XmlErrorCode resultCode() { return _resultCode; }
}
