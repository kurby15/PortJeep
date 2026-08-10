package com.example.portjeep;

import android.graphics.Color;
import android.os.Bundle;
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

public class ScheduleFragment extends Fragment {

    private TextView tabToday, tabUpcoming, tabPrevious;
    private RecyclerView rvScheduleList;
    private ScheduleAdapter adapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private List<ScheduleItem> todayList = new ArrayList<>();
    private List<ScheduleItem> upcomingList = new ArrayList<>();
    private List<ScheduleItem> previousList = new ArrayList<>();

    private int activeTab = 0; // 0 = Today, 1 = Upcoming, 2 = Previous

    public ScheduleFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

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
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        db.collection("Schedules")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!isAdded()) return;

                    List<DocumentSnapshot> matchingSchedules = new ArrayList<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        String dId = getFieldString(doc, "driver", "driver_id", "driverId");
                        String pId = getFieldString(doc, "pao", "pao_id", "paoId");

                        if (uid.equals(dId) || uid.equals(pId)) {
                            matchingSchedules.add(doc);
                        }
                    }

                    processAndCategorizeSchedules(matchingSchedules);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to load schedules: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void processAndCategorizeSchedules(List<DocumentSnapshot> docs) {
        if (!isAdded()) return;

        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
        todayList.clear();
        upcomingList.clear();
        previousList.clear();

        Calendar todayCal = Calendar.getInstance();

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);

        for (DocumentSnapshot doc : docs) {
            Object rawDate = doc.get("date");
            if (rawDate == null) rawDate = doc.get("schedule_date");

            Date schedDate = null;
            if (rawDate instanceof Timestamp) {
                schedDate = ((Timestamp) rawDate).toDate();
            }

            String rawDay = getFieldString(doc, "day");
            String day = CryptoUtils.decrypt(rawDay, secretKey);
            String dayStr = schedDate != null ? dayFormat.format(schedDate) : (day != null && !day.isEmpty() ? day : "Scheduled");
            
            String dateStr = schedDate != null ? dateFormat.format(schedDate) : "N/A";
            
            String rawStatus = getFieldString(doc, "status");
            String status = CryptoUtils.decrypt(rawStatus, secretKey);
            if (status == null || status.isEmpty()) status = "Assigned";

            String jeepId = getFieldString(doc, "jeep", "jeep_id", "jeepId");
            String driverId = getFieldString(doc, "driver", "driver_id", "driverId");
            String paoId = getFieldString(doc, "pao", "pao_id", "paoId");

            ScheduleItem item = new ScheduleItem(dayStr, dateStr, status, "Loading Unit...", "Loading Driver...", "Loading PAO...");

            if (schedDate != null) {
                Calendar schedCal = Calendar.getInstance();
                schedCal.setTime(schedDate);

                if (isSameDay(todayCal, schedCal)) {
                    todayList.add(item);
                } else if (schedCal.after(todayCal)) {
                    upcomingList.add(item);
                } else {
                    previousList.add(item);
                }
            } else {
                todayList.add(item);
            }

            fetchScheduleItemDetails(item, jeepId, driverId, paoId);
        }

        if (activeTab == 0) selectTab(0, tabToday, todayList);
        else if (activeTab == 1) selectTab(1, tabUpcoming, upcomingList);
        else selectTab(2, tabPrevious, previousList);
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void fetchScheduleItemDetails(ScheduleItem item, String jeepId, String driverId, String paoId) {
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        // 1. Fetch Jeep Info
        if (jeepId != null && !jeepId.isEmpty()) {
            db.collection("Jeeps").document(jeepId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String rawUnit = getFieldString(doc, "unit_number", "unit_no");
                    String rawPlate = getFieldString(doc, "plate_number", "plate_no");

                    String unit = CryptoUtils.decrypt(rawUnit, secretKey);
                    String plate = CryptoUtils.decrypt(rawPlate, secretKey);

                    item.setJeepUnit("Unit " + (unit != null && !unit.isEmpty() ? unit : "N/A") + " · " + (plate != null && !plate.isEmpty() ? plate : "N/A"));
                    adapter.notifyDataSetChanged();
                }
            });
        } else {
            item.setJeepUnit("Unassigned Unit");
        }

        // 2. Fetch Driver Info
        if (driverId != null && !driverId.isEmpty()) {
            db.collection("File201").document(driverId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String fn = CryptoUtils.decrypt(getFieldString(doc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(doc, "last_name"), secretKey);
                    String fullName = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    item.setDriverName(!fullName.isEmpty() ? fullName : "Assigned Driver");
                    adapter.notifyDataSetChanged();
                }
            });
        } else {
            item.setDriverName("Unassigned");
        }

        // 3. Fetch PAO Info
        if (paoId != null && !paoId.isEmpty()) {
            db.collection("File201").document(paoId).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String fn = CryptoUtils.decrypt(getFieldString(doc, "first_name"), secretKey);
                    String ln = CryptoUtils.decrypt(getFieldString(doc, "last_name"), secretKey);
                    String fullName = ((fn != null ? fn : "") + " " + (ln != null ? ln : "")).trim();
                    item.setPaoName(!fullName.isEmpty() ? fullName : "Assigned PAO");
                    adapter.notifyDataSetChanged();
                }
            });
        } else {
            item.setPaoName("Unassigned");
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