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

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.PreferenceManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeContentFragment extends Fragment {

    private static final String TAG = "HomeContentFragment";

    // Loading Skeletons
    private ShimmerFrameLayout shimmerContainer, shimmerSummary, shimmerQuickAccess, shimmerBanner, shimmerUpcoming;
    private View llQuickAccessContent, llBannerContent;

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
    private TextView tvUnitNo, tvPlateNo, tvDriverFullName, tvPaoFullName;
    private TextView tvTodayDate, tvJeepStatus, tvAssignmentStatus;
    private MaterialCardView cardMySchedule, cardSalary;
    private LinearLayout containerUpcoming;
    private LinearLayout containerDriverPill, containerPaoPill;
    private ImageView ivDriverChevron, ivPaoChevron;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    private FirebaseFirestore db;

    public HomeContentFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_content, container, false);

        db = FirebaseFirestore.getInstance();

        // Bind Views
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

        tvUnitNo = view.findViewById(R.id.tv_unit_no);
        tvPlateNo = view.findViewById(R.id.tv_plate_no);
        tvJeepStatus = view.findViewById(R.id.tv_jeep_status);
        tvAssignmentStatus = view.findViewById(R.id.tv_assignment_status);
        tvDriverFullName = view.findViewById(R.id.tv_driver_fullname);
        tvPaoFullName = view.findViewById(R.id.tv_pao_fullname);
        containerDriverPill = view.findViewById(R.id.container_driver_pill);
        containerPaoPill = view.findViewById(R.id.container_pao_pill);
        ivDriverChevron = view.findViewById(R.id.iv_driver_chevron);
        ivPaoChevron = view.findViewById(R.id.iv_pao_chevron);
        cardMySchedule = view.findViewById(R.id.card_my_schedule);
        cardSalary = view.findViewById(R.id.card_salary);

        tvTodayDate = view.findViewById(R.id.tv_today_date);
        if (tvTodayDate != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvTodayDate.setText(dateFormat.format(new Date()));
        }

        setupStatusCarousel();
        setupClickListeners();
        setNoAssignmentUI(false);

        if (savedInstanceState != null) {
            hideLoadingSkeleton();
        }

        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).onContentFragmentReady();
        }

        return view;
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

    public void refreshStatusCarousel(List<String> filteredRestDays, List<JSONObject> unassignedSchedulesList) {
        if (vpStatusCarousel == null || getContext() == null) return;
        bannerItems.clear();
        bannerItems.add(new StatusBannerAdapter.BannerItem(StatusBannerAdapter.BannerItem.TYPE_REST_DAY, "Rest Day Schedule", "Your upcoming rest day assignments:", filteredRestDays, null));
        String unassignedDesc = unassignedSchedulesList.isEmpty() ? "Perfect! All your shifts are successfully assigned." : "Heads up! These shifts currently have no unit assigned:";
        bannerItems.add(new StatusBannerAdapter.BannerItem(StatusBannerAdapter.BannerItem.TYPE_UNASSIGNED, "Unassigned Log", unassignedDesc, null, unassignedSchedulesList));
        vpStatusCarousel.post(() -> {
            if (vpStatusCarousel != null && bannerAdapter != null && getContext() != null) {
                bannerAdapter.notifyDataSetChanged();
                setupDotsIndicator(bannerItems.size());
            }
        });
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

    public void showLoadingSkeleton() {
        if (shimmerContainer != null) { shimmerContainer.startShimmer(); shimmerContainer.setVisibility(View.VISIBLE); }
        if (cardTodayAssignment != null) cardTodayAssignment.setVisibility(View.GONE);
        if (shimmerSummary != null) { shimmerSummary.startShimmer(); shimmerSummary.setVisibility(View.VISIBLE); }
        if (llTodaysSummary != null) llTodaysSummary.setVisibility(View.GONE);
        if (shimmerQuickAccess != null) { shimmerQuickAccess.startShimmer(); shimmerQuickAccess.setVisibility(View.VISIBLE); }
        if (llQuickAccessContent != null) llQuickAccessContent.setVisibility(View.GONE);
        if (shimmerBanner != null) { shimmerBanner.startShimmer(); shimmerBanner.setVisibility(View.VISIBLE); }
        if (llBannerContent != null) llBannerContent.setVisibility(View.GONE);
        if (shimmerUpcoming != null) { shimmerUpcoming.startShimmer(); shimmerUpcoming.setVisibility(View.VISIBLE); }
        if (containerUpcoming != null) containerUpcoming.setVisibility(View.GONE);
    }

    public void hideLoadingSkeleton() {
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

    public void updateTodaySummaryUI(int finalTrips, double finalGross, double finalNet, double finalShare) {
        if (tvSummaryTrips == null) return;
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

        if (context != null) {
            String role = PreferenceManager.getUserRole(context);
            if (role != null && role.toUpperCase().contains("DRIVER")) {
                String name = (tvDriverFullName != null) ? tvDriverFullName.getText().toString() : "Driver";
                if (name.isEmpty() || name.contains("Unassigned") || name.contains("Duty")) name = "Driver";
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText(context.getString(R.string.possessive_name, name, "Share"));
                if (tvSummaryDistance != null) tvSummaryDistance.setText(shareStr);
            } else if (role != null && (role.toUpperCase().contains("PAO") || role.toUpperCase().contains("ASSISTANT"))) {
                String name = (tvPaoFullName != null) ? tvPaoFullName.getText().toString() : "PAO";
                if (name.isEmpty() || name.contains("Unassigned") || name.contains("Duty")) name = "PAO";
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText(context.getString(R.string.possessive_name, name, "Share"));
                if (tvSummaryDistance != null) tvSummaryDistance.setText(shareStr);
            }
        }
        if (tvSummaryShareUnit != null) tvSummaryShareUnit.setText("Today");
    }

    public void resetTodaySummaryUI() {
        if (tvSummaryTrips == null) return;
        if (tvSummaryTrips != null) tvSummaryTrips.setText("0");
        if (tvSummaryGross != null) tvSummaryGross.setText("₱0.00");
        if (tvSummaryNet != null) tvSummaryNet.setText("₱0.00");
        if (tvSummaryDistance != null) tvSummaryDistance.setText("₱0.00");
        if (tvSummaryShareUnit != null) tvSummaryShareUnit.setText("Today");

        Context context = getContext();
        if (context != null) {
            String role = PreferenceManager.getUserRole(context);
            if (role != null && role.toUpperCase().contains("DRIVER")) {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("Driver's Share");
            } else if (role != null && (role.toUpperCase().contains("PAO") || role.toUpperCase().contains("ASSISTANT"))) {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("PAO's Share");
            } else {
                if (tvSummaryShareLabel != null) tvSummaryShareLabel.setText("Your Share");
            }
        }
    }

    public void setNoAssignmentUI(boolean isRestDay) {
        if (tvUnitNo == null) return;
        tvUnitNo.setText("No Unit");
        tvPlateNo.setText(isRestDay ? "Rest Day Today" : "No Duty Today");
        tvJeepStatus.setText(isRestDay ? "● Rest Day" : "● Off Duty");
        tvAssignmentStatus.setText(isRestDay ? "● Rest Day" : "● Off Duty");
        tvDriverFullName.setText(isRestDay ? "Rest Day" : "Unassigned / No Duty");
        tvPaoFullName.setText(isRestDay ? "Rest Day" : "Unassigned / No Duty");
        if (ivDriverChevron != null) ivDriverChevron.setVisibility(View.GONE);
        if (ivPaoChevron != null) ivPaoChevron.setVisibility(View.GONE);
    }

    public void processTodaySchedule(JSONObject doc) {
        if (tvUnitNo == null) return;
        String rawJeep = doc.optString("jeep", "N/A");
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
        if (tvDriverFullName != null) tvDriverFullName.setText(dName);
        if (tvPaoFullName != null) tvPaoFullName.setText(pName);
        if (tvAssignmentStatus != null) tvAssignmentStatus.setText("● Assigned");
        if (tvJeepStatus != null) tvJeepStatus.setText("● Active");

        // Set Chevron Visibility based on assignment
        boolean isDriverAssigned = !dName.toLowerCase().contains("unassigned") && !dName.toLowerCase().contains("no duty");
        if (ivDriverChevron != null) {
            ivDriverChevron.setVisibility(isDriverAssigned ? View.VISIBLE : View.GONE);
            ivDriverChevron.setRotation(0f);
        }
        
        boolean isPaoAssigned = !pName.toLowerCase().contains("unassigned") && !pName.toLowerCase().contains("no duty");
        if (ivPaoChevron != null) {
            ivPaoChevron.setVisibility(isPaoAssigned ? View.VISIBLE : View.GONE);
            ivPaoChevron.setRotation(0f);
        }

        final String fDName = dName, fDEmail = dEmail, fDContact = dContact, fDriverId = driverId;
        if (containerDriverPill != null) {
            containerDriverPill.setClickable(isDriverAssigned);
            containerDriverPill.setOnClickListener(isDriverAssigned ? v -> showBottomSheet("DRIVER DETAILS", fDName, fDEmail, fDContact, fDriverId, ivDriverChevron) : null);
        }
        
        final String fPName = pName, fPEmail = pEmail, fPContact = pContact, fPaoId = paoId;
        if (containerPaoPill != null) {
            containerPaoPill.setClickable(isPaoAssigned);
            containerPaoPill.setOnClickListener(isPaoAssigned ? v -> showBottomSheet("PAO DETAILS", fPName, fPEmail, fPContact, fPaoId, ivPaoChevron) : null);
        }
    }

    public void renderUpcomingScheduleList(List<JSONObject> docs) {
        if (containerUpcoming == null || getContext() == null) return;
        containerUpcoming.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (docs.isEmpty()) {
            TextView tvEmpty = new TextView(getContext()); tvEmpty.setText("No upcoming schedules found.");
            tvEmpty.setGravity(android.view.Gravity.CENTER);
            tvEmpty.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.color_text_secondary));
            tvEmpty.setTextSize(14);
            int padding = (int) (20 * getResources().getDisplayMetrics().density);
            tvEmpty.setPadding(0, padding, 0, padding); containerUpcoming.addView(tvEmpty);
            return;
        }
        for (JSONObject doc : docs) {
            View itemView = inflater.inflate(R.layout.item_upcoming_schedule, containerUpcoming, false);
            String day = doc.optString("day", "Scheduled"), date = doc.optString("date", "N/A");
            String jeep = doc.optString("jeep", "Unassigned Unit");
            String driver = parseName(doc, "driver", "Unassigned Driver"), pao = parseName(doc, "pao", "Unassigned PAO");
            ((TextView) itemView.findViewById(R.id.tv_schedule_day)).setText(day);
            ((TextView) itemView.findViewById(R.id.tv_schedule_date)).setText(date);
            ((TextView) itemView.findViewById(R.id.tv_schedule_status)).setText("● Scheduled");
            itemView.setOnClickListener(v -> showScheduleDetailsModal(day, date, jeep, driver, pao));
            containerUpcoming.addView(itemView);
        }
    }

    private String parseName(JSONObject doc, String key, String fallback) {
        try {
            Object obj = doc.opt(key);
            if (obj instanceof JSONObject) return ((JSONObject) obj).optString("name", fallback);
            return obj != null ? obj.toString() : fallback;
        } catch (Exception e) { return fallback; }
    }

    private void showScheduleDetailsModal(String day, String date, String jeep, String driver, String pao) {
        Context context = getContext(); if (context == null) return;
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
        ((TextView) view.findViewById(R.id.tv_driver_name)).setText(driver);
        ((TextView) view.findViewById(R.id.tv_pao_name)).setText(pao);
        showCenteredDialog(view);
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

    private void showBottomSheet(String role, String name, String email, String contact, String userId, ImageView chevron) {
        Context context = getContext(); if (context == null) return;

        // Animate chevron down
        if (chevron != null) {
            chevron.animate().rotation(90f).setDuration(250).start();
        }

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
                    if (doc.exists() && tvContact != null) {
                        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                        String phone = CryptoUtils.decrypt(doc.getString("contact_no"), secretKey);
                        if (phone != null && !phone.isEmpty() && !phone.equalsIgnoreCase("null")) {
                            tvContact.setText(phone);
                        }
                    }
                });
            }
        }

        // Reset chevron rotation when bottom sheet is dismissed
        dialog.setOnDismissListener(d -> {
            if (chevron != null) {
                chevron.animate().rotation(0f).setDuration(250).start();
            }
        });

        dialog.setContentView(view); dialog.show();
    }
}