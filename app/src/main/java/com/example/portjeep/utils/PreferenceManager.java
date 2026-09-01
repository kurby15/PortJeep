package com.example.portjeep.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreferenceManager {
    private static final String PREF_NAME = "PortJeepPrefs";
    private static final String KEY_SCHEDULES_CACHE = "schedules_cache";
    private static final String KEY_USER_FIRST_NAME = "user_first_name";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_REST_DAYS = "user_rest_days";

    public static void saveSchedulesCache(Context context, String json) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SCHEDULES_CACHE, json).apply();
    }

    public static String getSchedulesCache(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SCHEDULES_CACHE, null);
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
