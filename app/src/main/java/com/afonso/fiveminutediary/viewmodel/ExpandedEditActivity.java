package com.afonso.fiveminutediary.viewmodel;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.TextFormattingSerializer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;

public class ExpandedEditActivity extends BaseActivity {

    private static final String TAG = "ExpandedEditActivity";
    private static final long AUTO_SAVE_DELAY = 2000;

    private EditText expandedInput;
    private CardView rootCard;
    private CardView formattingPanel;
    private ImageButton closeButton;
    private ImageButton undoButton;
    private ImageButton redoButton;
    private ImageButton toggleFormattingButton;
    private TextView charCountText;

    private ImageButton boldButton;
    private ImageButton italicButton;
    private ImageButton underlineButton;
    private ImageButton highlightYellowButton;
    private ImageButton highlightGreenButton;
    private ImageButton highlightPinkButton;
    private ImageButton highlightBlueButton;
    private ImageButton colorRedButton;
    private ImageButton colorBlueButton;
    private ImageButton colorGreenButton;

    private DataRepository repo;
    private String originalText;
    private String currentFormatting;

    private Stack<EditorState> undoStack = new Stack<>();
    private Stack<EditorState> redoStack = new Stack<>();
    private boolean isUndoRedoOperation = false;
    private boolean isRestoringState = false;

    private boolean isBoldActive = false;
    private boolean isItalicActive = false;
    private boolean isUnderlineActive = false;
    private Integer activeHighlightColor = null;
    private Integer activeTextColor = null;

    private boolean isPanelVisible = false;

    private Handler autoSaveHandler;
    private Runnable autoSaveRunnable;

    private static class EditorState {
        String text;
        String formatting;
        int cursorPosition;

        EditorState(String text, String formatting, int cursorPosition) {
            this.text = text;
            this.formatting = formatting;
            this.cursorPosition = cursorPosition;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expanded_edit);

        repo = DataRepository.getInstance(this);
        originalText = getIntent().getStringExtra("text");
        currentFormatting = getIntent().getStringExtra("formatting");

        autoSaveHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupListeners();
        setupAutoSave();
        animateEntrance();

        saveCurrentState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveToFirebase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
        }
    }

    private void initViews() {
        expandedInput = findViewById(R.id.expandedInput);
        rootCard = findViewById(R.id.rootCard);
        formattingPanel = findViewById(R.id.formattingPanel);
        closeButton = findViewById(R.id.closeButton);
        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        toggleFormattingButton = findViewById(R.id.toggleFormattingButton);
        charCountText = findViewById(R.id.charCountText);

        boldButton = findViewById(R.id.boldButton);
        italicButton = findViewById(R.id.italicButton);
        underlineButton = findViewById(R.id.underlineButton);
        highlightYellowButton = findViewById(R.id.highlightYellowButton);
        highlightGreenButton = findViewById(R.id.highlightGreenButton);
        highlightPinkButton = findViewById(R.id.highlightPinkButton);
        highlightBlueButton = findViewById(R.id.highlightBlueButton);
        colorRedButton = findViewById(R.id.colorRedButton);
        colorBlueButton = findViewById(R.id.colorBlueButton);
        colorGreenButton = findViewById(R.id.colorGreenButton);

        loadInitialText();

        expandedInput.requestFocus();
        updateCharCount();
        updateUndoRedoButtons();
        updateButtonStates();
    }

    private void loadInitialText() {
        isRestoringState = true;

        if (originalText != null && !originalText.isEmpty()) {
            if (currentFormatting != null && !currentFormatting.isEmpty()) {
                try {
                    SpannableString formatted = TextFormattingSerializer.deserializeFormatting(
                            originalText, currentFormatting);
                    expandedInput.setText(formatted);
                    Log.d(TAG, "Loaded with formatting: " + originalText.length() + " chars");
                } catch (Exception e) {
                    Log.e(TAG, "Error loading formatting", e);
                    expandedInput.setText(originalText);
                }
            } else {
                expandedInput.setText(originalText);
                Log.d(TAG, "Loaded plain text: " + originalText.length() + " chars");
            }
        }

        isRestoringState = false;
    }

    private void setupAutoSave() {
        autoSaveRunnable = this::saveToFirebase;
    }

    private void scheduleAutoSave() {
        if (autoSaveHandler != null && autoSaveRunnable != null) {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
            autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY);
        }
    }

    private void saveToFirebase() {
        CharSequence formattedText = expandedInput.getText();
        if (formattedText == null) return;

        String plainText = formattedText.toString().trim();
        if (plainText.isEmpty()) {
            Log.d(TAG, "Empty text, not saving");
            return;
        }

        String formatting = null;
        try {
            formatting = TextFormattingSerializer.serializeFormatting(formattedText);
            Log.d(TAG, "Serialized formatting: " + (formatting != null ? formatting.length() : 0) + " chars");
        } catch (Exception e) {
            Log.e(TAG, "Error serializing formatting", e);
        }

        final String finalFormatting = formatting;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        repo.getEntryForDay(today, entry -> {
            runOnUiThread(() -> {
                if (entry != null) {
                    entry.setText(plainText);
                    entry.setFormatting(finalFormatting);
                    entry.setTimestamp(System.currentTimeMillis());

                    repo.updateEntry(entry, task -> {
                        Log.d(TAG, "Entry updated in Firebase");
                        storeInIntent(plainText, finalFormatting);
                    });
                } else {
                    repo.saveOrUpdateTodayEntry(plainText, finalFormatting, task -> {
                        Log.d(TAG, "New entry saved to Firebase");
                        storeInIntent(plainText, finalFormatting);
                    });
                }
            });
        });
    }

    private void storeInIntent(String text, String formatting) {
        getIntent().putExtra("edited_text", text);
        getIntent().putExtra("formatting", formatting);
        Log.d(TAG, "Stored in intent: " + text.length() + " chars");
    }

    private void setupListeners() {
        closeButton.setOnClickListener(v -> finishWithSave());

        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());

        toggleFormattingButton.setOnClickListener(v -> toggleFormattingPanel());

        boldButton.setOnClickListener(v -> toggleBold());
        italicButton.setOnClickListener(v -> toggleItalic());
        underlineButton.setOnClickListener(v -> toggleUnderline());

        highlightYellowButton.setOnClickListener(v -> toggleHighlight(Color.parseColor("#FEF3C7")));
        highlightGreenButton.setOnClickListener(v -> toggleHighlight(Color.parseColor("#D1FAE5")));
        highlightPinkButton.setOnClickListener(v -> toggleHighlight(Color.parseColor("#FCE7F3")));
        highlightBlueButton.setOnClickListener(v -> toggleHighlight(Color.parseColor("#DBEAFE")));

        colorRedButton.setOnClickListener(v -> toggleTextColor(Color.parseColor("#EF4444")));
        colorBlueButton.setOnClickListener(v -> toggleTextColor(Color.parseColor("#3B82F6")));
        colorGreenButton.setOnClickListener(v -> toggleTextColor(Color.parseColor("#10B981")));

        expandedInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isRestoringState || isUndoRedoOperation) return;

                updateCharCount();

                if (count > 0 && before == 0 && hasActiveFormatting()) {
                    applyFormattingToNewText(start, count);
                }

                saveCurrentState();
                scheduleAutoSave();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void toggleFormattingPanel() {
        if (isPanelVisible) {
            hideFormattingPanel();
        } else {
            showFormattingPanel();
        }
    }

    private void showFormattingPanel() {
        isPanelVisible = true;
        formattingPanel.setVisibility(View.VISIBLE);
        formattingPanel.setAlpha(0f);
        formattingPanel.setTranslationX(100f);
        formattingPanel.setScaleX(0.8f);
        formattingPanel.setScaleY(0.8f);

        formattingPanel.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        toggleFormattingButton.animate()
                .rotation(180f)
                .setDuration(300)
                .start();
    }

    private void hideFormattingPanel() {
        isPanelVisible = false;

        formattingPanel.animate()
                .alpha(0f)
                .translationX(100f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> formattingPanel.setVisibility(View.GONE))
                .start();

        toggleFormattingButton.animate()
                .rotation(0f)
                .setDuration(300)
                .start();
    }

    private void toggleBold() {
        isBoldActive = !isBoldActive;
        updateButtonStates();
    }

    private void toggleItalic() {
        isItalicActive = !isItalicActive;
        updateButtonStates();
    }

    private void toggleUnderline() {
        isUnderlineActive = !isUnderlineActive;
        updateButtonStates();
    }

    private void toggleHighlight(int color) {
        if (activeHighlightColor != null && activeHighlightColor == color) {
            activeHighlightColor = null;
        } else {
            activeHighlightColor = color;
        }
        updateButtonStates();
    }

    private void toggleTextColor(int color) {
        if (activeTextColor != null && activeTextColor == color) {
            activeTextColor = null;
        } else {
            activeTextColor = color;
        }
        updateButtonStates();
    }

    private boolean hasActiveFormatting() {
        return isBoldActive || isItalicActive || isUnderlineActive ||
                activeHighlightColor != null || activeTextColor != null;
    }

    private void applyFormattingToNewText(int start, int count) {
        Editable editable = expandedInput.getText();
        if (editable == null) return;

        int end = start + count;
        if (end > editable.length()) {
            end = editable.length();
        }

        if (start >= end || count <= 0) return;

        int spanFlags = Spannable.SPAN_EXCLUSIVE_INCLUSIVE;

        try {
            if (isBoldActive) {
                editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, spanFlags);
            }

            if (isItalicActive) {
                editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, spanFlags);
            }

            if (isUnderlineActive) {
                editable.setSpan(new UnderlineSpan(), start, end, spanFlags);
            }

            if (activeHighlightColor != null) {
                editable.setSpan(new BackgroundColorSpan(activeHighlightColor), start, end, spanFlags);
            }

            if (activeTextColor != null) {
                editable.setSpan(new ForegroundColorSpan(activeTextColor), start, end, spanFlags);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying formatting", e);
        }
    }

    private void updateButtonStates() {
        updateButtonState(boldButton, isBoldActive);
        updateButtonState(italicButton, isItalicActive);
        updateButtonState(underlineButton, isUnderlineActive);

        updateButtonState(highlightYellowButton, isHighlightActive(Color.parseColor("#FEF3C7")));
        updateButtonState(highlightGreenButton, isHighlightActive(Color.parseColor("#D1FAE5")));
        updateButtonState(highlightPinkButton, isHighlightActive(Color.parseColor("#FCE7F3")));
        updateButtonState(highlightBlueButton, isHighlightActive(Color.parseColor("#DBEAFE")));

        updateButtonState(colorRedButton, isColorActive(Color.parseColor("#EF4444")));
        updateButtonState(colorBlueButton, isColorActive(Color.parseColor("#3B82F6")));
        updateButtonState(colorGreenButton, isColorActive(Color.parseColor("#10B981")));
    }

    private void updateButtonState(ImageButton button, boolean active) {
        button.setAlpha(active ? 1f : 0.4f);
        button.setScaleX(active ? 1.15f : 1f);
        button.setScaleY(active ? 1.15f : 1f);
    }

    private boolean isHighlightActive(int color) {
        return activeHighlightColor != null && activeHighlightColor == color;
    }

    private boolean isColorActive(int color) {
        return activeTextColor != null && activeTextColor == color;
    }

    private void saveCurrentState() {
        if (isUndoRedoOperation || isRestoringState) return;

        try {
            CharSequence text = expandedInput.getText();
            if (text == null) return;

            String formatting = TextFormattingSerializer.serializeFormatting(text);
            int cursor = expandedInput.getSelectionStart();

            EditorState state = new EditorState(text.toString(), formatting, cursor);
            undoStack.push(state);
            redoStack.clear();

            if (undoStack.size() > 50) {
                undoStack.remove(0);
            }

            updateUndoRedoButtons();
        } catch (Exception e) {
            Log.e(TAG, "Error saving state", e);
        }
    }

    private void undo() {
        if (undoStack.size() <= 1) return;

        isUndoRedoOperation = true;

        EditorState current = undoStack.pop();
        redoStack.push(current);

        EditorState previous = undoStack.peek();
        restoreState(previous);

        isUndoRedoOperation = false;
        updateUndoRedoButtons();
        scheduleAutoSave();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;

        isUndoRedoOperation = true;

        EditorState state = redoStack.pop();
        undoStack.push(state);
        restoreState(state);

        isUndoRedoOperation = false;
        updateUndoRedoButtons();
        scheduleAutoSave();
    }

    private void restoreState(EditorState state) {
        isRestoringState = true;

        try {
            if (state.formatting != null && !state.formatting.isEmpty()) {
                SpannableString formatted = TextFormattingSerializer.deserializeFormatting(
                        state.text, state.formatting);
                expandedInput.setText(formatted);
            } else {
                expandedInput.setText(state.text);
            }

            if (state.cursorPosition >= 0 && state.cursorPosition <= state.text.length()) {
                expandedInput.setSelection(state.cursorPosition);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restoring state", e);
            expandedInput.setText(state.text);
        }

        isRestoringState = false;
    }

    private void updateUndoRedoButtons() {
        boolean canUndo = undoStack.size() > 1;
        boolean canRedo = !redoStack.isEmpty();

        undoButton.setEnabled(canUndo);
        redoButton.setEnabled(canRedo);

        undoButton.setAlpha(canUndo ? 1f : 0.3f);
        redoButton.setAlpha(canRedo ? 1f : 0.3f);
    }

    private void updateCharCount() {
        int count = expandedInput.getText() != null ? expandedInput.getText().length() : 0;
        charCountText.setText(getString(R.string.char_count, count));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void animateEntrance() {
        rootCard.setAlpha(0f);
        rootCard.setScaleX(0.95f);
        rootCard.setScaleY(0.95f);

        rootCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void finishWithSave() {
        saveToFirebase();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            setResult(RESULT_OK, getIntent());
            finish();
            overridePendingTransition(0, 0);
        }, 300);
    }

    @Override
    public void onBackPressed() {
        finishWithSave();
    }
}