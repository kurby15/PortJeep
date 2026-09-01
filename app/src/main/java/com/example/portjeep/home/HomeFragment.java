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
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.salary.SalaryFragment;
import com.example.portjeep.utils.CryptoUtils;
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

    // Today's Assignment Loading Shimmer
    private ShimmerFrameLayout shimmerContainer;
    private MaterialCardView cardTodayAssignment;

    // ViewPager2 Status Carousel References
    private ViewPager2 vpStatusCarousel;
    private LinearLayout containerDotsIndicator;
    private StatusBannerAdapter bannerAdapter;
    private final List<StatusBannerAdapter.BannerItem> bannerItems = new ArrayList<>();

    // View References
    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;
    private LinearLayout containerDriverPill, containerPaoPill;

    // Firebase & Background Thread
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Skeleton Loading Views
        shimmerContainer = view.findViewById(R.id.shimmer_view_container);
        cardTodayAssignment = view.findViewById(R.id.card_today_assignment);

        // Bind ViewPager2 Carousel Views
        vpStatusCarousel = view.findViewById(R.id.vp_status_carousel);
        containerDotsIndicator = view.findViewById(R.id.container_dots_indicator);

        // Bind Main Views
        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDriverName = view.findViewById(R.id.tv_driver_name);
        tvRoleBadge = view.findViewById(R.id.tv_role_badge);

        // Robot GIF (Safely loaded)
        ivRobot = view.findViewById(R.id.iv_robot);
        if (ivRobot != null && isAdded() && getContext() != null) {
            Glide.with(requireContext())
                    .asGif()
                    .load(R.drawable.robot2)
                    .into(ivRobot);
        }

        tvUnitNo = view.findViewById(R.id.tv_unit_no);
        tvPlateNo = view.findViewById(R.id.tv_plate_no);
        tvJeepStatus = view.findViewById(R.id.tv_jeep_status);
        tvAssignmentStatus = view.findViewById(R.id.tv_assignment_status);
        tvDriverFullName = view.findViewById(R.id.tv_driver_fullname);
        tvPaoFullName = view.findViewById(R.id.tv_pao_fullname);
        containerDriverPill = view.findViewById(R.id.container_driver_pill);
        containerPaoPill = view.findViewById(R.id.container_pao_pill);
        cardMySchedule = view.findViewById(R.id.card_my_schedule);
        cardSalary = view.findViewById(R.id.card_salary);
        containerUpcoming = view.findViewById(R.id.container_upcoming);

        // Set Today's Date
        tvTodayDate = view.findViewById(R.id.tv_today_date);
        if (tvTodayDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvTodayDate.setText(dateFormat.format(new Date()));
        }

        setupStatusCarousel();
        resetDynamicUI();
        showLoadingSkeleton();

        updateDynamicGreeting();
        loadUserProfile();
        loadSchedulesFromApi();

        setupClickListeners();

        return view;
    }

    private void setupStatusCarousel() {
        if (vpStatusCarousel == null) return;

        bannerAdapter = new StatusBannerAdapter(getContext(), bannerItems);
        vpStatusCarousel.setAdapter(bannerAdapter);

        vpStatusCarousel.setOffscreenPageLimit(3);
        vpStatusCarousel.setClipToPadding(false);
        vpStatusCarousel.setClipChildren(false);

        // Optimized padding for a "peek" effect on the stack
        int paddingHorizontal = (int) (24 * getResources().getDisplayMetrics().density);
        vpStatusCarousel.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);

        vpStatusCarousel.setPageTransformer((page, position) -> {
            float density = getResources().getDisplayMetrics().density;

            if (position <= -1f) {
                // Completely off-screen to the left
                page.setAlpha(0f);
                page.setTranslationX(0f);
            } else if (position < 0f) {
                // Card swiping left - smooth fade and slight upward lift
                float factor = Math.abs(position);
                page.setAlpha(1f - factor);
                page.setTranslationY(-factor * 60f * density);
                page.setRotation(position * 8f);
                page.setScaleX(1f);
                page.setScaleY(1f);
                page.setTranslationX(0f);
                page.setTranslationZ((1f - factor) * 10f);
            } else if (position <= 3f) {
                // Stacked background cards on the right
                page.setAlpha(Math.max(0.6f, 1f - (position * 0.15f)));
                page.setTranslationY(0f);
                page.setRotation(0f);

                // Subtle scale down for background cards
                float scale = 1f - (position * 0.04f);
                page.setScaleX(scale);
                page.setScaleY(scale);

                // Advanced stack pinning: perfectly aligns cards to the peek offset
                float peekOffset = 20 * density;
                float translationX = -position * page.getWidth() + (position * peekOffset);
                page.setTranslationX(translationX);

                // Ensure Z-index layers cards correctly (front card is top)
                page.setTranslationZ(-position * 10f);
            } else {
                // Completely off-screen to the right
                page.setAlpha(0f);
            }
        });

        vpStatusCarousel.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        vpStatusCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDotsIndicator(position);
            }
        });
    }

    private void refreshStatusCarousel() {
        if (!isAdded() || getContext() == null) return;

        bannerItems.clear();

        // 1. Rest Day Item - Red/Accent Themed
        bannerItems.add(new StatusBannerAdapter.BannerItem(
                StatusBannerAdapter.BannerItem.TYPE_REST_DAY,
                "Rest Day Schedule",
                "Your upcoming rest day assignments:",
                userRestDays,
                null
        ));

        // 2. Unassigned Item - Blue/Brand Themed (Matches "Work" feel)
        String unassignedDesc = unassignedSchedulesList.isEmpty()
                ? "Perfect! All your shifts are successfully assigned."
                : "Heads up! These shifts currently have no unit assigned:";

        bannerItems.add(new StatusBannerAdapter.BannerItem(
                StatusBannerAdapter.BannerItem.TYPE_UNASSIGNED,
                "Unassigned Log",
                unassignedDesc,
                null,
                unassignedSchedulesList
        ));

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

        ImageView[] dots = new ImageView[count];

        // Convert dp to pixels for proper density scaling
        float density = getResources().getDisplayMetrics().density;
        int dotSizePx = (int) (10 * density); // Slightly smaller dot diameter (10dp)
        int marginPx = (int) (5 * density);   // 5dp spacing between dots

        for (int i = 0; i < count; i++) {
            dots[i] = new ImageView(getContext());
            dots[i].setImageResource(R.drawable.bg_circle_icon);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            dots[i].setLayoutParams(params);

            containerDotsIndicator.addView(dots[i]);
        }
        updateDotsIndicator(0);
    }

    private void updateDotsIndicator(int position) {
        if (containerDotsIndicator == null || getContext() == null) return;

        // Define active and inactive colors
        int activeColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_brand_primary);
        int inactiveColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.color_divider);

        int childCount = containerDotsIndicator.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView dot = (ImageView) containerDotsIndicator.getChildAt(i);
            if (dot != null) {
                if (i == position) {
                    // Active dot style: brand color, full opacity
                    dot.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                    dot.setAlpha(1.0f);
                    dot.setScaleX(1.0f);
                    dot.setScaleY(1.0f);
                } else {
                    // Inactive dot style: muted color, subtle scale down
                    dot.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
                    dot.setAlpha(0.6f);
                    dot.setScaleX(0.85f);
                    dot.setScaleY(0.85f);
                }
            }
        }
    }

    private void setupClickListeners() {
        if (cardMySchedule != null) {
            cardMySchedule.setOnClickListener(v -> {
                FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    BottomNavigationView navBar = activity.findViewById(R.id.bottom_navigation);
                    if (navBar != null) {
                        navBar.setSelectedItemId(R.id.nav_schedule);
                    }
                }
            });
        }

        if (cardSalary != null) {
            cardSalary.setOnClickListener(v -> {
                FragmentActivity activity = getActivity();
                if (activity != null && !activity.isFinishing()) {
                    BottomNavigationView navBar = activity.findViewById(R.id.bottom_navigation);
                    if (navBar != null) {
                        navBar.setSelectedItemId(R.id.nav_salary);
                    } else {
                        activity.getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new SalaryFragment())
                                .addToBackStack(null)
                                .commit();
                    }
                }
            });
        }
    }

    private void showLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.startShimmer();
            shimmerContainer.setVisibility(View.VISIBLE);
        }
        if (cardTodayAssignment != null) {
            cardTodayAssignment.setVisibility(View.GONE);
        }
    }

    private void hideLoadingSkeleton() {
        if (shimmerContainer != null) {
            shimmerContainer.stopShimmer();
            shimmerContainer.setVisibility(View.GONE);
        }
        if (cardTodayAssignment != null) {
            cardTodayAssignment.setVisibility(View.VISIBLE);
        }
    }

    private void resetDynamicUI() {
        if (tvDriverName != null) tvDriverName.setText("");
        if (tvRoleBadge != null) tvRoleBadge.setText("");
        setNoAssignmentUI();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("File201")
                .document(currentUser.getUid())
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) {
                        extractUserProfileAndRestDays(documentSnapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    db.collection("File201")
                            .document(currentUser.getUid())
                            .get(Source.CACHE)
                            .addOnSuccessListener(cacheSnapshot -> {
                                if (isAdded() && cacheSnapshot.exists()) {
                                    extractUserProfileAndRestDays(cacheSnapshot);
                                }
                            });
                });
    }

    @SuppressWarnings("unchecked")
    private void extractUserProfileAndRestDays(DocumentSnapshot doc) {
        if (!isAdded()) return;

        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        String rawFirstName = doc.getString("first_name");
        String firstName = CryptoUtils.decrypt(rawFirstName, secretKey);
        if (firstName != null && !firstName.isEmpty() && tvDriverName != null) {
            tvDriverName.setText(firstName);
        }

        Object restDaysObj = doc.get("rest_days");
        if (restDaysObj instanceof List<?>) {
            userRestDays.clear();
            List<?> rawList = (List<?>) restDaysObj;
            for (Object item : rawList) {
                if (item instanceof String) {
                    userRestDays.add(((String) item).trim());
                }
            }
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            user.getIdToken(false).addOnSuccessListener(result -> {
                if (!isAdded()) return;
                Object claimRole = result.getClaims().get("role");
                if (claimRole != null && !claimRole.toString().isEmpty()) {
                    applyRoleBadgeUI(claimRole.toString());
                }
            });
        }

        String positionId = doc.getString("position_id");
        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions")
                    .document(positionId)
                    .get()
                    .addOnSuccessListener(posDoc -> {
                        if (isAdded() && posDoc.exists()) {
                            String rawTitle = posDoc.getString("title");
                            if (rawTitle == null) rawTitle = posDoc.getString("name");
                            if (rawTitle == null) rawTitle = posDoc.getString("position");

                            String positionTitle = CryptoUtils.decrypt(rawTitle, secretKey);
                            applyRoleBadgeUI(positionTitle);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error fetching position title", e));
        }

        refreshStatusCarousel();
    }

    private void applyRoleBadgeUI(String positionTitle) {
        if (!isAdded() || positionTitle == null || positionTitle.trim().isEmpty()) return;

        String upperPosition = positionTitle.toUpperCase().trim();

        if (upperPosition.contains("PUBLIC ASSISTANT") || upperPosition.contains("PAO")) {
            userRole = "PUBLIC ASSISTANT OFFICER";
        } else if (upperPosition.contains("DRIVER")) {
            userRole = "DRIVER";
        } else {
            userRole = upperPosition;
        }

        if (tvRoleBadge != null && !userRole.isEmpty()) {
            tvRoleBadge.setText(userRole);
            tvRoleBadge.setVisibility(View.VISIBLE);
        }
    }

    private void loadSchedulesFromApi() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            hideLoadingSkeleton();
            return;
        }

        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    fetchSchedulesFromApi(result.getToken());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    setNoAssignmentUI();
                    hideLoadingSkeleton();
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
                    if (!isAdded()) return;

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        parseAndDisplaySchedules(rawResult);
                    } else {
                        setNoAssignmentUI();
                    }
                    hideLoadingSkeleton();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (isAdded()) {
                        setNoAssignmentUI();
                        hideLoadingSkeleton();
                    }
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
        if (!isAdded()) return;

        try {
            JSONObject root = new JSONObject(jsonResponse);
            boolean success = root.optBoolean("success", false);

            if (!success) {
                setNoAssignmentUI();
                return;
            }

            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) {
                setNoAssignmentUI();
                renderUpcomingScheduleList(new ArrayList<>());
                return;
            }

            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);
            Date todayAtMidnight = todayCal.getTime();

            int currentYear = todayCal.get(Calendar.YEAR);
            int todayIndex = todayCal.get(Calendar.DAY_OF_WEEK);

            String[] datePatterns = new String[]{
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

            JSONObject todayScheduleDoc = null;
            List<JSONObject> upcomingScheduleDocs = new ArrayList<>();
            unassignedSchedulesList.clear();

            Set<String> scheduledDays = new HashSet<>();

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);

                String rawDate = doc.optString("date", "N/A");
                String dayStr = doc.optString("day", "").trim();
                String rawStatus = doc.optString("status", "").toLowerCase(Locale.US);

                if (!dayStr.isEmpty()) {
                    scheduledDays.add(dayStr.toLowerCase(Locale.US));
                }

                String driverName = parseName(doc, "driver", "Unassigned Driver");
                String paoName = parseName(doc, "pao", "Unassigned PAO");
                String jeep = doc.optString("jeep", "").trim();

                boolean isUnassignedDriver = "Unassigned Driver".equalsIgnoreCase(driverName);
                boolean isUnassignedPao = "Unassigned PAO".equalsIgnoreCase(paoName);
                boolean isNoJeep = jeep.isEmpty() || "N/A".equalsIgnoreCase(jeep) || "Unassigned".equalsIgnoreCase(jeep);

                if (isUnassignedDriver || isUnassignedPao || isNoJeep) {
                    unassignedSchedulesList.add(doc);
                }

                boolean isExplicitlyCompleted = "completed".equals(rawStatus) || "done".equals(rawStatus) || "finished".equals(rawStatus);

                if (isExplicitlyCompleted) {
                    continue;
                }

                Date parsedDate = parseDateString(rawDate, datePatterns, currentYear);

                if (parsedDate != null) {
                    Calendar parsedCal = Calendar.getInstance();
                    parsedCal.setTime(parsedDate);
                    parsedCal.set(Calendar.HOUR_OF_DAY, 0);
                    parsedCal.set(Calendar.MINUTE, 0);
                    parsedCal.set(Calendar.SECOND, 0);
                    parsedCal.set(Calendar.MILLISECOND, 0);
                    Date itemDateAtMidnight = parsedCal.getTime();

                    if (itemDateAtMidnight.equals(todayAtMidnight)) {
                        todayScheduleDoc = doc;
                    } else if (itemDateAtMidnight.after(todayAtMidnight)) {
                        upcomingScheduleDocs.add(doc);
                    }
                } else {
                    int schedIndex = getDayIndex(dayStr);
                    if (schedIndex != -1) {
                        if (schedIndex == todayIndex) {
                            todayScheduleDoc = doc;
                        } else if (isDayUpcoming(todayIndex, schedIndex)) {
                            upcomingScheduleDocs.add(doc);
                        }
                    }
                }
            }

            String[] weekDays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            for (String dayName : weekDays) {
                if (!scheduledDays.contains(dayName.toLowerCase(Locale.US))) {
                    boolean isUserRestDay = false;
                    for (String restDay : userRestDays) {
                        if (restDay.equalsIgnoreCase(dayName)) {
                            isUserRestDay = true;
                            break;
                        }
                    }
                    if (!isUserRestDay) {
                        JSONObject unassignedDoc = new JSONObject();
                        unassignedDoc.put("day", dayName);
                        unassignedDoc.put("status", "Unassigned");
                        unassignedSchedulesList.add(unassignedDoc);
                    }
                }
            }

            if (todayScheduleDoc != null) {
                processTodaySchedule(todayScheduleDoc);
            } else {
                setNoAssignmentUI();
            }

            renderUpcomingScheduleList(upcomingScheduleDocs);
            refreshStatusCarousel();

        } catch (Exception e) {
            setNoAssignmentUI();
        }
    }

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

    private void processTodaySchedule(JSONObject doc) {
        if (!isAdded()) return;

        String rawJeep = doc.optString("jeep", "N/A");
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        // Driver details extraction
        String driverName = "Unassigned Driver", driverEmail = "", driverContact = "";
        if (doc.has("driver") && !doc.isNull("driver")) {
            Object obj = doc.opt("driver");
            if (obj instanceof JSONObject) {
                JSONObject dJson = (JSONObject) obj;
                driverName = dJson.optString("name", "Unassigned Driver");
                driverEmail = CryptoUtils.decrypt(dJson.optString("email", ""), secretKey);
                driverContact = CryptoUtils.decrypt(dJson.optString("contact_no", dJson.optString("contact", "")), secretKey);
            } else if (obj instanceof String) {
                driverName = (String) obj;
            }
        }
        driverName = CryptoUtils.decrypt(driverName, secretKey);
        if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("null")) driverName = "Unassigned Driver";
        else if (driverName.equalsIgnoreCase("off") || driverName.equalsIgnoreCase("rest")) driverName = "Rest Day";

        // PAO details extraction
        String paoName = "Unassigned PAO", paoEmail = "", paoContact = "";
        if (doc.has("pao") && !doc.isNull("pao")) {
            Object obj = doc.opt("pao");
            if (obj instanceof JSONObject) {
                JSONObject pJson = (JSONObject) obj;
                paoName = pJson.optString("name", "Unassigned PAO");
                paoEmail = CryptoUtils.decrypt(pJson.optString("email", ""), secretKey);
                paoContact = CryptoUtils.decrypt(pJson.optString("contact_no", pJson.optString("contact", "")), secretKey);
            } else if (obj instanceof String) {
                paoName = (String) obj;
            }
        }
        paoName = CryptoUtils.decrypt(paoName, secretKey);
        if (paoName == null || paoName.isEmpty() || paoName.equalsIgnoreCase("null")) paoName = "Unassigned PAO";
        else if (paoName.equalsIgnoreCase("off") || paoName.equalsIgnoreCase("rest")) paoName = "Rest Day";

        String unitDisplay = "Unit N/A";
        String plateDisplay = "N/A";

        if (!rawJeep.equals("N/A") && !rawJeep.isEmpty()) {
            if (rawJeep.contains("(") && rawJeep.contains(")")) {
                try {
                    int startParen = rawJeep.indexOf("(");
                    int endParen = rawJeep.indexOf(")");

                    plateDisplay = rawJeep.substring(0, startParen).trim();
                    unitDisplay = rawJeep.substring(startParen + 1, endParen).trim();
                } catch (Exception e) {
                    unitDisplay = rawJeep;
                    plateDisplay = "Active Duty Today";
                }
            } else {
                unitDisplay = rawJeep;
                plateDisplay = "Active Duty Today";
            }
        }

        if (tvUnitNo != null) tvUnitNo.setText(unitDisplay);
        if (tvPlateNo != null) tvPlateNo.setText(plateDisplay);

        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("●  Assigned");
        if (tvJeepStatus != null) tvJeepStatus.setText("●  Active");

        if (tvDriverFullName != null) tvDriverFullName.setText(driverName);
        if (tvPaoFullName != null) tvPaoFullName.setText(paoName);

        final String finalDriverName = driverName;
        final String finalDriverEmail = driverEmail;
        final String finalDriverContact = driverContact;
        if (containerDriverPill != null) {
            containerDriverPill.setOnClickListener(v -> {
                if (!finalDriverName.equals("Unassigned Driver") && !finalDriverName.equals("Rest Day")) {
                    showBottomSheet("DRIVER DETAILS", finalDriverName, finalDriverEmail, finalDriverContact);
                }
            });
        }

        final String finalPaoName = paoName;
        final String finalPaoEmail = paoEmail;
        final String finalPaoContact = paoContact;
        if (containerPaoPill != null) {
            containerPaoPill.setOnClickListener(v -> {
                if (!finalPaoName.equals("Unassigned PAO") && !finalPaoName.equals("Rest Day")) {
                    showBottomSheet("PAO DETAILS", finalPaoName, finalPaoEmail, finalPaoContact);
                }
            });
        }
    }

    private void renderUpcomingScheduleList(List<JSONObject> docs) {
        Context context = getContext();
        if (!isAdded() || context == null || containerUpcoming == null) return;

        containerUpcoming.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (docs.isEmpty()) {
            TextView tvEmpty = new TextView(context);
            tvEmpty.setText("No upcoming schedules found.");

            tvEmpty.setGravity(android.view.Gravity.CENTER);
            tvEmpty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            tvEmpty.setTextColor(getResources().getColor(R.color.color_text_muted, context.getTheme()));
            tvEmpty.setTextSize(14);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tvEmpty.setLayoutParams(params);

            int verticalPadding = (int) (20 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(0, verticalPadding, 0, verticalPadding);

            containerUpcoming.addView(tvEmpty);
            return;
        }

        for (JSONObject doc : docs) {
            View itemView = inflater.inflate(R.layout.item_upcoming_schedule, containerUpcoming, false);

            TextView tvScheduleDay = itemView.findViewById(R.id.tv_schedule_day);
            TextView tvScheduleDate = itemView.findViewById(R.id.tv_schedule_date);
            TextView tvScheduleStatus = itemView.findViewById(R.id.tv_schedule_status);

            String dayText = doc.optString("day", "Scheduled");
            String dateText = doc.optString("date", "N/A");
            String jeepUnit = doc.optString("jeep", "Unassigned Unit");
            String driverName = parseName(doc, "driver", "Unassigned Driver");
            String paoName = parseName(doc, "pao", "Unassigned PAO");

            if (tvScheduleDay != null) tvScheduleDay.setText(dayText);
            if (tvScheduleDate != null) tvScheduleDate.setText(dateText);
            if (tvScheduleStatus != null) tvScheduleStatus.setText("●  Scheduled");

            // Split logic for upcoming items
            String unitDisplay = jeepUnit;
            String plateDisplay = "N/A";
            if (jeepUnit != null && jeepUnit.contains("(") && jeepUnit.contains(")")) {
                try {
                    int startParen = jeepUnit.indexOf("(");
                    int endParen = jeepUnit.indexOf(")");
                    plateDisplay = jeepUnit.substring(0, startParen).trim();
                    unitDisplay = jeepUnit.substring(startParen + 1, endParen).trim();
                } catch (Exception ignored) {}
            } else if (jeepUnit != null && jeepUnit.contains(" · ")) {
                String[] parts = jeepUnit.split(" · ");
                unitDisplay = parts[0];
                plateDisplay = parts[1];
            }

            itemView.setOnClickListener(v -> showScheduleDetailsModal(dayText, dateText, jeepUnit, driverName, paoName));

            containerUpcoming.addView(itemView);
        }
    }

    private String parseName(JSONObject doc, String key, String fallback) {
        if (!doc.has(key) || doc.isNull(key)) return fallback;
        String resolvedName = fallback;
        try {
            Object obj = doc.get(key);
            if (obj instanceof JSONObject) {
                resolvedName = ((JSONObject) obj).optString("name", fallback);
            } else if (obj instanceof String) {
                resolvedName = (String) obj;
            }
        } catch (Exception ignored) {}

        if (resolvedName.trim().isEmpty() || resolvedName.equalsIgnoreCase("null")) {
            return fallback;
        } else if (resolvedName.equalsIgnoreCase("off") || resolvedName.equalsIgnoreCase("rest")) {
            return "Rest Day";
        }

        return resolvedName;
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

    private void showScheduleDetailsModal(String dayText, String dateText, String jeepUnit, String driverName, String paoName) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_schedule_details, null);

        TextView tvModalDay = dialogView.findViewById(R.id.tv_schedule_day);
        TextView tvModalDate = dialogView.findViewById(R.id.tv_schedule_date);
        TextView tvModalStatus = dialogView.findViewById(R.id.tv_schedule_status);
        TextView tvModalJeepUnit = dialogView.findViewById(R.id.tv_jeep_unit);
        TextView tvModalPlateNo = dialogView.findViewById(R.id.tv_plate_no);
        TextView tvModalDriverName = dialogView.findViewById(R.id.tv_driver_name);
        TextView tvModalPaoName = dialogView.findViewById(R.id.tv_pao_name);

        if (tvModalDay != null) tvModalDay.setText(dayText);
        if (tvModalDate != null) tvModalDate.setText(dateText);
        if (tvModalStatus != null) tvModalStatus.setText("●  Scheduled");

        // Split unit and plate for upcoming schedules
        String unitDisplay = jeepUnit;
        String plateDisplay = "N/A";
        if (jeepUnit != null && jeepUnit.contains("(") && jeepUnit.contains(")")) {
            try {
                int startParen = jeepUnit.indexOf("(");
                int endParen = jeepUnit.indexOf(")");
                plateDisplay = jeepUnit.substring(0, startParen).trim();
                unitDisplay = jeepUnit.substring(startParen + 1, endParen).trim();
            } catch (Exception ignored) {}
        } else if (jeepUnit != null && jeepUnit.contains(" · ")) {
            String[] parts = jeepUnit.split(" · ");
            unitDisplay = parts[0];
            plateDisplay = parts[1];
        }

        if (tvModalJeepUnit != null) tvModalJeepUnit.setText(unitDisplay);
        if (tvModalPlateNo != null) tvModalPlateNo.setText(plateDisplay);
        if (tvModalDriverName != null) tvModalDriverName.setText(driverName);
        if (tvModalPaoName != null) tvModalPaoName.setText(paoName);

        showCenteredDialog(dialogView);
    }

    private void showCenteredDialog(View dialogView) {
        Context context = getContext();
        if (context == null) return;

        Dialog dialog = new Dialog(context);
        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void showBottomSheet(String role, String name, String email, String contact) {
        Context context = getContext();
        if (context == null || !isAdded()) return;

        try {
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_info, null, false);

            TextView tvRole = sheetView.findViewById(R.id.tv_dialog_role);
            TextView tvName = sheetView.findViewById(R.id.tv_dialog_name);
            TextView tvEmail = sheetView.findViewById(R.id.tv_dialog_email);
            TextView tvContact = sheetView.findViewById(R.id.tv_dialog_contact);

            if (tvRole != null) tvRole.setText(role);
            if (tvName != null) tvName.setText(name);

            if (tvEmail != null) {
                tvEmail.setText((email != null && !email.trim().isEmpty() && !email.equalsIgnoreCase("null")) ? email : "N/A");
            }

            if (tvContact != null) {
                tvContact.setText((contact != null && !contact.trim().isEmpty() && !contact.equalsIgnoreCase("null")) ? contact : "N/A");
            }

            bottomSheetDialog.setContentView(sheetView);
            bottomSheetDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setNoAssignmentUI() {
        if (tvUnitNo != null) tvUnitNo.setText("No Unit");
        if (tvPlateNo != null) tvPlateNo.setText("No Duty Today");
        if (tvJeepStatus != null) tvJeepStatus.setText("●  Off Duty");
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("●  Off Duty");
        if (tvDriverFullName != null) tvDriverFullName.setText("Rest Day / Unassigned");
        if (tvPaoFullName != null) tvPaoFullName.setText("Rest Day / Unassigned");

        if (containerDriverPill != null) containerDriverPill.setOnClickListener(null);
        if (containerPaoPill != null) containerPaoPill.setOnClickListener(null);

        refreshStatusCarousel();
    }

    private void updateDynamicGreeting() {
        if (tvGreeting == null) return;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 1 && hour < 5) {
            tvGreeting.setText("Drive Safe boss,");
        } else if (hour >= 5 && hour < 12) {
            tvGreeting.setText("Good morning boss,");
        } else if (hour >= 12 && hour < 18) {
            tvGreeting.setText("Good afternoon boss,");
        } else {
            tvGreeting.setText("Good evening boss,");
        }
    }

    @Override
    public void onDestroyView() {
        if (ivRobot != null) {
            Glide.with(this).clear(ivRobot);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
