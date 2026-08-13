package com.example.portjeep;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleFragment extends Fragment {

    private static final String TAG = "ScheduleFragment";
    private static final String API_URL = "https://port-jeep.vercel.app/api/mobile/schedules";

    private TextView tabToday, tabUpcoming, tabPrevious;
    private RecyclerView rvScheduleList;
    private ScheduleAdapter adapter;

    private FirebaseAuth mAuth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final List<ScheduleItem> todayList = new ArrayList<>();
    private final List<ScheduleItem> upcomingList = new ArrayList<>();
    private final List<ScheduleItem> previousList = new ArrayList<>();

    private int activeTab = 0; // 0 = Today, 1 = Upcoming, 2 = Previous

    public ScheduleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        mAuth = FirebaseAuth.getInstance();

        tabToday = view.findViewById(R.id.tab_today);
        tabUpcoming = view.findViewById(R.id.tab_upcoming);
        tabPrevious = view.findViewById(R.id.tab_previous);
        rvScheduleList = view.findViewById(R.id.rv_schedule_list);

        rvScheduleList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ScheduleAdapter(new ArrayList<>());
        rvScheduleList.setAdapter(adapter);

        tabToday.setOnClickListener(v -> selectTab(0, tabToday, todayList));
        tabUpcoming.setOnClickListener(v -> selectTab(1, tabUpcoming, upcomingList));
        tabPrevious.setOnClickListener(v -> selectTab(2, tabPrevious, previousList));

        loadUserSchedules();

        return view;
    }

    private void selectTab(int tabIndex, TextView selected, List<ScheduleItem> data) {
        this.activeTab = tabIndex;

        tabToday.setBackgroundResource(R.drawable.bg_tab_unselected);
        tabToday.setTextColor(Color.parseColor("#546E7A"));

        tabUpcoming.setBackgroundResource(R.drawable.bg_tab_unselected);
        tabUpcoming.setTextColor(Color.parseColor("#546E7A"));

        tabPrevious.setBackgroundResource(R.drawable.bg_tab_unselected);
        tabPrevious.setTextColor(Color.parseColor("#546E7A"));

        selected.setBackgroundResource(R.drawable.bg_tab_selected);
        selected.setTextColor(Color.WHITE);

        adapter.updateList(data);
    }

    private void loadUserSchedules() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    String idToken = result.getToken();
                    Log.d(TAG, "Firebase ID Token fetched successfully");
                    fetchSchedulesFromApi(idToken);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to get ID token: " + e.getMessage());
                    Toast.makeText(requireContext(), "Auth Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchSchedulesFromApi(String idToken) {
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + idToken);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "API Response Code: " + responseCode);

                InputStream inputStream = (responseCode == HttpURLConnection.HTTP_OK)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseStr = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseStr.append(line);
                }
                reader.close();

                String rawResult = responseStr.toString();
                Log.d(TAG, "API Response Payload: " + rawResult);

                handler.post(() -> {
                    if (!isAdded()) return;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        parseAndDisplaySchedules(rawResult);
                    } else {
                        Toast.makeText(requireContext(), "Server Error (" + responseCode + "): " + rawResult, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Network Request Failed", e);
                handler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Connection failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            boolean success = root.optBoolean("success", false);

            if (!success) {
                String errMsg = root.optString("error", "Unknown server error");
                Toast.makeText(requireContext(), "Error: " + errMsg, Toast.LENGTH_SHORT).show();
                return;
            }

            todayList.clear();
            upcomingList.clear();
            previousList.clear();

            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) {
                Toast.makeText(requireContext(), "No schedules found.", Toast.LENGTH_SHORT).show();
                selectTab(activeTab, getActiveTabTextView(), getActiveTabList());
                return;
            }

            int todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);

                String rawDate = doc.optString("date", "N/A");
                String dayStr = doc.optString("day", "Scheduled");

                // --- PARSE BACKEND JEEP STRING ---
                String rawJeep = doc.optString("jeep", "Unassigned Unit");
                String jeepFormatted = rawJeep;

                if (rawJeep.contains("(") && rawJeep.contains(")")) {
                    try {
                        int startParen = rawJeep.indexOf("(");
                        int endParen = rawJeep.indexOf(")");

                        String plate = rawJeep.substring(0, startParen).trim();
                        String unit = rawJeep.substring(startParen + 1, endParen).trim();

                        // Converts "AAA-0002 (Unit 3)" -> "Unit 3 · AAA-0002"
                        jeepFormatted = unit + " · " + plate;
                    } catch (Exception e) {
                        jeepFormatted = rawJeep;
                    }
                }

                // Parse Driver Info
                String driverName = "Unassigned Driver";
                if (doc.has("driver") && !doc.isNull("driver")) {
                    Object dObj = doc.get("driver");
                    if (dObj instanceof JSONObject) {
                        driverName = ((JSONObject) dObj).optString("name", "Assigned Driver");
                    } else if (dObj instanceof String) {
                        driverName = (String) dObj;
                    }
                }

                // Parse PAO Info
                String paoName = "Unassigned PAO";
                if (doc.has("pao") && !doc.isNull("pao")) {
                    Object pObj = doc.get("pao");
                    if (pObj instanceof JSONObject) {
                        paoName = ((JSONObject) pObj).optString("name", "Assigned PAO");
                    } else if (pObj instanceof String) {
                        paoName = (String) pObj;
                    }
                }

                int schedIndex = getDayIndex(dayStr);

                String status;
                ScheduleItem item;

                if (schedIndex != -1) {
                    if (schedIndex == todayIndex) {
                        status = "Assigned";
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, paoName);
                        todayList.add(item);
                    } else if (schedIndex > todayIndex) {
                        status = "Scheduled";
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, paoName);
                        upcomingList.add(item);
                    } else {
                        status = "Completed";
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, paoName);
                        previousList.add(item);
                    }
                } else {
                    status = "Scheduled";
                    item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, paoName);
                    upcomingList.add(item);
                }
            }

            selectTab(activeTab, getActiveTabTextView(), getActiveTabList());

        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing error", e);
            Toast.makeText(requireContext(), "Data Parsing Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int getDayIndex(String dayName) {
        if (dayName == null || dayName.trim().isEmpty()) return -1;

        switch (dayName.trim().toLowerCase(Locale.US)) {
            case "sunday":    return Calendar.SUNDAY;    // 1
            case "monday":    return Calendar.MONDAY;    // 2
            case "tuesday":   return Calendar.TUESDAY;   // 3
            case "wednesday": return Calendar.WEDNESDAY; // 4
            case "thursday":  return Calendar.THURSDAY;  // 5
            case "friday":    return Calendar.FRIDAY;    // 6
            case "saturday":  return Calendar.SATURDAY;  // 7
            default:          return -1;
        }
    }

    private TextView getActiveTabTextView() {
        if (activeTab == 0) return tabToday;
        if (activeTab == 1) return tabUpcoming;
        return tabPrevious;
    }

    private List<ScheduleItem> getActiveTabList() {
        if (activeTab == 0) return todayList;
        if (activeTab == 1) return upcomingList;
        return previousList;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}