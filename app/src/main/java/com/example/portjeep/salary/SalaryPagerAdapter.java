package com.example.portjeep.salary;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.transition.TransitionManager;
import android.util.Log;
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
import com.example.portjeep.utils.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.DecimalFormat;

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

    private Date parseItemDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("N/A")) {
            return new Date(0);
        }
        String cleaned = dateStr.trim();
        String[] datePatterns = new String[]{
                "EEEE, MMM d", "EEEE, MMMM d", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"
        };
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (String pattern : datePatterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                Date parsed = sdf.parse(cleaned);
                if (parsed != null) {
                    if (!pattern.contains("yyyy")) {
                        Calendar pCal = Calendar.getInstance();
                        pCal.set(Calendar.YEAR, currentYear);
                        return pCal.getTime();
                    }
                    return parsed;
                }
            } catch (Exception ignored) {}
        }

        // Fallback for day names like "Monday", "Tuesday"
        String low = cleaned.toLowerCase(Locale.US);
        int targetDayOfWeek = -1;
        if (low.contains("sunday")) targetDayOfWeek = Calendar.SUNDAY;
        else if (low.contains("monday")) targetDayOfWeek = Calendar.MONDAY;
        else if (low.contains("tuesday")) targetDayOfWeek = Calendar.TUESDAY;
        else if (low.contains("wednesday")) targetDayOfWeek = Calendar.WEDNESDAY;
        else if (low.contains("thursday")) targetDayOfWeek = Calendar.THURSDAY;
        else if (low.contains("friday")) targetDayOfWeek = Calendar.FRIDAY;
        else if (low.contains("saturday")) targetDayOfWeek = Calendar.SATURDAY;

        if (targetDayOfWeek != -1) {
            Calendar c = Calendar.getInstance();
            int currentDayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            int diff = targetDayOfWeek - currentDayOfWeek;
            if (diff > 0) diff -= 7; // force it to be past or current week
            c.add(Calendar.DATE, diff);
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            return c.getTime();
        }

        return new Date(0);
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
        int todayIndex = cal.get(Calendar.DAY_OF_WEEK);

        cal.add(Calendar.DATE, -1);
        Date yesterdayDate = cal.getTime();
        String yesterdayName = dayFormat.format(yesterdayDate);
        String yesterdayFormatted = fullFormat.format(yesterdayDate);
        String yesterdayDateKey = dateKeyFormat.format(yesterdayDate);

        boolean foundToday = false;
        boolean foundYesterday = false;

        if (remittanceData != null) {
            for (int i = 0; i < remittanceData.length(); i++) {
                try {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", dayName);
                    String scheduleId = dayGroup.optString("schedule_id", "");

                    boolean isToday = dayName.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayFormatted) ||
                            groupDate.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayDateKey);

                    boolean isYesterday = dayName.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayFormatted) ||
                            groupDate.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayDateKey);

                    // Robust upcoming filter check
                    boolean isUpcoming = false;

                    Date parsedDate = null;
                    String[] datePatterns = new String[]{"yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"};
                    int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                    for (String pattern : datePatterns) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                            Date parsed = sdf.parse(groupDate.trim());
                            if (parsed != null) {
                                if (!pattern.contains("yyyy")) {
                                    Calendar pCal = Calendar.getInstance(); pCal.setTime(parsed);
                                    pCal.set(Calendar.YEAR, currentYear);
                                    parsedDate = pCal.getTime();
                                } else {
                                    parsedDate = parsed;
                                }
                                break;
                            }
                        } catch (Exception ignored) {}
                    }

                    if (parsedDate != null) {
                        Calendar todayCal = Calendar.getInstance();
                        todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0);
                        todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);
                        Date todayAtMidnight = todayCal.getTime();

                        Calendar parsedCal = Calendar.getInstance(); parsedCal.setTime(parsedDate);
                        parsedCal.set(Calendar.HOUR_OF_DAY, 0); parsedCal.set(Calendar.MINUTE, 0);
                        parsedCal.set(Calendar.SECOND, 0); parsedCal.set(Calendar.MILLISECOND, 0);
                        if (parsedCal.getTime().after(todayAtMidnight)) {
                            isUpcoming = true;
                        }
                    } else {
                        // Fallback check based on day index if it's a day name
                        int targetIndex = -1;
                        switch (dayName.toLowerCase(Locale.US)) {
                            case "sunday": targetIndex = Calendar.SUNDAY; break;
                            case "monday": targetIndex = Calendar.MONDAY; break;
                            case "tuesday": targetIndex = Calendar.TUESDAY; break;
                            case "wednesday": targetIndex = Calendar.WEDNESDAY; break;
                            case "thursday": targetIndex = Calendar.THURSDAY; break;
                            case "friday": targetIndex = Calendar.FRIDAY; break;
                            case "saturday": targetIndex = Calendar.SATURDAY; break;
                        }
                        if (targetIndex != -1 && !isToday && !isYesterday) {
                            int diff = targetIndex - todayIndex;
                            if (diff < 0) diff += 7;
                            if (diff > 0 && diff <= 3) {
                                isUpcoming = true;
                            }
                        }
                    }

                    if (isUpcoming) {
                        // Skip any upcoming day (e.g. Thursday, Friday when it's not their day yet)
                        continue;
                    }

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
                        Collections.sort(sortedPartials, (a, b) -> {
                            long t1 = parseCreatedAtMillis(a.optString("created_at", ""));
                            long t2 = parseCreatedAtMillis(b.optString("created_at", ""));
                            return Long.compare(t1, t2);
                        });

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

                    // Add incentive from schedule to history
                    double totalIncentives = dayGroup.optDouble("incentive", 0.0);
                    totalRemittance += (totalIncentives * 2);
                    totalNet -= (totalIncentives * 2);

                    boolean hasSchedule = hasScheduleForDate(context, isToday ? todayName : (isYesterday ? yesterdayName : dayName), isToday ? todayDateKey : (isYesterday ? yesterdayDateKey : groupDate));
                    boolean isRest = !hasRemittances && !hasSchedule;

                    HistoryAdapter.HistoryItem item = new HistoryAdapter.HistoryItem(
                            groupDate.isEmpty() ? (isToday ? todayFormatted : (isYesterday ? yesterdayFormatted : dayName)) : groupDate,
                            df.format(totalGross),
                            df.format(totalRemittance),
                            df.format(totalNet),
                            isRest
                    );

                    for (HistoryAdapter.PartialReport report : reports) {
                        item.addPartial(report.header, report.amount, report.remittance, report.net);
                    }

                    if (isToday) {
                        item.setToday(true);
                        item.setExpanded(true);
                        foundToday = true;
                    } else if (isYesterday) {
                        foundYesterday = true;
                    }

                    items.add(item);

                } catch (Exception e) {
                    Log.e("SalaryPagerAdapter", "Error parsing history group", e);
                }
            }
        }

        if (!foundToday) {
            boolean hasScheduleToday = hasScheduleForDate(context, todayName, todayDateKey);
            double todayIncentive = getIncentiveForDate(context, todayName, todayDateKey, "");
            HistoryAdapter.HistoryItem todayItem = new HistoryAdapter.HistoryItem(todayFormatted, "0.00", df.format(todayIncentive * 2), df.format(-(todayIncentive * 2)), !hasScheduleToday);
            todayItem.setToday(true);
            todayItem.setExpanded(true);
            items.add(todayItem);
        }

        if (!foundYesterday) {
            boolean hasScheduleYesterday = hasScheduleForDate(context, yesterdayName, yesterdayDateKey);
            double yesterdayIncentive = getIncentiveForDate(context, yesterdayName, yesterdayDateKey, "");
            HistoryAdapter.HistoryItem yesterdayItem = new HistoryAdapter.HistoryItem(yesterdayFormatted, "0.00", df.format(yesterdayIncentive * 2), df.format(-(yesterdayIncentive * 2)), !hasScheduleYesterday);
            items.add(yesterdayItem);
        }

        // Sort everything descendingly so that the current data (most recent) is on top, and older days are below
        Collections.sort(items, (a, b) -> parseItemDate(b.date).compareTo(parseItemDate(a.date)));

        HistoryAdapter historyAdapter = new HistoryAdapter(items);
        historyAdapter.setVisible(isSalaryVisible);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(historyAdapter);
    }

    private boolean hasScheduleForDate(Context context, String dayName, String dateStr) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);
        if (cachedSchedules == null || cachedSchedules.trim().isEmpty()) return false;
        try {
            JSONArray schedules = null;
            if (cachedSchedules.trim().startsWith("[")) {
                schedules = new JSONArray(cachedSchedules);
            } else {
                JSONObject root = new JSONObject(cachedSchedules);
                schedules = root.optJSONArray("schedules");
            }
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
        String overallNetStr = "₱ 0.00";
        String overallGrossStr = "₱ 0.00";
        String overallExpensesStr = "₱ 0.00";
        String overallShareStr = "₱ 0.00";

        String lastGrossVal = "₱0.00";
        String lastExpensesVal = "₱0.00";
        String lastNetVal = "₱0.00";

        if (remittanceData != null && remittanceData.length() > 0) {
            try {
                double overallNet = 0;
                double overallGross = 0;
                double overallExpenses = 0;
                double overallShare = 0;
                int totalPartialCount = 0;

                for (int i = 0; i < remittanceData.length(); i++) {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;
                    JSONArray partialsArray = dayGroup.optJSONArray("remittances");
                    double dayNet = 0;
                    if (partialsArray != null) {
                        totalPartialCount += partialsArray.length();
                        for (int j = 0; j < partialsArray.length(); j++) {
                            JSONObject p = partialsArray.optJSONObject(j);
                            if (p != null) {
                                overallGross += p.optDouble("gross", 0);
                                overallExpenses += p.optDouble("expenses", 0);
                                dayNet += p.optDouble("net", 0);
                                overallShare += p.optDouble("employeeCut", 0);
                            }
                        }
                    }
                    double totalIncentives = dayGroup.optDouble("incentive", 0.0);
                    overallExpenses += (totalIncentives * 2);
                    dayNet -= (totalIncentives * 2);
                    overallNet += dayNet;
                }
                overallNetStr = "₱ " + df.format(overallNet);
                overallGrossStr = "₱ " + df.format(overallGross);
                overallExpensesStr = "₱ " + df.format(overallExpenses);
                overallShareStr = "₱ " + df.format(overallShare);
                tripCountTrend = totalPartialCount + (totalPartialCount == 1 ? " partial" : " partials");

                JSONObject latestGroup = null;
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
                SimpleDateFormat fullFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
                SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

                Date now = new Date();
                String todayName = dayFormat.format(now);
                String todayFormatted = fullFormat.format(now);
                String todayDateKey = dateKeyFormat.format(now);

                for (int i = 0; i < remittanceData.length(); i++) {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", dayName);

                    boolean isToday = dayName.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayFormatted) ||
                            groupDate.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayDateKey);

                    if (isToday) {
                        latestGroup = dayGroup;
                        break;
                    }
                }

                if (latestGroup != null) {
                    scheduleId = latestGroup.optString("schedule_id", "");
                    JSONArray partialsArray = latestGroup.optJSONArray("remittances");

                    double sumGross = 0, sumExpenses = 0, sumNet = 0, sumShare = 0;
                    int count = 0;
                    long maxTimeMillis = -1;

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
                            long currentMillis = parseCreatedAtMillis(currentTime);
                            if (currentMillis >= maxTimeMillis) {
                                maxTimeMillis = currentMillis;
                                lastPartialTime = currentTime;
                                lastGrossVal = "₱" + df.format(p.optDouble("gross", 0));
                                lastExpensesVal = "₱" + df.format(p.optDouble("expenses", 0));
                                lastNetVal = "₱" + df.format(p.optDouble("net", 0));
                            }
                        }
                    }

                    double totalIncentives = latestGroup.optDouble("incentive", 0.0);
                    sumExpenses += (totalIncentives * 2);
                    sumNet -= (totalIncentives * 2);

                    grossStr = "₱ " + df.format(sumGross);
                    expensesStr = "- ₱ " + df.format(sumExpenses);
                    totalNetStr = "₱ " + df.format(sumNet);
                    shareStr = "₱ " + df.format(sumShare);
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

            safeSetText(holder.tvTotalNet, overallNetStr);
            safeSetText(holder.tvNetBottom, totalNetStr);

            safeSetText(holder.tvTotalGrossOverall, overallGrossStr);
            safeSetText(holder.tvTotalExpensesOverall, overallExpensesStr);
            safeSetText(holder.tvTotalShareOverall, overallShareStr);

            safeSetText(holder.tvDailyGross, grossStr);
            safeSetText(holder.tvDailyExpenses, expensesStr.replace("- ", ""));
            safeSetText(holder.tvDailyNet, totalNetStr);

            safeSetText(holder.tvScheduleGross, lastGrossVal);
            safeSetText(holder.tvScheduleExpenses, lastExpensesVal);
            safeSetText(holder.tvScheduleNet, lastNetVal);

            holder.ivToggleVisibility.setImageResource(R.drawable.view);
        } else {
            safeSetText(holder.tvGross, hiddenText);
            safeSetText(holder.tvRemittance, hiddenText);
            safeSetText(holder.tvDeductions, hiddenText);
            safeSetText(holder.tvIncentives, hiddenText);

            safeSetText(holder.tvTotalNet, hiddenText);
            safeSetText(holder.tvNetBottom, hiddenText);

            safeSetText(holder.tvTotalGrossOverall, hiddenText);
            safeSetText(holder.tvTotalExpensesOverall, hiddenText);
            safeSetText(holder.tvTotalShareOverall, hiddenText);

            safeSetText(holder.tvDailyGross, hiddenText);
            safeSetText(holder.tvDailyExpenses, hiddenText);
            safeSetText(holder.tvDailyNet, hiddenText);

            safeSetText(holder.tvScheduleGross, hiddenText);
            safeSetText(holder.tvScheduleExpenses, hiddenText);
            safeSetText(holder.tvScheduleNet, hiddenText);

            holder.ivToggleVisibility.setImageResource(R.drawable.hide);
        }
    }

    private long parseCreatedAtMillis(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            // Match "Sep 16, 2026, 12:34 AM"
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US);
            Date d = sdf.parse(dateStr);
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            try {
                // Try "Sep 16, 2026 h:mm a" (no comma after year)
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US);
                Date d = sdf.parse(dateStr);
                return d != null ? d.getTime() : 0;
            } catch (Exception e2) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    Date d = sdf.parse(dateStr);
                    return d != null ? d.getTime() : 0;
                } catch (Exception e3) {
                    return 0;
                }
            }
        }
    }

    private double getIncentiveForDate(Context context, String dayName, String dateStr, String scheduleId) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);
        if (cachedSchedules == null || cachedSchedules.trim().isEmpty()) return 0.0;

        try {
            JSONArray schedules = null;
            if (cachedSchedules.trim().startsWith("[")) {
                schedules = new JSONArray(cachedSchedules);
            } else {
                JSONObject root = new JSONObject(cachedSchedules);
                schedules = root.optJSONArray("schedules");
            }
            if (schedules == null) return 0.0;

            for (int i = 0; i < schedules.length(); i++) {
                JSONObject sched = schedules.getJSONObject(i);

                String schedId = sched.optString("schedule_id", "");
                String schedDay = sched.optString("day", "");
                String schedDate = sched.optString("date", "");

                boolean isMatch = (!scheduleId.isEmpty() && schedId.equals(scheduleId)) ||
                        dayName.equalsIgnoreCase(schedDay) ||
                        dateStr.equals(schedDate) ||
                        (schedDate.length() >= 10 && schedDate.startsWith(dateStr));

                if (isMatch) {
                    double totalIncentive = 0.0;

                    if (sched.has("incentive") && !sched.isNull("incentive")) {
                        Object incObj = sched.get("incentive");
                        if (incObj instanceof Number) {
                            totalIncentive = ((Number) incObj).doubleValue();
                        } else if (incObj instanceof String) {
                            String incStr = (String) incObj;
                            if (!incStr.equalsIgnoreCase("Found")) {
                                try {
                                    totalIncentive = Double.parseDouble(incStr);
                                } catch (NumberFormatException e) {
                                    totalIncentive = 0.0;
                                }
                            }
                        }
                    }

                    if (sched.has("driver") && !sched.isNull("driver")) {
                        Object d = sched.get("driver");
                        if (d instanceof JSONObject) {
                            totalIncentive += ((JSONObject) d).optDouble("incentive", 0.0);
                        }
                    }
                    if (sched.has("pao") && !sched.isNull("pao")) {
                        Object p = sched.get("pao");
                        if (p instanceof JSONObject) {
                            totalIncentive += ((JSONObject) p).optDouble("incentive", 0.0);
                        }
                    }
                    return totalIncentive;
                }
            }
        } catch (Exception e) {
            Log.e("SalaryPagerAdapter", "Error parsing incentives", e);
        }
        return 0.0;
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

        if (cachedSchedules == null || cachedSchedules.trim().isEmpty()) return;

        try {
            JSONArray schedules = null;
            if (cachedSchedules.trim().startsWith("[")) {
                schedules = new JSONArray(cachedSchedules);
            } else {
                JSONObject root = new JSONObject(cachedSchedules);
                schedules = root.optJSONArray("schedules");
            }
            if (schedules == null) return;

            String todayName = new SimpleDateFormat("EEEE", Locale.US).format(new Date());
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

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
                    try {
                        String dec = com.example.portjeep.utils.CryptoUtils.decrypt(driver, com.example.portjeep.BuildConfig.CRYPTO_SECRET_KEY);
                        if (dec != null && !dec.isEmpty() && !dec.equalsIgnoreCase("null")) driver = dec;
                    } catch (Exception ignored) {}

                    if (sched.has("pao") && !sched.isNull("pao")) {
                        Object p = sched.get("pao");
                        pao = (p instanceof JSONObject) ? ((JSONObject) p).optString("name", "PAO") : p.toString();
                    }
                    try {
                        String dec = com.example.portjeep.utils.CryptoUtils.decrypt(pao, com.example.portjeep.BuildConfig.CRYPTO_SECRET_KEY);
                        if (dec != null && !dec.isEmpty() && !dec.equalsIgnoreCase("null")) pao = dec;
                    } catch (Exception ignored) {}

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
        TextView tvTotalGrossOverall, tvTotalExpensesOverall, tvTotalShareOverall;
        TextView tvDailyGross, tvDailyExpenses, tvDailyNet;
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

            tvTotalGrossOverall = itemView.findViewById(R.id.tv_val_total_gross_overall);
            tvTotalExpensesOverall = itemView.findViewById(R.id.tv_val_total_expenses_overall);
            tvTotalShareOverall = itemView.findViewById(R.id.tv_val_total_share_overall);

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
