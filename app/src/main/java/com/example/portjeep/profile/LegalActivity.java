package com.example.portjeep.profile;

import android.os.Bundle;
import android.text.Html;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.portjeep.R;

public class LegalActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "legal_type";
    public static final int TYPE_PRIVACY = 1;
    public static final int TYPE_TERMS = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_legal);

        ImageView btnBack = findViewById(R.id.btn_back);
        TextView tvTitle = findViewById(R.id.tv_legal_title);
        TextView tvContent = findViewById(R.id.tv_legal_content);

        btnBack.setOnClickListener(v -> finish());

        int type = getIntent().getIntExtra(EXTRA_TYPE, TYPE_PRIVACY);

        if (type == TYPE_TERMS) {
            tvTitle.setText(R.string.settings_terms_conditions);
            tvContent.setText(Html.fromHtml(getString(R.string.terms_conditions_content), Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvTitle.setText(R.string.settings_privacy_policy);
            tvContent.setText(Html.fromHtml(getString(R.string.privacy_policy_content), Html.FROM_HTML_MODE_COMPACT));
        }
    }
}