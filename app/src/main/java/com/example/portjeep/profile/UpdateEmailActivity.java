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

public class UpdateEmailActivity extends AppCompatActivity {

    private TextView tvCurrentEmail;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private View mainLayout;
    private boolean isMaintenance = false; // Set to true to show maintenance screen

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_email);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mainLayout = findViewById(R.id.main_layout);
        tvCurrentEmail = findViewById(R.id.tv_current_email);
        ImageView btnBack = findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Apply Window Insets
        View statusBarSpacer = findViewById(R.id.status_bar_spacer);
        if (mainLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                if (statusBarSpacer != null) {
                    statusBarSpacer.getLayoutParams().height = systemBars.top;
                    statusBarSpacer.requestLayout();
                }

                int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
                v.setPadding(systemBars.left, 0, systemBars.right, bottomPadding);

                return WindowInsetsCompat.CONSUMED;
            });
        }

        // Load the real data
        loadCurrentEmail();

        // Show maintenance overlay if active, but don't stop the background data loading
        if (isMaintenance) {
            showMaintenance();
        }
    }

    private void showMaintenance() {
        View container = findViewById(R.id.no_internet_container);
        if (container != null) {
            container.setVisibility(View.VISIBLE);
            
            // Apply blur effect like in No Internet
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

    private void loadCurrentEmail() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("File201").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                    if (documentSnapshot.exists()) {
                        String email = CryptoUtils.decrypt(documentSnapshot.getString("email"), secretKey);
                        if (email != null && tvCurrentEmail != null) {
                            tvCurrentEmail.setText(email);
                        }
                    } else if (currentUser.getEmail() != null && tvCurrentEmail != null) {
                        tvCurrentEmail.setText(currentUser.getEmail());
                    }
                })
                .addOnFailureListener(e -> {
                    if (tvCurrentEmail != null) tvCurrentEmail.setText("Error loading email");
                });
    }
}
