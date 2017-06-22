package com.ti.app.mydoctor.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {

	static public String formatMillis(long val) {
	    StringBuilder                       buf=new StringBuilder(20);
	    String                              sgn="";

	    if(val<0) { sgn="-"; val=Math.abs(val); }

	    append(buf,sgn,0,( val/3600000             ));
	    append(buf,"h ",2,((val%3600000)/60000      ));
	    append(buf,"m ",2,((val         %60000)/1000));
	    buf.append("s");
	    
	    //append(buf,".",3,( val                %1000));
	    return buf.toString();
	    }

	/** Append a right-aligned and zero-padded numeric value to a `StringBuilder`. */
	static private void append(StringBuilder tgt, String pfx, int dgt, long val) {
	    tgt.append(pfx);
	    if(dgt>1) {
	        int pad=(dgt-1);
	        for(long xa=val; xa>9 && pad>0; xa/=10) { pad--;           }
	        for(int  xa=0;   xa<pad;        xa++  ) { tgt.append('0'); }
	        }
	    tgt.append(val);
	    }

	public static String getCurrentTimeId() {
		
		return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
	}
}
