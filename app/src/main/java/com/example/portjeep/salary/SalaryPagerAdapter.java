package com.example.portjeep.salary;

import android.content.Context;
import android.content.SharedPreferences;
import android.transition.AutoTransition;
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
    private JSONArray rawIncomingData;

    public SalaryPagerAdapter() {
    }

    public void setRemittanceData(JSONArray data) {
        this.rawIncomingData = data;
        this.remittanceData = data;
        notifyDataSetChanged();
    }

    private JSONArray getMergedRemittanceData(Context context) {
        String userId = PreferenceManager.getCurrentUserId(context);
        String localPrefsKey = "local_remittance_history_" + userId;
        SharedPreferences sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        JSONArray mergedData = new JSONArray();
        String savedHistoryStr = sharedPrefs.getString(localPrefsKey, "[]");
        try {
            mergedData = new JSONArray(savedHistoryStr);
        } catch (Exception e) {
            mergedData = new JSONArray();
        }

        if (this.rawIncomingData != null) {
            if (this.rawIncomingData.length() > 0) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
                SimpleDateFormat fullFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
                SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

                Date now = new Date();
                String todayName = dayFormat.format(now);
                String todayFormatted = fullFormat.format(now);
                String todayDateKey = dateKeyFormat.format(now);

                for (int i = 0; i < this.rawIncomingData.length(); i++) {
                    JSONObject newGroup = this.rawIncomingData.optJSONObject(i);
                    if (newGroup == null) continue;

                    String newDate = newGroup.optString("date", "").trim();
                    if (newDate.isEmpty()) {
                        newDate = newGroup.optString("day", "").trim();
                    }
                    if (newDate.isEmpty()) continue;

                    boolean isToday = newDate.equalsIgnoreCase(todayName) ||
                            newDate.contains(todayFormatted) ||
                            newDate.equalsIgnoreCase(todayDateKey);

                    boolean found = false;
                    for (int j = 0; j < mergedData.length(); j++) {
                        JSONObject existingGroup = mergedData.optJSONObject(j);
                        if (existingGroup != null) {
                            String existingDate = existingGroup.optString("date", "").trim();
                            if (existingDate.isEmpty()) {
                                existingDate = existingGroup.optString("day", "").trim();
                            }
                            if (newDate.equalsIgnoreCase(existingDate)) {
                                if (isToday) {
                                    try {
                                        mergedData.put(j, newGroup);
                                    } catch (Exception ignored) {}
                                }
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        mergedData.put(newGroup);
                    }
                }
                sharedPrefs.edit().putString(localPrefsKey, mergedData.toString()).apply();
                PreferenceManager.saveSalaryCache(context, mergedData.toString());
            }
            this.rawIncomingData = null;
        }

        this.remittanceData = mergedData;
        return mergedData;
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

            getMergedRemittanceData(context);

            updateSummaryUI(summaryHolder);

            summaryHolder.ivToggleVisibility.setOnClickListener(v -> {
                Context ctx = v.getContext();
                
                // Snappy animation for toggle
                AutoTransition transition = new AutoTransition();
                transition.setDuration(100);
                TransitionManager.beginDelayedTransition((ViewGroup) summaryHolder.itemView, transition);

                isSalaryVisible = !isSalaryVisible;
                String uId = PreferenceManager.getCurrentUserId(ctx);
                String k = KEY_VISIBLE + "_" + uId;
                SharedPreferences p = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                p.edit().putBoolean(k, isSalaryVisible).apply();

                updateSummaryUI(summaryHolder);
                notifyItemChanged(TYPE_HISTORY);
            });
        } else if (holder instanceof HistoryViewHolder) {
            HistoryViewHolder historyHolder = (HistoryViewHolder) holder;
            getMergedRemittanceData(historyHolder.itemView.getContext());
            setupHistoryList(historyHolder);
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
                    Calendar pCal = Calendar.getInstance();
                    pCal.setTime(parsed);
                    if (!pattern.contains("yyyy")) {
                        pCal.set(Calendar.YEAR, currentYear);
                    }
                    pCal.set(Calendar.HOUR_OF_DAY, 0);
                    pCal.set(Calendar.MINUTE, 0);
                    pCal.set(Calendar.SECOND, 0);
                    pCal.set(Calendar.MILLISECOND, 0);
                    return pCal.getTime();
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

    private boolean isCurrentWeek(Date date) {
        if (date == null || date.getTime() == 0) return false;
        Calendar currentCal = Calendar.getInstance();
        Calendar targetCal = Calendar.getInstance();
        targetCal.setTime(date);
        return currentCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                currentCal.get(Calendar.WEEK_OF_YEAR) == targetCal.get(Calendar.WEEK_OF_YEAR);
    }

    private void setupHistoryList(HistoryViewHolder holder) {
        List<HistoryAdapter.HistoryItem> dailyItems = new ArrayList<>();
        List<HistoryAdapter.HistoryItem> previousItems = new ArrayList<>();
        Context context = holder.itemView.getContext();

        getMergedRemittanceData(context);

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.US);
        SimpleDateFormat fullFormat = new SimpleDateFormat("EEEE, MMM d", Locale.US);
        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Date now = new Date();
        String todayName = dayFormat.format(now);
        String todayFormatted = fullFormat.format(now);
        String todayDateKey = dateKeyFormat.format(now);

        Calendar cal = Calendar.getInstance();
        int todayIndex = cal.get(Calendar.DAY_OF_WEEK);
        boolean isAfter10PM = cal.get(Calendar.HOUR_OF_DAY) >= 22;

        cal.add(Calendar.DATE, -1);
        Date yesterdayDate = cal.getTime();
        String yesterdayName = dayFormat.format(yesterdayDate);
        String yesterdayFormatted = fullFormat.format(yesterdayDate);
        String yesterdayDateKey = dateKeyFormat.format(yesterdayDate);

        if (remittanceData != null) {
            for (int i = 0; i < remittanceData.length(); i++) {
                try {
                    JSONObject dayGroup = remittanceData.optJSONObject(i);
                    if (dayGroup == null) continue;

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", "").trim();
                    if (groupDate.isEmpty()) {
                        groupDate = dayName;
                    }

                    Date itemDate = parseItemDate(groupDate);
                    if (!isCurrentWeek(itemDate)) continue;

                    boolean isToday = dayName.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayFormatted) ||
                            groupDate.equalsIgnoreCase(todayName) ||
                            groupDate.contains(todayDateKey);

                    boolean isYesterday = dayName.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayFormatted) ||
                            groupDate.equalsIgnoreCase(yesterdayName) ||
                            groupDate.contains(yesterdayDateKey);

                    JSONArray partialsArray = dayGroup.optJSONArray("remittances");
                    boolean hasRemittances = partialsArray != null && partialsArray.length() > 0;

                    // Upcoming filter check
                    boolean isUpcoming = false;
                    if (!hasRemittances) {
                        Date parsedDate = null;
                        String[] datePatterns = new String[]{"yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"};
                        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
                        for (String pattern : datePatterns) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                                Date parsed = sdf.parse(groupDate.trim());
                                if (parsed != null) {
                                    Calendar pCal = Calendar.getInstance();
                                    pCal.setTime(parsed);
                                    if (!pattern.contains("yyyy")) {
                                        pCal.set(Calendar.YEAR, currentYear);
                                    }
                                    pCal.set(Calendar.HOUR_OF_DAY, 0);
                                    pCal.set(Calendar.MINUTE, 0);
                                    pCal.set(Calendar.SECOND, 0);
                                    pCal.set(Calendar.MILLISECOND, 0);
                                    parsedDate = pCal.getTime();
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }

                        if (parsedDate != null) {
                            Calendar todayCal = Calendar.getInstance();
                            todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0);
                            todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);
                            Date todayAtMidnight = todayCal.getTime();

                            if (parsedDate.after(todayAtMidnight)) {
                                isUpcoming = true;
                            }
                        } else {
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
                                if (diff > 0) {
                                    isUpcoming = true;
                                }
                            }
                        }
                    }

                    if (isUpcoming) continue;

                    double totalGross = 0, totalRemittance = 0, totalNet = 0;
                    List<HistoryAdapter.PartialReport> reports = new ArrayList<>();

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
                            reports.add(new HistoryAdapter.PartialReport("Report #" + (j + 1) + " · " + time, df.format(g), df.format(e), df.format(n)));
                        }
                    }

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
                    item.setNoRecorded(!hasRemittances && hasSchedule);

                    for (HistoryAdapter.PartialReport report : reports) {
                        item.addPartial(report.header, report.amount, report.remittance, report.net);
                    }

                    if (isToday) {
                        if (isAfter10PM) {
                            item.setToday(false);
                            item.setExpanded(false);
                            previousItems.add(item);
                        } else {
                            item.setToday(true);
                            item.setExpanded(true);
                            dailyItems.add(item);
                        }
                    } else {
                        previousItems.add(item);
                    }

                } catch (Exception e) {
                    Log.e("SalaryPagerAdapter", "Error parsing history group", e);
                }
            }
        }

        // Fill any missing days in the last 7 days continuous timeline
        for (int d = 0; d < 7; d++) {
            Calendar checkCal = Calendar.getInstance();
            checkCal.add(Calendar.DATE, -d);
            checkCal.set(Calendar.HOUR_OF_DAY, 0); checkCal.set(Calendar.MINUTE, 0);
            checkCal.set(Calendar.SECOND, 0); checkCal.set(Calendar.MILLISECOND, 0);
            Date checkDate = checkCal.getTime();

            if (!isCurrentWeek(checkDate)) continue;

            boolean alreadyAdded = false;
            int y1 = checkCal.get(Calendar.YEAR);
            int dayOfYear1 = checkCal.get(Calendar.DAY_OF_YEAR);

            for (HistoryAdapter.HistoryItem item : dailyItems) {
                Calendar c2 = Calendar.getInstance();
                c2.setTime(parseItemDate(item.date));
                if (c2.get(Calendar.YEAR) == y1 && c2.get(Calendar.DAY_OF_YEAR) == dayOfYear1) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                for (HistoryAdapter.HistoryItem item : previousItems) {
                    Calendar c2 = Calendar.getInstance();
                    c2.setTime(parseItemDate(item.date));
                    if (c2.get(Calendar.YEAR) == y1 && c2.get(Calendar.DAY_OF_YEAR) == dayOfYear1) {
                        alreadyAdded = true;
                        break;
                    }
                }
            }

            if (!alreadyAdded) {
                String dName = dayFormat.format(checkDate);
                String dFormatted = fullFormat.format(checkDate);
                String dDateKey = dateKeyFormat.format(checkDate);

                boolean hasSched = hasScheduleForDate(context, dName, dDateKey);
                double incentive = getIncentiveForDate(context, dName, dDateKey, "");
                HistoryAdapter.HistoryItem missingItem = new HistoryAdapter.HistoryItem(
                        dFormatted, "0.00", df.format(incentive * 2), df.format(-(incentive * 2)), !hasSched
                );
                missingItem.setNoRecorded(hasSched);

                if (d == 0) { // Today
                    if (isAfter10PM) {
                        missingItem.setToday(false);
                        missingItem.setExpanded(false);
                        previousItems.add(missingItem);
                    } else {
                        missingItem.setToday(true);
                        missingItem.setExpanded(true);
                        dailyItems.add(missingItem);
                    }
                } else {
                    missingItem.setToday(false);
                    missingItem.setExpanded(false);
                    previousItems.add(missingItem);
                }
            }
        }

        // Fill any other missing days specifically listed in the schedules cache
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);
        if (cachedSchedules != null && !cachedSchedules.trim().isEmpty()) {
            try {
                JSONArray schedules;
                if (cachedSchedules.trim().startsWith("[")) {
                    schedules = new JSONArray(cachedSchedules);
                } else {
                    JSONObject root = new JSONObject(cachedSchedules);
                    schedules = root.optJSONArray("schedules");
                }
                if (schedules != null) {
                    Calendar todayMidnight = Calendar.getInstance();
                    todayMidnight.set(Calendar.HOUR_OF_DAY, 0); todayMidnight.set(Calendar.MINUTE, 0);
                    todayMidnight.set(Calendar.SECOND, 0); todayMidnight.set(Calendar.MILLISECOND, 0);

                    for (int i = 0; i < schedules.length(); i++) {
                        JSONObject sched = schedules.getJSONObject(i);
                        String schedDateStr = sched.optString("date", "");
                        if (schedDateStr.isEmpty()) continue;

                        Date schedDate = parseItemDate(schedDateStr);
                        if (schedDate.getTime() == 0 || schedDate.after(todayMidnight.getTime())) {
                            continue;
                        }

                        if (!isCurrentWeek(schedDate)) continue;

                        Calendar c1 = Calendar.getInstance();
                        c1.setTime(schedDate);
                        int y1 = c1.get(Calendar.YEAR);
                        int dayOfYear1 = c1.get(Calendar.DAY_OF_YEAR);

                        boolean alreadyAdded = false;
                        for (HistoryAdapter.HistoryItem item : dailyItems) {
                            Calendar c2 = Calendar.getInstance();
                            c2.setTime(parseItemDate(item.date));
                            if (c2.get(Calendar.YEAR) == y1 && c2.get(Calendar.DAY_OF_YEAR) == dayOfYear1) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        if (!alreadyAdded) {
                            for (HistoryAdapter.HistoryItem item : previousItems) {
                                Calendar c2 = Calendar.getInstance();
                                c2.setTime(parseItemDate(item.date));
                                if (c2.get(Calendar.YEAR) == y1 && c2.get(Calendar.DAY_OF_YEAR) == dayOfYear1) {
                                    alreadyAdded = true;
                                    break;
                                }
                            }
                        }

                        if (!alreadyAdded) {
                            String dName = dayFormat.format(schedDate);
                            String dFormatted = fullFormat.format(schedDate);
                            double incentive = getIncentiveForDate(context, dName, schedDateStr, "");
                            HistoryAdapter.HistoryItem missingItem = new HistoryAdapter.HistoryItem(
                                    dFormatted, "0.00", df.format(incentive * 2), df.format(-(incentive * 2)), false
                            );
                            missingItem.setNoRecorded(true);
                            missingItem.setToday(false);
                            missingItem.setExpanded(false);
                            previousItems.add(missingItem);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("SalaryPagerAdapter", "Error processing missing schedules", e);
            }
        }

        Collections.sort(dailyItems, (a, b) -> parseItemDate(b.date).compareTo(parseItemDate(a.date)));
        Collections.sort(previousItems, (a, b) -> parseItemDate(b.date).compareTo(parseItemDate(a.date)));

        // Animation Container
        ViewGroup animContainer = null;
        if (holder.itemView instanceof ViewGroup) {
            View child = ((ViewGroup) holder.itemView).getChildAt(0);
            if (child instanceof ViewGroup) animContainer = (ViewGroup) child;
        }

        // Daily Records Adapter
        HistoryAdapter dailyAdapter = new HistoryAdapter(dailyItems);
        dailyAdapter.setVisible(isSalaryVisible);
        dailyAdapter.setTransitionContainer(animContainer);
        holder.rvHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvHistory.setAdapter(dailyAdapter);

        // Previous Records Logic
        if (!previousItems.isEmpty()) {
            holder.tvPreviousHeader.setVisibility(View.VISIBLE);
            holder.rvPreviousHistory.setVisibility(View.VISIBLE);
            
            HistoryAdapter previousAdapter = new HistoryAdapter(previousItems);
            previousAdapter.setVisible(isSalaryVisible);
            previousAdapter.setTransitionContainer(animContainer);
            holder.rvPreviousHistory.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
            holder.rvPreviousHistory.setAdapter(previousAdapter);
        } else {
            holder.tvPreviousHeader.setVisibility(View.GONE);
            holder.rvPreviousHistory.setVisibility(View.GONE);
        }
    }

    private boolean hasScheduleForDate(Context context, String dayName, String dateStr) {
        String cachedSchedules = PreferenceManager.getSchedulesCache(context);
        if (cachedSchedules == null || cachedSchedules.trim().isEmpty()) return false;
        try {
            JSONArray schedules;
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
        getMergedRemittanceData(context);

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

                    String dayName = dayGroup.optString("day", "N/A");
                    String groupDate = dayGroup.optString("date", "").trim();
                    if (groupDate.isEmpty()) {
                        groupDate = dayName;
                    }
                    Date itemDate = parseItemDate(groupDate);
                    if (!isCurrentWeek(itemDate)) continue;

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
                    String groupDate = dayGroup.optString("date", "").trim();
                    if (groupDate.isEmpty()) {
                        groupDate = dayName;
                    }

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
                    long maxTimeMillis = -1;

                    if (partialsArray != null && partialsArray.length() > 0) {
                        for (int j = 0; j < partialsArray.length(); j++) {
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
        safeSetText(holder.tvDriverLastName, "");
        safeSetText(holder.tvWorkingDays, "Unassigned");
        safeSetText(holder.tvPaoLastName, "");
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
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US);
            Date d = sdf.parse(dateStr);
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            try {
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
            JSONArray schedules;
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
        safeSetText(holder.tvDriverLastName, "");
        safeSetText(holder.tvWorkingDays, "Unassigned");
        safeSetText(holder.tvPaoLastName, "");
        safeSetText(holder.tvDriverShareName, "Unassigned Driver");
        safeSetText(holder.tvPaoShareName, "Unassigned PAO");
        safeSetText(holder.tvJeepUnit, "Unassigned");
        safeSetText(holder.tvFuelDay, "No Plate");
        safeSetText(holder.tvScheduleDate, "Today");

        if (cachedSchedules == null || cachedSchedules.trim().isEmpty()) return;

        try {
            JSONArray schedules;
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

                    // Split names for Stats Row
                    setSplitName(holder.tvBoundaryDay, holder.tvDriverLastName, driver);
                    setSplitName(holder.tvWorkingDays, holder.tvPaoLastName, pao);
                    
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

    private void setSplitName(TextView tvFirst, TextView tvLast, String fullName) {
        if (fullName == null || fullName.isEmpty() || fullName.equalsIgnoreCase("Unassigned") || fullName.equalsIgnoreCase("Driver") || fullName.equalsIgnoreCase("PAO")) {
            safeSetText(tvFirst, fullName);
            safeSetText(tvLast, "");
            return;
        }

        String first = fullName;
        String last = "";
        int lastSpace = fullName.lastIndexOf(' ');
        if (lastSpace >= 0) {
            first = fullName.substring(0, lastSpace).trim();
            String rawLast = fullName.substring(lastSpace + 1).trim();
            if (!rawLast.isEmpty()) {
                last = rawLast.substring(0, 1).toUpperCase() + rawLast.substring(1).toLowerCase();
            }
        }
        
        safeSetText(tvFirst, first);
        safeSetText(tvLast, last);
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
        TextView tvBoundaryDay, tvDriverLastName, tvJeepUnit, tvFuelDay, tvWorkingDays, tvPaoLastName, tvNetCalc, tvTrend;
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
            tvDriverLastName = itemView.findViewById(R.id.tv_val_driver_lastname);
            tvJeepUnit = itemView.findViewById(R.id.tv_val_jeep_unit);
            tvFuelDay = itemView.findViewById(R.id.tv_val_fuel_day);
            tvWorkingDays = itemView.findViewById(R.id.tv_val_working_days);
            tvPaoLastName = itemView.findViewById(R.id.tv_val_pao_lastname);
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
        RecyclerView rvHistory, rvPreviousHistory;
        TextView tvPreviousHeader;
        HistoryViewHolder(View itemView) {
            super(itemView);
            rvHistory = itemView.findViewById(R.id.rv_salary_history);
            rvPreviousHistory = itemView.findViewById(R.id.rv_previous_history);
            tvPreviousHeader = itemView.findViewById(R.id.tv_previous_header);
        }
    }
}
