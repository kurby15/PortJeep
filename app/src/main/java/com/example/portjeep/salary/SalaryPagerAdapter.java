package com.example.portjeep.salary;

import android.content.Context;
import android.content.SharedPreferences;
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
    private final String hiddenCalc = "••••• - ••••• - •••• - •••• + •••";

    // Values for display
    private final String valTotalNet = "₱ 17,459.00";
    private final String valGross = "₱ 35,200.00";
    private final String valRemittance = "- ₱ 13,200.00";
    private final String valFuelCostsBreakdown = "- ₱ 3,960.00";
    private final String valDeductions = "- ₱ 1,081.00";
    private final String valIncentives = "+ ₱ 500.00";
    
    private final String valBoundaryDay = "₱ 600";
    private final String valFuelDay = "₱ 180";
    private final String valWorkingDays = "22";

    private final String valGrossCollected = "₱ 1,680";
    private final String valDueOperator = "₱ 600";
    private final String valFuelExpense = "₱ 180";
    private final String valNetRemittance = "₱ 900";

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
                // Apply modern transition for a smoother user experience
                TransitionManager.beginDelayedTransition((ViewGroup) summaryHolder.itemView);
                
                isSalaryVisible = !isSalaryVisible;
                SharedPreferences prefs = v.getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_VISIBLE, isSalaryVisible).apply();
                
                updateSummaryUI(summaryHolder);
            });

            summaryHolder.btnSubmit.setOnClickListener(v -> {
                Toast.makeText(v.getContext(), "Remittance Submitted Successfully", Toast.LENGTH_SHORT).show();
            });
        } else if (holder instanceof HistoryViewHolder) {
            setupHistoryList((HistoryViewHolder) holder);
        }
    }

    private void setupHistoryList(HistoryViewHolder holder) {
        List<HistoryAdapter.HistoryItem> items = new ArrayList<>();
        items.add(new HistoryAdapter.HistoryItem("Saturday, Aug 8", "1,680", "780", "900", false));
        items.add(new HistoryAdapter.HistoryItem("Friday, Aug 7", "1,740", "780", "960", false));
        items.add(new HistoryAdapter.HistoryItem("Thursday, Aug 6", "1,560", "780", "780", false));
        items.add(new HistoryAdapter.HistoryItem("Wednesday, Aug 5", "1,480", "780", "700", false));
        items.add(new HistoryAdapter.HistoryItem("Tuesday, Aug 4", "", "", "", true));
        items.add(new HistoryAdapter.HistoryItem("Monday, Aug 3", "1,620", "780", "840", false));

        HistoryAdapter historyAdapter = new HistoryAdapter(items);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(historyAdapter);
    }

    private void updateSummaryUI(SummaryViewHolder holder) {
        if (holder == null) return;

        // Populate static data
        safeSetText(holder.tvGross, valGross);
        safeSetText(holder.tvRemittance, valRemittance);
        safeSetText(holder.tvFuelCosts, valFuelCostsBreakdown);
        safeSetText(holder.tvDeductions, valDeductions);
        safeSetText(holder.tvIncentives, valIncentives);
        safeSetText(holder.tvBoundaryDay, valBoundaryDay);
        safeSetText(holder.tvFuelDay, valFuelDay);
        safeSetText(holder.tvWorkingDays, valWorkingDays);
        safeSetText(holder.tvGrossCollected, valGrossCollected);
        safeSetText(holder.tvDueOperator, valDueOperator);
        safeSetText(holder.tvFuelExpense, valFuelExpense);
        safeSetText(holder.tvNetRemittance, valNetRemittance);

        // Handle visibility toggling
        if (isSalaryVisible) {
            safeSetText(holder.tvTotalNet, valTotalNet);
            safeSetText(holder.tvNetBottom, valTotalNet);
            safeSetText(holder.tvNetCalc, "35,200 - 13,200 - 3,960 - 1,081 + 500");
            safeSetText(holder.tvTrend, "↑ 3.9% vs July");
            holder.ivToggleVisibility.setImageResource(R.drawable.view);
        } else {
            safeSetText(holder.tvTotalNet, hiddenText);
            safeSetText(holder.tvNetBottom, hiddenText);
            safeSetText(holder.tvNetCalc, hiddenCalc);
            safeSetText(holder.tvTrend, "↑ ••% vs July");
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
        MaterialButton btnSubmit;
        TextView tvTotalNet, tvNetBottom, tvGross, tvRemittance, tvDeductions, tvIncentives;
        TextView tvGrossCollected, tvDueOperator, tvFuelExpense, tvNetRemittance;
        TextView tvBoundaryDay, tvFuelDay, tvWorkingDays, tvFuelCosts, tvNetCalc, tvTrend;
        ImageView ivToggleVisibility;

        SummaryViewHolder(View itemView) {
            super(itemView);
            btnSubmit = itemView.findViewById(R.id.btn_submit_remittance);
            tvTotalNet = itemView.findViewById(R.id.tv_total_net_income_large);
            tvNetBottom = itemView.findViewById(R.id.tv_val_net_income_bottom);
            tvGross = itemView.findViewById(R.id.tv_val_gross_income);
            tvRemittance = itemView.findViewById(R.id.tv_val_remittance_due);
            tvDeductions = itemView.findViewById(R.id.tv_val_deductions);
            tvIncentives = itemView.findViewById(R.id.tv_val_incentives);
            tvGrossCollected = itemView.findViewById(R.id.tv_val_gross_collected);
            tvDueOperator = itemView.findViewById(R.id.tv_val_due_operator);
            tvFuelExpense = itemView.findViewById(R.id.tv_val_fuel_expense);
            tvNetRemittance = itemView.findViewById(R.id.tv_val_net_remittance);
            tvBoundaryDay = itemView.findViewById(R.id.tv_val_boundary_day);
            tvFuelDay = itemView.findViewById(R.id.tv_val_fuel_day);
            tvWorkingDays = itemView.findViewById(R.id.tv_val_working_days);
            tvFuelCosts = itemView.findViewById(R.id.tv_val_fuel_costs_breakdown);
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
