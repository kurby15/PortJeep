package com.example.portjeep.salary;

import android.content.Context;
import android.content.SharedPreferences;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.portjeep.R;
import com.example.portjeep.utils.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SalaryPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SUMMARY = 0;
    private static final int TYPE_HISTORY = 1;
    private static final String PREF_NAME = "salary_prefs";
    private static final String KEY_VISIBLE = "is_visible";

    private boolean isSalaryVisible = true;
    private final String hiddenText = "₱ ••••••";
    private final DecimalFormat df = new DecimalFormat("#,##0.00");

    private JSONArray remittanceData;

    public SalaryPagerAdapter() {
    }

    public void setRemittanceData(JSONArray data) {
        this.remittanceData = data;
        notifyDataSetChanged();
    }

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

        String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
        String todayFormatted = new SimpleDateFormat("EEEE, MMM d", Locale.US).format(new Date());
        boolean foundToday = false;

        if (remittanceData != null) {
            for (int i = 0; i < remittanceData.length(); i++) {
                try {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", dayName);

                    // Check if this entry matches today's day or date
                    boolean isToday = dayName.equalsIgnoreCase(todayName) || groupDate.contains(todayFormatted) || groupDate.equalsIgnoreCase(todayName);

                    if (!isToday) {
                        continue; // Skip non-today entries to show ONLY today
                    }

                    foundToday = true;
                    JSONArray partialsArray = dayGroup.optJSONArray("remittances");

                    double totalGross = 0, totalExpenses = 0, totalNet = 0;
                    List<HistoryAdapter.PartialReport> reports = new ArrayList<>();
                    boolean hasRemittances = partialsArray != null && partialsArray.length() > 0;

                    if (hasRemittances) {
                        List<JSONObject> sortedPartials = new ArrayList<>();
                        for (int k = 0; k < partialsArray.length(); k++) {
                            JSONObject p = partialsArray.optJSONObject(k);
                            if (p != null) sortedPartials.add(p);
                        }

                        // Sort by time ascending for chronological numbering
                        Collections.sort(sortedPartials, (a, b) ->
                                a.optString("created_at", "").compareTo(b.optString("created_at", "")));

                        for (int j = 0; j < sortedPartials.size(); j++) {
                            JSONObject partial = sortedPartials.get(j);
                            double g = partial.optDouble("gross", 0);
                            double e = partial.optDouble("expenses", 0);
                            double n = partial.optDouble("net", 0);
                            String time = partial.optString("created_at", "");

                            totalGross += g;
                            totalExpenses += e;
                            totalNet += n;

                            reports.add(0, new HistoryAdapter.PartialReport(
                                    "Report #" + (j + 1) + " · " + time,
                                    df.format(g),
                                    df.format(e),
                                    df.format(n)
                            ));
                        }
                    }

                    HistoryAdapter.HistoryItem item = new HistoryAdapter.HistoryItem(
                            groupDate.isEmpty() ? todayFormatted : groupDate,
                            df.format(totalGross),
                            df.format(totalExpenses),
                            df.format(totalNet),
                            !hasRemittances
                    );

                    for (HistoryAdapter.PartialReport report : reports) {
                        item.addPartial(report.header, report.amount, report.expenses, report.net);
                    }

                    item.setExpanded(true);
                    items.add(item);
                    break; // Stop after processing today's entry

                } catch (Exception e) {
                    Log.e("SalaryPagerAdapter", "Error parsing history group", e);
                }
            }
        }

        // If today has no record in remittanceData, display it as unassigned/rest
        if (!foundToday) {
            HistoryAdapter.HistoryItem unassignedItem = new HistoryAdapter.HistoryItem(
                    todayFormatted,
                    "0.00",
                    "0.00",
                    "0.00",
                    true
            );
            items.add(unassignedItem);
        }

        HistoryAdapter historyAdapter = new HistoryAdapter(items);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(historyAdapter);
    }

    private void updateSummaryUI(SummaryViewHolder holder) {
        if (holder == null) return;

        Context context = holder.itemView.getContext();
        String totalNetStr = "₱ 0.00";
        String grossStr = "₱ 0.00";
        String expensesStr = "- ₱ 0.00";
        String shareStr = "₱ 0.00";
        String lastPartialTime = "N/A";
        String tripCountTrend = "0 partials";
        String scheduleId = "";

        if (remittanceData != null && remittanceData.length() > 0) {
            try {
                // Latest group (usually today)
                JSONObject latestGroup = remittanceData.optJSONObject(0);
                if (latestGroup != null) {
                    scheduleId = latestGroup.optString("schedule_id", "");
                    JSONArray partialsArray = latestGroup.optJSONArray("remittances");

                    double sumGross = 0, sumExpenses = 0, sumNet = 0, sumShare = 0;
                    int count = 0;
                    String maxTime = "";

                    if (partialsArray != null && partialsArray.length() > 0) {
                        count = partialsArray.length();
                        for (int j = 0; j < count; j++) {
                            JSONObject p = partialsArray.optJSONObject(j);
                            if (p == null) continue;

                            sumGross += p.optDouble("gross", 0);
                            sumExpenses += p.optDouble("expenses", 0);
                            sumNet += p.optDouble("net", 0);
                            sumShare += p.optDouble("employeeCut", 0);

                            // Dynamically find the latest partial time by comparison
                            String currentTime = p.optString("created_at", "");
                            if (currentTime.compareTo(maxTime) >= 0) {
                                maxTime = currentTime;
                            }
                        }
                    }

                    grossStr = "₱ " + df.format(sumGross);
                    expensesStr = "- ₱ " + df.format(sumExpenses);
                    totalNetStr = "₱ " + df.format(sumNet);
                    shareStr = "₱ " + df.format(sumShare);
                    tripCountTrend = count + (count == 1 ? " partial" : " partials");
                    lastPartialTime = maxTime.isEmpty() ? "N/A" : maxTime;
                }
            } catch (Exception e) {
                Log.e("SalaryPagerAdapter", "Error updating summary aggregation", e);
            }
        }

        safeSetText(holder.tvGross, grossStr);
        safeSetText(holder.tvRemittance, expensesStr);

        String role = PreferenceManager.getUserRole(context);
        if (role != null && role.toUpperCase().contains("DRIVER")) {
            safeSetText(holder.tvDeductions, shareStr);
            safeSetText(holder.tvIncentives, "₱ 0.00");
        } else if (role != null && (role.toUpperCase().contains("PAO") || role.toUpperCase().contains("ASSISTANT"))) {
            safeSetText(holder.tvDeductions, "₱ 0.00");
            safeSetText(holder.tvIncentives, shareStr);
        } else {
            safeSetText(holder.tvDeductions, "₱ 0.00");
            safeSetText(holder.tvIncentives, "₱ 0.00");
        }

        safeSetText(holder.tvLastPartial, lastPartialTime);
        safeSetText(holder.tvTrend, tripCountTrend);

        safeSetText(holder.tvBoundaryDay, "Unassigned");
        safeSetText(holder.tvWorkingDays, "Unassigned");
        safeSetText(holder.tvDriverShareName, "Unassigned Driver");
        safeSetText(holder.tvPaoShareName, "Unassigned PAO");

        populateScheduleContext(holder, context, scheduleId);

        if (isSalaryVisible) {
            safeSetText(holder.tvTotalNet, totalNetStr);
            safeSetText(holder.tvNetBottom, totalNetStr);
            holder.ivToggleVisibility.setImageResource(R.drawable.view);
        } else {
            safeSetText(holder.tvTotalNet, hiddenText);
            safeSetText(holder.tvNetBottom, hiddenText);
            holder.ivToggleVisibility.setImageResource(R.drawable.hide);
        }

        safeSetText(holder.tvScheduleGross, grossStr.replace("₱ ", "₱"));
        safeSetText(holder.tvScheduleExpenses, expensesStr.replace("- ₱ ", "₱"));
        safeSetText(holder.tvScheduleNet, totalNetStr.replace("₱ ", "₱"));
    }

    private void populateScheduleContext(SummaryViewHolder holder, Context context, String scheduleId) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);

        // Default text if no schedule matches today
        safeSetText(holder.tvBoundaryDay, "Unassigned");
        safeSetText(holder.tvWorkingDays, "Unassigned");
        safeSetText(holder.tvDriverShareName, "Unassigned Driver");
        safeSetText(holder.tvPaoShareName, "Unassigned PAO");
        safeSetText(holder.tvJeepUnit, "Unassigned");
        safeSetText(holder.tvFuelDay, "No Plate");
        safeSetText(holder.tvScheduleDate, "Today");

        if (cachedSchedules == null) return;

        try {
            JSONObject root = new JSONObject(cachedSchedules);
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null) return;

            // Get today's day name (e.g., "Monday") and formatted date for matching
            String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String secretKey = com.example.portjeep.BuildConfig.CRYPTO_SECRET_KEY;

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject sched = schedules.getJSONObject(i);
                boolean isMatch = false;

                String schedDay = sched.optString("day", "");
                String schedDate = sched.optString("date", "");

                // Strictly check if it matches today's day or today's specific date
                if (todayName.equalsIgnoreCase(schedDay) || todayDateStr.equals(schedDate)) {
                    isMatch = true;
                }

                if (isMatch) {
                    String jeep = sched.optString("jeep", "N/A");
                    String driver = "Unassigned";
                    String pao = "Unassigned";

                    if (sched.has("driver") && !sched.isNull("driver")) {
                        Object d = sched.get("driver");
                        driver = (d instanceof JSONObject) ? ((JSONObject) d).optString("name", "Driver") : d.toString();
                    }
                    driver = com.example.portjeep.utils.CryptoUtils.decrypt(driver, secretKey);
                    if (driver == null || driver.isEmpty() || driver.equalsIgnoreCase("null")) driver = "Unassigned Driver";

                    if (sched.has("pao") && !sched.isNull("pao")) {
                        Object p = sched.get("pao");
                        pao = (p instanceof JSONObject) ? ((JSONObject) p).optString("name", "PAO") : p.toString();
                    }
                    pao = com.example.portjeep.utils.CryptoUtils.decrypt(pao, secretKey);
                    if (pao == null || pao.isEmpty() || pao.equalsIgnoreCase("null")) pao = "Unassigned PAO";

                    safeSetText(holder.tvBoundaryDay, driver);
                    safeSetText(holder.tvWorkingDays, pao);
                    safeSetText(holder.tvDriverShareName, driver);
                    safeSetText(holder.tvPaoShareName, pao);

                    if (jeep.contains("(") && jeep.contains(")")) {
                        int s = jeep.indexOf("("), e = jeep.indexOf(")");
                        safeSetText(holder.tvJeepUnit, jeep.substring(s + 1, e).trim());
                        safeSetText(holder.tvFuelDay, jeep.substring(0, s).trim());
                    } else {
                        safeSetText(holder.tvJeepUnit, jeep.isEmpty() ? "Unassigned" : jeep);
                        safeSetText(holder.tvFuelDay, jeep.isEmpty() ? "No Plate" : jeep);
                    }
                    safeSetText(holder.tvScheduleDate, sched.optString("date", "Today"));
                    break;
                }
            }
        } catch (Exception e) {
            Log.e("SalaryPagerAdapter", "Error syncing schedule context", e);
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
        TextView tvScheduleGross, tvScheduleExpenses, tvScheduleNet;
        TextView tvBoundaryDay, tvJeepUnit, tvFuelDay, tvWorkingDays, tvNetCalc, tvTrend;
        TextView tvDriverShareName, tvPaoShareName;
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
            
            tvScheduleGross = itemView.findViewById(R.id.tv_val_schedule_gross);
            tvScheduleExpenses = itemView.findViewById(R.id.tv_val_schedule_expenses);
            tvScheduleNet = itemView.findViewById(R.id.tv_val_schedule_net);

            tvBoundaryDay = itemView.findViewById(R.id.tv_val_boundary_day);
            tvJeepUnit = itemView.findViewById(R.id.tv_val_jeep_unit);
            tvFuelDay = itemView.findViewById(R.id.tv_val_fuel_day);
            tvWorkingDays = itemView.findViewById(R.id.tv_val_working_days);
            tvNetCalc = itemView.findViewById(R.id.tv_net_income_calculation);
            tvTrend = itemView.findViewById(R.id.tv_income_trend);
            tvDriverShareName = itemView.findViewById(R.id.tv_desc_gov_deductions);
            tvPaoShareName = itemView.findViewById(R.id.tv_desc_attendance_incentive);
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
