package com.example.portjeep.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.portjeep.R;
import com.example.portjeep.data.model.ScheduleItem;

import java.util.ArrayList;
import java.util.List;

public class SchedulePageFragment extends Fragment {

    private RecyclerView rvList;
    private LinearLayout layoutEmpty;
    private TextView tvEmpty;
    private ScheduleAdapter adapter;
    private final List<ScheduleItem> itemList = new ArrayList<>();
    private ScheduleViewModel viewModel;
    private int position = 0;

    public static SchedulePageFragment newInstance(int position) {
        SchedulePageFragment fragment = new SchedulePageFragment();
        Bundle args = new Bundle();
        args.putInt("position", position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            position = getArguments().getInt("position");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule_page, container, false);

        rvList = view.findViewById(R.id.rv_page_schedule_list);
        layoutEmpty = view.findViewById(R.id.layout_page_empty_state);
        tvEmpty = view.findViewById(R.id.tv_page_empty_state);

        rvList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ScheduleAdapter(itemList);
        rvList.setAdapter(adapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Use the parent fragment as the ViewModelStoreOwner to share the same ViewModel instance
        if (getParentFragment() != null) {
            viewModel = new ViewModelProvider(getParentFragment()).get(ScheduleViewModel.class);
            setupObservers();
        }
    }

    private void setupObservers() {
        if (position == 0) {
            viewModel.getTodayList().observe(getViewLifecycleOwner(), this::updateData);
        } else if (position == 1) {
            viewModel.getUpcomingList().observe(getViewLifecycleOwner(), this::updateData);
        } else {
            viewModel.getPreviousList().observe(getViewLifecycleOwner(), this::updateData);
        }
    }

    public void updateData(List<ScheduleItem> newData) {
        itemList.clear();
        if (newData != null) {
            itemList.addAll(newData);
        }
        if (adapter != null) {
            adapter.updateList(itemList);
        }
        updateVisibility();
    }

    private void updateVisibility() {
        if (rvList == null || layoutEmpty == null) return;
        if (itemList.isEmpty()) {
            if (tvEmpty != null) {
                if (position == 0) {
                    tvEmpty.setText("No assigned trips for today.");
                } else if (position == 1) {
                    tvEmpty.setText("No upcoming trips scheduled.");
                } else {
                    tvEmpty.setText("No previous trip history found.");
                }
            }
            rvList.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvList.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
    }
}