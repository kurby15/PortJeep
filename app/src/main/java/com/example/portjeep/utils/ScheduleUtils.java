package com.example.portjeep.utils;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScheduleUtils {
    private static final String TAG = "ScheduleUtils";

    public static boolean hasScheduleToday(Context context) {
        String cachedData = PreferenceManager.getSchedulesCache(context);
        if (cachedData == null || cachedData.isEmpty()) return false;

        try {
            JSONObject root = new JSONObject(cachedData);
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) return false;

            Calendar todayCal = Calendar.getInstance();
            int todayIndex = todayCal.get(Calendar.DAY_OF_WEEK);
            String[] weekDays = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            String todayName = weekDays[todayIndex];

            SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String todayDateStr = sdfKey.format(new Date());
            String currentUid = PreferenceManager.getCurrentUserId(context);

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);
                
                String driverId = doc.optString("driver_id", doc.optString("driverId", ""));
                String paoId = doc.optString("pao_id", doc.optString("paoId", ""));
                
                if (driverId.isEmpty() && doc.has("driver")) {
                    JSONObject d = doc.optJSONObject("driver");
                    if (d != null) driverId = d.optString("uid", d.optString("id", ""));
                }
                if (paoId.isEmpty() && doc.has("pao")) {
                    JSONObject p = doc.optJSONObject("pao");
                    if (p != null) paoId = p.optString("uid", p.optString("id", ""));
                }

                boolean isCurrentUser = !currentUid.isEmpty() && (currentUid.equals(driverId) || currentUid.equals(paoId));
                if (!isCurrentUser) continue;

                String dayStr = doc.optString("day", "").trim();
                String dateStr = doc.optString("date", "").trim();

                if (todayDateStr.equalsIgnoreCase(dateStr) || todayName.equalsIgnoreCase(dayStr)) {
                    if (!"completed".equalsIgnoreCase(doc.optString("status", ""))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "hasScheduleToday check error", e);
        }
        return false;
    }
}
