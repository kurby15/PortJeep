package com.example.portjeep.profile;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.MaintenanceFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ChangeMobileActivity extends AppCompatActivity {

    private TextView tvCurrentMobile;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private View mainLayout;
    private boolean isMaintenance = false; // Set to true to show maintenance screen

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_mobile);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mainLayout = findViewById(R.id.main_layout);
        tvCurrentMobile = findViewById(R.id.tv_current_mobile);
        ImageView btnBack = findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

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

        // Load the real data
        loadCurrentMobile();

        // Show maintenance overlay if active, but don't stop the background data loading
        if (isMaintenance) {
            showMaintenance();
        }
    }

    private void showMaintenance() {
        View container = findViewById(R.id.no_internet_container);
        if (container != null) {
            container.setVisibility(View.VISIBLE);

            // Apply blur effect for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mainLayout != null) {
                mainLayout.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP));
            }

            MaintenanceFragment maintenanceFragment = new MaintenanceFragment();
            maintenanceFragment.setOnBackListener(this::finish);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.no_internet_container, maintenanceFragment)
                    .commit();
        }
    }

    private void loadCurrentMobile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("File201").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                        String phone = CryptoUtils.decrypt(documentSnapshot.getString("contact_no"), secretKey);
                        if (phone != null && !phone.isEmpty()) {
                            tvCurrentMobile.setText(phone);
                        } else {
                            tvCurrentMobile.setText("N/A");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (tvCurrentMobile != null) {
                        tvCurrentMobile.setText("Error loading mobile");
                    }
                });
    }
}
