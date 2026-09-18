package com.example.portjeep.home;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.PreferenceManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
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

    private static boolean isProfileRefreshed = false;
    private static boolean isScheduleRefreshed = false;
    private static boolean isSummaryRefreshed = false;

    // Header Views
    private ShimmerFrameLayout shimmerHeader;
    private View rlHeaderContent;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;

    // Content Fragment
    private HomeContentFragment contentFragment;

    // Firebase & Background Thread
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Robot Talking Loop Handler & Runnables
    private final Handler robotHandler = new Handler(Looper.getMainLooper());
    private Runnable robotShowRunnable;
    private Runnable robotHideRunnable;
    private int messageIndex = 0;

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

        // Bind Header Views
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
        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDriverName = view.findViewById(R.id.tv_driver_name);
        tvRoleBadge = view.findViewById(R.id.tv_role_badge);

        ivRobot = view.findViewById(R.id.iv_robot);
        if (ivRobot != null && isAdded() && getContext() != null) {
            Glide.with(requireContext()).asGif().load(R.drawable.robot2).into(ivRobot);
        }

        // Attach HomeContentFragment into the container
        contentFragment = (HomeContentFragment) getChildFragmentManager().findFragmentById(R.id.fl_home_content_container);
        if (contentFragment == null) {
            contentFragment = new HomeContentFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.fl_home_content_container, contentFragment)
                    .commit();
        }

        resetDynamicUI();
        loadOfflineUserProfile();

        Context ctx = getContext();
        if (ctx != null) {
            String cachedName = PreferenceManager.getUserFirstName(ctx);
            if (cachedName == null || cachedName.isEmpty()) {
                isProfileRefreshed = false;
                isScheduleRefreshed = false;
                isSummaryRefreshed = false;
            }
        }

        boolean hasValidCache = false;
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

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Context ctx = getContext();
        if (ctx != null) {
            String cachedData = PreferenceManager.getSchedulesCache(ctx);
            if (cachedData != null) {
                parseAndDisplaySchedules(cachedData);
            }
            loadRemittanceSummary();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRemittanceSummary();
        startRobotTalkingLoop();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopRobotTalkingLoop();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadRemittanceSummary();
            startRobotTalkingLoop();
        } else {
            stopRobotTalkingLoop();
        }
    }

    public void onContentFragmentReady() {
        Context ctx = getContext();
        if (ctx != null) {
            String cachedData = PreferenceManager.getSchedulesCache(ctx);
            if (cachedData != null) {
                parseAndDisplaySchedules(cachedData);
            }
            String cachedSalary = PreferenceManager.getSalaryCache(ctx);
            if (cachedSalary != null) {
                try {
                    processRemittanceSummary(new JSONArray(cachedSalary));
                } catch (Exception ignored) {}
            }
        }
        if (isScheduleRefreshed) {
            hideLoadingSkeleton();
        }
    }

    private void startRobotTalkingLoop() {
        stopRobotTalkingLoop();

        robotShowRunnable = new Runnable() {
            @Override
            public void run() {
                if (getView() == null || !isAdded()) return;
                View clRobotThought = getView().findViewById(R.id.cl_robot_thought);
                TextView tvRobotBubbleText = getView().findViewById(R.id.tv_robot_bubble_text);

                if (clRobotThought != null && tvRobotBubbleText != null) {
                    String name = (tvDriverName != null && tvDriverName.getText() != null)
                            ? tvDriverName.getText().toString().trim() : "boss";
                    if (name.isEmpty()) name = "boss";

                    String[] currentThoughts = getTimeBasedThoughts();
                    String messageTemplate = currentThoughts[messageIndex % currentThoughts.length];
                    tvRobotBubbleText.setText(String.format(messageTemplate, name));
                    messageIndex++;

                    clRobotThought.setVisibility(View.VISIBLE);
                    clRobotThought.setAlpha(0f);
                    clRobotThought.animate().alpha(1f).setDuration(300).start();
                }

                robotHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (getView() == null || !isAdded()) return;
                        View clRobotThought = getView().findViewById(R.id.cl_robot_thought);
                        if (clRobotThought != null) {
                            clRobotThought.animate().alpha(0f).setDuration(300).withEndAction(() ->
                                clRobotThought.setVisibility(View.GONE)).start();
                        }
                    }
                };
                robotHandler.postDelayed(robotHideRunnable, 5000);
                robotHandler.postDelayed(this, 15000);
            }
        };

        robotHandler.postDelayed(robotShowRunnable, 2000);
    }

    private String[] getTimeBasedThoughts() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isPAO = userRole.toUpperCase().contains("PAO") || userRole.toUpperCase().contains("ASSISTANT");

        if (hour >= 0 && hour < 5) { // Madaling Araw
            if (isPAO) {
                return new String[]{
                    "Madaling araw na, PAO %s. Gising na ba?",
                    "Sipag naman ni PAO %s, hataw agad.",
                    "Ingat sa biyahe boss %s, dilim pa.",
                    "Pang-kape muna bago mangolekta, %s.",
                    "Keep alert, %s! Ready na sa first trip?",
                    "Stay warm, boss %s. Malamig pa."
                };
            } else {
                return new String[]{
                    "Madaling araw na, Driver %s. Antok pa?",
                    "Drive safe sa dilim, %s!",
                    "Ingat sa biyahe boss %s, dilim pa.",
                    "Sipag naman ni %s, gising na.",
                    "Coffee break muna, %s?",
                    "Keep alert, %s! Hataw na.",
                    "Ready for the early shift, %s?",
                    "Stay warm, boss %s.",
                    "Tulog pa ba sila, %s?"
                };
            }
        } else if (hour >= 5 && hour < 11) { // Umaga
            if (isPAO) {
                return new String[]{
                    "Gandang umaga, PAO %s! Ready mangolekta?",
                    "Smile sa pasahero, %s! Positive vibes.",
                    "Check your change bag, %s.",
                    "Morning boss %s! Fighting tayo ngayon.",
                    "Breakfast check, %s? Energy is key.",
                    "Dami nating pasahero ngayon, %s!",
                    "Handa na ba ang barya, PAO %s?"
                };
            } else {
                return new String[]{
                    "Gandang umaga, Driver %s! Kape tayo?",
                    "Ready na ba pumasok, %s?",
                    "Morning boss %s! Fighting!",
                    "Breakfast check, %s?",
                    "Have a productive day, %s!",
                    "Smiling face tayo, %s!",
                    "Road trip na, %s!",
                    "Check your tires, Driver %s.",
                    "May gas na ba ang jeep, %s?",
                    "Ang ganda ng gising natin, %s!"
                };
            }
        } else if (hour >= 11 && hour < 13) { // Tanghalian
            if (isPAO) {
                return new String[]{
                    "Tanghalian na, PAO %s! Kain na tayo.",
                    "Bilangin ang barya, %s! Solve ba?",
                    "Nag-break ka na ba, %s? Rest muna.",
                    "Gutom ka na ba boss %s?",
                    "Ano ulam natin, %s? Ulam reveal!",
                    "Siesta time muna kahit 5 mins, %s.",
                    "Remit check tayo mamaya, PAO %s."
                };
            } else {
                return new String[]{
                    "Tanghalian na, Driver %s! Kain na tayo.",
                    "Nag-break ka na ba, %s?",
                    "Gutom ka na ba boss %s?",
                    "Ano ulam natin, %s?",
                    "Siesta time muna, %s?",
                    "Hydrate yourself, boss %s.",
                    "Kain na tayo, boss %s!",
                    "Lunch time na, rest muna %s."
                };
            }
        } else if (hour >= 13 && hour < 18) { // Hapon
            if (isPAO) {
                return new String[]{
                    "Gandang hapon, PAO %s! Mainit ba?",
                    "Konti na lang boss %s, uwian na.",
                    "Kamusta ang koleksyon ngayong hapon, %s?",
                    "Keep hydrated, %s! Init sa pagsingil.",
                    "Stay safe sa kalsada, %s.",
                    "Afternoon rush is coming, ready na %s?",
                    "Miryenda muna tayo, PAO %s!",
                    "Sukli check muna, %s."
                };
            } else {
                return new String[]{
                    "Gandang hapon, Driver %s! Mainit ba?",
                    "Konti na lang boss %s, uwian na.",
                    "Kamusta ang biyahe ngayong hapon, %s?",
                    "Keep hydrated, %s! Init.",
                    "Stay safe sa kalsada, %s.",
                    "Looking good today, boss %s.",
                    "Miryenda muna tayo, %s!",
                    "Traffic check tayo, Driver %s."
                };
            }
        } else { // Gabi
            if (isPAO) {
                return new String[]{
                    "Gandang gabi, PAO %s! Pagod ka ba?",
                    "Good job sa pagkolekta ngayon, %s!",
                    "Pahinga na tayo mamaya, %s.",
                    "Ingat sa pag-uwi, boss %s!",
                    "Safe trip pauwi, PAO %s.",
                    "Miss ka na nila sa bahay, %s.",
                    "Kumpleto ba ang remit, PAO %s?",
                    "Good night in advance, %s!"
                };
            } else {
                return new String[]{
                    "Gandang gabi, Driver %s! Pagod ka ba?",
                    "Pahinga na tayo mamaya, %s.",
                    "Ingat sa pag-uwi, boss %s!",
                    "Uwi na tayo, %s. Miss ka na nila.",
                    "One last trip, %s?",
                    "Good job for today, %s!",
                    "You earned this rest, boss %s.",
                    "Check your lights, Driver %s.",
                    "Good night in advance, %s!"
                };
            }
        }
    }

    private void stopRobotTalkingLoop() {
        if (robotShowRunnable != null) robotHandler.removeCallbacks(robotShowRunnable);
        if (robotHideRunnable != null) robotHandler.removeCallbacks(robotHideRunnable);
        if (getView() == null) {
            View clRobotThought = getView().findViewById(R.id.cl_robot_thought);
            if (clRobotThought != null) clRobotThought.setVisibility(View.GONE);
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
                    InputStream in = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
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
            if (contentFragment != null) contentFragment.resetTodaySummaryUI();
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
                if (contentFragment != null) contentFragment.resetTodaySummaryUI();
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
                if (contentFragment != null) {
                    contentFragment.updateTodaySummaryUI(finalTrips, finalGross, finalNet, finalShare);
                }
            });

        } catch (Exception e) { Log.e(TAG, "Error processing summary UI", e); }
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
            if (contentFragment != null) contentFragment.refreshStatusCarousel(userRestDays, unassignedSchedulesList);
        }
    }

    private void showLoadingSkeleton() {
        if (shimmerHeader != null) { shimmerHeader.startShimmer(); shimmerHeader.setVisibility(View.VISIBLE); }
        if (rlHeaderContent != null) rlHeaderContent.setVisibility(View.GONE);
        if (contentFragment != null) contentFragment.showLoadingSkeleton();
    }

    private void hideLoadingSkeleton() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        if (shimmerHeader != null) { shimmerHeader.stopShimmer(); shimmerHeader.setVisibility(View.GONE); }
        if (rlHeaderContent != null) rlHeaderContent.setVisibility(View.VISIBLE);
        if (contentFragment != null) contentFragment.hideLoadingSkeleton();
    }

    private void resetDynamicUI() {
        if (tvDriverName != null) tvDriverName.setText("");
        if (tvRoleBadge != null) tvRoleBadge.setText("");
        if (contentFragment != null) contentFragment.setNoAssignmentUI();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        if (isProfileRefreshed) return;
        db.collection("File201").document(currentUser.getUid()).get().addOnSuccessListener(documentSnapshot -> {
            if (isAdded() && documentSnapshot.exists()) {
                isProfileRefreshed = true;
                extractUserProfileAndRestDays(documentSnapshot);
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error loading user profile", e));
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
        if (contentFragment != null) contentFragment.refreshStatusCarousel(userRestDays, unassignedSchedulesList);
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
            if (!root.optBoolean("success", false)) {
                if (contentFragment != null) {
                    contentFragment.setNoAssignmentUI();
                    contentFragment.renderUpcomingScheduleList(new ArrayList<>());
                }
                return;
            }
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null || schedules.length() == 0) {
                if (contentFragment != null) {
                    contentFragment.setNoAssignmentUI();
                    contentFragment.renderUpcomingScheduleList(new ArrayList<>());
                }
                return;
            }
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

            if (contentFragment != null) {
                if (todayScheduleDoc != null) contentFragment.processTodaySchedule(todayScheduleDoc); else contentFragment.setNoAssignmentUI();
                contentFragment.renderUpcomingScheduleList(upcomingScheduleDocs);
                contentFragment.refreshStatusCarousel(userRestDays, unassignedSchedulesList);
            }
        } catch (Exception e) {
            if (contentFragment != null) contentFragment.setNoAssignmentUI();
        }
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

    private void updateDynamicGreeting() {
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) tvGreeting.setText("Drive Safe boss,");
        else if (hour < 12) tvGreeting.setText("Good morning boss,");
        else if (hour < 13) tvGreeting.setText("Tanghalian boss,");
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