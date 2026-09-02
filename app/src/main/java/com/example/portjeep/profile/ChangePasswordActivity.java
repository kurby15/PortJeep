package com.example.portjeep.profile;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
    private ImageView ivReq10, ivReqUpper, ivReqLower, ivReqNumber, ivReqSpecial, ivReqMatch;
    private TextView tvReq10, tvReqUpper, tvReqLower, tvReqNumber, tvReqSpecial, tvReqMatch;
    private TextView tvStrengthLabel;
    private LinearProgressIndicator strengthProgress;
    private MaterialButton btnUpdate, btnCancel;

    private FirebaseAuth mAuth;

    // Compiled Regex for performance (front-end instant validation)
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.{10,}$)(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%&*]).*$"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_change_password);

        mAuth = FirebaseAuth.getInstance();

        View mainView = findViewById(R.id.main_layout);
        View statusBarSpacer = findViewById(R.id.status_bar_spacer);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
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

        ivReq10 = findViewById(R.id.iv_req_10chars);
        ivReqUpper = findViewById(R.id.iv_req_upper);
        ivReqLower = findViewById(R.id.iv_req_lower);
        ivReqNumber = findViewById(R.id.iv_req_number);
        ivReqSpecial = findViewById(R.id.iv_req_special);
        ivReqMatch = findViewById(R.id.iv_req_match);

        tvReq10 = findViewById(R.id.tv_req_10chars);
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
            String currentPwd = etCurrent.getText().toString().trim();
            String newPwd = etNew.getText().toString().trim();
            performPasswordUpdate(currentPwd, newPwd);
        });
    }

    private void validatePassword() {
        String current = etCurrent.getText().toString().trim();
        String newPwd = etNew.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

        boolean has10 = newPwd.length() >= 10;
        boolean hasUpper = newPwd.matches(".*[A-Z].*");
        boolean hasLower = newPwd.matches(".*[a-z].*");
        boolean hasNum = newPwd.matches(".*[0-9].*");
        boolean hasSpecial = newPwd.matches(".*[!@#$%&*].*");
        boolean matches = !newPwd.isEmpty() && newPwd.equals(confirm);

        boolean hasNewInput = !newPwd.isEmpty();
        boolean hasConfirmInput = !confirm.isEmpty();

        updateRequirementUI(has10, hasNewInput, ivReq10, tvReq10);
        updateRequirementUI(hasUpper, hasNewInput, ivReqUpper, tvReqUpper);
        updateRequirementUI(hasLower, hasNewInput, ivReqLower, tvReqLower);
        updateRequirementUI(hasNum, hasNewInput, ivReqNumber, tvReqNumber);
        updateRequirementUI(hasSpecial, hasNewInput, ivReqSpecial, tvReqSpecial);
        updateRequirementUI(matches, hasConfirmInput, ivReqMatch, tvReqMatch);

        int score = 0;
        if (has10) score += 20;
        if (hasUpper && hasLower) score += 20;
        if (hasNum) score += 20;
        if (hasSpecial) score += 20;
        if (matches) score += 20;

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

        boolean isRegexValid = PASSWORD_PATTERN.matcher(newPwd).matches();
        boolean isValid = !current.isEmpty() && isRegexValid && matches;

        btnUpdate.setEnabled(isValid);
        btnUpdate.setAlpha(isValid ? 1.0f : 0.6f);
    }

    private void updateRequirementUI(boolean valid, boolean hasInput, ImageView iv, TextView tv) {
        int color;
        int iconRes;

        if (valid) {
            color = ContextCompat.getColor(this, R.color.color_brand_primary);
            iconRes = R.drawable.ic_check;
        } else if (hasInput) {
            color = ContextCompat.getColor(this, R.color.color_brand_accent);
            iconRes = R.drawable.ic_close;
        } else {
            color = ContextCompat.getColor(this, R.color.color_text_muted);
            iconRes = R.drawable.bg_circle_icon;
        }

        iv.setImageResource(iconRes);
        iv.setImageTintList(ColorStateList.valueOf(color));
        tv.setTextColor(color);
    }

    private void performPasswordUpdate(String currentPwd, String newPwd) {
        if (currentPwd.equals(newPwd)) {
            showErrorDialog("Same Password", "The new password cannot be the same as your current password. Please choose a different one.");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        btnUpdate.setEnabled(false);
        btnUpdate.setText("Updating...");

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPwd);

        user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
            if (reauthTask.isSuccessful()) {
                user.updatePassword(newPwd).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        mAuth.signOut();
                        showSuccessDialog();
                    } else {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("UPDATE PASSWORD");
                        String error = updateTask.getException() != null ? updateTask.getException().getMessage() : "Unknown error occurred.";
                        showErrorDialog("Update Failed", error);
                    }
                });
            } else {
                btnUpdate.setEnabled(true);
                btnUpdate.setText("UPDATE PASSWORD");
                String error = reauthTask.getException() != null ? reauthTask.getException().getMessage() : "Authentication failed.";
                showErrorDialog("Current Password Incorrect", error);
            }
        });
    }

    private void showSuccessDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_success, null);
        TextView tvCountdown = dialogView.findViewById(R.id.tv_countdown);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();

        new CountDownTimer(3500, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                tvCountdown.setText("You will logout in " + secondsRemaining + "...");
            }

            @Override
            public void onFinish() {
                if (!isFinishing()) {
                    dialog.dismiss();
                    Intent intent = new Intent(ChangePasswordActivity.this, LogInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }
        }.start();
    }

    private void showErrorDialog(String title, String message) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_error, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.tv_error_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_error_message);
        MaterialButton btnOk = dialogView.findViewById(R.id.btn_error_ok);

        tvTitle.setText(title);
        tvMessage.setText(message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
