package com.example.portjeep.schedule;

import android.content.Context;
import android.graphics.Color;
import android.text.Html;
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
        if (holder.tvDriverName != null) holder.tvDriverName.setText(item.getDriverName());
        if (holder.tvPaoName != null) holder.tvPaoName.setText(item.getPaoName());
        if (holder.tvRoute != null) holder.tvRoute.setText(item.getRoute());

        // Handle Jeep Unit and Plate split
        String jeepUnit = item.getJeepUnit();
        if (jeepUnit != null && jeepUnit.contains(" · ")) {
            String[] parts = jeepUnit.split(" · ");
            if (holder.tvJeepUnit != null) holder.tvJeepUnit.setText(parts[0]);
            if (holder.tvPlateNo != null) holder.tvPlateNo.setText(parts[1]);
        } else {
            if (holder.tvJeepUnit != null) holder.tvJeepUnit.setText(jeepUnit);
            if (holder.tvPlateNo != null) holder.tvPlateNo.setText("N/A");
        }

        // Dynamic Banner Pill Background, Text Color & Dot Color
        if (holder.tvStatus != null) {
            String status = item.getStatus();
            if (status == null || status.trim().isEmpty()) {
                status = "Assigned";
            }

            int bgColor;
            int textColor;

            if ("Assigned".equalsIgnoreCase(status)) {
                bgColor = Color.parseColor("#1B3B6F");   // Deep Blue Fill
                textColor = Color.parseColor("#70A1FF"); // Bright Blue Text & Dot
            } else if ("Scheduled".equalsIgnoreCase(status)) {
                bgColor = Color.parseColor("#0F3854");   // Deep Cyan Fill
                textColor = Color.parseColor("#00D2D3"); // Bright Cyan Text & Dot
            } else if ("Completed".equalsIgnoreCase(status)) {
                bgColor = Color.parseColor("#2C3A47");   // Dark Slate Fill
                textColor = Color.parseColor("#CAD3C8"); // Soft Grey Text & Dot
            } else if ("Unassigned".equalsIgnoreCase(status) || "Rest Day".equalsIgnoreCase(status)) {
                bgColor = Color.parseColor("#4A2810");   // Dark Amber/Orange Fill
                textColor = Color.parseColor("#FF9F43"); // Bright Orange Text & Dot
            } else {
                bgColor = Color.parseColor("#2C3A47");
                textColor = Color.parseColor("#CAD3C8");
            }

            // Apply Solid Fill to the Pill Background
            holder.tvStatus.setBackgroundResource(R.drawable.bg_status_pill);
            holder.tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));

            // Apply Matching Text Color
            holder.tvStatus.setTextColor(textColor);

            // Render Dot & Status Text with HTML
            String hexTextColor = String.format("#%06X", (0xFFFFFF & textColor));
            String formattedHtml = "<font color='" + hexTextColor + "'>●</font>&nbsp;&nbsp;" + status;
            holder.tvStatus.setText(Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY));
        }

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
        TextView tvDay, tvDate, tvStatus, tvJeepUnit, tvPlateNo, tvDriverName, tvPaoName, tvRoute;
        View cardDriver, cardPao;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tv_schedule_day);
            tvDate = itemView.findViewById(R.id.tv_schedule_date);
            tvStatus = itemView.findViewById(R.id.tv_schedule_status);
            tvJeepUnit = itemView.findViewById(R.id.tv_jeep_unit);
            tvPlateNo = itemView.findViewById(R.id.tv_plate_no);
            tvDriverName = itemView.findViewById(R.id.tv_driver_name);
            tvPaoName = itemView.findViewById(R.id.tv_pao_name);
            tvRoute = itemView.findViewById(R.id.tv_schedule_route);

            cardDriver = itemView.findViewById(R.id.card_driver);
            cardPao = itemView.findViewById(R.id.card_pao);
        }
    }
}