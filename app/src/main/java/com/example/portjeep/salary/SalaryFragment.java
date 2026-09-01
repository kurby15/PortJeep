package com.example.portjeep.salary;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.portjeep.R;
import com.facebook.shimmer.ShimmerFrameLayout;

public class SalaryFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerSalary;
    private View clSalaryContent;

    public SalaryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_salary, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_salary);
        shimmerSalary = view.findViewById(R.id.shimmer_salary);
        clSalaryContent = view.findViewById(R.id.cl_salary_content);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }

        // Initial load
        showLoadingSkeleton();
        simulateDataLoad();

        return view;
    }

    private void refreshData() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        showLoadingSkeleton();
        simulateDataLoad();
    }

    private void simulateDataLoad() {
        // Simulate a data reload since this fragment is currently a placeholder
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded()) {
                hideLoadingSkeleton();
            }
        }, 1500);
    }

    private void showLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.startShimmer();
            shimmerSalary.setVisibility(View.VISIBLE);
        }
        if (clSalaryContent != null) {
            clSalaryContent.setVisibility(View.GONE);
        }
    }

    private void hideLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.stopShimmer();
            shimmerSalary.setVisibility(View.GONE);
        }
        if (clSalaryContent != null) {
            clSalaryContent.setVisibility(View.VISIBLE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}