package com.example.portjeep.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.auth.LogInActivity;
import com.example.portjeep.utils.CryptoUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private TextView tvAvatarInitials, tvProfileName, tvEmployeeNumber;
    private TextView tvPosition, tvDateHired, tvCivilStatus, tvSex;
    private TextView tvEmail, tvPhone, tvAddress;
    private MaterialButton btnChangePassword, btnSignOut;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userEmail = "";

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        tvAvatarInitials = view.findViewById(R.id.tv_avatar_initials);
        tvProfileName = view.findViewById(R.id.tv_profile_name);
        tvEmployeeNumber = view.findViewById(R.id.tv_employee_number);

        tvPosition = view.findViewById(R.id.tv_position);
        tvDateHired = view.findViewById(R.id.tv_date_hired);
        tvCivilStatus = view.findViewById(R.id.tv_civil_status);
        tvSex = view.findViewById(R.id.tv_sex);

        tvEmail = view.findViewById(R.id.tv_email);
        tvPhone = view.findViewById(R.id.tv_phone);
        tvAddress = view.findViewById(R.id.tv_address);

        btnChangePassword = view.findViewById(R.id.btn_change_password);
        btnSignOut = view.findViewById(R.id.btn_sign_out);

        // Load profile from Firestore
        loadUserProfile();

        // Change Password Handler with Dialog Prompt
        btnChangePassword.setOnClickListener(v -> showResetPasswordDialog());

        // Sign Out Handler
        btnSignOut.setOnClickListener(v -> showLogoutConfirmationDialog());

        return view;
    }

    private void showResetPasswordDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_reset_password, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        com.google.android.material.card.MaterialCardView cardView = (com.google.android.material.card.MaterialCardView) dialogView;
        TextInputLayout tilEmail = dialogView.findViewById(R.id.til_reset_email);
        TextInputEditText etEmail = dialogView.findViewById(R.id.et_reset_email);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_reset);
        MaterialButton btnSend = dialogView.findViewById(R.id.btn_send_reset);

        // Pre-fill user's email if available
        if (etEmail != null && !userEmail.isEmpty()) {
            etEmail.setText(userEmail);
        }

        // Dynamic Theme Change Watcher (Changes dialog background without closing)
        if (etEmail != null) {
            etEmail.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String input = s.toString().trim();

                    // 1. Toggle preset theme themes based on keywords
                    if (input.equalsIgnoreCase("dark")) {
                        cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));
                    } else if (input.equalsIgnoreCase("light")) {
                        cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
                    }
                    // 2. Custom Hex Color code live change (e.g., #2196F3 or #000000)
                    else if (input.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")) {
                        try {
                            cardView.setCardBackgroundColor(android.graphics.Color.parseColor(input));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSend.setOnClickListener(v -> {
            String inputEmail = etEmail != null && etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (inputEmail.isEmpty()) {
                tilEmail.setError("Email address is required");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                tilEmail.setError("Please enter a valid email address");
                return;
            }

            tilEmail.setError(null);
            dialog.dismiss();
            sendPasswordResetEmail(inputEmail);
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(getContext(), "Password reset link sent to " + email, Toast.LENGTH_LONG).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void showLogoutConfirmationDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_logout_confirmation, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_logout);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            performLogout();
        });

        dialog.show();
    }

    private void performLogout() {
        mAuth.signOut();
        Toast.makeText(getContext(), "Signed Out", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(requireActivity(), LogInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();

        db.collection("File201")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) {
                        updateUiWithProfileData(documentSnapshot);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUiWithProfileData(DocumentSnapshot doc) {
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;

        String rawFirstName = doc.getString("first_name");
        String rawLastName = doc.getString("last_name");
        String rawEmail = doc.getString("email");
        String rawPhone = doc.getString("contact_no");
        String rawStreet = doc.getString("street_c");
        if (rawStreet == null) rawStreet = doc.getString("street_p");

        String employeeNo = doc.getString("employee_number");
        String civilStatus = doc.getString("civil_status");
        String sex = doc.getString("sex");
        String positionId = doc.getString("position_id");

        String firstName = CryptoUtils.decrypt(rawFirstName, secretKey);
        String lastName = CryptoUtils.decrypt(rawLastName, secretKey);
        String email = CryptoUtils.decrypt(rawEmail, secretKey);
        String phone = CryptoUtils.decrypt(rawPhone, secretKey);
        String address = CryptoUtils.decrypt(rawStreet, secretKey);

        if ((email == null || email.isEmpty()) && mAuth.getCurrentUser() != null) {
            email = mAuth.getCurrentUser().getEmail();
        }

        userEmail = email != null ? email : "";

        String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        tvProfileName.setText(!fullName.isEmpty() ? fullName : "User Profile");

        String initials = "";
        if (firstName != null && !firstName.isEmpty()) initials += firstName.toUpperCase().charAt(0);
        if (lastName != null && !lastName.isEmpty()) initials += lastName.toUpperCase().charAt(0);
        tvAvatarInitials.setText(!initials.isEmpty() ? initials : "U");

        tvEmployeeNumber.setText(employeeNo != null ? "EMP ID: " + employeeNo : "N/A");
        tvCivilStatus.setText(civilStatus != null ? civilStatus : "N/A");
        tvSex.setText(sex != null ? sex : "N/A");

        Object dateHiredObj = doc.get("date_hired");
        if (dateHiredObj instanceof Timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
            tvDateHired.setText(sdf.format(((Timestamp) dateHiredObj).toDate()));
        } else if (dateHiredObj instanceof String) {
            tvDateHired.setText((String) dateHiredObj);
        } else {
            tvDateHired.setText("N/A");
        }

        tvEmail.setText(email != null ? email : "N/A");
        tvPhone.setText(phone != null ? phone : "N/A");
        tvAddress.setText(address != null && !address.isEmpty() ? address : "San Jose del Monte, Bulacan");

        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions")
                    .document(positionId)
                    .get()
                    .addOnSuccessListener(posDoc -> {
                        if (isAdded() && posDoc.exists()) {
                            String rawTitle = null;
                            Map<String, Object> data = posDoc.getData();

                            if (data != null && !data.isEmpty()) {
                                String[] commonKeys = {"title", "name", "position", "position_name", "role", "description", "Title", "Name"};
                                for (String key : commonKeys) {
                                    if (data.containsKey(key) && data.get(key) instanceof String) {
                                        rawTitle = (String) data.get(key);
                                        break;
                                    }
                                }

                                if (rawTitle == null) {
                                    for (Object val : data.values()) {
                                        if (val instanceof String && !((String) val).trim().isEmpty()) {
                                            rawTitle = (String) val;
                                            break;
                                        }
                                    }
                                }
                            }

                            String title = CryptoUtils.decrypt(rawTitle, secretKey);
                            tvPosition.setText(title != null ? title : "Driver / PAO");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) tvPosition.setText("Driver / PAO");
                    });
        }
    }
}