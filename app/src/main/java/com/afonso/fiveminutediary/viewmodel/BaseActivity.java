package com.afonso.fiveminutediary.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.util.LocaleManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Base activity that applies the selected language to all child activities
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply the saved language before the activity is created
        super.attachBaseContext(LocaleManager.applyLanguage(newBase));
    }

    /**
     * Setup bottom navigation with animations and proper navigation flow
     * Call this method in onCreate() after setContentView()
     */
    protected void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav == null) return;

        // Set the correct selected item based on current activity
        bottomNav.setSelectedItemId(getNavigationMenuItemId());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Haptic feedback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottomNav.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            } else {
                bottomNav.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }

            // Animate the clicked item
            animateNavItem(bottomNav, itemId);

            // Navigate to the selected activity
            return handleNavigation(itemId);
        });
    }

    /**
     * Animate the navigation item on click
     */
    private void animateNavItem(BottomNavigationView bottomNav, int itemId) {
        View view = bottomNav.findViewById(itemId);
        if (view != null) {
            view.animate()
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        view.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start();
                    })
                    .start();
        }
    }

    /**
     * Handle navigation between activities
     */
    private boolean handleNavigation(int itemId) {
        if (itemId == R.id.nav_home) {
            if (!(this instanceof MainActivity)) {
                navigateToActivity(MainActivity.class);
            }
            return true;
        } else if (itemId == R.id.nav_history) {
            if (!(this instanceof ListActivity)) {
                navigateToActivity(ListActivity.class);
            }
            return true;
        } else if (itemId == R.id.nav_profile) {
            if (!(this instanceof ProfileActivity)) {
                navigateToActivity(ProfileActivity.class);
            }
            return true;
        }
        return false;
    }

    /**
     * Navigate to a specific activity with smooth transition
     */
    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /**
     * Update the selected item in onResume to ensure correct state
     */
    protected void updateBottomNavSelection() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(getNavigationMenuItemId());
        }
    }

    /**
     * Abstract method - each activity must return its corresponding menu item ID
     */
    protected abstract int getNavigationMenuItemId();
}