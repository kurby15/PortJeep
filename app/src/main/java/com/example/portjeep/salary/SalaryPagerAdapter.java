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
    private final String hiddenText = "₱ ••••";
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

        Context context = parent.getContext();
        String userId = PreferenceManager.getCurrentUserId(context);
        String key = KEY_VISIBLE + "_" + userId;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        isSalaryVisible = prefs.getBoolean(key, true);

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

            Context context = summaryHolder.itemView.getContext();
            String userId = PreferenceManager.getCurrentUserId(context);
            String key = KEY_VISIBLE + "_" + userId;
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            isSalaryVisible = prefs.getBoolean(key, true);

            updateSummaryUI(summaryHolder);

            summaryHolder.ivToggleVisibility.setOnClickListener(v -> {
                Context ctx = v.getContext();
                TransitionManager.beginDelayedTransition((ViewGroup) summaryHolder.itemView);

                isSalaryVisible = !isSalaryVisible;
                String uId = PreferenceManager.getCurrentUserId(ctx);
                String k = KEY_VISIBLE + "_" + uId;
                SharedPreferences p = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                p.edit().putBoolean(k, isSalaryVisible).apply();

                updateSummaryUI(summaryHolder);
                notifyItemChanged(TYPE_HISTORY);
            });
        } else if (holder instanceof HistoryViewHolder) {
            setupHistoryList((HistoryViewHolder) holder);
        }
    }

    private void setupHistoryList(HistoryViewHolder holder) {
        List<HistoryAdapter.HistoryItem> items = new ArrayList<>();
        Context context = holder.itemView.getContext();

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        SimpleDateFormat fullFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Date now = new Date();
        String todayName = dayFormat.format(now);
        String todayFormatted = fullFormat.format(now);
        String todayDateKey = dateKeyFormat.format(now);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Date yesterdayDate = cal.getTime();
        String yesterdayName = dayFormat.format(yesterdayDate);
        String yesterdayFormatted = fullFormat.format(yesterdayDate);
        String yesterdayDateKey = dateKeyFormat.format(yesterdayDate);

        HistoryAdapter.HistoryItem todayItem = null;
        HistoryAdapter.HistoryItem yesterdayItem = null;

        if (remittanceData != null) {
            for (int i = 0; i < remittanceData.length(); i++) {
                try {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", dayName);

                    boolean isToday = dayName.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayFormatted) ||
                            groupDate.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayDateKey);

                    boolean isYesterday = dayName.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayFormatted) ||
                            groupDate.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayDateKey);

                    if (!isToday && !isYesterday) continue;

                    JSONArray partialsArray = dayGroup.optJSONArray("remittances");
                    double totalGross = 0, totalRemittance = 0, totalNet = 0;
                    List<HistoryAdapter.PartialReport> reports = new ArrayList<>();
                    boolean hasRemittances = partialsArray != null && partialsArray.length() > 0;

                    if (hasRemittances) {
                        List<JSONObject> sortedPartials = new ArrayList<>();
                        for (int k = 0; k < partialsArray.length(); k++) {
                            JSONObject p = partialsArray.optJSONObject(k);
                            if (p != null) sortedPartials.add(p);
                        }
                        Collections.sort(sortedPartials, (a, b) ->
                                a.optString("created_at", "").compareTo(b.optString("created_at", "")));

                        for (int j = 0; j < sortedPartials.size(); j++) {
                            JSONObject partial = sortedPartials.get(j);
                            double g = partial.optDouble("gross", 0);
                            double e = partial.optDouble("expenses", 0);
                            double n = partial.optDouble("net", 0);
                            String time = partial.optString("created_at", "");
                            totalGross += g;
                            totalRemittance += e;
                            totalNet += n;
                            reports.add(0, new HistoryAdapter.PartialReport("Report #" + (j + 1) + " · " + time, df.format(g), df.format(e), df.format(n)));
                        }
                    }

                    boolean hasSchedule = hasScheduleForDate(context, isToday ? todayName : yesterdayName, isToday ? todayDateKey : yesterdayDateKey);
                    boolean isRest = !hasRemittances && !hasSchedule;

                    HistoryAdapter.HistoryItem item = new HistoryAdapter.HistoryItem(
                            groupDate.isEmpty() ? (isToday ? todayFormatted : yesterdayFormatted) : groupDate,
                            df.format(totalGross),
                            df.format(totalRemittance),
                            df.format(totalNet),
                            isRest
                    );

                    for (HistoryAdapter.PartialReport report : reports) {
                        item.addPartial(report.header, report.amount, report.remittance, report.net);
                    }
                    item.setExpanded(isToday);

                    if (isToday) {
                        todayItem = item;
                    } else {
                        yesterdayItem = item;
                    }

                } catch (Exception e) {
                    Log.e("SalaryPagerAdapter", "Error parsing history group", e);
                }
            }
        }

        if (todayItem == null) {
            boolean hasScheduleToday = hasScheduleForDate(context, todayName, todayDateKey);
            todayItem = new HistoryAdapter.HistoryItem(todayFormatted, "0.00", "0.00", "0.00", !hasScheduleToday);
            todayItem.setExpanded(true);
        }

        if (yesterdayItem == null) {
            boolean hasScheduleYesterday = hasScheduleForDate(context, yesterdayName, yesterdayDateKey);
            yesterdayItem = new HistoryAdapter.HistoryItem(yesterdayFormatted, "0.00", "0.00", "0.00", !hasScheduleYesterday);
        }

        items.add(todayItem);
        items.add(yesterdayItem);

        HistoryAdapter historyAdapter = new HistoryAdapter(items);
        historyAdapter.setVisible(isSalaryVisible);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(historyAdapter);
    }

    private boolean hasScheduleForDate(Context context, String dayName, String dateStr) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);
        if (cachedSchedules == null) return false;
        try {
            JSONObject root = new JSONObject(cachedSchedules);
            JSONArray schedules = root.optJSONArray("schedules");
            if (schedules == null) return false;
            for (int i = 0; i < schedules.length(); i++) {
                JSONObject sched = schedules.getJSONObject(i);
                String schedDay = sched.optString("day", "");
                String schedDate = sched.optString("date", "");

                if (dayName.equalsIgnoreCase(schedDay) ||
                        dateStr.equals(schedDate) ||
                        (schedDate.length() >= 10 && schedDate.startsWith(dateStr))) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e("SalaryPagerAdapter", "Error checking schedule", e);
        }
        return false;
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

        safeSetText(holder.tvLastPartial, lastPartialTime);
        safeSetText(holder.tvTrend, tripCountTrend);

        safeSetText(holder.tvBoundaryDay, "Unassigned");
        safeSetText(holder.tvWorkingDays, "Unassigned");
        safeSetText(holder.tvDriverShareName, "Unassigned Driver");
        safeSetText(holder.tvPaoShareName, "Unassigned PAO");

        populateScheduleContext(holder, context, scheduleId);

        if (isSalaryVisible) {
            safeSetText(holder.tvGross, grossStr);
            safeSetText(holder.tvRemittance, expensesStr);

            String role = PreferenceManager.getUserRole(context);
            if (role != null) {
                String upperRole = role.toUpperCase();
                boolean isDriver = upperRole.contains("DRIVER");
                boolean isPao = upperRole.contains("PAO") || upperRole.contains("ASSISTANT");

                // Control Driver Share row visibility & values
                if (holder.rowDriverShare != null) {
                    if (isDriver || (!isDriver && !isPao)) {
                        holder.rowDriverShare.setVisibility(View.VISIBLE);
                        if (holder.dividerDriverShare != null) holder.dividerDriverShare.setVisibility(View.VISIBLE);
                        safeSetText(holder.tvDeductions, shareStr);
                    } else {
                        holder.rowDriverShare.setVisibility(View.GONE);
                        if (holder.dividerDriverShare != null) holder.dividerDriverShare.setVisibility(View.GONE);
                    }
                }

                // Control PAO Share row visibility & values
                if (holder.rowPaoShare != null) {
                    if (isPao || (!isDriver && !isPao)) {
                        holder.rowPaoShare.setVisibility(View.VISIBLE);
                        if (holder.dividerPaoShare != null) holder.dividerPaoShare.setVisibility(View.VISIBLE);
                        safeSetText(holder.tvIncentives, shareStr);
                    } else {
                        holder.rowPaoShare.setVisibility(View.GONE);
                        if (holder.dividerPaoShare != null) holder.dividerPaoShare.setVisibility(View.GONE);
                    }
                }
            } else {
                safeSetText(holder.tvDeductions, shareStr);
                safeSetText(holder.tvIncentives, "₱ 0.00");
            }

            safeSetText(holder.tvTotalNet, totalNetStr);
            safeSetText(holder.tvNetBottom, totalNetStr);

            safeSetText(holder.tvScheduleGross, grossStr.replace("₱ ", "₱"));
            safeSetText(holder.tvScheduleExpenses, expensesStr.replace("- ₱ ", "₱"));
            safeSetText(holder.tvScheduleNet, totalNetStr.replace("₱ ", "₱"));

            holder.ivToggleVisibility.setImageResource(R.drawable.view);
        } else {
            safeSetText(holder.tvGross, hiddenText);
            safeSetText(holder.tvRemittance, hiddenText);
            safeSetText(holder.tvDeductions, hiddenText);
            safeSetText(holder.tvIncentives, hiddenText);

            safeSetText(holder.tvTotalNet, hiddenText);
            safeSetText(holder.tvNetBottom, hiddenText);

            safeSetText(holder.tvScheduleGross, hiddenText);
            safeSetText(holder.tvScheduleExpenses, hiddenText);
            safeSetText(holder.tvScheduleNet, hiddenText);

            holder.ivToggleVisibility.setImageResource(R.drawable.hide);
        }
    }

    private void populateScheduleContext(SummaryViewHolder holder, Context context, String scheduleId) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);

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

            String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String secretKey = com.example.portjeep.BuildConfig.CRYPTO_SECRET_KEY;

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject sched = schedules.getJSONObject(i);
                boolean isMatch = false;

                String schedDay = sched.optString("day", "");
                String schedDate = sched.optString("date", "");

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

        View rowDriverShare, dividerDriverShare;
        View rowPaoShare, dividerPaoShare;

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

            rowDriverShare = itemView.findViewById(R.id.row_driver_share);
            dividerDriverShare = itemView.findViewById(R.id.divider_driver_share);
            rowPaoShare = itemView.findViewById(R.id.row_pao_share);
            dividerPaoShare = itemView.findViewById(R.id.divider_pao_share);
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