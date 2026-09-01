package com.example.portjeep.schedule;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.data.model.ScheduleItem;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.PreferenceManager;
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

    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerContainer;
    private ViewPager2 viewPagerSchedule;
    private LinearLayout layoutEmptyState;
    private TextView tvEmptyState;

    private TextView tabToday, tabUpcoming, tabPrevious;
    private SchedulePagerAdapter pagerAdapter;
    private ScheduleViewModel viewModel;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ExecutorService executor;

    public ScheduleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        executor = Executors.newSingleThreadExecutor();
        // Initialize ViewModel scoped to this fragment so it's shared with child fragments
        viewModel = new ViewModelProvider(this).get(ScheduleViewModel.class);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Swipe Refresh
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_schedule);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::loadUserSchedules);
        }

        shimmerContainer = view.findViewById(R.id.shimmer_schedule_container);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        tabToday = view.findViewById(R.id.tab_today);
        tabUpcoming = view.findViewById(R.id.tab_upcoming);
        tabPrevious = view.findViewById(R.id.tab_previous);
        viewPagerSchedule = view.findViewById(R.id.view_pager_schedule);

        pagerAdapter = new SchedulePagerAdapter(this);
        viewPagerSchedule.setAdapter(pagerAdapter);

        tabToday.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(0, true));
        tabUpcoming.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(1, true));
        tabPrevious.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(2, true));

        viewPagerSchedule.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                viewModel.setActiveTab(position);
                updateTabsUi(position);
            }
        });

        setupObservers();

        if (!viewModel.hasData()) {
            loadUserSchedules();
        } else {
            // Restore active tab from ViewModel state
            int savedTab = viewModel.getActiveTab().getValue() != null ? viewModel.getActiveTab().getValue() : 0;
            viewPagerSchedule.setCurrentItem(savedTab, false);
            updateTabsUi(savedTab);
            hideLoadingSkeleton();
        }

        return view;
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading) {
                showLoadingSkeleton();
            } else {
                hideLoadingSkeleton();
            }
        });

        viewModel.getActiveTab().observe(getViewLifecycleOwner(), tabIndex -> {
            updateEmptyStateVisibility(getActiveTabList(tabIndex));
        });

        // Observe data changes to refresh empty state for the current tab
        viewModel.getTodayList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 0) {
                updateEmptyStateVisibility(list);
            }
        });
        viewModel.getUpcomingList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 1) {
                updateEmptyStateVisibility(list);
            }
        });
        viewModel.getPreviousList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 2) {
                updateEmptyStateVisibility(list);
            }
        });
    }

    private void showLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.startShimmer();
            shimmerContainer.setVisibility(View.VISIBLE);
        }
        if (viewPagerSchedule != null) viewPagerSchedule.setVisibility(View.GONE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
    }

    private void hideLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.stopShimmer();
            shimmerContainer.setVisibility(View.GONE);
        }

        // Decide what to show based on the current list's content
        int currentTab = viewModel.getActiveTab().getValue() != null ? viewModel.getActiveTab().getValue() : 0;
        updateEmptyStateVisibility(getActiveTabList(currentTab));

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateEmptyStateVisibility(List<ScheduleItem> currentList) {
        // Prevent showing empty state while loading is in progress
        Boolean loading = viewModel.getIsLoading().getValue();
        if (loading != null && loading) {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (viewPagerSchedule != null) viewPagerSchedule.setVisibility(View.GONE);
            return;
        }

        // FIX: Always keep viewPagerSchedule visible so swiping remains functional even if data is empty.
        // The per-page empty state is now handled in SchedulePageFragment.
        if (viewPagerSchedule != null) {
            viewPagerSchedule.setVisibility(View.VISIBLE);
        }
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void updateTabsUi(int tabIndex) {
        TextView[] tabs = {tabToday, tabUpcoming, tabPrevious};
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                if (i == tabIndex) {
                    tabs[i].setBackgroundResource(R.drawable.bg_tab_selected);
                    tabs[i].setTextColor(Color.WHITE);
                } else {
                    tabs[i].setBackgroundResource(R.drawable.bg_tab_unselected);
                    tabs[i].setTextColor(Color.parseColor("#546E7A"));
                }
            }
        }
    }

    private List<ScheduleItem> getActiveTabList(int tabIndex) {
        if (viewModel == null) return new ArrayList<>();
        if (tabIndex == 0) return viewModel.getTodayList().getValue();
        if (tabIndex == 1) return viewModel.getUpcomingList().getValue();
        return viewModel.getPreviousList().getValue();
    }

    private static class SchedulePagerAdapter extends FragmentStateAdapter {
        public SchedulePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return SchedulePageFragment.newInstance(position);
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }

    private void loadUserSchedules() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (getContext() != null) Toast.makeText(getContext(), "Please log in first.", Toast.LENGTH_SHORT).show();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        viewModel.setLoading(true);
        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    fetchSchedulesFromApi(result.getToken());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    // Try loading from cache if auth fails (likely offline)
                    String cachedData = PreferenceManager.getSchedulesCache(getContext());
                    if (cachedData != null) {
                        parseAndDisplaySchedules(cachedData);
                    } else {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Auth Error: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    viewModel.setLoading(false);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
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
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                InputStream inputStream = (responseCode == HttpURLConnection.HTTP_OK) ? connection.getInputStream() : connection.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseStr = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) responseStr.append(line);
                reader.close();

                String rawResult = responseStr.toString();
                handler.post(() -> {
                    if (!isAdded()) return;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        PreferenceManager.saveSchedulesCache(getContext(), rawResult);
                        parseAndDisplaySchedules(rawResult);
                    } else {
                        // Try cache on error
                        String cachedData = PreferenceManager.getSchedulesCache(getContext());
                        if (cachedData != null) {
                            parseAndDisplaySchedules(cachedData);
                        } else {
                            Toast.makeText(getContext(), "Server Error (" + responseCode + ")", Toast.LENGTH_LONG).show();
                        }
                        viewModel.setLoading(false);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Network Error", e);
                handler.post(() -> {
                    if (isAdded()) {
                        // Network failure, try cache
                        String cachedData = PreferenceManager.getSchedulesCache(getContext());
                        if (cachedData != null) {
                            parseAndDisplaySchedules(cachedData);
                        } else {
                            Toast.makeText(getContext(), "Connection failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        }
                        viewModel.setLoading(false);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            if (!root.optBoolean("success", false)) {
                String error = root.optString("error", "Unknown error");
                if (getContext() != null) Toast.makeText(getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                viewModel.setLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                return;
            }

            List<ScheduleItem> today = new ArrayList<>();
            List<ScheduleItem> upcoming = new ArrayList<>();
            List<ScheduleItem> previous = new ArrayList<>();

            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) {
                viewModel.setSchedules(today, upcoming, previous);
                viewModel.setLoading(false);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                return;
            }

            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);
            Date todayMidnight = todayCal.getTime();
            int currentYear = todayCal.get(Calendar.YEAR);
            String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

            // Restored full list of patterns
            String[] patterns = {
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
                String jeep = formatJeepUnit(doc.optString("jeep", "Unassigned Unit"));

                // Driver Parsing
                String driverName = "Unassigned Driver", driverEmail = "", driverContact = "";
                String driverId = doc.optString("driver_id", doc.optString("driverId", ""));

                if (doc.has("driver") && !doc.isNull("driver")) {
                    Object dObj = doc.get("driver");
                    if (dObj instanceof JSONObject) {
                        JSONObject dJson = (JSONObject) dObj;
                        if (driverId.isEmpty()) driverId = dJson.optString("uid", dJson.optString("id", ""));
                        driverName = dJson.optString("name", dJson.optString("full_name", "Unassigned Driver"));
                        driverEmail = CryptoUtils.decrypt(dJson.optString("email", ""), secretKey);
                        driverContact = CryptoUtils.decrypt(dJson.optString("contact_no", dJson.optString("contact", "")), secretKey);
                    } else if (dObj instanceof String) {
                        String strVal = (String) dObj;
                        if (!strVal.contains(" ") && strVal.length() > 15) driverId = strVal;
                        else driverName = strVal;
                    }
                }
                driverName = CryptoUtils.decrypt(driverName, secretKey);
                if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("null")) driverName = "Unassigned Driver";
                else if (driverName.equalsIgnoreCase("off") || driverName.equalsIgnoreCase("rest")) driverName = "Rest Day";

                // PAO Parsing
                String paoName = "Unassigned PAO", paoEmail = "", paoContact = "";
                String paoId = doc.optString("pao_id", doc.optString("paoId", ""));

                if (doc.has("pao") && !doc.isNull("pao")) {
                    Object pObj = doc.get("pao");
                    if (pObj instanceof JSONObject) {
                        JSONObject pJson = (JSONObject) pObj;
                        if (paoId.isEmpty()) paoId = pJson.optString("uid", pJson.optString("id", ""));
                        paoName = pJson.optString("name", pJson.optString("full_name", "Unassigned PAO"));
                        paoEmail = CryptoUtils.decrypt(pJson.optString("email", ""), secretKey);
                        paoContact = CryptoUtils.decrypt(pJson.optString("contact_no", pJson.optString("contact", "")), secretKey);
                    } else if (pObj instanceof String) {
                        String strVal = (String) pObj;
                        if (!strVal.contains(" ") && strVal.length() > 15) paoId = strVal;
                        else paoName = strVal;
                    }
                }
                paoName = CryptoUtils.decrypt(paoName, secretKey);
                if (paoName == null || paoName.isEmpty() || paoName.equalsIgnoreCase("null")) paoName = "Unassigned PAO";
                else if (paoName.equalsIgnoreCase("off") || paoName.equalsIgnoreCase("rest")) paoName = "Rest Day";

                boolean isRestDay = "Rest Day".equalsIgnoreCase(driverName) || "Rest Day".equalsIgnoreCase(paoName);
                boolean isUnassigned = "Unassigned Driver".equalsIgnoreCase(driverName) || "Unassigned PAO".equalsIgnoreCase(paoName);

                Date parsedDate = parseDateString(rawDate, patterns, currentYear);
                String status;
                ScheduleItem item;

                if ("completed".equalsIgnoreCase(rawStatus) || "done".equalsIgnoreCase(rawStatus) || "finished".equalsIgnoreCase(rawStatus)) {
                    status = "Completed";
                    item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                    previous.add(item);
                } else if (parsedDate != null) {
                    Calendar parsedCal = Calendar.getInstance();
                    parsedCal.setTime(parsedDate);
                    parsedCal.set(Calendar.HOUR_OF_DAY, 0); parsedCal.set(Calendar.MINUTE, 0);
                    parsedCal.set(Calendar.SECOND, 0); parsedCal.set(Calendar.MILLISECOND, 0);
                    Date itemDate = parsedCal.getTime();

                    if (itemDate.equals(todayMidnight)) {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Assigned");
                        item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        today.add(item);
                    } else if (itemDate.after(todayMidnight)) {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Scheduled");
                        item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        upcoming.add(item);
                    } else {
                        status = "Completed";
                        item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        previous.add(item);
                    }
                } else {
                    // Fallback to day of week comparison if date parsing fails
                    int todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
                    int schedIndex = getDayIndex(dayStr);
                    if (schedIndex != -1) {
                        if (schedIndex == todayIndex) {
                            status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Assigned");
                            item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                            today.add(item);
                        } else {
                            // Assume completed if day index passed this week or something? Simplified fallback.
                            status = "Completed";
                            item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                            previous.add(item);
                        }
                    } else {
                        status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Scheduled");
                        item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                        upcoming.add(item);
                    }
                }

                fetchMissingProfileDetails(driverId, item, true);
                fetchMissingProfileDetails(paoId, item, false);
            }

            viewModel.setSchedules(today, upcoming, previous);
            viewModel.setLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        } catch (Exception e) {
            Log.e(TAG, "Parsing Error", e);
            viewModel.setLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        }
    }

    private Date parseDateString(String rawDate, String[] patterns, int currentYear) {
        if (rawDate == null || rawDate.isEmpty() || "N/A".equalsIgnoreCase(rawDate)) return null;
        String cleanDate = rawDate.trim();
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(cleanDate);
                if (d != null) {
                    if (!pattern.contains("yyyy")) {
                        Calendar c = Calendar.getInstance(); c.setTime(d);
                        c.set(Calendar.YEAR, currentYear);
                        return c.getTime();
                    }
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void fetchMissingProfileDetails(String userId, ScheduleItem item, boolean isDriver) {
        if (userId == null || userId.isEmpty()) return;

        // Firestore has built-in offline persistence by default on Android.
        // We can just use the normal get() and it will return cached data if offline.
        db.collection("File201").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists() && isAdded()) {
                String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                String first = CryptoUtils.decrypt(doc.getString("first_name"), secretKey);
                String last = CryptoUtils.decrypt(doc.getString("last_name"), secretKey);
                String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                if (!full.isEmpty()) {
                    if (isDriver) item.setDriverName(full); else item.setPaoName(full);
                    viewModel.notifyDataChanged();
                }
            }
        });
    }

    private int getDayIndex(String dayName) {
        if (dayName == null) return -1;
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

    private String formatJeepUnit(String rawJeep) {
        if (rawJeep.contains("(") && rawJeep.contains(")")) {
            int s = rawJeep.indexOf("("), e = rawJeep.indexOf(")");
            return rawJeep.substring(s + 1, e).trim() + " · " + rawJeep.substring(0, s).trim();
        }
        return rawJeep;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (shimmerContainer != null) shimmerContainer.stopShimmer();
        if (executor != null) executor.shutdownNow();
    }
}
