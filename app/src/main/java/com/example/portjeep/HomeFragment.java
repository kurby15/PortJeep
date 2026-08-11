package com.example.portjeep;

import android.app.Dialog;
import android.os.Bundle;
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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView tvGreeting, tvDriverName, tvRoleBadge;
    private ImageView ivRobot;
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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
        loadUserProfileAndSchedule();

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

    private void loadUserProfileAndSchedule() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        // 1. Load User Profile
        db.collection("File201")
                .document(uid)
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

        // 2. Load Today's Assignment & Upcoming Schedules
        loadTodaySchedule(uid);
        loadUpcomingSchedules(uid);
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

    private void loadTodaySchedule(String uid) {
        db.collection("Schedules")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;

                    DocumentSnapshot todayDoc = null;
                    Calendar calToday = Calendar.getInstance();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String dId = getFieldString(doc, "driver", "driver_id", "driverId");
                        String pId = getFieldString(doc, "pao", "pao_id", "paoId");

                        if (uid.equals(dId) || uid.equals(pId)) {
                            Object rawDate = doc.get("date");
                            if (rawDate == null) rawDate = doc.get("schedule_date");

                            if (rawDate instanceof Timestamp) {
                                Date schedDate = ((Timestamp) rawDate).toDate();
                                Calendar calSched = Calendar.getInstance();
                                calSched.setTime(schedDate);

                                if (calSched.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
                                        calSched.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)) {
                                    todayDoc = doc;
                                    break;
                                }
                            }
                        }
                    }

                    if (todayDoc != null) {
                        processTodayScheduleDoc(todayDoc);
                    } else {
                        setNoAssignmentUI();
                    }
                })
                .addOnFailureListener(e -> setNoAssignmentUI());
    }

    private void processTodayScheduleDoc(DocumentSnapshot scheduleDoc) {
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        String jeepId = getFieldString(scheduleDoc, "jeep", "jeep_id", "jeepId");
        String driverId = getFieldString(scheduleDoc, "driver", "driver_id", "driverId");
        String paoId = getFieldString(scheduleDoc, "pao", "pao_id", "paoId");

        // Status forced to Assigned for Today
        if (tvAssignmentStatus != null) {
            tvAssignmentStatus.setText("●  Assigned");
        }

        // Fetch Jeep Unit Details
        if (jeepId != null && !jeepId.isEmpty()) {
            db.collection("Jeeps").document(jeepId).get().addOnSuccessListener(jeepDoc -> {
                if (isAdded() && jeepDoc.exists()) {
                    String rawUnitNo = getFieldString(jeepDoc, "unit_number", "unit_no");
                    String rawPlateNo = getFieldString(jeepDoc, "plate_number", "plate_no");
                    String rawJeepStatus = getFieldString(jeepDoc, "status", "jeep_status");

                    String unitNo = CryptoUtils.decrypt(rawUnitNo, secretKey);
                    String plateNo = CryptoUtils.decrypt(rawPlateNo, secretKey);
                    String jeepStatus = CryptoUtils.decrypt(rawJeepStatus, secretKey);

                    tvUnitNo.setText(unitNo != null && !unitNo.isEmpty() ? "Unit " + unitNo : "Unit N/A");
                    tvPlateNo.setText(plateNo != null && !plateNo.isEmpty() ? plateNo : "N/A");
                    if (tvJeepStatus != null) {
                        tvJeepStatus.setText("●  " + (jeepStatus != null && !jeepStatus.isEmpty() ? jeepStatus : "Active"));
                    }
                }
            });
        } else {
            tvUnitNo.setText("No Unit");
            tvPlateNo.setText("N/A");
        }

        // Fetch Assigned Driver Name
        if (driverId != null && !driverId.isEmpty()) {
            db.collection("File201").document(driverId).get().addOnSuccessListener(dDoc -> {
                if (isAdded() && dDoc.exists()) {
                    String fn = CryptoUtils.decrypt(getFieldString(dDoc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(dDoc, "last_name"), secretKey);
                    String name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    tvDriverFullName.setText(!name.isEmpty() ? name : "Assigned Driver");
                }
            });
        } else {
            tvDriverFullName.setText("Unassigned");
        }

        // Fetch Assigned PAO Name
        if (paoId != null && !paoId.isEmpty()) {
            db.collection("File201").document(paoId).get().addOnSuccessListener(pDoc -> {
                if (isAdded() && pDoc.exists()) {
                    String fn = CryptoUtils.decrypt(getFieldString(pDoc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(pDoc, "last_name"), secretKey);
                    String name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    tvPaoFullName.setText(!name.isEmpty() ? name : "Assigned PAO");
                }
            });
        } else {
            tvPaoFullName.setText("Unassigned");
        }
    }

    private void setNoAssignmentUI() {
        if (tvUnitNo != null) tvUnitNo.setText("No Unit");
        if (tvPlateNo != null) tvPlateNo.setText("No Duty Today");
    }

    private void loadUpcomingSchedules(String uid) {
        db.collection("Schedules")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;

                    List<DocumentSnapshot> upcomingDocs = new ArrayList<>();

                    Calendar todayEnd = Calendar.getInstance();
                    todayEnd.set(Calendar.HOUR_OF_DAY, 23);
                    todayEnd.set(Calendar.MINUTE, 59);
                    todayEnd.set(Calendar.SECOND, 59);
                    todayEnd.set(Calendar.MILLISECOND, 999);

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String dId = getFieldString(doc, "driver", "driver_id", "driverId");
                        String pId = getFieldString(doc, "pao", "pao_id", "paoId");

                        if (uid.equals(dId) || uid.equals(pId)) {
                            Object rawDate = doc.get("date");
                            if (rawDate == null) rawDate = doc.get("schedule_date");

                            if (rawDate instanceof Timestamp) {
                                Date schedDate = ((Timestamp) rawDate).toDate();
                                if (schedDate.after(todayEnd.getTime())) {
                                    upcomingDocs.add(doc);
                                }
                            }
                        }
                    }

                    LayoutInflater inflater = LayoutInflater.from(requireContext());
                    renderUpcomingScheduleList(upcomingDocs, inflater);
                });
    }

    private void renderUpcomingScheduleList(List<DocumentSnapshot> docs, LayoutInflater inflater) {
        containerUpcoming.removeAllViews();

        if (docs.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("No upcoming schedules found.");
            tvEmpty.setPadding(16, 16, 16, 16);
            containerUpcoming.addView(tvEmpty);
            return;
        }

        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);

        for (DocumentSnapshot doc : docs) {
            View itemView = inflater.inflate(R.layout.item_upcoming_schedule, containerUpcoming, false);

            TextView tvScheduleDay = itemView.findViewById(R.id.tv_schedule_day);
            TextView tvScheduleDate = itemView.findViewById(R.id.tv_schedule_date);
            TextView tvScheduleStatus = itemView.findViewById(R.id.tv_schedule_status);

            Object scheduleDateObj = doc.get("date");
            if (scheduleDateObj == null) scheduleDateObj = doc.get("schedule_date");

            final String dayText;
            final String dateText;

            if (scheduleDateObj instanceof Timestamp) {
                Date date = ((Timestamp) scheduleDateObj).toDate();
                dayText = dayFormat.format(date);
                dateText = dateFormat.format(date);
            } else {
                String rawDay = getFieldString(doc, "day");
                String day = CryptoUtils.decrypt(rawDay, secretKey);
                dayText = (day != null && !day.isEmpty()) ? day : "Scheduled";
                dateText = "Upcoming";
            }

            if (tvScheduleDay != null) tvScheduleDay.setText(dayText);
            if (tvScheduleDate != null) tvScheduleDate.setText(dateText);

            if (tvScheduleStatus != null) {
                // Changed from Assigned to Scheduled for upcoming shifts
                tvScheduleStatus.setText("●  Scheduled");
            }

            // Click listener to trigger the schedule details modal
            itemView.setOnClickListener(v -> showScheduleDetailsModal(doc, dayText, dateText));

            containerUpcoming.addView(itemView);
        }
    }

    private void showScheduleDetailsModal(DocumentSnapshot doc, String dayText, String dateText) {
        if (getContext() == null || !isAdded()) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_schedule_details, null);

        // Binding to exact IDs from item_upcoming_schedule.xml
        TextView tvModalDay = dialogView.findViewById(R.id.tv_schedule_day);
        TextView tvModalDate = dialogView.findViewById(R.id.tv_schedule_date);
        TextView tvModalStatus = dialogView.findViewById(R.id.tv_schedule_status);
        TextView tvModalJeepUnit = dialogView.findViewById(R.id.tv_jeep_unit);
        TextView tvModalDriverName = dialogView.findViewById(R.id.tv_driver_name);
        TextView tvModalPaoName = dialogView.findViewById(R.id.tv_pao_name);

        if (tvModalDay != null) tvModalDay.setText(dayText);
        if (tvModalDate != null) tvModalDate.setText(dateText);
        if (tvModalStatus != null) {
            // Also updated modal status for upcoming schedule details to Scheduled
            tvModalStatus.setText("●  Scheduled");
        }
        if (tvModalJeepUnit != null) tvModalJeepUnit.setText("Loading Unit...");
        if (tvModalDriverName != null) tvModalDriverName.setText("Loading Driver...");
        if (tvModalPaoName != null) tvModalPaoName.setText("Loading PAO...");

        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
        String jeepId = getFieldString(doc, "jeep", "jeep_id", "jeepId");
        String driverId = getFieldString(doc, "driver", "driver_id", "driverId");
        String paoId = getFieldString(doc, "pao", "pao_id", "paoId");

        // Fetch Jeep Info
        if (jeepId != null && !jeepId.isEmpty()) {
            db.collection("Jeeps").document(jeepId).get().addOnSuccessListener(jeepDoc -> {
                if (isAdded() && jeepDoc.exists() && tvModalJeepUnit != null) {
                    String rawUnit = getFieldString(jeepDoc, "unit_number", "unit_no");
                    String rawPlate = getFieldString(jeepDoc, "plate_number", "plate_no");
                    String unit = CryptoUtils.decrypt(rawUnit, secretKey);
                    String plate = CryptoUtils.decrypt(rawPlate, secretKey);
                    tvModalJeepUnit.setText("Unit " + (unit != null && !unit.isEmpty() ? unit : "N/A") + " · " + (plate != null && !plate.isEmpty() ? plate : "N/A"));
                }
            }).addOnFailureListener(e -> {
                if (isAdded() && tvModalJeepUnit != null) tvModalJeepUnit.setText("Unit N/A");
            });
        } else if (tvModalJeepUnit != null) {
            tvModalJeepUnit.setText("Unassigned Unit");
        }

        // Fetch Driver Info
        if (driverId != null && !driverId.isEmpty()) {
            db.collection("File201").document(driverId).get().addOnSuccessListener(dDoc -> {
                if (isAdded() && dDoc.exists() && tvModalDriverName != null) {
                    String fn = CryptoUtils.decrypt(getFieldString(dDoc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(dDoc, "last_name"), secretKey);
                    String name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    tvModalDriverName.setText(!name.isEmpty() ? name : "Unassigned");
                }
            }).addOnFailureListener(e -> {
                if (isAdded() && tvModalDriverName != null) tvModalDriverName.setText("Unassigned");
            });
        } else if (tvModalDriverName != null) {
            tvModalDriverName.setText("Unassigned");
        }

        // Fetch PAO Info
        if (paoId != null && !paoId.isEmpty()) {
            db.collection("File201").document(paoId).get().addOnSuccessListener(pDoc -> {
                if (isAdded() && pDoc.exists() && tvModalPaoName != null) {
                    String fn = CryptoUtils.decrypt(getFieldString(pDoc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(pDoc, "last_name"), secretKey);
                    String name = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    tvModalPaoName.setText(!name.isEmpty() ? name : "Unassigned");
                }
            }).addOnFailureListener(e -> {
                if (isAdded() && tvModalPaoName != null) tvModalPaoName.setText("Unassigned");
            });
        } else if (tvModalPaoName != null) {
            tvModalPaoName.setText("Unassigned");
        }

        // Display as a centered dialog popup via dedicated method
        showCenteredDialog(dialogView);
    }

    private void showCenteredDialog(View dialogView) {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            // Set explicit width to 90% of screen width to prevent squishing
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void updateDynamicGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 1 && hour < 5) {
            tvGreeting.setText("Drive Safe,");
        } else if (hour >= 5 && hour < 12) {
            tvGreeting.setText("Good morning,");
        } else if (hour >= 12 && hour < 18) {
            tvGreeting.setText("Good afternoon,");
        } else {
            tvGreeting.setText("Good evening,");
        }
    }

    private String getFieldString(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val != null) return String.valueOf(val);
        }
        return null;
    }
}