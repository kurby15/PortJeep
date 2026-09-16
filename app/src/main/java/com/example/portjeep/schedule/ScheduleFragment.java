package com.example.portjeep.schedule;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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

    // Tab Views (Order: 0: Previous, 1: Today, 2: Upcoming)
    private FrameLayout btnPrevious, btnToday, btnUpcoming;
    private View viewPreviousIndicator, viewTodayIndicator, viewUpcomingIndicator;
    private TextView tabPrevious, tabToday, tabUpcoming;
    
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
        viewModel = new ViewModelProvider(this).get(ScheduleViewModel.class);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_schedule);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::loadUserSchedules);
        }

        shimmerContainer = view.findViewById(R.id.shimmer_schedule_container);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        
        btnPrevious = view.findViewById(R.id.btn_previous);
        btnToday = view.findViewById(R.id.btn_today);
        btnUpcoming = view.findViewById(R.id.btn_upcoming);
        
        viewPreviousIndicator = view.findViewById(R.id.view_previous_indicator);
        viewTodayIndicator = view.findViewById(R.id.view_today_indicator);
        viewUpcomingIndicator = view.findViewById(R.id.view_upcoming_indicator);
        
        tabPrevious = view.findViewById(R.id.tab_previous);
        tabToday = view.findViewById(R.id.tab_today);
        tabUpcoming = view.findViewById(R.id.tab_upcoming);
        
        viewPagerSchedule = view.findViewById(R.id.view_pager_schedule);

        pagerAdapter = new SchedulePagerAdapter(this);
        viewPagerSchedule.setAdapter(pagerAdapter);

        if (btnPrevious != null) btnPrevious.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(0, true));
        if (btnToday != null) btnToday.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(1, true));
        if (btnUpcoming != null) btnUpcoming.setOnClickListener(v -> viewPagerSchedule.setCurrentItem(2, true));

        viewPagerSchedule.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                viewModel.setActiveTab(position);
                updateTabsUi(position);
            }
        });

        setupObservers();

        // Initialize to Today (1) by default
        int initialTab = viewModel.getActiveTab().getValue() != null ? viewModel.getActiveTab().getValue() : 1;
        viewPagerSchedule.setCurrentItem(initialTab, false);
        updateTabsUi(initialTab);

        if (!viewModel.hasData()) {
            loadUserSchedules();
        } else {
            hideLoadingSkeleton();
        }

        return view;
    }

    private void setupObservers() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (loading) showLoadingSkeleton(); else hideLoadingSkeleton();
        });

        viewModel.getActiveTab().observe(getViewLifecycleOwner(), tabIndex -> {
            updateEmptyStateVisibility(getActiveTabList(tabIndex));
        });

        viewModel.getPreviousList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 0) updateEmptyStateVisibility(list);
        });
        viewModel.getTodayList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 1) updateEmptyStateVisibility(list);
        });
        viewModel.getUpcomingList().observe(getViewLifecycleOwner(), list -> {
            if (viewModel.getActiveTab().getValue() != null && viewModel.getActiveTab().getValue() == 2) updateEmptyStateVisibility(list);
        });
    }

    private void showLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.startShimmer();
            shimmerContainer.setVisibility(View.VISIBLE);
        }
        // Use INVISIBLE instead of GONE to help ViewPager2 maintain layout state
        if (viewPagerSchedule != null) viewPagerSchedule.setVisibility(View.INVISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
    }

    private void hideLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.stopShimmer();
            shimmerContainer.setVisibility(View.GONE);
        }
        
        int currentTab = viewModel.getActiveTab().getValue() != null ? viewModel.getActiveTab().getValue() : 1;
        
        if (viewPagerSchedule != null) {
            viewPagerSchedule.setVisibility(View.VISIBLE);
            // Re-enforce the selection after making it visible to prevent jumping to index 0
            viewPagerSchedule.setCurrentItem(currentTab, false);
        }
        
        updateTabsUi(currentTab);
        updateEmptyStateVisibility(getActiveTabList(currentTab));
        
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updateEmptyStateVisibility(List<ScheduleItem> currentList) {
        Boolean loading = viewModel.getIsLoading().getValue();
        if (loading != null && loading) {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            // Stay invisible if loading
            return;
        }
        if (viewPagerSchedule != null) viewPagerSchedule.setVisibility(View.VISIBLE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
    }

    private void updateTabsUi(int tabIndex) {
        if (!isAdded()) return;
        int colorPrimary = ContextCompat.getColor(requireContext(), R.color.color_brand_primary);
        int colorWhite = ContextCompat.getColor(requireContext(), R.color.white);

        if (viewPreviousIndicator != null) viewPreviousIndicator.setVisibility(tabIndex == 0 ? View.VISIBLE : View.GONE);
        if (viewTodayIndicator != null) viewTodayIndicator.setVisibility(tabIndex == 1 ? View.VISIBLE : View.GONE);
        if (viewUpcomingIndicator != null) viewUpcomingIndicator.setVisibility(tabIndex == 2 ? View.VISIBLE : View.GONE);

        if (tabPrevious != null) tabPrevious.setTextColor(tabIndex == 0 ? colorPrimary : colorWhite);
        if (tabToday != null) tabToday.setTextColor(tabIndex == 1 ? colorPrimary : colorWhite);
        if (tabUpcoming != null) tabUpcoming.setTextColor(tabIndex == 2 ? colorPrimary : colorWhite);
    }

    private List<ScheduleItem> getActiveTabList(int tabIndex) {
        if (viewModel == null) return new ArrayList<>();
        if (tabIndex == 0) return viewModel.getPreviousList().getValue();
        if (tabIndex == 1) return viewModel.getTodayList().getValue();
        return viewModel.getUpcomingList().getValue();
    }

    private static class SchedulePagerAdapter extends FragmentStateAdapter {
        public SchedulePagerAdapter(@NonNull Fragment fragment) { super(fragment); }
        @NonNull @Override public Fragment createFragment(int position) { return SchedulePageFragment.newInstance(position); }
        @Override public int getItemCount() { return 3; }
    }

    private void loadUserSchedules() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            if (getContext() != null) Toast.makeText(getContext(), "Please log in first.", Toast.LENGTH_SHORT).show();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        viewModel.setLoading(true);
        currentUser.getIdToken(true).addOnSuccessListener(result -> {
            if (!isAdded()) return;
            fetchSchedulesFromApi(result.getToken());
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            String cachedData = PreferenceManager.getSchedulesCache(getContext());
            if (cachedData != null) parseAndDisplaySchedules(cachedData);
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
                        String cachedData = PreferenceManager.getSchedulesCache(getContext());
                        if (cachedData != null) parseAndDisplaySchedules(cachedData);
                        viewModel.setLoading(false);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isAdded()) {
                        String cachedData = PreferenceManager.getSchedulesCache(getContext());
                        if (cachedData != null) parseAndDisplaySchedules(cachedData);
                        viewModel.setLoading(false);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    }
                });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
        try {
            JSONObject root = new JSONObject(jsonResponse);
            if (!root.optBoolean("success", false)) {
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

            String[] patterns = {"yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"};

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);
                String rawDate = doc.optString("date", "N/A");
                String dayStr = doc.optString("day", "Scheduled");
                String rawStatus = doc.optString("status", "").toLowerCase(Locale.US);
                String jeep = formatJeepUnit(doc.optString("jeep", "Unassigned Unit"));

                String driverName = "Unassigned Driver", driverEmail = "", driverContact = "";
                String driverId = doc.optString("driver_id", doc.optString("driverId", ""));
                if (doc.has("driver") && !doc.isNull("driver")) {
                    Object dObj = doc.get("driver");
                    if (dObj instanceof JSONObject) {
                        JSONObject dJson = (JSONObject) dObj;
                        if (driverId.isEmpty()) driverId = dJson.optString("uid", dJson.optString("id", ""));
                        driverName = CryptoUtils.decrypt(dJson.optString("name", dJson.optString("full_name", "Unassigned Driver")), secretKey);
                        driverEmail = CryptoUtils.decrypt(dJson.optString("email", ""), secretKey);
                        driverContact = CryptoUtils.decrypt(dJson.optString("contact_no", dJson.optString("contact", "")), secretKey);
                    }
                }
                if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("null")) driverName = "Unassigned Driver";

                String paoName = "Unassigned PAO", paoEmail = "", paoContact = "";
                String paoId = doc.optString("pao_id", doc.optString("paoId", ""));
                if (doc.has("pao") && !doc.isNull("pao")) {
                    Object pObj = doc.get("pao");
                    if (pObj instanceof JSONObject) {
                        JSONObject pJson = (JSONObject) pObj;
                        if (paoId.isEmpty()) paoId = pJson.optString("uid", pJson.optString("id", ""));
                        paoName = CryptoUtils.decrypt(pJson.optString("name", pJson.optString("full_name", "Unassigned PAO")), secretKey);
                        paoEmail = CryptoUtils.decrypt(pJson.optString("email", ""), secretKey);
                        paoContact = CryptoUtils.decrypt(pJson.optString("contact_no", pJson.optString("contact", "")), secretKey);
                    }
                }
                if (paoName == null || paoName.isEmpty() || paoName.equalsIgnoreCase("null")) paoName = "Unassigned PAO";

                boolean isRestDay = "Rest Day".equalsIgnoreCase(driverName) || "Rest Day".equalsIgnoreCase(paoName);
                boolean isUnassigned = "Unassigned Driver".equalsIgnoreCase(driverName) || "Unassigned PAO".equalsIgnoreCase(paoName);

                Date parsedDate = parseDateString(rawDate, patterns, currentYear);
                String status;
                ScheduleItem item;

                if ("completed".equalsIgnoreCase(rawStatus) || "done".equalsIgnoreCase(rawStatus) || "finished".equalsIgnoreCase(rawStatus)) {
                    status = "Completed"; item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
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
                    status = isRestDay ? "Rest Day" : (isUnassigned ? "Unassigned" : "Scheduled");
                    item = new ScheduleItem(dayStr, rawDate, status, jeep, driverName, driverEmail, driverContact, paoName, paoEmail, paoContact);
                    upcoming.add(item);
                }
                fetchMissingProfileDetails(driverId, item, true);
                fetchMissingProfileDetails(paoId, item, false);
            }

            viewModel.setSchedules(today, upcoming, previous);
            viewModel.setLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        } catch (Exception e) {
            viewModel.setLoading(false);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        }
    }

    private Date parseDateString(String rawDate, String[] patterns, int currentYear) {
        if (rawDate == null || rawDate.isEmpty() || "N/A".equalsIgnoreCase(rawDate)) return null;
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(rawDate.trim());
                if (d != null) {
                    if (!pattern.contains("yyyy")) { Calendar c = Calendar.getInstance(); c.setTime(d); c.set(Calendar.YEAR, currentYear); return c.getTime(); }
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void fetchMissingProfileDetails(String userId, ScheduleItem item, boolean isDriver) {
        if (userId == null || userId.isEmpty()) return;
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