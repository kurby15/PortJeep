package com.example.portjeep.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.portjeep.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatusBannerAdapter extends RecyclerView.Adapter<StatusBannerAdapter.BannerViewHolder> {

    public static class BannerItem {
        public static final int TYPE_REST_DAY = 0;
        public static final int TYPE_UNASSIGNED = 1;

        public int type;
        public String title;
        public String desc;
        public List<String> restDays;
        public List<JSONObject> unassignedSchedules;

        public BannerItem(int type, String title, String desc, List<String> restDays, List<JSONObject> unassignedSchedules) {
            this.type = type;
            this.title = title;
            this.desc = desc;
            this.restDays = restDays;
            this.unassignedSchedules = unassignedSchedules;
        }
    }

    private final Context context;
    private final List<BannerItem> items;

    public StatusBannerAdapter(Context context, List<BannerItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner_card, parent, false);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        BannerItem item = items.get(position);

        holder.tvTitle.setText(item.title);
        holder.tvDesc.setText(item.desc);

        if (item.type == BannerItem.TYPE_REST_DAY) {
            holder.ivIcon.setImageResource(R.drawable.calendar);
            holder.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_brand_accent)));
            holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_background_secondary)));
            renderRestDays(holder.containerRows, item.restDays);
        } else {
            holder.ivIcon.setImageResource(R.drawable.work);
            holder.ivIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_brand_primary)));
            holder.ivIcon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_background_secondary)));
            renderUnassigned(holder.containerRows, item.unassignedSchedules);
        }
    }

    private void renderRestDays(LinearLayout container, List<String> restDays) {
        container.removeAllViews();
        if (restDays == null || restDays.isEmpty()) {
            addEmptyStateView(container, "No upcoming rest days found.");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (String day : restDays) {
            View rowView = inflater.inflate(R.layout.item_rest_day_row, container, false);
            TextView tvDayName = rowView.findViewById(R.id.tv_rest_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_rest_day_status);

            if (tvDayName != null) tvDayName.setText(day);
            if (tvStatus != null) {
                tvStatus.setText("●  REST DAY");
                tvStatus.setBackgroundResource(R.drawable.bg_status_rest);
            }

            container.addView(rowView);
        }
    }

    private void renderUnassigned(LinearLayout container, List<JSONObject> unassignedList) {
        container.removeAllViews();
        if (unassignedList == null || unassignedList.isEmpty()) {
            addEmptyStateView(container, "No unassigned schedules found.");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (JSONObject doc : unassignedList) {
            View rowView = inflater.inflate(R.layout.item_unassigned_row, container, false);
            TextView tvDayName = rowView.findViewById(R.id.tv_unassigned_day_name);
            TextView tvStatus = rowView.findViewById(R.id.tv_unassigned_status);

            String dayText = doc.optString("day", doc.optString("date", "Unassigned"));

            if (tvDayName != null) tvDayName.setText(dayText);
            if (tvStatus != null) {
                // Clear any preset text color conflicts
                tvStatus.setTextColor(ContextCompat.getColor(context, android.R.color.transparent));

                String text = "●  UNASSIGNED";
                SpannableStringBuilder ssb = new SpannableStringBuilder(text);

                // Color for the leading dot (index 0 to 1)
                int dotColor = ContextCompat.getColor(context, R.color.color_brand_accent);
                ssb.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Color for the text label portion (index 2 to end)
                int textColor = ContextCompat.getColor(context, R.color.color_error_text);
                ssb.setSpan(new ForegroundColorSpan(textColor), 2, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                tvStatus.setText(ssb);
                tvStatus.setBackgroundResource(R.drawable.bg_status_unassigned);
            }

            container.addView(rowView);
        }
    }

    private void addEmptyStateView(LinearLayout container, String message) {
        TextView tvEmpty = new TextView(context);
        tvEmpty.setText(message);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvEmpty.setTextColor(ContextCompat.getColor(context, R.color.color_text_muted));
        tvEmpty.setTextSize(13);

        // Styling the background as a pill-shaped container like the screenshot
        tvEmpty.setBackgroundResource(R.drawable.bg_status_assigned);
        tvEmpty.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.color_background_secondary)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        int marginVertical = (int) (16 * context.getResources().getDisplayMetrics().density);
        params.setMargins(0, marginVertical, 0, 0);
        tvEmpty.setLayoutParams(params);

        int paddingVertical = (int) (18 * context.getResources().getDisplayMetrics().density);
        int paddingHorizontal = (int) (12 * context.getResources().getDisplayMetrics().density);
        tvEmpty.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        container.addView(tvEmpty);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvDesc;
        LinearLayout containerRows;

        public BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_banner_icon);
            tvTitle = itemView.findViewById(R.id.tv_banner_title);
            tvDesc = itemView.findViewById(R.id.tv_banner_desc);
            containerRows = itemView.findViewById(R.id.container_card_rows);
        }
    }
}