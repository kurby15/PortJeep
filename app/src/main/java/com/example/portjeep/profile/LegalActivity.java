package com.example.portjeep.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.portjeep.R;

public class LegalActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "legal_type";
    public static final int TYPE_PRIVACY = 1;
    public static final int TYPE_TERMS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_legal);

        View mainLayout = findViewById(R.id.main_layout);
        View statusBarSpacer = findViewById(R.id.status_bar_spacer);

        if (mainLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (statusBarSpacer != null) {
                    statusBarSpacer.getLayoutParams().height = systemBars.top;
                    statusBarSpacer.requestLayout();
                }
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvHeaderTitle = findViewById(R.id.tv_header_title);
        TextView tvLastUpdated = findViewById(R.id.tv_last_updated);
        LinearLayout containerSections = findViewById(R.id.container_sections);
        TextView tvContent = findViewById(R.id.tv_legal_content);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        int type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_PRIVACY);

        if (tvLastUpdated != null) {
            tvLastUpdated.setVisibility(View.VISIBLE);
            tvLastUpdated.setText(getString(R.string.legal_last_updated));
        }
        
        if (tvContent != null) tvContent.setVisibility(View.GONE);

        if (type == TYPE_TERMS) {
            if (tvHeaderTitle != null) tvHeaderTitle.setText("TERMS & CONDITIONS");
            
            // Inflate sections for Terms & Conditions (1-9)
            addSection(containerSections, R.string.terms_1_title, R.string.terms_1_content);
            addSection(containerSections, R.string.terms_2_title, R.string.terms_2_content);
            addSection(containerSections, R.string.terms_3_title, R.string.terms_3_content);
            addSection(containerSections, R.string.terms_4_title, R.string.terms_4_content);
            addSection(containerSections, R.string.terms_5_title, R.string.terms_5_content);
            addSection(containerSections, R.string.terms_6_title, R.string.terms_6_content);
            addSection(containerSections, R.string.terms_7_title, R.string.terms_7_content);
            addSection(containerSections, R.string.terms_8_title, R.string.terms_8_content);
            addSection(containerSections, R.string.terms_9_title, R.string.terms_9_content);

        } else {
            if (tvHeaderTitle != null) tvHeaderTitle.setText("PRIVACY POLICY");
            
            // Inflate sections for Privacy Policy (1-6)
            addSection(containerSections, R.string.privacy_1_title, R.string.privacy_1_content);
            addSection(containerSections, R.string.privacy_2_title, R.string.privacy_2_content);
            addSection(containerSections, R.string.privacy_3_title, R.string.privacy_3_content);
            addSection(containerSections, R.string.privacy_4_title, R.string.privacy_4_content);
            addSection(containerSections, R.string.privacy_5_title, R.string.privacy_5_content);
            addSection(containerSections, R.string.privacy_6_title, R.string.privacy_6_content);
        }
    }

    private void addSection(LinearLayout container, int titleRes, int contentRes) {
        if (container == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.item_legal_section, container, false);
        TextView tvTitle = view.findViewById(R.id.tv_section_title);
        TextView tvContent = view.findViewById(R.id.tv_section_content);
        
        tvTitle.setText(getString(titleRes));
        tvContent.setText(getString(contentRes));
        
        container.addView(view);
    }
}
