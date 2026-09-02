package com.example.portjeep.salary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
            holder.tvGross.setVisibility(View.GONE);
            holder.tvNet.setText("-");
            holder.badgeRecorded.setVisibility(View.GONE);
            holder.badgeRest.setVisibility(View.VISIBLE);
        } else {
            holder.tvGross.setVisibility(View.VISIBLE);
            holder.badgeRecorded.setVisibility(View.VISIBLE);
            holder.badgeRest.setVisibility(View.GONE);
            
            holder.tvGross.setText("Gross: " + item.gross);
            holder.tvNet.setText(item.net);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvGross, tvNet;
        LinearLayout badgeRecorded;
        TextView badgeRest;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            tvGross = itemView.findViewById(R.id.tv_history_gross);
            tvNet = itemView.findViewById(R.id.tv_history_net);
            badgeRecorded = itemView.findViewById(R.id.ll_status_badge);
            badgeRest = itemView.findViewById(R.id.tv_rest_badge);
        }
    }

    public static class HistoryItem {
        String date;
        String gross;
        String net;
        boolean isRest;

        public HistoryItem(String date, String gross, String net, boolean isRest) {
            this.date = date;
            this.gross = gross;
            this.net = net;
            this.isRest = isRest;
        }
    }
}