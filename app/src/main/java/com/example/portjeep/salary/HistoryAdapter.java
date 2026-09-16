package com.example.portjeep.salary;

import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.portjeep.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> items;
    private boolean isVisible = true;
    private final String hiddenText = "₱ ••••";

    public HistoryAdapter(List<HistoryItem> items) {
        this.items = items;
    }

    public void setVisible(boolean visible) {
        this.isVisible = visible;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_salary_history, parent, false);
        return new ViewHolder(view);
    }

    private String formatDisplayDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("N/A")) {
            return dateStr;
        }
        
        String cleaned = dateStr.trim();
        String[] datePatterns = new String[]{
                "EEEE, MMM d", "EEEE, MMMM d", "yyyy-MM-dd", "MM/dd/yyyy", "dd/MM/yyyy",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "MMM dd, yyyy", "MMMM dd, yyyy", "MMM dd", "MMMM dd"
        };
        
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        Date parsedDate = null;
        
        for (String pattern : datePatterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                Date parsed = sdf.parse(cleaned);
                if (parsed != null) {
                    if (!pattern.contains("yyyy")) {
                        Calendar pCal = Calendar.getInstance();
                        pCal.setTime(parsed);
                        pCal.set(Calendar.YEAR, currentYear);
                        parsedDate = pCal.getTime();
                    } else {
                        parsedDate = parsed;
                    }
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (parsedDate == null) {
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
                if (diff > 0) diff -= 7;
                c.add(Calendar.DATE, diff);
                parsedDate = c.getTime();
            }
        }

        if (parsedDate != null) {
            SimpleDateFormat targetFormat = new SimpleDateFormat("EEE, MMM d", Locale.US);
            return targetFormat.format(parsedDate).toUpperCase(Locale.US);
        }

        return dateStr;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        
        String displayDate = formatDisplayDate(item.date);
        holder.tvDate.setText(displayDate);
        
        if (item.isRest) {
            holder.llStatusBadge.setVisibility(View.GONE);
            holder.badgeRest.setVisibility(View.VISIBLE);
            holder.llDetailSection.setVisibility(View.GONE);
            holder.ivChevron.setVisibility(View.GONE);
            holder.tvTripCount.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            holder.llStatusBadge.setVisibility(View.VISIBLE);
            holder.badgeRest.setVisibility(View.GONE);
            holder.ivChevron.setVisibility(View.VISIBLE);
            holder.tvTripCount.setVisibility(View.VISIBLE);
            holder.itemView.setClickable(true);

            if (item.isToday) {
                holder.tvStatusLabel.setText(R.string.status_record);
            } else {
                holder.tvStatusLabel.setText(R.string.status_recorded);
            }

            String tripText = item.partialReports.size() + (item.partialReports.size() == 1 ? " trip recorded" : " trips recorded");
            holder.tvTripCount.setText(tripText);
            
            DecimalFormat df = new DecimalFormat("#,##0.00");
            // Detail values
            if (isVisible) {
                holder.tvValGross.setText("₱ " + df.format(item.getGrossValue()));
                holder.tvValRemittance.setText("₱ " + df.format(item.getRemittanceValue()));
                holder.tvValNet.setText("₱ " + df.format(item.getNetValue()));
            } else {
                holder.tvValGross.setText(hiddenText);
                holder.tvValRemittance.setText(hiddenText);
                holder.tvValNet.setText(hiddenText);
            }

            // Populate partial reports
            holder.llPartialContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (PartialReport report : item.partialReports) {
                View reportView = inflater.inflate(R.layout.item_partial_report, holder.llPartialContainer, false);
                ((TextView) reportView.findViewById(R.id.tv_partial_header)).setText(report.header);
                
                TextView tvAmount = reportView.findViewById(R.id.tv_val_partial_amount);
                TextView tvRemittance = reportView.findViewById(R.id.tv_val_partial_remittance);
                TextView tvNet = reportView.findViewById(R.id.tv_val_partial_net);
                
                if (isVisible) {
                    tvAmount.setText("₱ " + report.amount);
                    tvRemittance.setText("₱ " + report.remittance);
                    tvNet.setText("₱ " + report.net);
                } else {
                    tvAmount.setText(hiddenText);
                    tvRemittance.setText(hiddenText);
                    tvNet.setText(hiddenText);
                }

                holder.llPartialContainer.addView(reportView);
            }

            // Expansion logic
            updateExpansionState(holder, item, false);

            holder.itemView.setOnClickListener(v -> {
                item.isExpanded = !item.isExpanded;
                if (holder.itemView.getParent() instanceof ViewGroup) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(150);
                    TransitionManager.beginDelayedTransition((ViewGroup) holder.itemView.getParent(), transition);
                }
                updateExpansionState(holder, item, true);
            });
        }
    }

    private void updateExpansionState(ViewHolder holder, HistoryItem item, boolean animate) {
        int visibility = item.isExpanded ? View.VISIBLE : View.GONE;
        holder.llDetailSection.setVisibility(visibility);
        
        float rotation = item.isExpanded ? 90f : 0f;
        if (animate) {
            holder.ivChevron.animate().rotation(rotation).setDuration(150).start();
        } else {
            holder.ivChevron.setRotation(rotation);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTripCount, tvValGross, tvValRemittance, tvValNet, tvStatusLabel;
        LinearLayout llStatusBadge, llDetailSection, llPartialContainer;
        TextView badgeRest;
        ImageView ivChevron;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            tvTripCount = itemView.findViewById(R.id.tv_trip_count);
            llStatusBadge = itemView.findViewById(R.id.ll_status_badge);
            tvStatusLabel = itemView.findViewById(R.id.tv_status_label);
            badgeRest = itemView.findViewById(R.id.tv_rest_badge);
            llDetailSection = itemView.findViewById(R.id.ll_detail_section);
            llPartialContainer = itemView.findViewById(R.id.ll_partial_reports_container);
            tvValGross = itemView.findViewById(R.id.tv_history_val_gross);
            tvValRemittance = itemView.findViewById(R.id.tv_history_val_remittance);
            tvValNet = itemView.findViewById(R.id.tv_history_val_net);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
        }
    }

    public static class HistoryItem {
        String date;
        String gross;
        String remittance;
        String net;
        boolean isRest;
        boolean isToday = false;
        boolean isExpanded = false;
        List<PartialReport> partialReports = new ArrayList<>();

        public HistoryItem(String date, String gross, String remittance, String net, boolean isRest) {
            this.date = date;
            this.gross = gross;
            this.remittance = remittance;
            this.net = net;
            this.isRest = isRest;
        }

        public HistoryItem addPartial(String header, String amount, String remittance, String net) {
            this.partialReports.add(new PartialReport(header, amount, remittance, net));
            return this;
        }

        public double getGrossValue() {
            if (isRest) return 0;
            return parse(gross);
        }

        public double getRemittanceValue() {
            if (isRest) return 0;
            return parse(remittance);
        }

        public double getNetValue() {
            if (isRest) return 0;
            return parse(net);
        }

        private double parse(String val) {
            if (val == null || val.isEmpty()) return 0;
            try {
                return Double.parseDouble(val.replace(",", "").replace("₱", "").trim());
            } catch (Exception e) {
                return 0;
            }
        }

        public HistoryItem setToday(boolean today) {
            this.isToday = today;
            return this;
        }

        public HistoryItem setExpanded(boolean expanded) {
            this.isExpanded = expanded;
            return this;
        }
    }

    public static class PartialReport {
        String header;
        String amount;
        String remittance;
        String net;

        public PartialReport(String header, String amount, String remittance, String net) {
            this.header = header;
            this.amount = amount;
            this.remittance = remittance;
            this.net = net;
        }
    }
}
