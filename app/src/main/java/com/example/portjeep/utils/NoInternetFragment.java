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

public class NoInternetFragment extends Fragment {

    private OnRetryListener retryListener;

    public interface OnRetryListener {
        void onRetry();
    }

    public void setOnRetryListener(OnRetryListener listener) {
        this.retryListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Removed the internal RenderEffect code so that text and buttons stay sharp.
        // The "blurred transparent" look is achieved by the parent blurring the layout underneath.
        View view = inflater.inflate(R.layout.fragment_no_internet, container, false);

        MaterialButton btnRetry = view.findViewById(R.id.btn_retry);
        btnRetry.setOnClickListener(v -> {
            if (retryListener != null) {
                retryListener.onRetry();
            }
        });

        return view;
    }
}
