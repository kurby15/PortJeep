package com.example.portjeep.profile;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.portjeep.BuildConfig;
import com.example.portjeep.R;
import com.example.portjeep.auth.LogInActivity;
import com.example.portjeep.utils.CryptoUtils;
import com.example.portjeep.utils.NetworkUtils;
import com.example.portjeep.utils.NoInternetFragment;
import com.example.portjeep.utils.PreferenceManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerProfile;
    private View llProfileContent;

    private TextView tvAvatarInitials, tvProfileName, tvEmployeeNumber;
    private TextView tvPosition, tvDateHired, tvCivilStatus, tvSex;
    private TextView tvEmail, tvPhone, tvAddress;
    private MaterialButton btnSettings, btnSignOut;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View noInternetContainer;
    private View mainContentLayout;

    public ProfileFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        noInternetContainer = view.findViewById(R.id.no_internet_container);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_profile);
        mainContentLayout = swipeRefreshLayout;

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::loadUserProfile);
        }

        shimmerProfile = view.findViewById(R.id.shimmer_profile);
        llProfileContent = view.findViewById(R.id.ll_profile_content);

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
        btnSettings = view.findViewById(R.id.btn_settings);
        btnSignOut = view.findViewById(R.id.btn_sign_out);

        showLoadingSkeleton();
        checkConnectionAndLoad();

        btnSettings.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnSignOut.setOnClickListener(v -> showLogoutConfirmationDialog());

        return view;
    }

    private void checkConnectionAndLoad() {
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            showNoInternetOverlay();
        } else {
            hideNoInternetOverlay();
            loadUserProfile();
        }
    }

    private void showNoInternetOverlay() {
        if (noInternetContainer != null) {
            noInternetContainer.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP));
            }
            NoInternetFragment fragment = new NoInternetFragment();
            fragment.setOnRetryListener(this::checkConnectionAndLoad);
            getChildFragmentManager().beginTransaction().replace(R.id.no_internet_container, fragment).commit();
        }
    }

    private void hideNoInternetOverlay() {
        if (noInternetContainer != null) {
            noInternetContainer.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mainContentLayout.setRenderEffect(null);
        }
    }

    private void showLoadingSkeleton() {
        if (shimmerProfile != null) { shimmerProfile.startShimmer(); shimmerProfile.setVisibility(View.VISIBLE); }
        if (llProfileContent != null) llProfileContent.setVisibility(View.GONE);
    }

    private void hideLoadingSkeleton() {
        if (shimmerProfile != null) { shimmerProfile.stopShimmer(); shimmerProfile.setVisibility(View.GONE); }
        if (llProfileContent != null) llProfileContent.setVisibility(View.VISIBLE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    private void showLogoutConfirmationDialog() {
        if (getContext() == null) return;
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_logout_confirmation, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_confirm_logout).setOnClickListener(v -> { dialog.dismiss(); performLogout(); });
        dialog.show();
    }

    private void performLogout() {
        // IMPORTANT: Clear all personal cache for multi-user security
        PreferenceManager.clearAllCache(getContext());

        mAuth.signOut();
        Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(requireActivity(), LogInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) { hideLoadingSkeleton(); return; }
        if (!NetworkUtils.isNetworkAvailable(getContext())) { showNoInternetOverlay(); if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false); return; }
        showLoadingSkeleton();
        db.collection("File201").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) updateUiWithProfileData(documentSnapshot);
                    hideLoadingSkeleton();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) hideLoadingSkeleton();
                });
    }

    private void updateUiWithProfileData(DocumentSnapshot doc) {
        String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
        String firstName = CryptoUtils.decrypt(doc.getString("first_name"), secretKey);
        String lastName = CryptoUtils.decrypt(doc.getString("last_name"), secretKey);
        String email = CryptoUtils.decrypt(doc.getString("email"), secretKey);
        String phone = CryptoUtils.decrypt(doc.getString("contact_no"), secretKey);
        String address = CryptoUtils.decrypt(doc.getString("street_c") != null ? doc.getString("street_c") : doc.getString("street_p"), secretKey);

        if ((email == null || email.isEmpty()) && mAuth.getCurrentUser() != null) email = mAuth.getCurrentUser().getEmail();

        String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        tvProfileName.setText(!fullName.isEmpty() ? fullName : "User Profile");
        String initials = (firstName != null && !firstName.isEmpty() ? String.valueOf(firstName.toUpperCase().charAt(0)) : "") + (lastName != null && !lastName.isEmpty() ? String.valueOf(lastName.toUpperCase().charAt(0)) : "");
        tvAvatarInitials.setText(!initials.isEmpty() ? initials : "U");

        tvEmployeeNumber.setText(doc.getString("employee_number") != null ? "EMP ID: " + doc.getString("employee_number") : "N/A");
        tvCivilStatus.setText(doc.getString("civil_status") != null ? doc.getString("civil_status") : "N/A");
        tvSex.setText(doc.getString("sex") != null ? doc.getString("sex") : "N/A");

        Object dateHiredObj = doc.get("date_hired");
        if (dateHiredObj instanceof Timestamp) tvDateHired.setText(new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(((Timestamp) dateHiredObj).toDate()));
        else tvDateHired.setText(dateHiredObj != null ? dateHiredObj.toString() : "N/A");

        tvEmail.setText(email != null ? email : "N/A");
        tvPhone.setText(phone != null ? phone : "N/A");
        tvAddress.setText(address != null && !address.isEmpty() ? address : "Bulacan");

        String positionId = doc.getString("position_id");
        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions").document(positionId).get().addOnSuccessListener(posDoc -> {
                if (isAdded() && posDoc.exists()) {
                    String rawTitle = posDoc.getString("title");
                    if (rawTitle == null) rawTitle = posDoc.getString("name");
                    if (rawTitle == null) rawTitle = posDoc.getString("position");

                    if (rawTitle != null) {
                        String decrypted = CryptoUtils.decrypt(rawTitle, secretKey);
                        // If decryption fails or returns empty, use the raw title (it might not be encrypted)
                        String finalTitle = (decrypted != null && !decrypted.isEmpty()) ? decrypted : rawTitle;
                        tvPosition.setText(finalTitle.toUpperCase());
                    } else {
                        tvPosition.setText("PUJ STAFF");
                    }
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Error fetching position", e));
        }
    }
}
