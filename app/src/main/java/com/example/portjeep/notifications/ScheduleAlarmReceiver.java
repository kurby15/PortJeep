package com.example.portjeep.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.portjeep.utils.NotificationHelper;
import com.example.portjeep.utils.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScheduleAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduleAlarmReceiver";
    private static final int REQ_MORNING = 1001;
    private static final int REQ_BREAKFAST = 1003;
    private static final int REQ_LUNCH = 1004;
    private static final int REQ_AFTERNOON = 1002;
    private static final int REQ_EVENING = 1005;

    @Override
    public void onReceive(Context context, Intent intent) {
        int requestCode = intent.getIntExtra("request_code", -1);
        Log.d(TAG, "Alarm received! RequestCode: " + requestCode);

        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            scheduleDailyAlarms(context);
            return;
        }

        switch (requestCode) {
            case REQ_MORNING:
                checkAndShowScheduleNotification(context);
                showMorningGreeting(context);
                break;
            case REQ_BREAKFAST:
                showBreakfastGreeting(context);
                break;
            case REQ_LUNCH:
                showLunchGreeting(context);
                break;
            case REQ_AFTERNOON:
                showAfternoonGreeting(context);
                break;
            case REQ_EVENING:
                showEveningGreeting(context);
                break;
        }

        // Reschedule to ensure continuity
        scheduleDailyAlarms(context);
    }

    public static void scheduleDailyAlarms(Context context) {
        scheduleAlarm(context, 4, 0, REQ_MORNING);    // 4:00 AM
        scheduleAlarm(context, 8, 0, REQ_BREAKFAST);  // 8:00 AM
        scheduleAlarm(context, 12, 0, REQ_LUNCH);      // 12:00 PM
        scheduleAlarm(context, 16, 0, REQ_AFTERNOON); // 4:00 PM
        scheduleAlarm(context, 20, 0, REQ_EVENING);   // 8:00 PM
    }

    private static void scheduleAlarm(Context context, int hour, int minute, int requestCode) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        intent.putExtra("request_code", requestCode);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
        }
    }

    private void checkAndShowScheduleNotification(Context context) {
        String cachedData = PreferenceManager.getSchedulesCache(context);
        if (cachedData == null || cachedData.isEmpty()) return;

        try {
            JSONObject root = new JSONObject(cachedData);
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) return;

            Calendar todayCal = Calendar.getInstance();
            int todayIndex = todayCal.get(Calendar.DAY_OF_WEEK);
            String[] weekDays = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            String todayName = weekDays[todayIndex];

            SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String todayDateStr = sdfKey.format(new Date());

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);
                String dayStr = doc.optString("day", "").trim();
                String dateStr = doc.optString("date", "").trim();

                if (todayDateStr.equalsIgnoreCase(dateStr) || todayName.equalsIgnoreCase(dayStr)) {
                    if (!"completed".equalsIgnoreCase(doc.optString("status", ""))) {
                        String jeep = doc.optString("jeep", "Assigned Unit");
                        String route = doc.optString("route", "Main Route");
                        NotificationHelper.showNotification(
                                context,
                                "Duty Assignment Today",
                                "Route: " + route + " | Unit: " + jeep
                        );
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Schedule check error", e);
        }
    }

    private String getUserName(Context context) {
        String firstName = PreferenceManager.getUserFirstName(context);
        return (firstName != null && !firstName.isEmpty()) ? firstName : "Driver";
    }

    private void showMorningGreeting(Context context) {
        String name = getUserName(context);
        String[] greetings = {
            "Good morning, " + name + "! Ready for a productive day?",
            "Hi " + name + "! Wishing you a safe and smooth shift today.",
            "Good morning! Stay alert and safe on the road, " + name + "."
        };
        NotificationHelper.showNotification(context, "PortJeep Assistant", getRandom(greetings));
    }

    private void showBreakfastGreeting(Context context) {
        String name = getUserName(context);
        String[] greetings = {
            "Time for a quick break, " + name + "? Don't forget to eat breakfast!",
            "Keep up the good work, " + name + "! Stay energized.",
            "Morning rush is here! Stay calm and safe, " + name + "."
        };
        NotificationHelper.showNotification(context, "PortJeep Assistant", getRandom(greetings));
    }

    private void showLunchGreeting(Context context) {
        String name = getUserName(context);
        String[] greetings = {
            "It's lunch time, " + name + "! Take a well-deserved rest.",
            "Hi " + name + "! Remember to stay hydrated and take a break.",
            "Enjoy your lunch, " + name + "! How has your day been so far?"
        };
        NotificationHelper.showNotification(context, "PortJeep Assistant", getRandom(greetings));
    }

    private void showAfternoonGreeting(Context context) {
        String name = getUserName(context);
        String[] greetings = {
            "Hi " + name + "! How's your afternoon going?",
            "Checking in, " + name + ". Hope your shift is still going smoothly!",
            "Stay safe during the afternoon heat, " + name + "!",
            "Hey " + name + "! You're doing great, keep it up!"
        };
        NotificationHelper.showNotification(context, "PortJeep Assistant", getRandom(greetings));
    }

    private void showEveningGreeting(Context context) {
        String name = getUserName(context);
        String[] greetings = {
            "Shift ending soon, " + name + "? Drive safely on your way back.",
            "Good evening, " + name + "! How was your day overall?",
            "Time to rest and recharge, " + name + ". You did a good job today!",
            "Stay safe tonight, " + name + ". See you tomorrow!"
        };
        NotificationHelper.showNotification(context, "PortJeep Assistant", getRandom(greetings));
    }

    private String getRandom(String[] array) {
        int idx = (int) (Math.random() * array.length);
        return array[idx];
    }
}
