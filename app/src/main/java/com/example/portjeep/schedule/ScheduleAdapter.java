package com.example.portjeep.schedule;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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

        // Dynamic Banner Pill Background, Text Color & Dot Color matched to new theme
        if (holder.tvStatus != null) {
            String status = item.getStatus();
            if (status == null || status.trim().isEmpty()) {
                status = "Assigned";
            }

            int textColor;
            int bgRes;
            Context context = holder.itemView.getContext();

            if ("Assigned".equalsIgnoreCase(status)) {
                bgRes = R.drawable.bg_status_assigned;
                textColor = ContextCompat.getColor(context, R.color.color_status_assigned_text);
            } else if ("Scheduled".equalsIgnoreCase(status) || "Upcoming".equalsIgnoreCase(status)) {
                bgRes = R.drawable.bg_status_scheduled;
                textColor = ContextCompat.getColor(context, R.color.color_status_scheduled_text);
            } else if ("Completed".equalsIgnoreCase(status)) {
                bgRes = R.drawable.bg_status_completed;
                textColor = ContextCompat.getColor(context, R.color.color_status_completed_text);
            } else if ("Unassigned".equalsIgnoreCase(status) || "Rest Day".equalsIgnoreCase(status)) {
                bgRes = R.drawable.bg_status_rest;
                textColor = ContextCompat.getColor(context, R.color.color_status_rest_text);
            } else {
                bgRes = R.drawable.bg_status_completed;
                textColor = ContextCompat.getColor(context, R.color.color_status_completed_text);
            }

            holder.tvStatus.setBackgroundResource(bgRes);
            holder.tvStatus.setBackgroundTintList(null);
            holder.tvStatus.setTextColor(textColor);

            String hexTextColor = String.format("#%06X", (0xFFFFFF & textColor));
            String formattedHtml = "<font color='" + hexTextColor + "'>●</font>&nbsp;&nbsp;" + status;
            holder.tvStatus.setText(Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY));
        }

        // Driver Card Click Listener & Animation
        if (holder.cardDriver != null) {
            boolean clickable = isClickableMember(item.getDriverName());
            holder.cardDriver.setClickable(clickable);
            holder.cardDriver.setFocusable(clickable);
            
            if (holder.ivDriverChevron != null) {
                holder.ivDriverChevron.setVisibility(clickable ? View.VISIBLE : View.GONE);
                holder.ivDriverChevron.setRotation(0f); // Reset to default rotation
            }

            if (clickable) {
                holder.cardDriver.setOnClickListener(v -> showBottomSheet(
                        v.getContext(),
                        "DRIVER DETAILS",
                        item.getDriverName(),
                        item.getDriverEmail(),
                        item.getDriverContact(),
                        holder.ivDriverChevron
                ));
            } else {
                holder.cardDriver.setOnClickListener(null);
            }
        }

        // PAO Card Click Listener & Animation
        if (holder.cardPao != null) {
            boolean clickable = isClickableMember(item.getPaoName());
            holder.cardPao.setClickable(clickable);
            holder.cardPao.setFocusable(clickable);
            
            if (holder.ivPaoChevron != null) {
                holder.ivPaoChevron.setVisibility(clickable ? View.VISIBLE : View.GONE);
                holder.ivPaoChevron.setRotation(0f); // Reset to default rotation
            }

            if (clickable) {
                holder.cardPao.setOnClickListener(v -> showBottomSheet(
                        v.getContext(),
                        "PAO DETAILS",
                        item.getPaoName(),
                        item.getPaoEmail(),
                        item.getPaoContact(),
                        holder.ivPaoChevron
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

    private void showBottomSheet(Context context, String role, String name, String email, String contact, ImageView chevron) {
        if (context == null) return;

        // Smoothly animate the chevron to face downward (90 degrees rotation)
        if (chevron != null) {
            chevron.animate().rotation(90f).setDuration(250).start();
        }

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

            // Smoothly animate the chevron back to default (0 degrees) when clicking any outside space or dismissing
            bottomSheetDialog.setOnDismissListener(dialog -> {
                if (chevron != null) {
                    chevron.animate().rotation(0f).setDuration(250).start();
                }
            });

            bottomSheetDialog.setContentView(sheetView);
            bottomSheetDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            if (chevron != null) {
                chevron.setRotation(0f);
            }
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
        TextView tvDay, tvDate, tvStatus, tvJeepUnit, tvPlateNo, tvDriverName, tvPaoName;
        ImageView ivDriverChevron, ivPaoChevron;
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
            ivDriverChevron = itemView.findViewById(R.id.iv_driver_chevron);
            ivPaoChevron = itemView.findViewById(R.id.iv_pao_chevron);
            cardDriver = itemView.findViewById(R.id.card_driver);
            cardPao = itemView.findViewById(R.id.card_pao);
        }
    }
}