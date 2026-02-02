package com.afonso.fiveminutediary.viewmodel;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.afonso.fiveminutediary.data.TextFormattingSerializer;
import com.afonso.fiveminutediary.utils.ZenToast;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends BaseActivity {

    private static final int REQUEST_EXPANDED_EDIT = 100;
    private static final long AUTO_SAVE_DELAY = 2000; // 2 seconds

    private DataRepository repo;
    private EditText entryInput;
    private TextView dayOfWeek;
    private TextView dateText;
    private TextView currentTime;
    private TextView wordCounter;
    private TextView motivationalText;
    private TextView dailyQuote;
    private ProgressBar wordProgressBar;
    private CardView todayCard;
    private ImageButton expandButton;

    private DiaryEntry todaysEntry;
    private boolean hadEntryToday = false;
    private boolean isSaving = false;

    private Handler timeHandler;
    private Runnable timeRunnable;
    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;

    private String[] motivationalTexts;
    private String[] dailyQuotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check authentication
        if (!checkAuthentication()) {
            return;
        }

        setContentView(R.layout.activity_main);

        repo = DataRepository.getInstance(this);
        autoSaveHandler = new Handler(Looper.getMainLooper());

        motivationalTexts = getResources().getStringArray(R.array.motivational_texts);
        dailyQuotes = getResources().getStringArray(R.array.daily_quotes);

        initViews();
        loadTodayEntry();
        setupListeners();
        setupBottomNavigation(); // Chama o método da superclasse!
        setRandomTexts();
        startClock();
        animateEntrance();
        setupAutoSave();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!checkAuthentication()) return;

        loadTodayEntry();
        startClock();
        updateBottomNavSelection(); // Atualiza a seleção
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_home;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (entryInput != null) {
            entryInput.clearFocus();
        }

        stopClock();
        saveCurrentEntry();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
    }

    private boolean checkAuthentication() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return false;
        }
        return true;
    }

    private void initViews() {
        entryInput = findViewById(R.id.entryInput);
        dayOfWeek = findViewById(R.id.dayOfWeek);
        dateText = findViewById(R.id.dateText);
        currentTime = findViewById(R.id.currentTime);
        wordCounter = findViewById(R.id.wordCounter);
        motivationalText = findViewById(R.id.motivationalText);
        dailyQuote = findViewById(R.id.dailyQuote);
        wordProgressBar = findViewById(R.id.wordProgressBar);
        todayCard = findViewById(R.id.todayCard);
        expandButton = findViewById(R.id.expandButton);

        // Use string resources for day and month names
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);

        // Get day of week from string array
        String[] dayNames = getResources().getStringArray(R.array.day_names);
        int dayOfWeekIndex = cal.get(Calendar.DAY_OF_WEEK) - 1; // Sunday = 0
        dayOfWeek.setText(capitalize(dayNames[dayOfWeekIndex]));

        // Get month from string array
        String[] monthNames = getResources().getStringArray(R.array.month_names);
        int monthIndex = cal.get(Calendar.MONTH);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        dateText.setText(dayOfMonth + " " + capitalize(monthNames[monthIndex]));

        entryInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWordCount(s.toString());
                scheduleAutoSave();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupAutoSave() {
        autoSaveRunnable = this::saveCurrentEntry;
    }

    private void scheduleAutoSave() {
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
            autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY);
        }
    }

    private void saveCurrentEntry() {
        String text = entryInput.getText().toString().trim();
        if (text.isEmpty() || isSaving) {
            return;
        }

        isSaving = true;
        boolean isFirstEntry = !hadEntryToday;

        // Extract formatting
        CharSequence formattedText = entryInput.getText();
        String formatting = TextFormattingSerializer.serializeFormatting(formattedText);

        repo.saveOrUpdateTodayEntry(text, formatting, task -> {
            runOnUiThread(() -> {
                isSaving = false;
                if (isFirstEntry) {
                    hadEntryToday = true;
                    expandButton.setVisibility(android.view.View.VISIBLE);
                }
            });
        });
    }

    private void startClock() {
        if (timeHandler == null) {
            timeHandler = new Handler(Looper.getMainLooper());
        }

        timeRunnable = new Runnable() {
            @Override
            public void run() {
                // Use 24-hour format (HH:mm) which is language-independent
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                currentTime.setText(timeFormat.format(new Date()));
                timeHandler.postDelayed(this, 1000);
            }
        };

        timeHandler.post(timeRunnable);
    }

    private void stopClock() {
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
    }

    private void updateWordCount(String text) {
        if (text.trim().isEmpty()) {
            wordCounter.setText(R.string.word_counter_zero);
            wordProgressBar.setProgress(0);
        } else {
            int words = text.trim().split("\\s+").length;
            wordCounter.setText(getResources().getQuantityString(R.plurals.word_count, words, words));
            int progress = Math.min((words * 100) / 50, 100);
            wordProgressBar.setProgress(progress);
        }
    }

    private void loadTodayEntry() {
        // Use Locale.getDefault() for date format (language-independent format)
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        repo.getEntryForDay(today, entry -> {
            runOnUiThread(() -> {
                todaysEntry = entry;
                if (todaysEntry != null) {
                    // Load text with formatting
                    String formatting = todaysEntry.getFormatting();
                    if (formatting != null && !formatting.isEmpty()) {
                        android.text.SpannableString formatted =
                                TextFormattingSerializer.deserializeFormatting(
                                        todaysEntry.getText(), formatting
                                );
                        entryInput.setText(formatted);
                    } else {
                        entryInput.setText(todaysEntry.getText());
                    }
                    expandButton.setVisibility(android.view.View.VISIBLE);
                    hadEntryToday = true;
                } else {
                    entryInput.setText("");
                    expandButton.setVisibility(android.view.View.GONE);
                    hadEntryToday = false;
                }
            });
        });
    }

    private void setRandomTexts() {
        Random random = new Random();
        motivationalText.setText(motivationalTexts[random.nextInt(motivationalTexts.length)]);
        dailyQuote.setText(dailyQuotes[random.nextInt(dailyQuotes.length)]);
    }

    private void animateEntrance() {
        todayCard.setAlpha(0f);
        todayCard.animate()
                .alpha(1f)
                .setDuration(500)
                .start();
    }

    private void setupListeners() {
        Button saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                    })
                    .start();
            saveAndShowConfirmation();
        });

        expandButton.setOnClickListener(v -> openExpandedEdit());
    }

    private void saveAndShowConfirmation() {
        String text = entryInput.getText().toString().trim();

        if (text.isEmpty()) {
            ZenToast.show(this, getString(R.string.try_writing_something), Gravity.BOTTOM, false);
            return;
        }

        boolean isFirstEntry = !hadEntryToday;

        CharSequence formattedText = entryInput.getText();
        String formatting = TextFormattingSerializer.serializeFormatting(formattedText);

        repo.saveOrUpdateTodayEntry(text, formatting, task -> {
            runOnUiThread(() -> {
                if (isFirstEntry) {
                    repo.calculateStreak(streak -> {
                        runOnUiThread(() -> {
                            ZenToast.showStreakIncrease(this, streak);
                        });
                    });
                } else {
                    ZenToast.show(this, getString(R.string.saved_toast), Gravity.BOTTOM, false);
                }
                loadTodayEntry();
            });
        });
    }

    private void openExpandedEdit() {
        // Save current state first
        saveCurrentEntry();

        Intent intent = new Intent(this, ExpandedEditActivity.class);
        intent.putExtra("text", entryInput.getText().toString());

        CharSequence formattedText = entryInput.getText();
        String formatting = TextFormattingSerializer.serializeFormatting(formattedText);
        intent.putExtra("formatting", formatting);

        startActivityForResult(intent, REQUEST_EXPANDED_EDIT);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EXPANDED_EDIT && resultCode == RESULT_OK && data != null) {
            String editedText = data.getStringExtra("edited_text");
            String formatting = data.getStringExtra("formatting");

            if (editedText != null) {
                // Apply text with formatting
                if (formatting != null && !formatting.isEmpty()) {
                    android.text.SpannableString formatted =
                            TextFormattingSerializer.deserializeFormatting(editedText, formatting);
                    entryInput.setText(formatted);
                } else {
                    entryInput.setText(editedText);
                }

                // Save immediately
                saveCurrentEntry();
            }
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}