package com.ti.app.telemed.core.common;

import java.util.ArrayList;
import java.util.List;

public class ECGDrawData {
    public static int nLead;
    public static int samplingRate; // Hz
    public static String[] lables;
    public static int maxVal;
    public static int baseline;
    public static int progress;

    private static final List<int[]> ecgData = new ArrayList<>();

    public static void addData(List<int[]> d) {
        synchronized (ecgData) {
            ecgData.addAll(d);
        }
    }

    public static void clearData() {
        synchronized (ecgData) {
            ecgData.clear();
        }
    }

    public static int[] popData() {
        if (ecgData.size() > 0)
            synchronized (ecgData) {
                return ecgData.remove(0);
        }
        return null;
    }

    public static int size() {
        synchronized (ecgData) {
            return ecgData.size();
        }
    }
}
