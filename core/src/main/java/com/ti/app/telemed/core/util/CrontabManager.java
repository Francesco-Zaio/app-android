package com.ti.app.telemed.core.util;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

// import android.util.Log;

public class CrontabManager {

    // * * * * * * *
    // - - - - - - -
    // | | | | | | |
    // | | | | | | |
    // | | | | | | +--- year (ignored, always *)
    // | | | | | +----- day of week (0 - 6) (Sunday=0)
    // | | | | +------- month (1 - 12)
    // | | | +--------- day of month (1 - 31)
    // | | +----------- hour (0 - 23)
    // | +------------- minute (0 - 59)
    // +--------------- second (ignored, always 0)

    static final protected int MINUTESPERHOUR = 60;
    static final protected int HOURESPERDAY = 24;
    static final protected int DAYSPERWEEK = 7;
    static final protected int MONTHSPERYEAR = 12;
    static final protected int DAYSPERMONTH = 31;

    private static HashMap<String, String> Minutes = new HashMap<>();
    private static HashMap<String, String> Hours = new HashMap<>();
    private static HashMap<String, String> DaysInMonth = new HashMap<>();
    private static HashMap<String, String> Month = new HashMap<>();
    private static HashMap<String, String> DaysInWeek = new HashMap<>();
    private static String configLine = "";

    private static HashMap<String, Object[]> DataCache = null;
    //private static String TAG = "CrontabManager";

    @SuppressWarnings("unchecked")
    public static void parse(String line) {
        configLine = line;
        String[] params = configLine.split(" ");
        // Log.d(TAG , "parse: " + line);

        if (DataCache == null) {
            DataCache = new HashMap<String, Object[]>();
        }

        if (DataCache.containsKey(line)) {
            Object[] arrayCache = DataCache.get(line);

            Minutes = (HashMap<String, String>)arrayCache[0];
            Hours = (HashMap<String, String>)arrayCache[1];
            DaysInMonth = (HashMap<String, String>)arrayCache[2];
            Month = (HashMap<String, String>)arrayCache[3];
            DaysInWeek = (HashMap<String, String>)arrayCache[4];
        }
        else {
            Minutes = parseRangeParam(params[1], MINUTESPERHOUR, 0);
            Hours = parseRangeParam(params[2], HOURESPERDAY, 0);
            DaysInMonth = parseRangeParam(params[3], DAYSPERMONTH, 1);
            Month = parseRangeParam(params[4], MONTHSPERYEAR, 1); // 1 = january
            DaysInWeek = parseRangeParam(params[5], DAYSPERWEEK, 0);

            Object[] arrayCache = new Object[] { Minutes, Hours, DaysInMonth, Month, DaysInWeek };
            DataCache.put(line, arrayCache);
        }
    }

    private static HashMap<String, String> parseRangeParam(String param,
                                                           int timelength, int minlength) {
        // split by ","

        String[] paramarray;
        if (param.contains(",")) {
            paramarray = param.split(",");
        } else {
            paramarray = new String[] { param };
        }
        StringBuffer rangeitems = new StringBuffer();
        for (int i = 0; i < paramarray.length; i++) {
            // you may mix */# syntax with other range syntax
            if (paramarray[i].contains("/")) {
                // handle */# syntax
                for (int a = 1; a <= timelength; a++) {
                    if (a % Integer.parseInt(paramarray[i]
                            .substring(paramarray[i].indexOf("/") + 1)) == 0) {
                        if (a == timelength) {
                            rangeitems.append(minlength + ",");
                        } else {
                            rangeitems.append(a + ",");
                        }
                    }
                }
            } else {
                if (paramarray[i].equals("*")) {
                    rangeitems.append(fillRange(minlength + "-" + timelength));
                } else {
                    rangeitems.append(fillRange(paramarray[i]));
                }
            }
        }
        String[] values = rangeitems.toString().split(",");
        HashMap<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < values.length; i++) {
            result.put(values[i], values[i]);
        }

        return result;
    }

    private static String fillRange(String range) {
        // split by "-"

        if (!range.contains("-")) {
            return range + ",";
        }

        String[] rangearray = range.split("-");
        StringBuffer result = new StringBuffer();
        for (int i = Integer.parseInt(rangearray[0]); i <= Integer
                .parseInt(rangearray[1]); i++) {
            result.append(i + ",");
        }
        return result.toString();
    }

    public static List<Date> getTomorrowEvents() {

        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DATE, 1);

        return getEventsOfDay(tomorrow);
    }

    public static List<Date> getTodayEvents() {

        return getEventsOfDay(Calendar.getInstance());
    }

    public static List<Date> getEventsOfDay(Calendar dayEvent) {

        Hashtable<String, Date> todayEvents = new Hashtable<>();

        int todayYear = dayEvent.get(Calendar.YEAR);
        int todayMonth = dayEvent.get(Calendar.MONTH);
        int todayDay = dayEvent.get(Calendar.DAY_OF_MONTH);

        Object[] nai = Minutes.keySet().toArray();
        for (int n = 0; n < nai.length; n++) {

            Object[] hai = Hours.keySet().toArray();
            for (int h = 0; h < hai.length; h++) {

                Object[] dai = DaysInMonth.keySet().toArray();
                for (int d = 0; d < dai.length; d++) {

                    // ADD DD
                    if (todayDay == (Integer.parseInt(DaysInMonth.get((String) dai[d])))) {

                        Object[] mai = Month.keySet().toArray();
                        for (int m = 0; m < mai.length; m++) {

                            // ADD MM
                            if (todayMonth == (Integer.parseInt(Month.get((String) mai[m]).toString()) - 1)) {

                                //int[] yai = new int [] { todayYear, todayYear+1 };
                                int[] yai = new int [] { todayYear };

                                for (int y = 0; y < yai.length; y++) {

                                    Object[] wai = DaysInWeek.keySet().toArray();
                                    for (int w = 0; w < wai.length; w++) {

                                        GregorianCalendar gc = new GregorianCalendar();
                                        gc.set(
                                                yai[y],
                                                Integer.parseInt(Month.get((String) mai[m])) - 1,
                                                Integer.parseInt(DaysInMonth.get((String) dai[d]).toString()),
                                                Integer.parseInt(Hours.get((String) hai[h]).toString()),
                                                Integer.parseInt(Minutes.get((String) nai[n]).toString()),
                                                0);

                                        String key = yai[y] + " " + Month.get((String) mai[m]) + " ";
                                        key = key.concat(DaysInMonth.get((String) dai[d]) + " ");
                                        key = key.concat(Hours.get((String) hai[h]) + " ");
                                        key = key.concat(Minutes.get((String) nai[n]));

                                        if ((gc.get(Calendar.YEAR) == todayYear) &&
                                                (gc.get(Calendar.MONTH) == todayMonth) &&
                                                (gc.get(Calendar.DAY_OF_MONTH) == todayDay) &&
                                                (!todayEvents.containsKey(key)) &&
                                                mayRunAt(gc)) {

                                            todayEvents.put(key, gc.getTime());
                                        }
                                    }
                                }
                            }
                            // ADD MM
                        }
                    }
                    // ADD DD
                }
            }
        }

        ArrayList<Date> result = new ArrayList<Date>(todayEvents.values());
        Collections.sort(result);

        return result;
    }

    public static boolean mayRunAt(Calendar cal) {
        int month = cal.get(Calendar.MONTH) + 1; 			// 1=Jan, 2=Feb, ...
        int day = cal.get(Calendar.DAY_OF_MONTH); 			// 1...
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; 	// 0=Sunday, 1=Monday
        int hour = cal.get(Calendar.HOUR_OF_DAY); 			// 0-23
        int minute = cal.get(Calendar.MINUTE); 				// 0-59

        if (Minutes.get(Integer.toString(minute)) != null) {
            if (Hours.get(Integer.toString(hour)) != null) {
                if (DaysInMonth.get(Integer.toString(day)) != null) {
                    if (Month.get(Integer.toString(month)) != null) {
                        if (DaysInWeek.get(Integer.toString(dayOfWeek)) != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean mayRunNow() {
        return mayRunAt(new GregorianCalendar());
    }
}

