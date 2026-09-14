package com.example.portjeep.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreferenceManager {
    private static final String PREF_NAME = "PortJeepPrefs";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_SCHEDULES_CACHE = "schedules_cache";
    private static final String KEY_SCHEDULES_FETCH_TIME = "schedules_fetch_time";
    private static final String KEY_SALARY_CACHE = "salary_cache";
    private static final String KEY_SALARY_FETCH_TIME = "salary_fetch_time";
    private static final String KEY_USER_FIRST_NAME = "user_first_name";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_REST_DAYS = "user_rest_days";

    public static void saveCurrentUserId(Context context, String userId) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedId = prefs.getString(KEY_USER_ID, "");
        
        // If a different user is logging in, clear all previous cache
        if (userId != null && !userId.equals(savedId)) {
            clearAllCache(context);
        }
        
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getCurrentUserId(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_ID, "");
    }

    public static void clearAllCache(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public static void saveSchedulesCache(Context context, String json) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SCHEDULES_CACHE, json)
                .putLong(KEY_SCHEDULES_FETCH_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String getSchedulesCache(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SCHEDULES_CACHE, null);
    }

    public static long getSchedulesLastFetchTime(Context context) {
        if (context == null) return 0;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getLong(KEY_SCHEDULES_FETCH_TIME, 0);
    }

    public static void saveSalaryCache(Context context, String json) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_SALARY_CACHE, json)
                .putLong(KEY_SALARY_FETCH_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String getSalaryCache(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SALARY_CACHE, null);
    }

    public static long getSalaryLastFetchTime(Context context) {
        if (context == null) return 0;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getLong(KEY_SALARY_FETCH_TIME, 0);
    }

    public static void saveUserProfile(Context context, String firstName, String role, List<String> restDays) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_FIRST_NAME, firstName);
        editor.putString(KEY_USER_ROLE, role);
        if (restDays != null) {
            editor.putStringSet(KEY_USER_REST_DAYS, new HashSet<>(restDays));
        }
        editor.apply();
    }

    public static String getUserFirstName(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_FIRST_NAME, "");
    }

    public static String getUserRole(Context context) {
        if (context == null) return null;
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER_ROLE, "");
    }

    public static Set<String> getUserRestDays(Context context) {
        if (context == null) return new HashSet<>();
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getStringSet(KEY_USER_REST_DAYS, new HashSet<>());
    }
}
