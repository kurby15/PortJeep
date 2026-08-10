package com.example.portjeep;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<ScheduleItem> list;

    public ScheduleAdapter(List<ScheduleItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScheduleItem item = list.get(position);
        holder.tvDay.setText(item.getDay());
        holder.tvDate.setText(item.getDate());
        holder.tvStatus.setText("●  " + item.getStatus());
        holder.tvJeepUnit.setText(item.getJeepUnit());
        holder.tvDriverName.setText(item.getDriverName());
        holder.tvPaoName.setText(item.getPaoName());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void updateList(List<ScheduleItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvDate, tvStatus, tvJeepUnit, tvDriverName, tvPaoName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_schedule_day);
            tvDate = itemView.findViewById(R.id.tv_schedule_date);
            tvStatus = itemView.findViewById(R.id.tv_schedule_status);
            tvJeepUnit = itemView.findViewById(R.id.tv_jeep_unit);
            tvDriverName = itemView.findViewById(R.id.tv_driver_name);
            tvPaoName = itemView.findViewById(R.id.tv_pao_name);
        }
    }
}