package com.example.portjeep;

import android.app.Dialog;
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
import android.widget.Toast;

import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String API_URL = "https://port-jeep.vercel.app/api/mobile/schedules";

    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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

        // Bind Views
        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvDriverName = view.findViewById(R.id.tv_driver_name);
        tvRoleBadge = view.findViewById(R.id.tv_role_badge);

        // Robot GIF
        ivRobot = view.findViewById(R.id.iv_robot);

        Glide.with(this)
                .asGif()
                .load(R.drawable.robot2)
                .into(ivRobot);

        tvUnitNo = view.findViewById(R.id.tv_unit_no);
        tvPlateNo = view.findViewById(R.id.tv_plate_no);
        tvJeepStatus = view.findViewById(R.id.tv_jeep_status);
        tvAssignmentStatus = view.findViewById(R.id.tv_assignment_status);
        tvDriverFullName = view.findViewById(R.id.tv_driver_fullname);
        tvPaoFullName = view.findViewById(R.id.tv_pao_fullname);
        cardMySchedule = view.findViewById(R.id.card_my_schedule);
        cardSalary = view.findViewById(R.id.card_salary);
        containerUpcoming = view.findViewById(R.id.container_upcoming);

        // Bind Today's Date header
        tvTodayDate = view.findViewById(R.id.tv_today_date);
        if (tvTodayDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvTodayDate.setText(dateFormat.format(new Date()));
        }

        updateDynamicGreeting();
        loadUserProfile();
        loadSchedulesFromApi();

        // Quick Access Handlers
        cardMySchedule.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView navBar = getActivity().findViewById(R.id.bottom_navigation);
                if (navBar != null) {
                    navBar.setSelectedItemId(R.id.nav_schedule);
                }
            }
        });

        cardSalary.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView navBar = getActivity().findViewById(R.id.bottom_navigation);
                if (navBar != null) {
                    navBar.setSelectedItemId(R.id.nav_salary);
                } else {
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new SalaryFragment())
                            .addToBackStack(null)
                            .commit();
                }
            }
        });

        return view;
    }

    // ==========================================
    // 1. USER PROFILE LOADING
    // ==========================================
    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("File201")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) {
                        updateUiWithUserData(documentSnapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to load user info", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUiWithUserData(DocumentSnapshot doc) {
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        String rawFirstName = getFieldString(doc, "first_name");
        String rawLastName = getFieldString(doc, "last_name");
        String positionId = getFieldString(doc, "position_id");

        String firstName = CryptoUtils.decrypt(rawFirstName, secretKey);
        String lastName = CryptoUtils.decrypt(rawLastName, secretKey);

        if (firstName != null && !firstName.isEmpty()) {
            tvDriverName.setText(firstName);
        }

        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions")
                    .document(positionId)
                    .get()
                    .addOnSuccessListener(posDoc -> {
                        if (isAdded() && posDoc.exists()) {
                            String rawTitle = null;
                            Map<String, Object> data = posDoc.getData();

                            if (data != null && !data.isEmpty()) {
                                String[] commonKeys = {"title", "name", "position", "position_name", "role", "description", "Title", "Name"};
                                for (String key : commonKeys) {
                                    if (data.containsKey(key) && data.get(key) instanceof String) {
                                        rawTitle = (String) data.get(key);
                                        break;
                                    }
                                }

                                if (rawTitle == null) {
                                    for (Object val : data.values()) {
                                        if (val instanceof String && !((String) val).trim().isEmpty()) {
                                            rawTitle = (String) val;
                                            break;
                                        }
                                    }
                                }
                            }

                            String positionTitle = CryptoUtils.decrypt(rawTitle, secretKey);
                            applyRoleAndNameUI(positionTitle, firstName, lastName);
                        } else {
                            applyRoleAndNameUI("DRIVER", firstName, lastName);
                        }
                    })
                    .addOnFailureListener(e -> applyRoleAndNameUI("DRIVER", firstName, lastName));
        } else {
            applyRoleAndNameUI("DRIVER", firstName, lastName);
        }
    }

    private void applyRoleAndNameUI(String positionTitle, String firstName, String lastName) {
        String roleText = "DRIVER";
        if (positionTitle != null && !positionTitle.trim().isEmpty()) {
            String upperPosition = positionTitle.toUpperCase().trim();

            if (upperPosition.contains("PUBLIC ASSISTANT") || upperPosition.contains("PAO")) {
                roleText = "PAO";
            } else if (upperPosition.contains("DRIVER")) {
                roleText = "DRIVER";
            } else {
                roleText = upperPosition;
            }
        }

        tvRoleBadge.setText(roleText);

        String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        if (!fullName.isEmpty()) {
            if ("PAO".equalsIgnoreCase(roleText)) {
                tvPaoFullName.setText(fullName);
            } else {
                tvDriverFullName.setText(fullName);
            }
        }
    }

    // ==========================================
    // 2. SCHEDULE FETCHING FROM API
    // ==========================================
    private void loadSchedulesFromApi() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        currentUser.getIdToken(true)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    fetchSchedulesFromApi(result.getToken());
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Failed to get ID Token: " + e.getMessage());
                    setNoAssignmentUI();
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
                        Log.e(TAG, "Server Error (" + responseCode + "): " + rawResult);
                        setNoAssignmentUI();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Network Request Failed", e);
                handler.post(() -> {
                    if (isAdded()) setNoAssignmentUI();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void parseAndDisplaySchedules(String jsonResponse) {
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
                renderUpcomingScheduleList(new ArrayList<>(), LayoutInflater.from(requireContext()));
                return;
            }

            int todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            JSONObject todayScheduleDoc = null;
            List<JSONObject> upcomingScheduleDocs = new ArrayList<>();

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject doc = schedules.getJSONObject(i);
                String dayStr = doc.optString("day", "");
                int schedIndex = getDayIndex(dayStr);

                if (schedIndex != -1) {
                    if (schedIndex == todayIndex) {
                        todayScheduleDoc = doc;
                    } else if (schedIndex > todayIndex) {
                        upcomingScheduleDocs.add(doc);
                    }
                }
            }

            // Display Today's Duty
            if (todayScheduleDoc != null) {
                processTodaySchedule(todayScheduleDoc);
            } else {
                setNoAssignmentUI();
            }

            // Display Upcoming List
            renderUpcomingScheduleList(upcomingScheduleDocs, LayoutInflater.from(requireContext()));

        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing error", e);
            setNoAssignmentUI();
        }
    }

    // Format matching Picture 2 (Unit 3 on top, Plate below)
    private void processTodaySchedule(JSONObject doc) {
        String rawJeep = doc.optString("jeep", "N/A"); // Example from API: "AAA-0003 (Unit 4)"

        // Parse Driver and PAO names
        String driverName = parseName(doc, "driver", "Unassigned Driver");
        String paoName = parseName(doc, "pao", "Unassigned PAO");

        String unitDisplay = "Unit N/A";
        String plateDisplay = "N/A";

        if (!rawJeep.equals("N/A") && !rawJeep.isEmpty()) {
            // Parse "PLATE (Unit X)" string pattern from API
            if (rawJeep.contains("(") && rawJeep.contains(")")) {
                try {
                    int startParen = rawJeep.indexOf("(");
                    int endParen = rawJeep.indexOf(")");

                    // Extract Plate (Everything before '(') -> e.g., "AAA-0003"
                    plateDisplay = rawJeep.substring(0, startParen).trim();

                    // Extract Unit (Everything inside '(' and ')') -> e.g., "Unit 4"
                    unitDisplay = rawJeep.substring(startParen + 1, endParen).trim();
                } catch (Exception e) {
                    unitDisplay = rawJeep;
                    plateDisplay = "Active Duty Today";
                }
            } else {
                // Fallback if backend format ever changes
                unitDisplay = rawJeep;
                plateDisplay = "Active Duty Today";
            }
        }

        // Bind to UI (Matching Picture 2)
        if (tvUnitNo != null) tvUnitNo.setText(unitDisplay);        // Top Line: "Unit 3"
        if (tvPlateNo != null) tvPlateNo.setText(plateDisplay);     // Bottom Line: "AAA-0002"

        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("●  Assigned");
        if (tvJeepStatus != null) tvJeepStatus.setText("●  Active");

        if (tvDriverFullName != null) tvDriverFullName.setText(driverName);
        if (tvPaoFullName != null) tvPaoFullName.setText(paoName);
    }

    private void renderUpcomingScheduleList(List<JSONObject> docs, LayoutInflater inflater) {
        containerUpcoming.removeAllViews();

        if (docs.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
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

            if (tvScheduleStatus != null) {
                tvScheduleStatus.setText("●  Scheduled");
            }

            itemView.setOnClickListener(v -> showScheduleDetailsModal(dayText, dateText, jeepUnit, driverName, paoName));

            containerUpcoming.addView(itemView);
        }
    }

    private String parseName(JSONObject doc, String key, String fallback) {
        if (!doc.has(key) || doc.isNull(key)) return fallback;
        try {
            Object obj = doc.get(key);
            if (obj instanceof JSONObject) {
                return ((JSONObject) obj).optString("name", fallback);
            } else if (obj instanceof String) {
                return (String) obj;
            }
        } catch (Exception ignored) {}
        return fallback;
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

    private void showScheduleDetailsModal(String dayText, String dateText, String jeepUnit, String driverName, String paoName) {
        if (getContext() == null || !isAdded()) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_schedule_details, null);

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
        Dialog dialog = new Dialog(getContext());
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
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("●  Off Duty");
    }

    private void updateDynamicGreeting() {
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

    private String getFieldString(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) return String.valueOf(val);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}