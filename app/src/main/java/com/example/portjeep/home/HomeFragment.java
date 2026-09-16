package com.example.portjeep.home;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.salary.SalaryFragment;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.PreferenceManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String API_URL = "https://port-jeep.vercel.app/api/mobile/schedules";
    private static final String REMITTANCE_API_URL = "https://port-jeep.vercel.app/api/mobile/remittances";
    private static final long CACHE_DURATION = 60 * 60 * 1000; // 1 hour

    // Session flags to ensure data is fresh on first load after app launch
    private static boolean isProfileRefreshed = false;
    private static boolean isScheduleRefreshed = false;
    private static boolean isSummaryRefreshed = false;

    // Loading Skeletons
    private ShimmerFrameLayout shimmerContainer, shimmerHeader, shimmerSummary;
    private ShimmerFrameLayout shimmerQuickAccess, shimmerBanner, shimmerUpcoming;
    private View llQuickAccessContent, llBannerContent, rlHeaderContent;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Actual Content Views
    private MaterialCardView cardTodayAssignment;
    private View llTodaysSummary;
    private TextView tvSummaryTrips, tvSummaryDistance, tvSummaryGross, tvSummaryNet;
    private TextView tvSummaryShareLabel, tvSummaryShareUnit;

    // ViewPager2 Status Carousel References
    private ViewPager2 vpStatusCarousel;
    private LinearLayout containerDotsIndicator;
    private StatusBannerAdapter bannerAdapter;
    private final List<StatusBannerAdapter.BannerItem> bannerItems = new ArrayList<>();

    // View References
    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName, tvTodayRoute;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;
    private LinearLayout containerDriverPill, containerPaoPill;

    // Firebase & Background Thread
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecimalFormat df = new DecimalFormat("#,##0.00");

    // User Profile & Unassigned Schedule Data State
    private final List<String> userRestDays = new ArrayList<>();
    private final List<JSONObject> unassignedSchedulesList = new ArrayList<>();
    private String userRole = "";

    public HomeFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                showLoadingSkeleton();
                updateDynamicGreeting();
                isProfileRefreshed = false;
                loadUserProfile();
                isScheduleRefreshed = false;
                isSummaryRefreshed = false;
                loadSchedulesFromApi();
                loadRemittanceSummary();
            });
        }

        shimmerHeader = view.findViewById(R.id.shimmer_header);
        rlHeaderContent = view.findViewById(R.id.rl_header_content);
        shimmerContainer = view.findViewById(R.id.shimmer_view_container);
        cardTodayAssignment = view.findViewById(R.id.card_today_assignment);
        shimmerSummary = view.findViewById(R.id.shimmer_summary);
        llTodaysSummary = view.findViewById(R.id.ll_todays_summary);
        shimmerQuickAccess = view.findViewById(R.id.shimmer_quick_access);
        llQuickAccessContent = view.findViewById(R.id.ll_quick_access_content);
        shimmerBanner = view.findViewById(R.id.shimmer_banner);
        llBannerContent = view.findViewById(R.id.ll_banner_content);
        shimmerUpcoming = view.findViewById(R.id.shimmer_upcoming);
        containerUpcoming = view.findViewById(R.id.container_upcoming);

        tvSummaryTrips = view.findViewById(R.id.tv_summary_trips);
        tvSummaryDistance = view.findViewById(R.id.tv_summary_distance);
        tvSummaryGross = view.findViewById(R.id.tv_summary_gross);
        tvSummaryNet = view.findViewById(R.id.tv_summary_net);
        tvSummaryShareLabel = view.findViewById(R.id.tv_summary_share_label);
        tvSummaryShareUnit = view.findViewById(R.id.tv_summary_share_unit);

        vpStatusCarousel = view.findViewById(R.id.vp_status_carousel);
        containerDotsIndicator = view.findViewById(R.id.container_dots_indicator);

        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDriverName = view.findViewById(R.id.tv_driver_name);
        tvRoleBadge = view.findViewById(R.id.tv_role_badge);

        ivRobot = view.findViewById(R.id.iv_robot);
        if (ivRobot != null && isAdded() && getContext() != null) {
            Glide.with(requireContext()).asGif().load(R.drawable.robot2).into(ivRobot);
        }

        tvUnitNo = view.findViewById(R.id.tv_unit_no);
        tvPlateNo = view.findViewById(R.id.tv_plate_no);
        tvTodayRoute = view.findViewById(R.id.tv_today_route);
        tvJeepStatus = view.findViewById(R.id.tv_jeep_status);
        tvAssignmentStatus = view.findViewById(R.id.tv_assignment_status);
        tvDriverFullName = view.findViewById(R.id.tv_driver_fullname);
        tvPaoFullName = view.findViewById(R.id.tv_pao_fullname);
        containerDriverPill = view.findViewById(R.id.container_driver_pill);
        containerPaoPill = view.findViewById(R.id.container_pao_pill);
        cardMySchedule = view.findViewById(R.id.card_my_schedule);
        cardSalary = view.findViewById(R.id.card_salary);

        tvTodayDate = view.findViewById(R.id.tv_today_date);
        if (tvTodayDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvTodayDate.setText(dateFormat.format(new Date()));
        }

        setupStatusCarousel();
        resetDynamicUI();
        loadOfflineUserProfile();

        // Optimize theme change: if valid cache exists and already refreshed in this session, don't show skeleton
        boolean hasValidCache = false;
        Context ctx = getContext();
        if (ctx != null) {
            String cachedData = PreferenceManager.getSchedulesCache(ctx);
            long lastFetch = PreferenceManager.getSchedulesLastFetchTime(ctx);
            if (cachedData != null && (System.currentTimeMillis() - lastFetch < CACHE_DURATION) && isScheduleRefreshed) {
                hasValidCache = true;
            }
        }

        if (hasValidCache) {
            hideLoadingSkeleton();
        } else {
            showLoadingSkeleton();
        }

        updateDynamicGreeting();
        loadUserProfile();
        loadSchedulesFromApi();
        loadRemittanceSummary();

        setupClickListeners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRemittanceSummary();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadRemittanceSummary();
        }
    }

    private void loadRemittanceSummary() {
        Context context = getContext();
        if (context == null) return;

        String cachedSalary = PreferenceManager.getSalaryCache(context);
        long lastFetch = PreferenceManager.getSalaryLastFetchTime(context);
        long now = System.currentTimeMillis();

        if (cachedSalary != null) {
            try {
                processRemittanceSummary(new JSONArray(cachedSalary));
                if (now - lastFetch < CACHE_DURATION && isSummaryRefreshed) return;
            } catch (Exception ignored) {}
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.getIdToken(false).addOnSuccessListener(result -> {
                isSummaryRefreshed = true;
                fetchRemittancesForHome(result.getToken());
            });
        }
    }

    private void fetchRemittancesForHome(String token) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(REMITTANCE_API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    String raw = sb.toString();
                    handler.post(() -> {
                        if (!isAdded()) return;
                        try {
                            JSONObject root = new JSONObject(raw);
                            if (root.optBoolean("success")) {
                                JSONArray remittances = root.optJSONArray("remittances");
                                if (remittances != null) {
                                    PreferenceManager.saveSalaryCache(getContext(), remittances.toString());
                                    processRemittanceSummary(remittances);
                                }
                            }
                        } catch (Exception e) { Log.e(TAG, "Error parsing remittance", e); }
                    });
                }
            } catch (Exception e) { Log.e(TAG, "Error fetching remittance", e); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void processRemittanceSummary(JSONArray remittances) {
        if (remittances == null || remittances.length() == 0 || !isAdded()) {
            resetTodaySummaryUI();
            return;
        }

        try {
            JSONObject latestGroup = null;
            
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
            SimpleDateFormat fullFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
            SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            
            Date now = new Date();
            String todayName = dayFormat.format(now);
            String todayFormatted = fullFormat.format(now);
            String todayDateKey = dateKeyFormat.format(now);

            for (int i = 0; i < remittances.length(); i++) {
                JSONObject dayGroup = remittances.optJSONObject(i);
                if (dayGroup == null) continue;

                String dayName = dayGroup.optString("day", "N/A");
                String groupDate = dayGroup.optString("date", dayName);

                boolean isToday = dayName.equalsIgnoreCase(todayName) || 
                                 groupDate.contains(todayFormatted) || 
                                 groupDate.equalsIgnoreCase(todayName) ||
                                 groupDate.contains(todayDateKey);

                if (isToday) {
                    latestGroup = dayGroup;
                    break;
                }
            }

            if (latestGroup == null) {
                resetTodaySummaryUI();
                return;
            }

            JSONArray partials = latestGroup.optJSONArray("remittances");
            int tripCount = 0;
            double sumGross = 0, sumNet = 0, sumShare = 0;

            if (partials != null) {
                tripCount = partials.length();
                for (int i = 0; i < partials.length(); i++) {
                    JSONObject p = partials.optJSONObject(i);
                    if (p == null) continue;
                    sumGross += p.optDouble("gross", 0);
                    sumNet += p.optDouble("net", 0);
                    sumShare += p.optDouble("employeeCut", 0);
                }
            }

            double totalIncentives = latestGroup.optDouble("incentive", 0.0);
            sumNet -= (totalIncentives * 2);

            final int finalTrips = tripCount;
            final double finalGross = sumGross;
            final double finalNet = sumNet;
            final double finalShare = sumShare;

            handler.post(() -> {
                if (!isAdded()) return;
                Context context = getContext();
                boolean isVisible = true;
                if (context != null) {
                    String userId = PreferenceManager.getCurrentUserId(context);
                    String key = "is_visible_" + userId;
                    android.content.SharedPreferences prefs = context.getSharedPreferences("salary_prefs", Context.MODE_PRIVATE);
                    isVisible = prefs.getBoolean(key, true);
                }

                final String hiddenText = "₱ ••••";
                final String grossStr = isVisible ? "₱" + df.format(finalGross) : hiddenText;
                final String netStr = isVisible ? "₱" + df.format(finalNet) : hiddenText;
                final String shareStr = isVisible ? "₱" + df.format(finalShare) : hiddenText;

                if (tvSummaryTrips != null) tvSummaryTrips.setText(String.valueOf(finalTrips));
                if (tvSummaryGross != null) tvSummaryGross.setText(grossStr);
                if (tvSummaryNet != null) tvSummaryNet.setText(netStr);

                String role = PreferenceManager.getUserRole(context);
                if (role != null && role.toUpperCase().contains("DRIVER")) {
                    String name = (tvDriverFullName != null) ? tvDriverFullName.getText().toString() : "Driver";
                    if (name.isEmpty() || name.contains("Unassigned") || name.contains("Duty")) name = "Driver";
                    if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText(getString(R.string.possessive_name, name, "Share"));
                    if (tvSummaryDistance != null) tvSummaryDistance.setText(shareStr);
                } else if (role != null && (role.toUpperCase().contains("PAO") || role.toUpperCase().contains("ASSISTANT"))) {
                    String name = (tvPaoFullName != null) ? tvPaoFullName.getText().toString() : "PAO";
                    if (name.isEmpty() || name.contains("Unassigned") || name.contains("Duty")) name = "PAO";
                    if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText(getString(R.string.possessive_name, name, "Share"));
                    if (tvSummaryDistance != null) tvSummaryDistance.setText(shareStr);
                }
                if (tvSummaryShareUnit != null) tvSummaryShareUnit.setText("Today");
            });

        } catch (Exception e) { Log.e(TAG, "Error processing summary UI", e); }
    }

    private void resetTodaySummaryUI() {
        handler.post(() -> {
            if (!isAdded()) return;
            if (tvSummaryTrips != null) tvSummaryTrips.setText("0");
            if (tvSummaryGross != null) tvSummaryGross.setText("₱0.00");
            if (tvSummaryNet != null) tvSummaryNet.setText("₱0.00");
            if (tvSummaryDistance != null) tvSummaryDistance.setText("₱0.00");
            if (tvSummaryShareUnit != null) tvSummaryShareUnit.setText("Today");
            
            Context context = getContext();
            String role = PreferenceManager.getUserRole(context);
            if (role != null && role.toUpperCase().contains("DRIVER")) {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("Driver's Share");
            } else if (role != null && (role.toUpperCase().contains("PAO") || role.toUpperCase().contains("ASSISTANT"))) {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("PAO's Share");
            } else {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("Your Share");
            }
        });
    }

    private void loadOfflineUserProfile() {
        Context context = getContext();
        if (context == null) return;
        String cachedName = PreferenceManager.getUserFirstName(context);
        String cachedRole = PreferenceManager.getUserRole(context);
        Set<String> cachedRestDays = PreferenceManager.getUserRestDays(context);

        if (cachedName != null && !cachedName.isEmpty() && tvDriverName != null) tvDriverName.setText(cachedName);
        if (cachedRole != null && !cachedRole.isEmpty()) {
            userRole = cachedRole;
            if (tvRoleBadge != null) { tvRoleBadge.setText(userRole); tvRoleBadge.setVisibility(View.VISIBLE); }
        }
        if (cachedRestDays != null && !cachedRestDays.isEmpty()) {
            userRestDays.clear(); userRestDays.addAll(cachedRestDays);
            refreshStatusCarousel();
        }
    }

    private void setupStatusCarousel() {
        if (vpStatusCarousel == null) return;
        bannerAdapter = new StatusBannerAdapter(getContext(), bannerItems);
        vpStatusCarousel.setAdapter(bannerAdapter);
        vpStatusCarousel.setOffscreenPageLimit(3);
        vpStatusCarousel.setClipToPadding(false);
        vpStatusCarousel.setClipChildren(false);
        int paddingHorizontal = (int) (24 * getResources().getDisplayMetrics().density);
        vpStatusCarousel.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        vpStatusCarousel.setPageTransformer((page, position) -> {
            float density = getResources().getDisplayMetrics().density;
            if (position <= -1f) { page.setAlpha(0f); page.setTranslationX(0f); }
            else if (position < 0f) {
                float factor = Math.abs(position);
                page.setAlpha(1f - factor);
                page.setTranslationY(-factor * 60f * density);
                page.setRotation(position * 8f);
                page.setScaleX(1f); page.setScaleY(1f); page.setTranslationX(0f);
                page.setTranslationZ((1f - factor) * 10f);
            } else if (position <= 3f) {
                page.setAlpha(Math.max(0.6f, 1f - (position * 0.15f)));
                float scale = 1f - (position * 0.04f);
                page.setScaleX(scale); page.setScaleY(scale);
                float peekOffset = 20 * density;
                page.setTranslationX(-position * page.getWidth() + (position * peekOffset));
                page.setTranslationZ(-position * 10f);
            } else { page.setAlpha(0f); }
        });
        vpStatusCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) { updateDotsIndicator(position); }
        });
    }

    private void refreshStatusCarousel() {
        if (!isAdded() || getContext() == null) return;
        bannerItems.clear();
        bannerItems.add(new StatusBannerAdapter.BannerItem(StatusBannerAdapter.BannerItem.TYPE_REST_DAY, "Rest Day Schedule", "Your upcoming rest day assignments:", userRestDays, null));
        String unassignedDesc = unassignedSchedulesList.isEmpty() ? "Perfect! All your shifts are successfully assigned." : "Heads up! These shifts currently have no unit assigned:";
        bannerItems.add(new StatusBannerAdapter.BannerItem(StatusBannerAdapter.BannerItem.TYPE_UNASSIGNED, "Unassigned Log", unassignedDesc, null, unassignedSchedulesList));
        if (vpStatusCarousel != null && bannerAdapter != null) {
            vpStatusCarousel.post(() -> {
                if (isAdded() && bannerAdapter != null) {
                    bannerAdapter.notifyDataSetChanged();
                    setupDotsIndicator(bannerItems.size());
                }
            });
        }
    }

    private void setupDotsIndicator(int count) {
        if (containerDotsIndicator == null || getContext() == null) return;
        containerDotsIndicator.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int dotSizePx = (int) (10 * density);
        int marginPx = (int) (5 * density);
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(getContext());
            dot.setImageResource(R.drawable.bg_circle_icon);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dot.setLayoutParams(params);
            containerDotsIndicator.addView(dot);
        }
        updateDotsIndicator(0);
    }

    private void updateDotsIndicator(int position) {
        if (containerDotsIndicator == null || getContext() == null) return;
        int activeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_brand_primary);
        int inactiveColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_divider);
        int childCount = containerDotsIndicator.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView dot = (ImageView) containerDotsIndicator.getChildAt(i);
            if (dot != null) {
                if (i == position) { dot.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor)); dot.setAlpha(1.0f); dot.setScaleX(1.0f); dot.setScaleY(1.0f); }
                else { dot.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor)); dot.setAlpha(0.6f); dot.setScaleX(0.85f); dot.setScaleY(0.85f); }
            }
        }
    }

    private void setupClickListeners() {
        if (cardMySchedule != null) cardMySchedule.setOnClickListener(v -> {
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                BottomNavigationView navBar = activity.findViewById(R.id.bottom_navigation);
                if (navBar != null) navBar.setSelectedItemId(R.id.nav_schedule);
            }
        });
        if (cardSalary != null) cardSalary.setOnClickListener(v -> {
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing()) {
                BottomNavigationView navBar = activity.findViewById(R.id.bottom_navigation);
                if (navBar != null) navBar.setSelectedItemId(R.id.nav_salary);
            }
        });
    }

    private void showLoadingSkeleton() {
        if (shimmerHeader != null) { shimmerHeader.startShimmer(); shimmerHeader.setVisibility(View.VISIBLE); }
        if (rlHeaderContent != null) rlHeaderContent.setVisibility(View.GONE);
        if (shimmerContainer != null) { shimmerContainer.startShimmer(); shimmerContainer.setVisibility(View.VISIBLE); }
        if (cardTodayAssignment != null) cardTodayAssignment.setVisibility(View.GONE);
        if (shimmerSummary != null) { shimmerSummary.startShimmer(); shimmerSummary.setVisibility(View.VISIBLE); }
        if (llTodaysSummary != null) llTodaysSummary.setVisibility(View.GONE);
        if (shimmerQuickAccess != null) { shimmerQuickAccess.startShimmer(); shimmerQuickAccess.setVisibility(View.VISIBLE); }
        if (llQuickAccessContent != null) llQuickAccessContent.setVisibility(View.GONE);
        if (shimmerBanner != null) { shimmerBanner.startShimmer(); shimmerBanner.setVisibility(View.VISIBLE); }
        if (llBannerContent != null) llBannerContent.setVisibility(View.VISIBLE);
        if (shimmerUpcoming != null) { shimmerUpcoming.startShimmer(); shimmerUpcoming.setVisibility(View.VISIBLE); }
        if (containerUpcoming != null) containerUpcoming.setVisibility(View.GONE);
    }

    private void hideLoadingSkeleton() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (shimmerHeader != null) { shimmerHeader.stopShimmer(); shimmerHeader.setVisibility(View.GONE); }
        if (rlHeaderContent != null) rlHeaderContent.setVisibility(View.VISIBLE);
        if (shimmerContainer != null) { shimmerContainer.stopShimmer(); shimmerContainer.setVisibility(View.GONE); }
        if (cardTodayAssignment != null) cardTodayAssignment.setVisibility(View.VISIBLE);
        if (shimmerSummary != null) { shimmerSummary.stopShimmer(); shimmerSummary.setVisibility(View.GONE); }
        if (llTodaysSummary != null) llTodaysSummary.setVisibility(View.VISIBLE);
        if (shimmerQuickAccess != null) { shimmerQuickAccess.stopShimmer(); shimmerQuickAccess.setVisibility(View.GONE); }
        if (llQuickAccessContent != null) llQuickAccessContent.setVisibility(View.VISIBLE);
        if (shimmerBanner != null) { shimmerBanner.stopShimmer(); shimmerBanner.setVisibility(View.GONE); }
        if (llBannerContent != null) llBannerContent.setVisibility(View.VISIBLE);
        if (shimmerUpcoming != null) { shimmerUpcoming.stopShimmer(); shimmerUpcoming.setVisibility(View.GONE); }
        if (containerUpcoming != null) containerUpcoming.setVisibility(View.VISIBLE);
    }

    private void resetDynamicUI() {
        if (tvDriverName != null) tvDriverName.setText("");
        if (tvRoleBadge != null) tvRoleBadge.setText("");
        setNoAssignmentUI();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        if (isProfileRefreshed) return;
        db.collection("File201").document(currentUser.getUid()).get(Source.SERVER).addOnSuccessListener(documentSnapshot -> {
            if (isAdded() && documentSnapshot.exists()) {
                isProfileRefreshed = true;
                extractUserProfileAndRestDays(documentSnapshot);
            }
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            db.collection("File201").document(currentUser.getUid()).get(Source.CACHE).addOnSuccessListener(cacheSnapshot -> {
                if (isAdded() && cacheSnapshot.exists()) extractUserProfileAndRestDays(cacheSnapshot);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void extractUserProfileAndRestDays(DocumentSnapshot doc) {
        if (!isAdded()) return;
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
        String rawFirstName = doc.getString("first_name");
        String firstName = CryptoUtils.decrypt(rawFirstName, secretKey);
        if (firstName != null && tvDriverName != null) tvDriverName.setText(firstName);
        Object restDaysObj = doc.get("rest_days");
        if (restDaysObj instanceof List<?>) {
            userRestDays.clear();
            for (Object item : (List<?>) restDaysObj) if (item instanceof String) userRestDays.add(((String) item).trim());
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.getIdToken(false).addOnSuccessListener(result -> {
                if (!isAdded()) return;
                Object claimRole = result.getClaims().get("role");
                if (claimRole != null) { applyRoleBadgeUI(claimRole.toString()); saveCurrentProfileToCache(); }
            });
        }
        String positionId = doc.getString("position_id");
        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions").document(positionId).get().addOnSuccessListener(posDoc -> {
                if (isAdded() && posDoc.exists()) {
                    String rawTitle = posDoc.getString("title");
                    if (rawTitle == null) rawTitle = posDoc.getString("name");
                    if (rawTitle == null) rawTitle = posDoc.getString("position");
                    String positionTitle = CryptoUtils.decrypt(rawTitle, secretKey);
                    applyRoleBadgeUI(positionTitle); saveCurrentProfileToCache();
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Error fetching position title", e));
        }
        refreshStatusCarousel();
        saveCurrentProfileToCache();
    }

    private void saveCurrentProfileToCache() {
        Context context = getContext();
        if (context == null) return;
        String firstName = tvDriverName != null ? tvDriverName.getText().toString() : "";
        PreferenceManager.saveUserProfile(context, firstName, userRole, userRestDays);
    }

    private void applyRoleBadgeUI(String positionTitle) {
        if (!isAdded() || positionTitle == null || positionTitle.trim().isEmpty()) return;
        String upperPosition = positionTitle.toUpperCase().trim();
        if (upperPosition.contains("PUBLIC ASSISTANT") || upperPosition.contains("PAO")) userRole = "PUBLIC ASSISTANT OFFICER";
        else if (upperPosition.contains("DRIVER")) userRole = "DRIVER";
        else userRole = upperPosition;
        if (tvRoleBadge != null && !userRole.isEmpty()) { tvRoleBadge.setText(userRole); tvRoleBadge.setVisibility(View.VISIBLE); }
        loadRemittanceSummary();
    }

    private void loadSchedulesFromApi() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) { hideLoadingSkeleton(); return; }
        String cachedData = PreferenceManager.getSchedulesCache(getContext());
        long lastFetch = PreferenceManager.getSchedulesLastFetchTime(getContext());
        long now = System.currentTimeMillis();

        if (cachedData != null) {
            parseAndDisplaySchedules(cachedData);
            if (now - lastFetch < CACHE_DURATION && isScheduleRefreshed) {
                hideLoadingSkeleton();
                return;
            }
        }

        currentUser.getIdToken(false).addOnSuccessListener(result -> {
            isScheduleRefreshed = true;
            fetchSchedulesFromApi(result.getToken());
        }).addOnFailureListener(e -> {
            if (!isAdded()) return;
            hideLoadingSkeleton();
        });
    }

    private void fetchSchedulesFromApi(String idToken) {
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
                    }
                    hideLoadingSkeleton();
                });
            } catch (Exception e) { handler.post(() -> { if (isAdded()) hideLoadingSkeleton(); }); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
        if (!isAdded()) return;
        try {
            JSONObject root = new JSONObject(jsonResponse);
            if (!root.optBoolean("success", false)) { setNoAssignmentUI(); return; }
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) { setNoAssignmentUI(); renderUpcomingScheduleList(new ArrayList<>()); return; }
            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);
            Date todayAtMidnight = todayCal.getTime();
            int currentYear = todayCal.get(Calendar.YEAR);
            int todayIndex = todayCal.get(Calendar.DAY_OF_WEEK);
            String[] datePatterns = new String[]{"yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"};
            JSONObject todayScheduleDoc = null;
            List<JSONObject> upcomingScheduleDocs = new ArrayList<>();
            unassignedSchedulesList.clear();
            Set<String> scheduledDays = new HashSet<>();

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);
                String dayStr = doc.optString("day", "").trim();
                if (!dayStr.isEmpty()) scheduledDays.add(dayStr.toLowerCase(Locale.US));

                String drvName = parseName(doc, "driver", "Unassigned Driver");
                String paoName = parseName(doc, "pao", "Unassigned PAO");
                String jeep = doc.optString("jeep", "").trim();

                // Logic for identifying unassigned shifts
                if ("Unassigned Driver".equalsIgnoreCase(drvName) || "Unassigned PAO".equalsIgnoreCase(paoName) || jeep.isEmpty() || "N/A".equalsIgnoreCase(jeep) || "Unassigned".equalsIgnoreCase(jeep)) {
                    unassignedSchedulesList.add(doc);
                }

                if ("completed".equalsIgnoreCase(doc.optString("status", ""))) continue;
                Date parsedDate = parseDateString(doc.optString("date", "N/A"), datePatterns, currentYear);
                if (parsedDate != null) {
                    Calendar parsedCal = Calendar.getInstance(); parsedCal.setTime(parsedDate);
                    parsedCal.set(Calendar.HOUR_OF_DAY, 0); parsedCal.set(Calendar.MINUTE, 0);
                    parsedCal.set(Calendar.SECOND, 0); parsedCal.set(Calendar.MILLISECOND, 0);
                    if (parsedCal.getTime().equals(todayAtMidnight)) todayScheduleDoc = doc;
                    else if (parsedCal.getTime().after(todayAtMidnight)) upcomingScheduleDocs.add(doc);
                } else {
                    int sIdx = getDayIndex(dayStr);
                    if (sIdx == todayIndex) todayScheduleDoc = doc;
                    else if (isDayUpcoming(todayIndex, sIdx)) upcomingScheduleDocs.add(doc);
                }
            }

            String[] weekDays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            for (String dName : weekDays) {
                if (!scheduledDays.contains(dName.toLowerCase(Locale.US))) {
                    boolean isRest = false;
                    for (String rDay : userRestDays) if (rDay.equalsIgnoreCase(dName)) { isRest = true; break; }
                    if (!isRest) {
                        JSONObject unDoc = new JSONObject(); unDoc.put("day", dName); unDoc.put("status", "Unassigned");
                        unassignedSchedulesList.add(unDoc);
                    }
                }
            }

            if (todayScheduleDoc != null) processTodaySchedule(todayScheduleDoc); else setNoAssignmentUI();
            renderUpcomingScheduleList(upcomingScheduleDocs);
            refreshStatusCarousel();
        } catch (Exception e) { setNoAssignmentUI(); }
    }

    private void renderUpcomingScheduleList(List<JSONObject> docs) {
        if (!isAdded() || containerUpcoming == null) return;
        containerUpcoming.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (docs.isEmpty()) {
            TextView tvEmpty = new TextView(getContext()); tvEmpty.setText("No upcoming schedules found.");
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            int padding = (int) (20 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(0, padding, 0, padding); containerUpcoming.addView(tvEmpty);
            return;
        }
        for (JSONObject doc : docs) {
            View itemView = inflater.inflate(R.layout.item_upcoming_schedule, containerUpcoming, false);
            String day = doc.optString("day", "Scheduled"), date = doc.optString("date", "N/A");
            String jeep = doc.optString("jeep", "Unassigned Unit"), route = doc.optString("route", "Minuyan - Starmall Loop");
            String driver = parseName(doc, "driver", "Unassigned Driver"), pao = parseName(doc, "pao", "Unassigned PAO");
            ((TextView) itemView.findViewById(R.id.tv_schedule_day)).setText(day);
            ((TextView) itemView.findViewById(R.id.tv_schedule_date)).setText(date);
            ((TextView) itemView.findViewById(R.id.tv_schedule_status)).setText("● Scheduled");
            itemView.setOnClickListener(v -> showScheduleDetailsModal(day, date, jeep, route, driver, pao));
            containerUpcoming.addView(itemView);
        }
    }

    private void showScheduleDetailsModal(String day, String date, String jeep, String route, String driver, String pao) {
        Context context = getContext(); if (context == null || !isAdded()) return;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_schedule_details, null);
        ((TextView) view.findViewById(R.id.tv_schedule_day)).setText(day);
        ((TextView) view.findViewById(R.id.tv_schedule_date)).setText(date);
        ((TextView) view.findViewById(R.id.tv_schedule_status)).setText("● Scheduled");
        String unit = jeep, plate = "N/A";
        if (jeep.contains("(") && jeep.contains(")")) {
            plate = jeep.substring(0, jeep.indexOf("(")).trim();
            unit = jeep.substring(jeep.indexOf("(") + 1, jeep.indexOf(")")).trim();
        } else if (jeep.contains(" · ")) {
            String[] parts = jeep.split(" · "); unit = parts[0]; plate = parts[1];
        }
        ((TextView) view.findViewById(R.id.tv_jeep_unit)).setText(unit);
        ((TextView) view.findViewById(R.id.tv_plate_no)).setText(plate);
        ((TextView) view.findViewById(R.id.tv_schedule_route)).setText(route);
        ((TextView) view.findViewById(R.id.tv_driver_name)).setText(driver);
        ((TextView) view.findViewById(R.id.tv_pao_name)).setText(pao);
        showCenteredDialog(view);
    }

    private Date parseDateString(String rawDate, String[] patterns, int currentYear) {
        if (rawDate == null || rawDate.trim().isEmpty() || "N/A".equalsIgnoreCase(rawDate)) return null;
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                Date parsed = sdf.parse(rawDate.trim());
                if (parsed != null) {
                    if (!pattern.contains("yyyy")) {
                        Calendar cal = Calendar.getInstance(); cal.setTime(parsed);
                        cal.set(Calendar.YEAR, currentYear); return cal.getTime();
                    }
                    return parsed;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isDayUpcoming(int todayIndex, int targetIndex) {
        int diff = targetIndex - todayIndex;
        if (diff < 0) diff += 7;
        return diff > 0 && diff <= 3;
    }

    private void processTodaySchedule(JSONObject doc) {
        if (!isAdded()) return;
        String rawJeep = doc.optString("jeep", "N/A");
        String route = doc.optString("route", "Minuyan - Starmall Loop");
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        String dName = "Unassigned Driver", dEmail = "", dContact = "";
        String driverId = doc.optString("driver_id", doc.optString("driverId", ""));
        if (doc.has("driver") && !doc.isNull("driver")) {
            Object obj = doc.opt("driver");
            if (obj instanceof JSONObject) {
                JSONObject dj = (JSONObject) obj;
                if (driverId.isEmpty()) driverId = dj.optString("uid", dj.optString("id", ""));
                dName = dj.optString("name", "Unassigned Driver");
                dEmail = CryptoUtils.decrypt(dj.optString("email", ""), secretKey);
                dContact = CryptoUtils.decrypt(dj.optString("contact_no", dj.optString("contact", "")), secretKey);
            } else if (obj instanceof String) dName = (String) obj;
        }
        dName = CryptoUtils.decrypt(dName, secretKey);

        String pName = "Unassigned PAO", pEmail = "", pContact = "";
        String paoId = doc.optString("pao_id", doc.optString("paoId", ""));
        if (doc.has("pao") && !doc.isNull("pao")) {
            Object obj = doc.opt("pao");
            if (obj instanceof JSONObject) {
                JSONObject pj = (JSONObject) obj;
                if (paoId.isEmpty()) paoId = pj.optString("uid", pj.optString("id", ""));
                pName = pj.optString("name", "Unassigned PAO");
                pEmail = CryptoUtils.decrypt(pj.optString("email", ""), secretKey);
                pContact = CryptoUtils.decrypt(pj.optString("contact_no", pj.optString("contact", "")), secretKey);
            } else if (obj instanceof String) pName = (String) obj;
        }
        pName = CryptoUtils.decrypt(pName, secretKey);

        String unit = rawJeep, plate = "Active Duty";
        if (rawJeep.contains("(") && rawJeep.contains(")")) {
            plate = rawJeep.substring(0, rawJeep.indexOf("(")).trim();
            unit = rawJeep.substring(rawJeep.indexOf("(") + 1, rawJeep.indexOf(")")).trim();
        }
        if (tvUnitNo != null) tvUnitNo.setText(unit);
        if (tvPlateNo != null) tvPlateNo.setText(plate);
        if (tvTodayRoute != null) tvTodayRoute.setText(route);
        if (tvDriverFullName != null) tvDriverFullName.setText(dName);
        if (tvPaoFullName != null) tvPaoFullName.setText(pName);
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("● Assigned");
        if (tvJeepStatus != null) tvJeepStatus.setText("● Active");

        final String fDName = dName, fDEmail = dEmail, fDContact = dContact, fDriverId = driverId;
        if (containerDriverPill != null) containerDriverPill.setOnClickListener(v -> showBottomSheet("DRIVER DETAILS", fDName, fDEmail, fDContact, fDriverId));
        final String fPName = pName, fPEmail = pEmail, fPContact = pContact, fPaoId = paoId;
        if (containerPaoPill != null) containerPaoPill.setOnClickListener(v -> showBottomSheet("PAO DETAILS", fPName, fPEmail, fPContact, fPaoId));
    }

    private void showBottomSheet(String role, String name, String email, String contact, String userId) {
        Context context = getContext(); if (context == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_info, null);
        ((TextView) view.findViewById(R.id.tv_dialog_role)).setText(role);
        ((TextView) view.findViewById(R.id.tv_dialog_name)).setText(name);
        ((TextView) view.findViewById(R.id.tv_dialog_email)).setText(email != null && !email.isEmpty() && !email.equalsIgnoreCase("null") ? email : "N/A");
        
        TextView tvContact = view.findViewById(R.id.tv_dialog_contact);
        if (contact != null && !contact.isEmpty() && !contact.equalsIgnoreCase("null")) {
            tvContact.setText(contact);
        } else {
            tvContact.setText("N/A");
            if (userId != null && !userId.isEmpty()) {
                db.collection("File201").document(userId).get().addOnSuccessListener(doc -> {
                    if (doc.exists() && isAdded()) {
                        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                        String phone = CryptoUtils.decrypt(doc.getString("contact_no"), secretKey);
                        if (phone != null && !phone.isEmpty() && !phone.equalsIgnoreCase("null")) {
                            tvContact.setText(phone);
                        }
                    }
                });
            }
        }
        dialog.setContentView(view); dialog.show();
    }

    private void showCenteredDialog(View dialogView) {
        Context context = getContext(); if (context == null) return;
        Dialog dialog = new Dialog(context); dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private String parseName(JSONObject doc, String key, String fallback) {
        try {
            Object obj = doc.opt(key);
            if (obj instanceof JSONObject) return ((JSONObject) obj).optString("name", fallback);
            return obj != null ? obj.toString() : fallback;
        } catch (Exception e) { return fallback; }
    }

    private int getDayIndex(String dayName) {
        switch (dayName.toLowerCase()) {
            case "sunday": return Calendar.SUNDAY; case "monday": return Calendar.MONDAY;
            case "tuesday": return Calendar.TUESDAY; case "wednesday": return Calendar.WEDNESDAY;
            case "thursday": return Calendar.THURSDAY; case "friday": return Calendar.FRIDAY;
            case "saturday": return Calendar.SATURDAY; default: return -1;
        }
    }

    private void setNoAssignmentUI() {
        if (tvUnitNo != null) tvUnitNo.setText("No Unit");
        if (tvPlateNo != null) tvPlateNo.setText("No Duty Today");
        if (tvTodayRoute != null) tvTodayRoute.setText("No Route");
        if (tvJeepStatus != null) tvJeepStatus.setText("● Off Duty");
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("● Off Duty");
        if (tvDriverFullName != null) tvDriverFullName.setText("Rest Day / Unassigned");
        if (tvPaoFullName != null) tvPaoFullName.setText("Rest Day / Unassigned");
    }

    private void updateDynamicGreeting() {
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) tvGreeting.setText("Drive Safe boss,");
        else if (hour < 12) tvGreeting.setText("Good morning boss,");
        else if (hour < 18) tvGreeting.setText("Good afternoon boss,");
        else tvGreeting.setText("Good evening boss,");
    }

    @Override
    public void onDestroyView() {
        if (ivRobot != null) Glide.with(this).clear(ivRobot);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
