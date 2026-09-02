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
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            holder.llStatusBadge.setVisibility(View.VISIBLE);
            holder.badgeRest.setVisibility(View.GONE);
            holder.ivChevron.setVisibility(View.VISIBLE);
            holder.itemView.setClickable(true);
            
            // Detail values with space after currency symbol: ₱ 1,680
            holder.tvValGross.setText("₱ " + item.gross);
            holder.tvValBoundaryFuel.setText("₱ " + item.boundaryFuel);
            holder.tvValNet.setText("₱ " + item.net);

            // Expansion logic
            updateExpansionState(holder, item, false);

            holder.itemView.setOnClickListener(v -> {
                item.isExpanded = !item.isExpanded;
                if (holder.itemView.getParent() instanceof ViewGroup) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(150); // Faster return of animation
                    TransitionManager.beginDelayedTransition((ViewGroup) holder.itemView.getParent(), transition);
                }
                updateExpansionState(holder, item, true);
            });
        }
    }

    private void updateExpansionState(ViewHolder holder, HistoryItem item, boolean animate) {
        int visibility = item.isExpanded ? View.VISIBLE : View.GONE;
        holder.llDetailSection.setVisibility(visibility);
        
        // Point down (90f) when expanded, point to the side (0f) when collapsed.
        float rotation = item.isExpanded ? 90f : 0f;
        if (animate) {
            // Snappy rotation: 150ms
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
        TextView tvDate, tvValGross, tvValBoundaryFuel, tvValNet;
        LinearLayout llStatusBadge, llDetailSection;
        TextView badgeRest;
        ImageView ivChevron;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            llStatusBadge = itemView.findViewById(R.id.ll_status_badge);
            badgeRest = itemView.findViewById(R.id.tv_rest_badge);
            llDetailSection = itemView.findViewById(R.id.ll_detail_section);
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

        public HistoryItem(String date, String gross, String boundaryFuel, String net, boolean isRest) {
            this.date = date;
            this.gross = gross;
            this.boundaryFuel = boundaryFuel;
            this.net = net;
            this.isRest = isRest;
        }
    }
}
