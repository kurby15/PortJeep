package com.example.portjeep;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private MaterialCardView navCardContainer;
    private long backPressedTime = 0;
    private Toast backToast;

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

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), false);
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_schedule) {
                selectedFragment = new ScheduleFragment();
            } else if (itemId == R.id.nav_salary) {
                selectedFragment = new SalaryFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment, true);
                return true;
            }
            return false;
        });

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null && bottomNavigationView != null) {
                if (currentFragment instanceof HomeFragment) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
                } else if (currentFragment instanceof ScheduleFragment) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_schedule).setChecked(true);
                } else if (currentFragment instanceof SalaryFragment) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_salary).setChecked(true);
                } else if (currentFragment instanceof ProfileFragment) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_profile).setChecked(true);
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
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
            }
        });
    }

    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        FragmentManager fm = getSupportFragmentManager();

        Fragment currentFragment = fm.findFragmentById(R.id.fragment_container);
        if (currentFragment != null && currentFragment.getClass().equals(fragment.getClass())) {
            return;
        }

        String tag = fragment.getClass().getName();
        FragmentTransaction transaction = fm.beginTransaction().replace(R.id.fragment_container, fragment, tag);

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