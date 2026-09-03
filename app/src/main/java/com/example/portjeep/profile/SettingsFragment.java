package com.example.portjeep.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.portjeep.MainActivity;
import com.example.portjeep.R;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "portjeep_settings";
    private static final String KEY_SCREEN_LOCK = "screen_lock_enabled";

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Back button
        ImageView btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        // Screen Lock Switch
        MaterialSwitch switchScreenLock = view.findViewById(R.id.switch_screen_lock);
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        if (switchScreenLock != null) {
            switchScreenLock.setChecked(prefs.getBoolean(KEY_SCREEN_LOCK, false));
            switchScreenLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEY_SCREEN_LOCK, isChecked).apply();
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

        return view;
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
