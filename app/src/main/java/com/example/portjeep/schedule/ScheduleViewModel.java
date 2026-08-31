package com.example.portjeep.schedule;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.portjeep.data.model.ScheduleItem;

import java.util.ArrayList;
import java.util.List;

public class ScheduleViewModel extends ViewModel {
    private final MutableLiveData<List<ScheduleItem>> todayList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScheduleItem>> upcomingList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ScheduleItem>> previousList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> activeTab = new MutableLiveData<>(0);

    public LiveData<List<ScheduleItem>> getTodayList() { return todayList; }
    public LiveData<List<ScheduleItem>> getUpcomingList() { return upcomingList; }
    public LiveData<List<ScheduleItem>> getPreviousList() { return previousList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<Integer> getActiveTab() { return activeTab; }

    public void setSchedules(List<ScheduleItem> today, List<ScheduleItem> upcoming, List<ScheduleItem> previous) {
        todayList.setValue(new ArrayList<>(today));
        upcomingList.setValue(new ArrayList<>(upcoming));
        previousList.setValue(new ArrayList<>(previous));
    }

    public void setLoading(boolean loading) {
        isLoading.setValue(loading);
    }

    public void setActiveTab(int tab) {
        activeTab.setValue(tab);
    }

    public boolean hasData() {
        List<ScheduleItem> t = todayList.getValue();
        List<ScheduleItem> u = upcomingList.getValue();
        List<ScheduleItem> p = previousList.getValue();
        return (t != null && !t.isEmpty()) || (u != null && !u.isEmpty()) || (p != null && !p.isEmpty());
    }

    public void notifyDataChanged() {
        todayList.setValue(todayList.getValue());
        upcomingList.setValue(upcomingList.getValue());
        previousList.setValue(previousList.getValue());
    }
}