package com.example.portjeep.schedule;

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

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.data.model.ScheduleItem;
import com.example.portjeep.utils.CryptoUtils;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleFragment extends Fragment {

    private static final String TAG = "ScheduleFragment";
    private static final String API_URL = "https://port-jeep.vercel.app/api/mobile/schedules";

    private ShimmerFrameLayout shimmerContainer;
    private RecyclerView rvScheduleList;

    private TextView tabToday, tabUpcoming, tabPrevious;
    private ScheduleAdapter adapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ExecutorService executor;

    private final List<ScheduleItem> todayList = new ArrayList<>();
    private final List<ScheduleItem> upcomingList = new ArrayList<>();
    private final List<ScheduleItem> previousList = new ArrayList<>();

    private int activeTab = 0; // 0 = Today, 1 = Upcoming, 2 = Previous

    public ScheduleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        executor = Executors.newSingleThreadExecutor();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        shimmerContainer = view.findViewById(R.id.shimmer_schedule_container);
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

        showLoadingSkeleton();
        loadUserSchedules();

        return view;
    }

    private void showLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.startShimmer();
            shimmerContainer.setVisibility(View.VISIBLE);
        }
        if (rvScheduleList != null) {
            rvScheduleList.setVisibility(View.GONE);
        }
    }

    private void hideLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.stopShimmer();
            shimmerContainer.setVisibility(View.GONE);
        }
        if (rvScheduleList != null) {
            rvScheduleList.setVisibility(View.VISIBLE);
        }
    }

    private void selectTab(int tabIndex, TextView selected, List<ScheduleItem> data) {
        this.activeTab = tabIndex;

        if (tabToday != null) {
            tabToday.setBackgroundResource(R.drawable.bg_tab_unselected);
            tabToday.setTextColor(Color.parseColor("#546E7A"));
        }

        if (tabUpcoming != null) {
            tabUpcoming.setBackgroundResource(R.drawable.bg_tab_unselected);
            tabUpcoming.setTextColor(Color.parseColor("#546E7A"));
        }

        if (tabPrevious != null) {
            tabPrevious.setBackgroundResource(R.drawable.bg_tab_unselected);
            tabPrevious.setTextColor(Color.parseColor("#546E7A"));
        }

        if (selected != null) {
            selected.setBackgroundResource(R.drawable.bg_tab_selected);
            selected.setTextColor(Color.WHITE);
        }

        if (adapter != null) {
            adapter.updateList(data);
        }
    }

    private void loadUserSchedules() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Please log in first.", Toast.LENGTH_SHORT).show();
            }
            hideLoadingSkeleton();
            return;
        }

        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    String idToken = result.getToken();
                    fetchSchedulesFromApi(idToken);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Auth Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                    hideLoadingSkeleton();
                });
    }

    private void fetchSchedulesFromApi(String idToken) {
        if (executor == null || executor.isShutdown()) return;

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

                handler.post(() -> {
                    if (!isAdded() || getContext() == null) return;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        parseAndDisplaySchedules(rawResult);
                    } else {
                        Toast.makeText(getContext(), "Server Error (" + responseCode + "): " + rawResult, Toast.LENGTH_LONG).show();
                        hideLoadingSkeleton();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Network Request Failed", e);
                handler.post(() -> {
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(getContext(), "Connection failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        hideLoadingSkeleton();
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
        if (!isAdded() || getContext() == null) return;

        try {
            JSONObject root = new JSONObject(jsonResponse);
            boolean success = root.optBoolean("success", false);

            if (!success) {
                String errMsg = root.optString("error", "Unknown server error");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + errMsg, Toast.LENGTH_SHORT).show();
                }
                hideLoadingSkeleton();
                return;
            }

            todayList.clear();
            upcomingList.clear();
            previousList.clear();

            JSONArray schedules = root.optJSONArray("schedules");

            if (schedules == null || schedules.length() == 0) {
                todayList.add(new ScheduleItem("Today", "N/A", "Rest Day", "No Unit Assigned", "Rest Day", "Rest Day"));
                upcomingList.add(new ScheduleItem("Upcoming", "N/A", "Unassigned", "No Unit Assigned", "Unassigned Driver", "Unassigned PAO"));
                selectTab(activeTab, getActiveTabTextView(), getActiveTabList());
                hideLoadingSkeleton();
                return;
            }

            // Get current date normalized to midnight
            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);
            Date todayAtMidnight = todayCal.getTime();

            int currentYear = todayCal.get(Calendar.YEAR);
            String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

            // Expanded date patterns covering common API outputs
            String[] patterns = new String[]{
                    "yyyy-MM-dd",
                    "MM/dd/yyyy",
                    "dd/MM/yyyy",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "MMM dd, yyyy",
                    "MMMM dd, yyyy",
                    "MMM dd",
                    "MMMM dd"
            };

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);

                String rawDate = doc.optString("date", "N/A");
                String dayStr = doc.optString("day", "Scheduled");
                String rawStatus = doc.optString("status", "").toLowerCase(Locale.US);
                String rawJeep = doc.optString("jeep", "Unassigned Unit");
                String jeepFormatted = formatJeepUnit(rawJeep);

                // Driver Parsing
                String driverName = "Unassigned Driver";
                String driverEmail = "";
                String driverContact = "";
                String driverId = doc.optString("driver_id", doc.optString("driverId", ""));

                if (doc.has("driver") && !doc.isNull("driver")) {
                    Object dObj = doc.get("driver");
                    if (dObj instanceof JSONObject) {
                        JSONObject dJson = (JSONObject) dObj;
                        if (driverId.isEmpty()) {
                            driverId = dJson.optString("uid",
                                    dJson.optString("id",
                                            dJson.optString("_id",
                                                    dJson.optString("driver_id",
                                                            dJson.optString("user_id", "")))));
                        }

                        driverName = dJson.optString("name", dJson.optString("full_name", "Unassigned Driver"));
                        driverEmail = CryptoUtils.decrypt(dJson.optString("email", ""), secretKey);
                        driverContact = CryptoUtils.decrypt(dJson.optString("contact_no", dJson.optString("contact", "")), secretKey);
                    } else if (dObj instanceof String) {
                        String strVal = (String) dObj;
                        if (!strVal.contains(" ") && strVal.length() > 15) {
                            driverId = strVal;
                        } else {
                            driverName = strVal;
                        }
                    }
                }

                driverName = CryptoUtils.decrypt(driverName, secretKey);

                if (driverName == null || driverName.trim().isEmpty() || driverName.equalsIgnoreCase("null")) {
                    driverName = "Unassigned Driver";
                } else if (driverName.equalsIgnoreCase("off") || driverName.equalsIgnoreCase("rest")) {
                    driverName = "Rest Day";
                }

                // PAO Parsing
                String paoName = "Unassigned PAO";
                String paoEmail = "";
                String paoContact = "";
                String paoId = doc.optString("pao_id", doc.optString("paoId", ""));

                if (doc.has("pao") && !doc.isNull("pao")) {
                    Object pObj = doc.get("pao");
                    if (pObj instanceof JSONObject) {
                        JSONObject pJson = (JSONObject) pObj;
                        if (paoId.isEmpty()) {
                            paoId = pJson.optString("uid",
                                    pJson.optString("id",
                                            pJson.optString("_id",
                                                    pJson.optString("pao_id",
                                                            pJson.optString("user_id", "")))));
                        }

                        paoName = pJson.optString("name", pJson.optString("full_name", "Unassigned PAO"));
                        paoEmail = CryptoUtils.decrypt(pJson.optString("email", ""), secretKey);
                        paoContact = CryptoUtils.decrypt(pJson.optString("contact_no", pJson.optString("contact", "")), secretKey);
                    } else if (pObj instanceof String) {
                        String strVal = (String) pObj;
                        if (!strVal.contains(" ") && strVal.length() > 15) {
                            paoId = strVal;
                        } else {
                            paoName = strVal;
                        }
                    }
                }

                paoName = CryptoUtils.decrypt(paoName, secretKey);

                if (paoName == null || paoName.trim().isEmpty() || paoName.equalsIgnoreCase("null")) {
                    paoName = "Unassigned PAO";
                } else if (paoName.equalsIgnoreCase("off") || paoName.equalsIgnoreCase("rest")) {
                    paoName = "Rest Day";
                }

                boolean isRestDay = "Rest Day".equalsIgnoreCase(driverName) || "Rest Day".equalsIgnoreCase(paoName);
                boolean isUnassigned = "Unassigned Driver".equalsIgnoreCase(driverName) || "Unassigned PAO".equalsIgnoreCase(paoName);

                // Parse rawDate into exact java.util.Date object
                Date parsedDate = parseDateString(rawDate, patterns, currentYear);

                String status;
                ScheduleItem item;

                // Priority 1: Check explicit backend status string if provided
                if ("completed".equalsIgnoreCase(rawStatus) || "done".equalsIgnoreCase(rawStatus) || "finished".equalsIgnoreCase(rawStatus)) {
                    status = "Completed";
                    item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                    previousList.add(item);
                }
                // Priority 2: Precise calendar date comparison
                else if (parsedDate != null) {
                    Calendar parsedCal = Calendar.getInstance();
                    parsedCal.setTime(parsedDate);
                    parsedCal.set(Calendar.HOUR_OF_DAY, 0);
                    parsedCal.set(Calendar.MINUTE, 0);
                    parsedCal.set(Calendar.SECOND, 0);
                    parsedCal.set(Calendar.MILLISECOND, 0);
                    Date itemDateAtMidnight = parsedCal.getTime();

                    if (itemDateAtMidnight.equals(todayAtMidnight)) {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Assigned");
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        todayList.add(item);
                    } else if (itemDateAtMidnight.after(todayAtMidnight)) {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Scheduled");
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        upcomingList.add(item);
                    } else {
                        status = "Completed";
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        previousList.add(item);
                    }
                }
                // Priority 3: Absolute fallback if no dates can be parsed
                else {
                    int todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
                    int schedIndex = getDayIndex(dayStr);

                    if (schedIndex != -1) {
                        if (schedIndex == todayIndex) {
                            status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Assigned");
                            item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                            todayList.add(item);
                        } else {
                            status = "Completed";
                            item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                            previousList.add(item);
                        }
                    } else {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Scheduled");
                        item = new ScheduleItem(dayStr, rawDate, status, jeepFormatted, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        upcomingList.add(item);
                    }
                }

                fetchMissingProfileDetails(driverId, item, true);
                fetchMissingProfileDetails(paoId, item, false);
            }

            if (todayList.isEmpty()) {
                todayList.add(new ScheduleItem("Today", "N/A", "Rest Day", "No Unit Assigned", "Rest Day", "Rest Day"));
            }

            if (upcomingList.isEmpty()) {
                upcomingList.add(new ScheduleItem("Upcoming", "N/A", "Unassigned", "No Unit Assigned", "Unassigned Driver", "Unassigned PAO"));
            }

            selectTab(activeTab, getActiveTabTextView(), getActiveTabList());
            hideLoadingSkeleton();

        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing error", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Data Parsing Error: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
            hideLoadingSkeleton();
        }
    }

    // Robust multi-format date parser helper
    private Date parseDateString(String rawDate, String[] patterns, int currentYear) {
        if (rawDate == null || rawDate.trim().isEmpty() || "N/A".equalsIgnoreCase(rawDate)) {
            return null;
        }

        String cleanDate = rawDate.trim();

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                Date parsed = sdf.parse(cleanDate);

                if (parsed != null) {
                    if (!pattern.contains("yyyy") && !pattern.contains("yy")) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(parsed);
                        cal.set(Calendar.YEAR, currentYear);
                        return cal.getTime();
                    }
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isDayUpcoming(int todayIndex, int targetIndex) {
        int diff = targetIndex - todayIndex;
        if (diff < 0) {
            diff += 7;
        }
        return diff > 0 && diff <= 3;
    }

    private void fetchMissingProfileDetails(String userId, ScheduleItem item, boolean isDriver) {
        if (userId == null || userId.trim().isEmpty()) return;

        db.collection("File201")
                .document(userId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && isAdded()) {
                        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

                        String rawFirstName = doc.getString("first_name");
                        String rawLastName = doc.getString("last_name");
                        String rawEmail = doc.getString("email");
                        String rawContact = doc.getString("contact_no");

                        String firstName = CryptoUtils.decrypt(rawFirstName, secretKey);
                        String lastName = CryptoUtils.decrypt(rawLastName, secretKey);
                        String email = CryptoUtils.decrypt(rawEmail, secretKey);
                        String contact = CryptoUtils.decrypt(rawContact, secretKey);

                        String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();

                        if (isDriver) {
                            if (!fullName.isEmpty() && ("Unassigned Driver".equals(item.getDriverName()) || item.getDriverName().length() > 20)) {
                                item.setDriverName(fullName);
                            }
                            if (email != null && !email.trim().isEmpty()) {
                                item.setDriverEmail(email);
                            }
                            if (contact != null && !contact.trim().isEmpty()) {
                                item.setDriverContact(contact);
                            }
                        } else {
                            if (!fullName.isEmpty() && ("Unassigned PAO".equals(item.getPaoName()) || item.getPaoName().length() > 20)) {
                                item.setPaoName(fullName);
                            }
                            if (email != null && !email.trim().isEmpty()) {
                                item.setPaoEmail(email);
                            }
                            if (contact != null && !contact.trim().isEmpty()) {
                                item.setPaoContact(contact);
                            }
                        }

                        if (getActivity() != null && !getActivity().isFinishing()) {
                            getActivity().runOnUiThread(() -> {
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching user profile for ID: " + userId, e));
    }

    private String formatJeepUnit(String rawJeep) {
        if (rawJeep.contains("(") && rawJeep.contains(")")) {
            try {
                int startParen = rawJeep.indexOf("(");
                int endParen = rawJeep.indexOf(")");

                String plate = rawJeep.substring(0, startParen).trim();
                String unit = rawJeep.substring(startParen + 1, endParen).trim();

                return unit + " · " + plate;
            } catch (Exception e) {
                return rawJeep;
            }
        } else if (rawJeep.trim().isEmpty() || rawJeep.equalsIgnoreCase("null")) {
            return "Unassigned Unit";
        }
        return rawJeep;
    }

    private int getDayIndex(String dayName) {
        if (dayName == null || dayName.trim().isEmpty()) return -1;

        switch (dayName.trim().toLowerCase(Locale.US)) {
            case "sunday":    return Calendar.SUNDAY;
            case "monday":    return Calendar.MONDAY;
            case "tuesday":   return Calendar.TUESDAY;
            case "wednesday": return Calendar.WEDNESDAY;
            case "thursday":  return Calendar.THURSDAY;
            case "friday":    return Calendar.FRIDAY;
            case "saturday":  return Calendar.SATURDAY;
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
        if (shimmerContainer != null) {
            shimmerContainer.stopShimmer();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}