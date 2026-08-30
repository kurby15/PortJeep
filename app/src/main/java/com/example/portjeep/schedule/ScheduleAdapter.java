package com.example.portjeep.schedule;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.portjeep.R;
import com.example.portjeep.data.model.ScheduleItem;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<ScheduleItem> list;

    public ScheduleAdapter(List<ScheduleItem> list) {
        this.list = (list != null) ? list : new ArrayList<>();
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
        if (item == null) return;

        if (holder.tvDay != null) holder.tvDay.setText(item.getDay());
        if (holder.tvDate != null) holder.tvDate.setText(item.getDate());
        if (holder.tvStatus != null) holder.tvStatus.setText("●  " + item.getStatus());
        if (holder.tvJeepUnit != null) holder.tvJeepUnit.setText(item.getJeepUnit());
        if (holder.tvDriverName != null) holder.tvDriverName.setText(item.getDriverName());
        if (holder.tvPaoName != null) holder.tvPaoName.setText(item.getPaoName());

        // Driver Card Click Listener
        if (holder.cardDriver != null) {
            boolean clickable = isClickableMember(item.getDriverName());
            holder.cardDriver.setClickable(clickable);
            holder.cardDriver.setFocusable(clickable);

            if (clickable) {
                holder.cardDriver.setOnClickListener(v -> showBottomSheet(
                        v.getContext(),
                        "DRIVER DETAILS",
                        item.getDriverName(),
                        item.getDriverEmail(),
                        item.getDriverContact()
                ));
            } else {
                holder.cardDriver.setOnClickListener(null);
            }
        }

        // PAO Card Click Listener
        if (holder.cardPao != null) {
            boolean clickable = isClickableMember(item.getPaoName());
            holder.cardPao.setClickable(clickable);
            holder.cardPao.setFocusable(clickable);

            if (clickable) {
                holder.cardPao.setOnClickListener(v -> showBottomSheet(
                        v.getContext(),
                        "PAO DETAILS",
                        item.getPaoName(),
                        item.getPaoEmail(),
                        item.getPaoContact()
                ));
            } else {
                holder.cardPao.setOnClickListener(null);
            }
        }
    }

    private boolean isClickableMember(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase();
        return !clean.contains("unassigned") && !clean.contains("rest day") && !clean.equals("n/a");
    }

    private void showBottomSheet(Context context, String role, String name, String email, String contact) {
        if (context == null) return;

        try {
            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_user_info, null, false);

            TextView tvRole = sheetView.findViewById(R.id.tv_dialog_role);
            TextView tvName = sheetView.findViewById(R.id.tv_dialog_name);
            TextView tvEmail = sheetView.findViewById(R.id.tv_dialog_email);
            TextView tvContact = sheetView.findViewById(R.id.tv_dialog_contact);

            if (tvRole != null) tvRole.setText(role);
            if (tvName != null) tvName.setText(name);

            if (tvEmail != null) {
                tvEmail.setText((email != null && !email.trim().isEmpty() && !email.equalsIgnoreCase("null")) ? email : "N/A");
            }

            if (tvContact != null) {
                tvContact.setText((contact != null && !contact.trim().isEmpty() && !contact.equalsIgnoreCase("null")) ? contact : "N/A");
            }

            bottomSheetDialog.setContentView(sheetView);
            bottomSheetDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return (list != null) ? list.size() : 0;
    }

    public void updateList(List<ScheduleItem> newList) {
        this.list = (newList != null) ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvDate, tvStatus, tvJeepUnit, tvDriverName, tvPaoName;
        View cardDriver, cardPao;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_schedule_day);
            tvDate = itemView.findViewById(R.id.tv_schedule_date);
            tvStatus = itemView.findViewById(R.id.tv_schedule_status);
            tvJeepUnit = itemView.findViewById(R.id.tv_jeep_unit);
            tvDriverName = itemView.findViewById(R.id.tv_driver_name);
            tvPaoName = itemView.findViewById(R.id.tv_pao_name);

            cardDriver = itemView.findViewById(R.id.card_driver);
            cardPao = itemView.findViewById(R.id.card_pao);
        }
    }
}