package com.example.portjeep;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.portjeep.home.HomeFragment;
import com.example.portjeep.notifications.ScheduleAlarmReceiver;
import com.example.portjeep.profile.ProfileFragment;
import com.example.portjeep.salary.SalaryFragment;
import com.example.portjeep.schedule.ScheduleFragment;
import com.example.portjeep.utils.NotificationHelper;
import com.example.portjeep.utils.PreferenceManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private MaterialCardView navCardContainer;
    private long backPressedTime = 0;
    private Toast backToast;

    private final ArrayList<Integer> tabHistory = new ArrayList<>();
    private static final int STANDARD_3_BUTTON_NAV_HEIGHT_DP = 48;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    updateFcmToken();
                    ScheduleAlarmReceiver.scheduleDailyAlarms(this);
                } else {
                    Toast.makeText(this, getString(R.string.notif_permission_denied), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        navCardContainer = findViewById(R.id.nav_card_container);

        if (bottomNavigationView != null) {
            bottomNavigationView.setItemActiveIndicatorEnabled(false);
        }

        NotificationHelper.createNotificationChannel(this);
        askNotificationPermission();
        ScheduleAlarmReceiver.scheduleDailyAlarms(this);

        if (navCardContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navCardContainer, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                float density = getResources().getDisplayMetrics().density;
                int baseMarginPx = (int) (16 * density);
                int standard3ButtonNavPx = (int) (STANDARD_3_BUTTON_NAV_HEIGHT_DP * density);
                int navBarHeight = Math.max(insets.bottom, standard3ButtonNavPx);
                params.bottomMargin = navBarHeight + baseMarginPx;
                v.setLayoutParams(params);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        if (bottomNavigationView != null) {
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (tabHistory.isEmpty() || tabHistory.get(tabHistory.size() - 1) != itemId) {
                    tabHistory.remove(Integer.valueOf(itemId));
                    tabHistory.add(itemId);
                }
                FragmentManager fm = getSupportFragmentManager();
                String tag = getFragmentTag(itemId);
                Fragment selectedFragment = fm.findFragmentByTag(tag);
                if (selectedFragment == null) {
                    selectedFragment = createFragment(itemId);
                }
                if (selectedFragment != null) {
                    loadFragment(selectedFragment, false);
                    return true;
                }
                return false;
            });
        }

        if (savedInstanceState != null) {
            ArrayList<Integer> savedHistory = savedInstanceState.getIntegerArrayList("tab_history");
            if (savedHistory != null && !savedHistory.isEmpty()) {
                tabHistory.addAll(savedHistory);
                int lastTabId = tabHistory.get(tabHistory.size() - 1);
                if (bottomNavigationView != null) {
                    bottomNavigationView.setSelectedItemId(lastTabId);
                }
            }
        } else {
            tabHistory.add(R.id.nav_home);
            loadFragment(new HomeFragment(), false);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getVisibleFragment();
            if (currentFragment != null && bottomNavigationView != null) {
                updateBottomNavSelection(currentFragment);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return;
                }
                if (tabHistory.size() > 1) {
                    tabHistory.remove(tabHistory.size() - 1);
                    int previousTabId = tabHistory.get(tabHistory.size() - 1);
                    if (bottomNavigationView != null) {
                        bottomNavigationView.setSelectedItemId(previousTabId);
                    }
                    return;
                }
                Fragment currentFragment = getVisibleFragment();
                if (!(currentFragment instanceof HomeFragment)) {
                    tabHistory.clear();
                    tabHistory.add(R.id.nav_home);
                    if (bottomNavigationView != null) {
                        bottomNavigationView.setSelectedItemId(R.id.nav_home);
                    }
                    return;
                }
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) backToast.cancel();
                    finish();
                } else {
                    backToast = Toast.makeText(MainActivity.this, "Tap again to exit", Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED) {
                updateFcmToken();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            updateFcmToken();
        }
    }

    private void updateFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();
            String savedToken = PreferenceManager.getFcmToken(this);
            if (token != null && !token.equals(savedToken)) {
                PreferenceManager.saveFcmToken(this, token);
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    FirebaseFirestore.getInstance().collection("File201").document(user.getUid())
                            .update("fcmToken", token);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntegerArrayList("tab_history", tabHistory);
    }

    private String getFragmentTag(int itemId) {
        if (itemId == R.id.nav_home) return HomeFragment.class.getName();
        if (itemId == R.id.nav_schedule) return ScheduleFragment.class.getName();
        if (itemId == R.id.nav_salary) return SalaryFragment.class.getName();
        if (itemId == R.id.nav_profile) return ProfileFragment.class.getName();
        return null;
    }

    private Fragment createFragment(int itemId) {
        if (itemId == R.id.nav_home) return new HomeFragment();
        if (itemId == R.id.nav_schedule) return new ScheduleFragment();
        if (itemId == R.id.nav_salary) return new SalaryFragment();
        if (itemId == R.id.nav_profile) return new ProfileFragment();
        return null;
    }

    private Fragment getVisibleFragment() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f != null && f.isVisible()) return f;
        }
        return null;
    }

    private void updateBottomNavSelection(Fragment currentFragment) {
        int id = -1;
        if (currentFragment instanceof HomeFragment) id = R.id.nav_home;
        else if (currentFragment instanceof ScheduleFragment) id = R.id.nav_schedule;
        else if (currentFragment instanceof SalaryFragment) id = R.id.nav_salary;
        else if (currentFragment instanceof ProfileFragment) id = R.id.nav_profile;
        if (id != -1 && bottomNavigationView != null) bottomNavigationView.getMenu().findItem(id).setChecked(true);
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = fragment.getClass().getName();
        Fragment existingFragment = fm.findFragmentByTag(tag);
        Fragment activeFragment = getVisibleFragment();
        if (activeFragment != null && activeFragment.getClass().equals(fragment.getClass())) return;
        FragmentTransaction transaction = fm.beginTransaction();
        if (activeFragment != null) transaction.hide(activeFragment);
        if (existingFragment == null) transaction.add(R.id.fragment_container, fragment, tag);
        else transaction.show(existingFragment);
        if (addToBackStack) transaction.addToBackStack(tag);
        transaction.commit();
    }

    public void setBottomNavVisibility(int visibility) {
        View targetView = navCardContainer != null ? navCardContainer : bottomNavigationView;
        if (targetView == null) return;

        if (visibility == View.VISIBLE) {
            targetView.setVisibility(View.VISIBLE);
            targetView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null)
                    .start();
        } else {
            // Slide down out of screen and fade out
            float translationTarget = targetView.getHeight() + 100f;
            targetView.animate()
                    .translationY(translationTarget)
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            targetView.setVisibility(visibility);
                        }
                    })
                    .start();
        }
    }
}