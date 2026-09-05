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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<HistoryItem> items;

    public HistoryAdapter(List<HistoryItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_salary_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = items.get(position);
        holder.tvDate.setText(item.date);
        
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

            String tripText = item.partialReports.size() + (item.partialReports.size() == 1 ? " trip recorded" : " trips recorded");
            holder.tvTripCount.setText(tripText);
            
            DecimalFormat df = new DecimalFormat("#,###.##");
            // Detail values
            holder.tvValGross.setText("₱ " + df.format(item.getGrossValue()));
            holder.tvValBoundaryFuel.setText("₱ " + df.format(item.getExpensesValue()));
            holder.tvValNet.setText("₱ " + df.format(item.getNetValue()));

            // Populate partial reports
            holder.llPartialContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(holder.itemView.getContext());
            for (PartialReport report : item.partialReports) {
                View reportView = inflater.inflate(R.layout.item_partial_report, holder.llPartialContainer, false);
                ((TextView) reportView.findViewById(R.id.tv_partial_header)).setText(report.header);
                ((TextView) reportView.findViewById(R.id.tv_val_partial_amount)).setText("₱ " + report.amount);
                ((TextView) reportView.findViewById(R.id.tv_val_partial_expenses)).setText("₱ " + report.expenses);
                ((TextView) reportView.findViewById(R.id.tv_val_partial_net)).setText("₱ " + report.net);
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
        TextView tvDate, tvTripCount, tvValGross, tvValBoundaryFuel, tvValNet;
        LinearLayout llStatusBadge, llDetailSection, llPartialContainer;
        TextView badgeRest;
        ImageView ivChevron;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            tvTripCount = itemView.findViewById(R.id.tv_trip_count);
            llStatusBadge = itemView.findViewById(R.id.ll_status_badge);
            badgeRest = itemView.findViewById(R.id.tv_rest_badge);
            llDetailSection = itemView.findViewById(R.id.ll_detail_section);
            llPartialContainer = itemView.findViewById(R.id.ll_partial_reports_container);
            tvValGross = itemView.findViewById(R.id.tv_history_val_gross);
            tvValBoundaryFuel = itemView.findViewById(R.id.tv_history_val_boundary_fuel);
            tvValNet = itemView.findViewById(R.id.tv_history_val_net);
            ivChevron = itemView.findViewById(R.id.iv_chevron);
        }
    }

    public static class HistoryItem {
        String date;
        String gross;
        String boundaryFuel;
        String net;
        boolean isRest;
        boolean isExpanded = false;
        List<PartialReport> partialReports = new ArrayList<>();

        public HistoryItem(String date, String gross, String boundaryFuel, String net, boolean isRest) {
            this.date = date;
            this.gross = gross;
            this.boundaryFuel = boundaryFuel;
            this.net = net;
            this.isRest = isRest;
        }

        public HistoryItem addPartial(String header, String amount, String expenses, String net) {
            this.partialReports.add(new PartialReport(header, amount, expenses, net));
            return this;
        }

        public double getGrossValue() {
            if (isRest) return 0;
            if (partialReports.isEmpty()) return parse(gross);
            double total = 0;
            for (PartialReport r : partialReports) total += parse(r.amount);
            return total;
        }

        public double getExpensesValue() {
            if (isRest) return 0;
            if (partialReports.isEmpty()) return parse(boundaryFuel);
            double total = 0;
            for (PartialReport r : partialReports) total += parse(r.expenses);
            return total;
        }

        public double getNetValue() {
            if (isRest) return 0;
            if (partialReports.isEmpty()) return parse(net);
            double total = 0;
            for (PartialReport r : partialReports) total += parse(r.net);
            return total;
        }

        private double parse(String val) {
            if (val == null || val.isEmpty()) return 0;
            try {
                return Double.parseDouble(val.replace(",", "").replace("₱", "").trim());
            } catch (Exception e) {
                return 0;
            }
        }

        public HistoryItem setExpanded(boolean expanded) {
            this.isExpanded = expanded;
            return this;
        }
    }

    public static class PartialReport {
        String header;
        String amount;
        String expenses;
        String net;

        public PartialReport(String header, String amount, String expenses, String net) {
            this.header = header;
            this.amount = amount;
            this.expenses = expenses;
            this.net = net;
        }
    }
}
