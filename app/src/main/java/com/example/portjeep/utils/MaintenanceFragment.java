package com.example.portjeep.utils;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.portjeep.R;
import com.google.android.material.button.MaterialButton;

public class MaintenanceFragment extends Fragment {

    private OnBackListener backListener;

    public interface OnBackListener {
        void onBack();
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_maintenance, container, false);

        MaterialButton btnBack = view.findViewById(R.id.btn_back_maintenance);
        btnBack.setOnClickListener(v -> {
            if (backListener != null) {
                backListener.onBack();
            }
        });

        return view;
    }
}
