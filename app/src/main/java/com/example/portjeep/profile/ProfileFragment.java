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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private SwipeRefreshLayout swipeRefreshLayout;
    private View noInternetContainer;
    private View mainContentLayout;

    private ShimmerFrameLayout shimmerHeader;
    private MaterialCardView cardSummary;
    private TextView tvAvatarInitials, tvProfileName, tvEmployeeNumber;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ProfileContentFragment contentFragment;
    private DocumentSnapshot lastLoadedDoc;

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

        shimmerHeader = view.findViewById(R.id.shimmer_profile_header);
        cardSummary = view.findViewById(R.id.card_profile_summary);
        tvAvatarInitials = view.findViewById(R.id.tv_avatar_initials);
        tvProfileName = view.findViewById(R.id.tv_profile_name);
        tvEmployeeNumber = view.findViewById(R.id.tv_employee_number);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::loadUserProfile);
        }

        // Attach ProfileContentFragment into the container
        contentFragment = (ProfileContentFragment) getChildFragmentManager().findFragmentById(R.id.fl_profile_content_container);
        if (contentFragment == null) {
            contentFragment = new ProfileContentFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.fl_profile_content_container, contentFragment)
                    .commit();
        }

        checkConnectionAndLoad();

        return view;
    }

    public void onContentFragmentReady() {
        if (lastLoadedDoc != null) {
            updateUiWithProfileData(lastLoadedDoc);
        }
    }

    public void navigateToSettings() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mainContentLayout != null) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mainContentLayout != null) {
                mainContentLayout.setRenderEffect(null);
            }
        }
    }

    private void showLoadingSkeleton() {
        if (shimmerHeader != null) { shimmerHeader.startShimmer(); shimmerHeader.setVisibility(View.VISIBLE); }
        if (cardSummary != null) cardSummary.setVisibility(View.GONE);
        if (contentFragment != null) contentFragment.showLoadingSkeleton();
    }

    private void hideLoadingSkeleton() {
        if (shimmerHeader != null) { shimmerHeader.stopShimmer(); shimmerHeader.setVisibility(View.GONE); }
        if (cardSummary != null) cardSummary.setVisibility(View.VISIBLE);
        if (contentFragment != null) contentFragment.hideLoadingSkeleton();
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    public void showLogoutConfirmationDialog() {
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
                    if (isAdded() && documentSnapshot.exists()) {
                        lastLoadedDoc = documentSnapshot;
                        updateUiWithProfileData(documentSnapshot);
                    }
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
        if (fullName.isEmpty()) fullName = "User Profile";
        String initials = (firstName != null && !firstName.isEmpty() ? String.valueOf(firstName.toUpperCase().charAt(0)) : "") + (lastName != null && !lastName.isEmpty() ? String.valueOf(lastName.toUpperCase().charAt(0)) : "");
        if (initials.isEmpty()) initials = "U";

        if (tvProfileName != null) tvProfileName.setText(fullName);
        if (tvAvatarInitials != null) tvAvatarInitials.setText(initials);
        String empNum = doc.getString("employee_number") != null ? "EMP ID: " + doc.getString("employee_number") : "N/A";
        if (tvEmployeeNumber != null) tvEmployeeNumber.setText(empNum);

        String civilStatus = doc.getString("civil_status") != null ? doc.getString("civil_status") : "N/A";
        String sex = doc.getString("sex") != null ? doc.getString("sex") : "N/A";

        String dateHired = "N/A";
        Object dateHiredObj = doc.get("date_hired");
        if (dateHiredObj instanceof Timestamp) dateHired = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(((Timestamp) dateHiredObj).toDate());
        else if (dateHiredObj != null) dateHired = dateHiredObj.toString();

        if (email == null) email = "N/A";
        if (phone == null) phone = "N/A";
        if (address == null || address.isEmpty()) address = "Bulacan";

        if (contentFragment != null && contentFragment.isAdded()) {
            contentFragment.updateUi(civilStatus, sex, dateHired, email, phone, address);
        }

        String positionId = doc.getString("position_id");
        if (positionId != null && !positionId.trim().isEmpty()) {
            db.collection("Positions").document(positionId).get().addOnSuccessListener(posDoc -> {
                if (isAdded() && posDoc.exists() && contentFragment != null) {
                    String rawTitle = posDoc.getString("title");
                    if (rawTitle == null) rawTitle = posDoc.getString("name");
                    if (rawTitle == null) rawTitle = posDoc.getString("position");

                    if (rawTitle != null) {
                        String decrypted = CryptoUtils.decrypt(rawTitle, secretKey);
                        String finalTitle = (decrypted != null && !decrypted.isEmpty()) ? decrypted : rawTitle;
                        contentFragment.updatePosition(finalTitle.toUpperCase());
                    } else {
                        contentFragment.updatePosition("PUJ STAFF");
                    }
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Error fetching position", e));
        }
    }
}
