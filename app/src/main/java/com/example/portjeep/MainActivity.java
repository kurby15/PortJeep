package com.example.portjeep;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.portjeep.home.HomeFragment;
import com.example.portjeep.profile.ProfileFragment;
import com.example.portjeep.salary.SalaryFragment;
import com.example.portjeep.schedule.ScheduleFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private MaterialCardView navCardContainer;
    private long backPressedTime = 0;
    private Toast backToast;

    // Track tab navigation history to provide a professional "Back" experience
    private final ArrayList<Integer> tabHistory = new ArrayList<>();

    // Fixed offset height reference based on standard 3-button nav height (~48dp)
    private static final int STANDARD_3_BUTTON_NAV_HEIGHT_DP = 48;

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

        // Lock bottom margin to match 3-Button Navigation height under all navigation modes
        if (navCardContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navCardContainer, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());

                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();

                float density = getResources().getDisplayMetrics().density;
                int baseMarginPx = (int) (16 * density);
                int standard3ButtonNavPx = (int) (STANDARD_3_BUTTON_NAV_HEIGHT_DP * density);

                // Use the greater value (either active 3-Button height or standard fallback)
                // so Gesture Navigation shifts up to match 3-Button Navigation height
                int navBarHeight = Math.max(insets.bottom, standard3ButtonNavPx);

                params.bottomMargin = navBarHeight + baseMarginPx;
                v.setLayoutParams(params);

                return WindowInsetsCompat.CONSUMED;
            });
        }

        // Setup listener first so setSelectedItemId triggers it during restoration
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Manage history: avoid duplicates and ensure back button follows usage flow
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

        // Restore navigation history and state if activity was recreated (e.g., theme change)
        if (savedInstanceState != null) {
            ArrayList<Integer> savedHistory = savedInstanceState.getIntegerArrayList("tab_history");
            if (savedHistory != null && !savedHistory.isEmpty()) {
                tabHistory.addAll(savedHistory);
                // Reselect the last active tab to ensure UI is in sync
                int lastTabId = tabHistory.get(tabHistory.size() - 1);
                bottomNavigationView.setSelectedItemId(lastTabId);
            }
        } else {
            // First launch
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
                
                // 1. If there's a backstack (deep navigation inside a tab), pop it
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return;
                }

                // 2. Tab History Navigation: Go back through the tabs visited
                if (tabHistory.size() > 1) {
                    tabHistory.remove(tabHistory.size() - 1); // Remove current tab from history
                    int previousTabId = tabHistory.get(tabHistory.size() - 1);
                    
                    bottomNavigationView.setSelectedItemId(previousTabId);
                    return;
                }

                // 3. Final step: always ensure we land on Home before exiting
                Fragment currentFragment = getVisibleFragment();
                if (!(currentFragment instanceof HomeFragment)) {
                    tabHistory.clear();
                    tabHistory.add(R.id.nav_home);
                    bottomNavigationView.setSelectedItemId(R.id.nav_home);
                    return;
                }

                // 4. Exit logic - ONLY triggered on the Home tab
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) {
                        backToast.cancel();
                    }
                    finish();
                } else {
                    backToast = Toast.makeText(MainActivity.this, "Tap again to exit", Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save history list so it survives activity recreation
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
            if (f != null && f.isVisible()) {
                return f;
            }
        }
        return null;
    }

    private void updateBottomNavSelection(Fragment currentFragment) {
        int id = -1;
        if (currentFragment instanceof HomeFragment) id = R.id.nav_home;
        else if (currentFragment instanceof ScheduleFragment) id = R.id.nav_schedule;
        else if (currentFragment instanceof SalaryFragment) id = R.id.nav_salary;
        else if (currentFragment instanceof ProfileFragment) id = R.id.nav_profile;
        
        if (id != -1) {
            bottomNavigationView.getMenu().findItem(id).setChecked(true);
        }
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        String tag = fragment.getClass().getName();
        Fragment existingFragment = fm.findFragmentByTag(tag);

        // Find the currently visible fragment to hide it
        Fragment activeFragment = getVisibleFragment();

        // If the fragment we want to load is already visible, do nothing
        if (activeFragment != null && activeFragment.getClass().equals(fragment.getClass())) {
            return;
        }

        FragmentTransaction transaction = fm.beginTransaction();
        
        // Hide the current active fragment
        if (activeFragment != null) {
            transaction.hide(activeFragment);
        }

        if (existingFragment == null) {
            // If the fragment hasn't been added yet, add it
            transaction.add(R.id.fragment_container, fragment, tag);
        } else {
            // If it already exists, just show it (preserving its state)
            transaction.show(existingFragment);
        }

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }
        transaction.commit();
    }

    public void setBottomNavVisibility(int visibility) {
        if (navCardContainer != null) {
            navCardContainer.setVisibility(visibility);
        } else if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(visibility);
        }
    }
}
