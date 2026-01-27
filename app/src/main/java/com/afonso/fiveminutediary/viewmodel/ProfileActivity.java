package com.afonso.fiveminutediary.viewmodel;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.afonso.fiveminutediary.data.UserProfile;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ProfileActivity extends AppCompatActivity {

    private DataRepository repo;
    private UserProfile userProfile;

    private EditText nameInput;
    private TextView journeyDaysText;
    private TextView totalEntriesText;
    private TextView streakText;
    private CardView premiumStatsCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        repo = DataRepository.getInstance(this);

        initViews();
        loadOrCreateProfile();
        loadProfileData();
        setupBottomNavigation();
        setupPremiumCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileData();

        // Update last opened timestamp
        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfile(userProfile);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }

    private void initViews() {
        nameInput = findViewById(R.id.nameInput);
        journeyDaysText = findViewById(R.id.journeyDaysText);
        totalEntriesText = findViewById(R.id.totalEntriesText);
        streakText = findViewById(R.id.streakText);
        premiumStatsCard = findViewById(R.id.premiumStatsCard);

        // Save name on focus change
        nameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveName();
            }
        });
    }

    private void loadOrCreateProfile() {
        // Use repository method to get or create profile
        userProfile = repo.getOrCreateUserProfile();

        // Load name
        if (userProfile.getUserName() != null && !userProfile.getUserName().isEmpty()) {
            nameInput.setText(userProfile.getUserName());
        }
    }

    private void loadProfileData() {
        if (userProfile == null) return;

        // Journey days
        long daysSinceFirstUse = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - userProfile.getFirstUseTimestamp()
        );

        if (daysSinceFirstUse == 0) {
            journeyDaysText.setText("Hoje");
        } else if (daysSinceFirstUse == 1) {
            journeyDaysText.setText("Há 1 dia");
        } else {
            journeyDaysText.setText("Há " + daysSinceFirstUse + " dias");
        }

        // Total entries
        int totalEntries = repo.getEntryCount();
        totalEntriesText.setText(String.valueOf(totalEntries));

        // Streak
        int currentStreak = calculateStreak();
        streakText.setText(String.valueOf(currentStreak));
    }

    private int calculateStreak() {
        List<DiaryEntry> allEntries = repo.getEntries();
        if (allEntries.isEmpty()) {
            return 0;
        }

        // Sort by date (already sorted DESC from DB)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long todayStart = calendar.getTimeInMillis();

        // Check if we have an entry for today
        boolean hasToday = false;
        for (DiaryEntry entry : allEntries) {
            if (entry.getTimestamp() >= todayStart) {
                hasToday = true;
                break;
            }
        }

        // Start counting streak from today or yesterday
        if (!hasToday) {
            // If no entry today, start from yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }

        int streak = 0;
        long currentDayStart = calendar.getTimeInMillis();

        // Go backwards checking consecutive days
        for (int i = 0; i < 365; i++) { // Max 1 year streak
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long dayStart = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, -1);

            // Check if we have an entry for this day
            boolean foundEntryForDay = false;
            for (DiaryEntry entry : allEntries) {
                if (entry.getTimestamp() >= dayStart && entry.getTimestamp() < dayEnd) {
                    foundEntryForDay = true;
                    break;
                }
            }

            if (foundEntryForDay) {
                streak++;
                calendar.add(Calendar.DAY_OF_YEAR, -1); // Move to previous day
            } else {
                break; // Streak broken
            }
        }

        return streak;
    }

    private void saveName() {
        String name = nameInput.getText().toString().trim();
        if (userProfile != null) {
            userProfile.setUserName(name);
            repo.updateUserProfile(userProfile);
        }
    }

    private void setupPremiumCard() {
        premiumStatsCard.setOnClickListener(v -> {
            // TODO: Future premium feature
            Toast.makeText(this, "Funcionalidade premium - Em breve!", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_profile);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(this, ListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveName();

        // Update last opened
        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfile(userProfile);
        }
    }
}