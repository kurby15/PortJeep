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

import com.bumptech.glide.Glide;
import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.salary.SalaryFragment;
import com.example.portjeep.utils.CryptoUtils;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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

    // Rest Day Card References
    private MaterialCardView cardRestDayBanner;
    private TextView tvRestDayTitle, tvRestDayDesc;
    private LinearLayout containerRestDaysList;

    // Unassigned Card References
    private MaterialCardView cardUnassignedBanner;
    private TextView tvUnassignedTitle, tvUnassignedDesc;
    private LinearLayout containerUnassignedList;

    // View References
    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;

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

        // Bind Rest Day Card Views
        cardRestDayBanner = view.findViewById(R.id.card_rest_day_banner);
        tvRestDayTitle = view.findViewById(R.id.tv_rest_day_title);
        tvRestDayDesc = view.findViewById(R.id.tv_rest_day_desc);
        containerRestDaysList = view.findViewById(R.id.container_rest_days_list);

        // Bind Unassigned Card Views
        cardUnassignedBanner = view.findViewById(R.id.card_unassigned_banner);
        tvUnassignedTitle = view.findViewById(R.id.tv_unassigned_title);
        tvUnassignedDesc = view.findViewById(R.id.tv_unassigned_desc);
        containerUnassignedList = view.findViewById(R.id.container_unassigned_list);

        // Bind Main Views
        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDriverName = view.findViewById(R.id.tv_driver_name);
        tvRoleBadge = view.findViewById(R.id.tv_role_badge);

        // Robot GIF
        ivRobot = view.findViewById(R.id.iv_robot);
        if (ivRobot != null && isAdded()) {
            Glide.with(this)
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
        cardMySchedule = view.findViewById(R.id.card_my_schedule);
        cardSalary = view.findViewById(R.id.card_salary);
        containerUpcoming = view.findViewById(R.id.container_upcoming);

        // Set Today's Date
        tvTodayDate = view.findViewById(R.id.tv_today_date);
        if (tvTodayDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvTodayDate.setText(dateFormat.format(new Date()));
        }

        resetDynamicUI();
        showLoadingSkeleton();

        updateDynamicGreeting();
        loadUserProfile();
        loadSchedulesFromApi();

        setupClickListeners();

        return view;
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

        checkTodayRestDay();
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

    private boolean checkTodayRestDay() {
        if (!isAdded()) return false;

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        String currentDayName = dayFormat.format(new Date());

        boolean isRestDayToday = false;
        for (String restDay : userRestDays) {
            if (restDay.equalsIgnoreCase(currentDayName)) {
                isRestDayToday = true;
                break;
            }
        }

        if (cardRestDayBanner != null) {
            if (isRestDayToday) {
                cardRestDayBanner.setVisibility(View.VISIBLE);
                if (tvRestDayTitle != null) tvRestDayTitle.setText("Scheduled Rest Day");
                if (tvRestDayDesc != null) tvRestDayDesc.setText("Your assigned rest day schedule:");
                renderRestDaysList();
            } else {
                cardRestDayBanner.setVisibility(View.GONE);
            }
        }

        return isRestDayToday;
    }

    private void renderRestDaysList() {
        Context context = getContext();
        if (!isAdded() || context == null || containerRestDaysList == null) return;

        containerRestDaysList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        String currentDayName = dayFormat.format(new Date());

        if (userRestDays.isEmpty()) {
            View rowView = inflater.inflate(R.layout.item_rest_day_row, containerRestDaysList, false);

            TextView tvDayName = rowView.findViewById(R.id.tv_rest_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_rest_day_status);

            if (tvDayName != null) tvDayName.setText(currentDayName);
            if (tvStatus != null) tvStatus.setText("REST DAY");

            containerRestDaysList.addView(rowView);
            return;
        }

        for (String day : userRestDays) {
            View rowView = inflater.inflate(R.layout.item_rest_day_row, containerRestDaysList, false);

            TextView tvDayName = rowView.findViewById(R.id.tv_rest_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_rest_day_status);

            if (tvDayName != null) tvDayName.setText(day);
            if (tvStatus != null) tvStatus.setText("REST DAY");

            containerRestDaysList.addView(rowView);
        }
    }

    private void renderUnassignedList() {
        Context context = getContext();
        if (!isAdded() || context == null || containerUnassignedList == null) return;

        containerUnassignedList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        if (unassignedSchedulesList.isEmpty()) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
            String currentDayName = dayFormat.format(new Date());

            View rowView = inflater.inflate(R.layout.item_unassigned_row, containerUnassignedList, false);

            TextView tvDayName = rowView.findViewById(R.id.tv_unassigned_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_unassigned_status);

            if (tvDayName != null) tvDayName.setText(currentDayName);
            if (tvStatus != null) tvStatus.setText("UNASSIGNED");

            containerUnassignedList.addView(rowView);
            return;
        }

        for (JSONObject doc : unassignedSchedulesList) {
            View rowView = inflater.inflate(R.layout.item_unassigned_row, containerUnassignedList, false);

            TextView tvDayName = rowView.findViewById(R.id.tv_unassigned_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_unassigned_status);

            String dayText = doc.optString("day", "");
            if (dayText.isEmpty()) {
                dayText = doc.optString("date", "Unassigned");
            }

            if (tvDayName != null) tvDayName.setText(dayText);
            if (tvStatus != null) tvStatus.setText("UNASSIGNED");

            containerUnassignedList.addView(rowView);
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

            // Midnight-normalized Calendar for accurate date checking
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

                // Explicitly check status string first
                boolean isExplicitlyCompleted = "completed".equals(rawStatus) || "done".equals(rawStatus) || "finished".equals(rawStatus);

                if (isExplicitlyCompleted) {
                    continue; // Skip past/completed schedules
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

            // Mark missing days of the week as unassigned
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

        String driverName = parseName(doc, "driver", "Unassigned Driver");
        String paoName = parseName(doc, "pao", "Unassigned PAO");

        boolean isUnassignedDriver = "Unassigned Driver".equalsIgnoreCase(driverName);
        boolean isUnassignedPao = "Unassigned PAO".equalsIgnoreCase(paoName);

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

        updateRestDayBanner(driverName, paoName);
        updateUnassignedBanner(isUnassignedDriver, isUnassignedPao);
    }

    private void updateRestDayBanner(String driverName, String paoName) {
        if (!isAdded() || cardRestDayBanner == null) return;

        boolean isDriverRest = "Rest Day".equalsIgnoreCase(driverName);
        boolean isPaoRest = "Rest Day".equalsIgnoreCase(paoName);

        if (isDriverRest || isPaoRest) {
            cardRestDayBanner.setVisibility(View.VISIBLE);

            if (isDriverRest && isPaoRest) {
                if (tvRestDayTitle != null) tvRestDayTitle.setText("Squad Rest Day");
                if (tvRestDayDesc != null) tvRestDayDesc.setText("Both Driver and PAO are off-duty today.");
            } else if ("PAO".equalsIgnoreCase(userRole) && isPaoRest) {
                if (tvRestDayTitle != null) tvRestDayTitle.setText("Scheduled Rest Day");
                if (tvRestDayDesc != null) tvRestDayDesc.setText("You are on your scheduled rest day today.");
            } else if ("DRIVER".equalsIgnoreCase(userRole) && isDriverRest) {
                if (tvRestDayTitle != null) tvRestDayTitle.setText("Scheduled Rest Day");
                if (tvRestDayDesc != null) tvRestDayDesc.setText("You are on your scheduled rest day today.");
            }
            renderRestDaysList();
        } else {
            cardRestDayBanner.setVisibility(View.GONE);
        }
    }

    private void updateUnassignedBanner(boolean isUnassignedDriver, boolean isUnassignedPao) {
        if (!isAdded() || cardUnassignedBanner == null) return;

        if (isUnassignedDriver || isUnassignedPao || !unassignedSchedulesList.isEmpty()) {
            cardUnassignedBanner.setVisibility(View.VISIBLE);

            if (isUnassignedDriver && isUnassignedPao) {
                if (tvUnassignedTitle != null) tvUnassignedTitle.setText("Unassigned Squad");
                if (tvUnassignedDesc != null) tvUnassignedDesc.setText("No Driver and PAO assigned for this unit today.");
            } else if ("PAO".equalsIgnoreCase(userRole) && isUnassignedPao) {
                if (tvUnassignedTitle != null) tvUnassignedTitle.setText("Unassigned PAO");
                if (tvUnassignedDesc != null) tvUnassignedDesc.setText("You have no assigned route/shift today.");
            } else if ("DRIVER".equalsIgnoreCase(userRole) && isUnassignedDriver) {
                if (tvUnassignedTitle != null) tvUnassignedTitle.setText("Unassigned Driver");
                if (tvUnassignedDesc != null) tvUnassignedDesc.setText("You have no assigned route/shift today.");
            } else {
                if (tvUnassignedTitle != null) tvUnassignedTitle.setText("Unassigned Status");
                if (tvUnassignedDesc != null) tvUnassignedDesc.setText("Current schedule has unassigned slot(s).");
            }
            renderUnassignedList();
        } else {
            cardUnassignedBanner.setVisibility(View.GONE);
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
            tvEmpty.setPadding(16, 16, 16, 16);
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
        TextView tvModalDriverName = dialogView.findViewById(R.id.tv_driver_name);
        TextView tvModalPaoName = dialogView.findViewById(R.id.tv_pao_name);

        if (tvModalDay != null) tvModalDay.setText(dayText);
        if (tvModalDate != null) tvModalDate.setText(dateText);
        if (tvModalStatus != null) tvModalStatus.setText("●  Scheduled");
        if (tvModalJeepUnit != null) tvModalJeepUnit.setText(jeepUnit);
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

    private void setNoAssignmentUI() {
        if (tvUnitNo != null) tvUnitNo.setText("No Unit");
        if (tvPlateNo != null) tvPlateNo.setText("No Duty Today");
        if (tvJeepStatus != null) tvJeepStatus.setText("●  Off Duty");
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("●  Off Duty");
        if (tvDriverFullName != null) tvDriverFullName.setText("Rest Day / Unassigned");
        if (tvPaoFullName != null) tvPaoFullName.setText("Rest Day / Unassigned");

        checkTodayRestDay();

        if (cardUnassignedBanner != null) {
            if (!unassignedSchedulesList.isEmpty()) {
                cardUnassignedBanner.setVisibility(View.VISIBLE);
                if (tvUnassignedTitle != null) tvUnassignedTitle.setText("Unassigned Shift");
                if (tvUnassignedDesc != null) tvUnassignedDesc.setText("You have unassigned route or vehicle slots.");
                renderUnassignedList();
            } else {
                cardUnassignedBanner.setVisibility(View.GONE);
            }
        }
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