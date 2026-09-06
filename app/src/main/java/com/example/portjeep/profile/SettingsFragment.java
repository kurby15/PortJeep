package com.example.portjeep.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.portjeep.MainActivity;
import com.example.portjeep.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "portjeep_settings";
    private static final String KEY_SCREEN_LOCK = "screen_lock_enabled";
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private String userKey;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mAuth = FirebaseAuth.getInstance();
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userKey = KEY_SCREEN_LOCK + "_" + currentUser.getUid();
        } else {
            userKey = KEY_SCREEN_LOCK; // Fallback
        }

        // Back button
        ImageView btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        // Email address row
        View rowEmail = view.findViewById(R.id.btn_email_address);
        if (rowEmail != null) {
            rowEmail.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), UpdateEmailActivity.class);
                startActivity(intent);
            });
        }

        // Change mobile number row
        View rowMobile = view.findViewById(R.id.btn_change_mobile);
        if (rowMobile != null) {
            rowMobile.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ChangeMobileActivity.class);
                startActivity(intent);
            });
        }

        // Screen Lock Switch
        MaterialSwitch switchScreenLock = view.findViewById(R.id.switch_screen_lock);
        if (switchScreenLock != null) {
            switchScreenLock.setChecked(prefs.getBoolean(userKey, false));
            
            // Using setOnClickListener to intercept the toggle before it's finalized
            switchScreenLock.setOnClickListener(v -> {
                boolean isChecked = switchScreenLock.isChecked();
                // Revert switch state immediately, we only apply it if password is correct
                switchScreenLock.setChecked(!isChecked);
                showPasswordConfirmationSheet(isChecked, switchScreenLock);
            });
        }

        // Change Password Row
        View rowChangePassword = view.findViewById(R.id.btn_change_password);
        if (rowChangePassword != null) {
            rowChangePassword.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ChangePasswordActivity.class);
                startActivity(intent);
            });
        }

        // Privacy Policy Row
        View rowPrivacyPolicy = view.findViewById(R.id.btn_privacy_policy);
        if (rowPrivacyPolicy != null) {
            rowPrivacyPolicy.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), LegalActivity.class);
                intent.putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_PRIVACY);
                startActivity(intent);
            });
        }

        // Terms & Conditions Row
        View rowTermsConditions = view.findViewById(R.id.btn_terms_conditions);
        if (rowTermsConditions != null) {
            rowTermsConditions.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), LegalActivity.class);
                intent.putExtra(LegalActivity.EXTRA_TYPE, LegalActivity.TYPE_TERMS);
                startActivity(intent);
            });
        }

        return view;
    }

    private void showPasswordConfirmationSheet(boolean targetState, MaterialSwitch switchView) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_password_confirm, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tv_sheet_title);
        TextView tvSubtitle = sheetView.findViewById(R.id.tv_sheet_subtitle);
        TextInputEditText etPassword = sheetView.findViewById(R.id.et_password);
        MaterialButton btnConfirm = sheetView.findViewById(R.id.btn_confirm);
        MaterialButton btnCancel = sheetView.findViewById(R.id.btn_cancel);

        if (targetState) {
            tvTitle.setText("Enable screen lock login");
            tvSubtitle.setText("Please enter your password to confirm that you are enabling screen lock login");
        } else {
            tvTitle.setText("Disable screen lock login");
            tvSubtitle.setText("Please enter your password to confirm that you are disabling screen lock login");
        }

        btnCancel.setOnClickListener(v -> bottomSheetDialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String password = etPassword.getText().toString().trim();
            if (password.isEmpty()) {
                etPassword.setError("Password required");
                return;
            }
            
            btnConfirm.setEnabled(false);
            btnConfirm.setText("Verifying...");

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password))
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Update preference and switch state
                            prefs.edit().putBoolean(userKey, targetState).apply();
                            switchView.setChecked(targetState);
                            bottomSheetDialog.dismiss();
                            showSuccessDialog("Settings updated", "Congratulations, you have successfully updated your preferred settings.");
                        } else {
                            btnConfirm.setEnabled(true);
                            btnConfirm.setText("Confirm");
                            bottomSheetDialog.dismiss();
                            showErrorDialog("Authentication Failed", "The password you entered is incorrect. Please try again.");
                        }
                    });
            }
        });

        bottomSheetDialog.show();
    }

    private void showSuccessDialog(String title, String message) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_password_success, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_success_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_success_message);
        
        if (tvTitle != null) tvTitle.setText(title);
        if (tvMsg != null) tvMsg.setText(message);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
    }

    private void showErrorDialog(String title, String message) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_password_error, null);
        TextView tvTitle = dialogView.findViewById(R.id.tv_error_title);
        TextView tvMsg = dialogView.findViewById(R.id.tv_error_message);
        MaterialButton btnOk = dialogView.findViewById(R.id.btn_error_ok);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMsg != null) tvMsg.setText(message);
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnOk != null) {
            btnOk.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisibility(View.VISIBLE);
        }
    }
}
