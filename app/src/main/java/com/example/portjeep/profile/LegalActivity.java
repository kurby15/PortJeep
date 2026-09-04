package com.example.portjeep.profile;

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
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
        TextView tvContent = findViewById(R.id.tv_legal_content);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        int type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_PRIVACY);

        if (type == TYPE_TERMS) {
            if (tvHeaderTitle != null) tvHeaderTitle.setText("TERMS & CONDITIONS");
            if (tvContent != null) {
                tvContent.setText(Html.fromHtml(getString(R.string.terms_conditions_content), Html.FROM_HTML_MODE_COMPACT));
            }
        } else {
            if (tvHeaderTitle != null) tvHeaderTitle.setText("PRIVACY POLICY");
            if (tvContent != null) {
                tvContent.setText(Html.fromHtml(getString(R.string.privacy_policy_content), Html.FROM_HTML_MODE_COMPACT));
            }
        }
    }
}
