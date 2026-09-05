package com.example.portjeep.salary;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.portjeep.R;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class SalaryPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SUMMARY = 0;
    private static final int TYPE_HISTORY = 1;
    private static final String PREF_NAME = "salary_prefs";
    private static final String KEY_VISIBLE = "is_visible";

    private boolean isSalaryVisible = true;
    private final String hiddenText = "₱ ••••••";

    // Values for display aligned with fragment_salary_summary.xml
    private final String valTotalNet = "₱ 4,901.06";
    private final String valGross = "₱ 8,120.00";
    private final String valRemittance = "- ₱ 3,218.94";
    private final String valFuelCostsBreakdown = "₱ 4,901.06";
    private final String valDeductions = "₱ 1,126.40";
    private final String valIncentives = "₱ 768.00";
    
    private final String valBoundaryDay = "Jillian";
    private final String valJeepUnit = "UNIT 01";
    private final String valFuelDay = "AAA-0000";
    private final String valWorkingDays = "Micah";

    // Schedule Details Data (Aligned with the requested 3-column layout)
    private final String valScheduleDate = "Sep 3, 12:00 AM";
    private final String valLastPartial = "Sep 3, 11:36 PM";
    
    // New fields for the End Shift Report row
    private final String valReportedAmount = "₱ 8,000";
    private final String valReportedIncentive = "₱ 100";
    private final String valReportedResult = "₱ 120";

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        SharedPreferences prefs = parent.getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        isSalaryVisible = prefs.getBoolean(KEY_VISIBLE, true);

        if (viewType == TYPE_SUMMARY) {
            return new SummaryViewHolder(inflater.inflate(R.layout.fragment_salary_summary, parent, false));
        } else {
            return new HistoryViewHolder(inflater.inflate(R.layout.fragment_salary_history, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SummaryViewHolder) {
            SummaryViewHolder summaryHolder = (SummaryViewHolder) holder;
            updateSummaryUI(summaryHolder);

            summaryHolder.ivToggleVisibility.setOnClickListener(v -> {
                TransitionManager.beginDelayedTransition((ViewGroup) summaryHolder.itemView);
                
                isSalaryVisible = !isSalaryVisible;
                SharedPreferences prefs = v.getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_VISIBLE, isSalaryVisible).apply();
                
                updateSummaryUI(summaryHolder);
            });
        } else if (holder instanceof HistoryViewHolder) {
            setupHistoryList((HistoryViewHolder) holder);
        }
    }

    private void setupHistoryList(HistoryViewHolder holder) {
        List<HistoryAdapter.HistoryItem> items = new ArrayList<>();
        
        // Saturday, Aug 8 with 4 partial reports as per image - Set to expanded by default
        items.add(new HistoryAdapter.HistoryItem("Saturday, Aug 8", "1,680", "780", "900", false)
                .addPartial("#1 · Aug 8, 6:14 AM", "420", "297.2", "122.8")
                .addPartial("#2 · Aug 8, 8:02 AM", "390", "277.2", "112.8")
                .addPartial("#3 · Aug 8, 9:55 AM", "450", "317.2", "132.8")
                .addPartial("#4 · Aug 8, 11:40 AM", "420", "297.2", "122.8")
                .setExpanded(true));

        items.add(new HistoryAdapter.HistoryItem("Friday, Aug 7", "1,740", "780", "960", false)
                .addPartial("#1 · Aug 7, 7:30 AM", "870", "390", "480")
                .addPartial("#2 · Aug 7, 12:45 PM", "870", "390", "480"));
                
        items.add(new HistoryAdapter.HistoryItem("Thursday, Aug 6", "1,560", "780", "780", false)
                .addPartial("#1 · Aug 6, 8:00 AM", "1,560", "780", "780"));
                
        items.add(new HistoryAdapter.HistoryItem("Wednesday, Aug 5", "1,480", "780", "700", false)
                .addPartial("#1 · Aug 5, 9:00 AM", "1,480", "780", "700"));
                
        items.add(new HistoryAdapter.HistoryItem("Tuesday, Aug 4", "", "", "", true));
        
        items.add(new HistoryAdapter.HistoryItem("Monday, Aug 3", "1,620", "780", "840", false)
                .addPartial("#1 · Aug 3, 6:00 AM", "1,620", "780", "840"));

        HistoryAdapter historyAdapter = new HistoryAdapter(items);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(historyAdapter);
    }

    private void updateSummaryUI(SummaryViewHolder holder) {
        if (holder == null) return;

        // Populate breakdown data
        safeSetText(holder.tvGross, valGross);
        safeSetText(holder.tvRemittance, valRemittance);
        safeSetText(holder.tvDeductions, valDeductions);
        safeSetText(holder.tvIncentives, valIncentives);

        // Populate stats row
        safeSetText(holder.tvBoundaryDay, valBoundaryDay);
        safeSetText(holder.tvJeepUnit, valJeepUnit);
        safeSetText(holder.tvFuelDay, valFuelDay);
        safeSetText(holder.tvWorkingDays, valWorkingDays);

        // Populate Schedule Timing
        safeSetText(holder.tvScheduleDate, valScheduleDate);
        safeSetText(holder.tvLastPartial, valLastPartial);

        // Populate End Shift Report
        safeSetText(holder.tvScheduleGross, valReportedAmount);
        safeSetText(holder.tvScheduleExpenses, valReportedIncentive);
        safeSetText(holder.tvScheduleNet, valReportedResult);
        
        // Always show labels regardless of visibility toggle
        safeSetText(holder.tvTrend, "2 partials");
        safeSetText(holder.tvNetCalc, "Calculated from partial reports");

        // Handle visibility toggling for sensitive values
        if (isSalaryVisible) {
            safeSetText(holder.tvTotalNet, valTotalNet);
            safeSetText(holder.tvNetBottom, valTotalNet);
            holder.ivToggleVisibility.setImageResource(R.drawable.view);
        } else {
            safeSetText(holder.tvTotalNet, hiddenText);
            safeSetText(holder.tvNetBottom, hiddenText);
            holder.ivToggleVisibility.setImageResource(R.drawable.hide);
        }
    }

    private void safeSetText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    @Override
    public int getItemCount() { return 2; }

    @Override
    public int getItemViewType(int position) { return position; }

    static class SummaryViewHolder extends RecyclerView.ViewHolder {
        TextView tvTotalNet, tvNetBottom, tvGross, tvRemittance, tvDeductions, tvIncentives;
        TextView tvScheduleDate, tvLastPartial;
        TextView tvScheduleGross, tvScheduleExpenses, tvScheduleNet; // End Shift Report fields
        TextView tvBoundaryDay, tvJeepUnit, tvFuelDay, tvWorkingDays, tvNetCalc, tvTrend;
        ImageView ivToggleVisibility;

        SummaryViewHolder(View itemView) {
            super(itemView);
            tvTotalNet = itemView.findViewById(R.id.tv_total_net_income_large);
            tvNetBottom = itemView.findViewById(R.id.tv_val_net_income_bottom);
            tvGross = itemView.findViewById(R.id.tv_val_gross_income);
            tvRemittance = itemView.findViewById(R.id.tv_val_remittance_due);
            tvDeductions = itemView.findViewById(R.id.tv_val_deductions);
            tvIncentives = itemView.findViewById(R.id.tv_val_incentives);
            
            tvScheduleDate = itemView.findViewById(R.id.tv_val_schedule_date);
            tvLastPartial = itemView.findViewById(R.id.tv_val_last_partial);
            
            // Map End Shift Report IDs
            tvScheduleGross = itemView.findViewById(R.id.tv_val_schedule_gross);
            tvScheduleExpenses = itemView.findViewById(R.id.tv_val_schedule_expenses);
            tvScheduleNet = itemView.findViewById(R.id.tv_val_schedule_net);

            tvBoundaryDay = itemView.findViewById(R.id.tv_val_boundary_day);
            tvJeepUnit = itemView.findViewById(R.id.tv_val_jeep_unit);
            tvFuelDay = itemView.findViewById(R.id.tv_val_fuel_day);
            tvWorkingDays = itemView.findViewById(R.id.tv_val_working_days);
            tvNetCalc = itemView.findViewById(R.id.tv_net_income_calculation);
            tvTrend = itemView.findViewById(R.id.tv_income_trend);
            ivToggleVisibility = itemView.findViewById(R.id.iv_toggle_visibility);
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvHistory;
        HistoryViewHolder(View itemView) {
            super(itemView);
            rvHistory = itemView.findViewById(R.id.rv_salary_history);
        }
    }
}
