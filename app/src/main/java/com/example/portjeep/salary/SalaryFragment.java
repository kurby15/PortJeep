package com.example.portjeep.salary;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.portjeep.R;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class SalaryFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerSalary;
    private ViewPager2 vpSalaryContent;

    // Tab Views
    private FrameLayout btnSummary, btnHistory;
    private View viewSummaryIndicator, viewHistoryIndicator;
    private TextView tvSummaryLabel, tvHistoryLabel;
    private TextView tvSalaryDate;

    // Added to prevent memory leaks from delayed background handling
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable loadRunnable;

    public SalaryFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_salary, container, false);

        // Bind Views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_salary);
        shimmerSalary = view.findViewById(R.id.shimmer_salary);
        vpSalaryContent = view.findViewById(R.id.vp_salary_content);

        tvSalaryDate = view.findViewById(R.id.tv_salary_date);
        btnSummary = view.findViewById(R.id.btn_summary);
        btnHistory = view.findViewById(R.id.btn_history);
        viewSummaryIndicator = view.findViewById(R.id.view_summary_indicator);
        viewHistoryIndicator = view.findViewById(R.id.view_history_indicator);
        tvSummaryLabel = view.findViewById(R.id.tv_summary_label);
        tvHistoryLabel = view.findViewById(R.id.tv_history_label);

        setupDate();
        setupViewPager();
        setupTabClickListeners();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }

        // Initial load
        showLoadingSkeleton();
        simulateDataLoad();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (loadRunnable != null) {
            handler.removeCallbacks(loadRunnable);
        }
        swipeRefreshLayout = null;
        shimmerSalary = null;
        vpSalaryContent = null;
        btnSummary = null;
        btnHistory = null;
        viewSummaryIndicator = null;
        viewHistoryIndicator = null;
        tvSummaryLabel = null;
        tvHistoryLabel = null;
        tvSalaryDate = null;
    }

    private void setupDate() {
        if (tvSalaryDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.US);
            tvSalaryDate.setText(sdf.format(Calendar.getInstance().getTime()));
        }
    }

    private void setupViewPager() {
        if (vpSalaryContent == null) return;

        SalaryPagerAdapter adapter = new SalaryPagerAdapter();
        vpSalaryContent.setAdapter(adapter);

        // Sync tabs with swipe
        vpSalaryContent.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabUI(position);
            }
        });
    }

    private void setupTabClickListeners() {
        if (btnSummary != null) {
            btnSummary.setOnClickListener(v -> vpSalaryContent.setCurrentItem(0, true));
        }
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> vpSalaryContent.setCurrentItem(1, true));
        }
    }

    private void updateTabUI(int position) {
        int colorPrimary = ContextCompat.getColor(requireContext(), R.color.color_brand_primary);
        int colorWhite = ContextCompat.getColor(requireContext(), R.color.white);

        if (position == 0) {
            // Summary Selected
            viewSummaryIndicator.setVisibility(View.VISIBLE);
            tvSummaryLabel.setTextColor(colorPrimary);

            viewHistoryIndicator.setVisibility(View.GONE);
            tvHistoryLabel.setTextColor(colorWhite);
        } else {
            // History Selected
            viewSummaryIndicator.setVisibility(View.GONE);
            tvSummaryLabel.setTextColor(colorWhite);

            viewHistoryIndicator.setVisibility(View.VISIBLE);
            tvHistoryLabel.setTextColor(colorPrimary);
        }
    }

    private void refreshData() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        showLoadingSkeleton();
        simulateDataLoad();
    }

    private void simulateDataLoad() {
        loadRunnable = () -> {
            if (isAdded()) {
                hideLoadingSkeleton();
            }
        };
        handler.postDelayed(loadRunnable, 1500);
    }

    private void showLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.startShimmer();
            shimmerSalary.setVisibility(View.VISIBLE);
        }
        if (vpSalaryContent != null) {
            vpSalaryContent.setVisibility(View.GONE);
        }
    }

    private void hideLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.stopShimmer();
            shimmerSalary.setVisibility(View.GONE);
        }
        if (vpSalaryContent != null) {
            vpSalaryContent.setVisibility(View.VISIBLE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}