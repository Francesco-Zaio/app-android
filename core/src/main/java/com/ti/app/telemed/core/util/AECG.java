package com.ti.app.telemed.core.util;

import android.content.Context;
import android.util.Log;

import com.ti.app.telemed.core.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class AECG {

    private static final String TAG = "AECG";

    private Context ctx;
    private ArrayList<short[]> signalData = new ArrayList<>();
    private ArrayList<String> signalName = new ArrayList<>();
    private ArrayList<String> signalOrigin = new ArrayList<>();
    private ArrayList<String> signalScale = new ArrayList<>();
    private String aECGTemplateStart;
    private String aECGTemplateEnd;
    private String aECGLeadStart;
    private String aECGLeadEnd;

    // sampling rate is in Hz
    public AECG(Context ctx, Date date, int samplingRate) {
        this.ctx = ctx;
        String time = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(date);
        float f = 1f / samplingRate;
        String increment;
        if(f == (long) f)
            increment = String.format(Locale.ENGLISH,"%d",(long)f);
        else
            increment = String.format("%s",f);
        aECGTemplateStart = loadTemplate(R.raw.aecg_start).replaceAll("@TIME", time).replaceAll("@INCREMENT", increment);
        aECGTemplateEnd = loadTemplate(R.raw.aecg_end);
        aECGLeadStart = loadTemplate(R.raw.aecg_lead_start);
        aECGLeadEnd = loadTemplate(R.raw.aecg_lead_end);
    }

    // values, origin, scale in uV
    // values and origin are signed
    public void addLead(String leadId, short[] values, float origin, float scale) {
        signalName.add(leadId);
        signalData.add(values);
        if(origin == (long)origin)
            signalOrigin.add(String.format(Locale.ENGLISH,"%d",(long)origin));
        else
            signalOrigin.add(String.format("%s",origin));
        if(scale == (long)scale)
            signalScale.add(String.format(Locale.ENGLISH,"%d",(long)scale));
        else
            signalScale.add(String.format("%s",scale));
    }

    // save the data to a file
    public String saveFile(File f) {
        try {
            PrintWriter out = new PrintWriter(f);
            out.print(aECGTemplateStart);
            for (int i=0; i<signalData.size(); i++) {
                out.print(aECGLeadStart.replaceAll("@LEAD",signalName.get(i)).replaceAll("@ORIGIN", signalOrigin.get(i)).replaceAll("@SCALE", signalScale.get(i)));
                short[] data = signalData.get(i);
                for (int j=0; j<data.length;j++) {
                    out.print(data[j] + " ");
                }
                out.print(aECGLeadEnd);
            }
            out.print(aECGTemplateEnd);
            out.close();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        }
        return f.getAbsolutePath();
    }

    private String  loadTemplate(int id) {
        InputStream inputStream = ctx.getResources().openRawResource(id);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder text = new StringBuilder();
        String line;
        try {
            while (( line = reader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            return text.toString();
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }
}
