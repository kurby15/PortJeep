package com.example.portjeep.salary;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.portjeep.R;
import com.example.portjeep.utils.PreferenceManager;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SalaryFragment extends Fragment {

    private static final String TAG = "SalaryFragment";
    private static final String API_URL = "https://port-jeep.vercel.app/api/mobile/remittances";

    private SwipeRefreshLayout swipeRefreshLayout;
    private ShimmerFrameLayout shimmerSalary;
    private ViewPager2 vpSalaryContent;

    // Tab Views
    private FrameLayout btnSummary, btnHistory;
    private View viewSummaryIndicator, viewHistoryIndicator;
    private TextView tvSummaryLabel, tvHistoryLabel;
    private TextView tvSalaryDate;

    private SalaryPagerAdapter adapter;
    private FirebaseAuth mAuth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final long CACHE_DURATION = 60 * 60 * 1000; // 1 hour cache
    private static boolean sessionRefreshed = false;

    public SalaryFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_salary, container, false);

        mAuth = FirebaseAuth.getInstance();

        // Bind Views
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_salary);
        shimmerSalary = view.findViewById(R.id.shimmer_salary);
        vpSalaryContent = view.findViewById(R.id.vp_salary_content);

        tvSalaryDate = view.findViewById(R.id.tv_salary_date);
        btnSummary = view.findViewById(R.id.btn_summary);
        btnHistory = view.findViewById(R.id.btn_history);
        viewSummaryIndicator = view.findViewById(R.id.view_summary_indicator);
        viewHistoryIndicator = view.findViewById(R.id.view_history_indicator);
        tvSummaryLabel = view.findViewById(R.id.tv_summary_label);
        tvHistoryLabel = view.findViewById(R.id.tv_history_label);

        setupDate();
        setupViewPager();
        setupTabClickListeners();

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setColorSchemeResources(R.color.color_brand_primary, R.color.color_brand_accent);
            swipeRefreshLayout.setOnRefreshListener(this::loadRemittanceData);
        }

        // Offline support and Read optimization
        loadCachedDataOrFetch();

        return view;
    }

    private void loadCachedDataOrFetch() {
        String cachedJson = PreferenceManager.getSalaryCache(getContext());
        long lastFetch = PreferenceManager.getSalaryLastFetchTime(getContext());
        long now = System.currentTimeMillis();

        if (cachedJson != null) {
            try {
                JSONArray remittances = new JSONArray(cachedJson);
                if (adapter != null) {
                    adapter.setRemittanceData(remittances);
                }
                
                // Use cache if fresh AND we have already refreshed once this session
                if (now - lastFetch < CACHE_DURATION && sessionRefreshed) {
                    hideLoadingSkeleton();
                    return; 
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading cache", e);
            }
        }

        // Fetch if no cache, expired, or first time in session
        sessionRefreshed = true;
        showLoadingSkeleton();
        loadRemittanceData();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        swipeRefreshLayout = null;
        shimmerSalary = null;
        vpSalaryContent = null;
        btnSummary = null;
        btnHistory = null;
        viewSummaryIndicator = null;
        viewHistoryIndicator = null;
        tvSummaryLabel = null;
        tvHistoryLabel = null;
        tvSalaryDate = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void setupDate() {
        if (tvSalaryDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US);
            tvSalaryDate.setText(sdf.format(Calendar.getInstance().getTime()));
        }
    }

    private void setupViewPager() {
        if (vpSalaryContent == null) return;

        adapter = new SalaryPagerAdapter();
        vpSalaryContent.setAdapter(adapter);

        vpSalaryContent.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTabUI(position);
            }
        });
    }

    private void setupTabClickListeners() {
        if (btnSummary != null) {
            btnSummary.setOnClickListener(v -> vpSalaryContent.setCurrentItem(0, true));
        }
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> vpSalaryContent.setCurrentItem(1, true));
        }
    }

    private void updateTabUI(int position) {
        if (!isAdded()) return;
        int colorPrimary = ContextCompat.getColor(requireContext(), R.color.color_brand_primary);
        int colorWhite = ContextCompat.getColor(requireContext(), R.color.white);

        if (position == 0) {
            viewSummaryIndicator.setVisibility(View.VISIBLE);
            tvSummaryLabel.setTextColor(colorPrimary);
            viewHistoryIndicator.setVisibility(View.GONE);
            tvHistoryLabel.setTextColor(colorWhite);
        } else {
            viewHistoryIndicator.setVisibility(View.VISIBLE);
            tvHistoryLabel.setTextColor(colorPrimary);
            viewSummaryIndicator.setVisibility(View.GONE);
            tvSummaryLabel.setTextColor(colorWhite);
        }
    }

    private void loadRemittanceData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            hideLoadingSkeleton();
            return;
        }

        currentUser.getIdToken(false)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) return;
                    fetchRemittancesFromApi(result.getToken());
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Authentication failed.", Toast.LENGTH_SHORT).show();
                        hideLoadingSkeleton();
                    }
                });
    }

    private void fetchRemittancesFromApi(String idToken) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + idToken);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                InputStream inputStream = (responseCode == HttpURLConnection.HTTP_OK) ? connection.getInputStream() : connection.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseStr = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) responseStr.append(line);
                reader.close();

                String rawResult = responseStr.toString();
                handler.post(() -> {
                    if (!isAdded()) return;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        parseAndSaveData(rawResult);
                    } else {
                        handleErrorResponse(responseCode, rawResult);
                    }
                    hideLoadingSkeleton();
                });
            } catch (Exception e) {
                Log.e(TAG, "Network error", e);
                handler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Offline: Showing latest cached data.", Toast.LENGTH_SHORT).show();
                        hideLoadingSkeleton();
                    }
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void handleErrorResponse(int responseCode, String rawResult) {
        String errorMsg = "Server error (" + responseCode + ")";
        try {
            JSONObject errJson = new JSONObject(rawResult);
            if (errJson.has("message")) errorMsg = errJson.getString("message");
        } catch (Exception ignored) {}
        Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
    }

    private void parseAndSaveData(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("success", false)) {
                JSONArray remittances = root.optJSONArray("remittances");
                if (remittances != null && adapter != null) {
                    // Save to persistent storage for offline and optimization
                    PreferenceManager.saveSalaryCache(getContext(), remittances.toString());
                    adapter.setRemittanceData(remittances);
                }
            } else {
                Toast.makeText(getContext(), root.optString("message", "Failed to update."), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Parsing error", e);
        }
    }

    private void showLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.startShimmer();
            shimmerSalary.setVisibility(View.VISIBLE);
        }
        if (vpSalaryContent != null) {
            vpSalaryContent.setVisibility(View.GONE);
        }
    }

    private void hideLoadingSkeleton() {
        if (shimmerSalary != null) {
            shimmerSalary.stopShimmer();
            shimmerSalary.setVisibility(View.GONE);
        }
        if (vpSalaryContent != null) {
            vpSalaryContent.setVisibility(View.VISIBLE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}