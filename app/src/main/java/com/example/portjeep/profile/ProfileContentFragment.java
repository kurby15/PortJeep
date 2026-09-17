package com.example.portjeep.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.portjeep.R;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;

public class ProfileContentFragment extends Fragment {

    private ShimmerFrameLayout shimmerProfile;
    private View llProfileContent;

    private TextView tvPosition, tvDateHired, tvCivilStatus, tvSex;
    private TextView tvEmail, tvPhone, tvAddress;
    private MaterialButton btnSettings, btnSignOut;

    public ProfileContentFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile_content, container, false);

        shimmerProfile = view.findViewById(R.id.shimmer_profile);
        llProfileContent = view.findViewById(R.id.ll_profile_content);

        tvPosition = view.findViewById(R.id.tv_position);
        tvDateHired = view.findViewById(R.id.tv_date_hired);
        tvCivilStatus = view.findViewById(R.id.tv_civil_status);
        tvSex = view.findViewById(R.id.tv_sex);
        tvEmail = view.findViewById(R.id.tv_email);
        tvPhone = view.findViewById(R.id.tv_phone);
        tvAddress = view.findViewById(R.id.tv_address);
        btnSettings = view.findViewById(R.id.btn_settings);
        btnSignOut = view.findViewById(R.id.btn_sign_out);

        btnSettings.setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof ProfileFragment) {
                ((ProfileFragment) parent).navigateToSettings();
            }
        });

        btnSignOut.setOnClickListener(v -> {
            Fragment parent = getParentFragment();
            if (parent instanceof ProfileFragment) {
                ((ProfileFragment) parent).showLogoutConfirmationDialog();
            }
        });

        // Let the parent know we are ready to populate data if needed
        Fragment parent = getParentFragment();
        if (parent instanceof ProfileFragment) {
            ((ProfileFragment) parent).onContentFragmentReady();
        }

        return view;
    }

    public void showLoadingSkeleton() {
        if (shimmerProfile != null) { shimmerProfile.startShimmer(); shimmerProfile.setVisibility(View.VISIBLE); }
        if (llProfileContent != null) llProfileContent.setVisibility(View.GONE);
    }

    public void hideLoadingSkeleton() {
        if (shimmerProfile != null) { shimmerProfile.stopShimmer(); shimmerProfile.setVisibility(View.GONE); }
        if (llProfileContent != null) llProfileContent.setVisibility(View.VISIBLE);
    }

    public void updateUi(String civilStatus, String sex, String dateHired, String email, String phone, String address) {
        if (tvCivilStatus != null) tvCivilStatus.setText(civilStatus);
        if (tvSex != null) tvSex.setText(sex);
        if (tvDateHired != null) tvDateHired.setText(dateHired);
        if (tvEmail != null) tvEmail.setText(email);
        if (tvPhone != null) tvPhone.setText(phone);
        if (tvAddress != null) tvAddress.setText(address);
    }

    public void updatePosition(String position) {
        if (tvPosition != null) tvPosition.setText(position);
    }
}
