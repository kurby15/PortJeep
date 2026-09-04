package com.example.portjeep.profile;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portjeep.R;
import com.example.portjeep.auth.LogInActivity;
import com.example.portjeep.utils.NetworkUtils;
import com.example.portjeep.utils.NoInternetFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.regex.Pattern;

public class ChangePasswordActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextInputEditText etCurrent, etNew, etConfirm;
    private ImageView ivReq8, ivReqUpper, ivReqLower, ivReqNumber, ivReqSpecial, ivReqMatch;
    private TextView tvReq8, tvReqUpper, tvReqLower, tvReqNumber, tvReqSpecial, tvReqMatch;
    private TextView tvStrengthLabel;
    private LinearProgressIndicator strengthProgress;
    private MaterialButton btnUpdate, btnCancel;

    private FirebaseAuth mAuth;
    private View noInternetContainer;
    private View mainLayout;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.{8,}$)(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%&*]).*$"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();
        noInternetContainer = findViewById(R.id.no_internet_container);
        mainLayout = findViewById(R.id.main_layout);

        View statusBarSpacer = findViewById(R.id.status_bar_spacer);
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

        // Initialize Views
        btnBack = findViewById(R.id.btn_back_change);
        etCurrent = findViewById(R.id.et_current_password);
        etNew = findViewById(R.id.et_new_password);
        etConfirm = findViewById(R.id.et_confirm_password);
        ivReq8 = findViewById(R.id.iv_req_8chars);
        ivReqUpper = findViewById(R.id.iv_req_upper);
        ivReqLower = findViewById(R.id.iv_req_lower);
        ivReqNumber = findViewById(R.id.iv_req_number);
        ivReqSpecial = findViewById(R.id.iv_req_special);
        ivReqMatch = findViewById(R.id.iv_req_match);
        tvReq8 = findViewById(R.id.tv_req_8chars);
        tvReqUpper = findViewById(R.id.tv_req_upper);
        tvReqLower = findViewById(R.id.tv_req_lower);
        tvReqNumber = findViewById(R.id.tv_req_number);
        tvReqSpecial = findViewById(R.id.tv_req_special);
        tvReqMatch = findViewById(R.id.tv_req_match);
        tvStrengthLabel = findViewById(R.id.tv_strength_label);
        strengthProgress = findViewById(R.id.strength_progress);
        btnUpdate = findViewById(R.id.btn_update_password);
        btnCancel = findViewById(R.id.btn_cancel_change);

        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        etCurrent.addTextChangedListener(watcher);
        etNew.addTextChangedListener(watcher);
        etConfirm.addTextChangedListener(watcher);

        btnUpdate.setOnClickListener(v -> {
            if (!NetworkUtils.isNetworkAvailable(this)) {
                showNoInternetOverlay();
                return;
            }
            performPasswordUpdate(etCurrent.getText().toString().trim(), etNew.getText().toString().trim());
        });

        checkConnection();
        setupNetworkListener();
    }

    private void setupNetworkListener() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> hideNoInternetOverlay());
                }
                @Override
                public void onLost(Network network) {
                    runOnUiThread(() -> showNoInternetOverlay());
                }
            };
            connectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    networkCallback
            );
        }
    }

    private void checkConnection() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNoInternetOverlay();
        } else {
            hideNoInternetOverlay();
        }
    }

    private void showNoInternetOverlay() {
        if (noInternetContainer != null && noInternetContainer.getVisibility() != View.VISIBLE) {
            noInternetContainer.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainLayout.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP));
            }
            NoInternetFragment fragment = new NoInternetFragment();
            fragment.setOnRetryListener(this::checkConnection);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.no_internet_container, fragment)
                    .commit();
        }
    }

    private void hideNoInternetOverlay() {
        if (noInternetContainer != null && noInternetContainer.getVisibility() == View.VISIBLE) {
            noInternetContainer.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainLayout.setRenderEffect(null);
            }
        }
    }

    private void validatePassword() {
        String current = etCurrent.getText().toString().trim();
        String newPwd = etNew.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

        boolean has8 = newPwd.length() >= 8;
        boolean hasUpper = newPwd.matches(".*[A-Z].*");
        boolean hasLower = newPwd.matches(".*[a-z].*");
        boolean hasNum = newPwd.matches(".*[0-9].*");
        boolean hasSpecial = newPwd.matches(".*[!@#$%&*].*");
        boolean matches = !newPwd.isEmpty() && newPwd.equals(confirm);

        updateRequirementUI(has8, !newPwd.isEmpty(), ivReq8, tvReq8);
        updateRequirementUI(hasUpper, !newPwd.isEmpty(), ivReqUpper, tvReqUpper);
        updateRequirementUI(hasLower, !newPwd.isEmpty(), ivReqLower, tvReqLower);
        updateRequirementUI(hasNum, !newPwd.isEmpty(), ivReqNumber, tvReqNumber);
        updateRequirementUI(hasSpecial, !newPwd.isEmpty(), ivReqSpecial, tvReqSpecial);
        updateRequirementUI(matches, !confirm.isEmpty(), ivReqMatch, tvReqMatch);

        int score = (has8 ? 20 : 0) + (hasUpper && hasLower ? 20 : 0) + (hasNum ? 20 : 0) + (hasSpecial ? 20 : 0) + (matches ? 20 : 0);
        strengthProgress.setProgress(score);
        
        if (score < 40) {
            tvStrengthLabel.setText("WEAK");
            tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.color_brand_accent));
            strengthProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.color_brand_accent));
        } else if (score < 100) {
            tvStrengthLabel.setText("FAIR");
            tvStrengthLabel.setTextColor(Color.parseColor("#FFA000"));
            strengthProgress.setIndicatorColor(Color.parseColor("#FFA000"));
        } else {
            tvStrengthLabel.setText("STRONG");
            tvStrengthLabel.setTextColor(Color.parseColor("#2E7D32"));
            strengthProgress.setIndicatorColor(Color.parseColor("#2E7D32"));
        }

        boolean isValid = !current.isEmpty() && PASSWORD_PATTERN.matcher(newPwd).matches() && matches;
        btnUpdate.setEnabled(isValid);
        btnUpdate.setAlpha(isValid ? 1.0f : 0.6f);
    }

    private void updateRequirementUI(boolean valid, boolean hasInput, ImageView iv, TextView tv) {
        int color = valid ? ContextCompat.getColor(this, R.color.color_brand_primary) : 
                    (hasInput ? ContextCompat.getColor(this, R.color.color_brand_accent) : 
                    ContextCompat.getColor(this, R.color.color_text_muted));
        int iconRes = valid ? R.drawable.ic_check : (hasInput ? R.drawable.ic_close : R.drawable.bg_circle_icon);
        iv.setImageResource(iconRes);
        iv.setImageTintList(ColorStateList.valueOf(color));
        tv.setTextColor(color);
    }

    private void performPasswordUpdate(String currentPwd, String newPwd) {
        if (currentPwd.equals(newPwd)) {
            showErrorDialog("Same Password", "The new password cannot be the same as your current password.");
            return;
        }
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        btnUpdate.setEnabled(false);
        btnUpdate.setText("Updating...");

        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), currentPwd)).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                user.updatePassword(newPwd).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        mAuth.signOut();
                        showSuccessDialog();
                    } else {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("UPDATE PASSWORD");
                        showErrorDialog("Update Failed", updateTask.getException().getMessage());
                    }
                });
            } else {
                btnUpdate.setEnabled(true);
                btnUpdate.setText("UPDATE PASSWORD");
                showErrorDialog("Current Password Incorrect", task.getException().getMessage());
            }
        });
    }

    private void showSuccessDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_success, null);
        TextView tvCountdown = dialogView.findViewById(R.id.tv_countdown);
        if (tvCountdown != null) {
            tvCountdown.setVisibility(View.VISIBLE);
        }
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogView).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        new CountDownTimer(3500, 1000) {
            @Override public void onTick(long millis) { 
                if (tvCountdown != null) {
                    tvCountdown.setText("You will logout in " + (millis / 1000) + "..."); 
                }
            }
            @Override public void onFinish() {
                dialog.dismiss();
                startActivity(new Intent(ChangePasswordActivity.this, LogInActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }
        }.start();
    }

    private void showErrorDialog(String title, String msg) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_error, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_error_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_error_message);
        
        if (tvTitle != null) tvTitle.setText(title);
        if (tvMsg != null) tvMsg.setText(msg);
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        
        View btnOk = dialogView.findViewById(R.id.btn_error_ok);
        if (btnOk != null) {
            btnOk.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }
    }
}
