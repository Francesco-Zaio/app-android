package com.viatom.checkmelib.bluetooth;


import com.viatom.checkmelib.utils.CRCUtils;
import com.viatom.checkmelib.utils.LogUtils;

public class GetInfoAckPkg {
	private byte cmd;
	private char[] dataBuf;
	
	public GetInfoAckPkg(byte[] buf) {
		if(buf.length!=BTConstant.GET_INFO_ACK_PKG_LENGTH){
			LogUtils.d("GetInfoAckPkg length error");
			return;
		}
		if(buf[0]!=(byte)0x55){
			LogUtils.d("GetInfoAckPkg head error");
			return;
		}else if ((cmd = buf[1]) != BTConstant.ACK_CMD_OK || buf[2] != ~BTConstant.ACK_CMD_OK) {
			LogUtils.d("GetInfoAckPkg cmd word error");
			return;
		}else if (buf[buf.length-1] != CRCUtils.calCRC8(buf)) {
			LogUtils.d("GetInfoAckPkg CRC error");
			return;
		}
		
		dataBuf = new char[BTConstant.GET_INFO_ACK_PKG_LENGTH - 8];//256
		for (int i = 0; i < dataBuf.length; i++) {
			dataBuf[i] = (char)buf[i+7];
		}
	}

	public byte getCmd() {
		return cmd;
	}

	public String getDataBufStr() {
		return String.valueOf(dataBuf);
	}

	
}
