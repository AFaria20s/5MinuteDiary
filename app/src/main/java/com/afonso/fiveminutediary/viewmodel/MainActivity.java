package com.afonso.fiveminutediary.viewmodel;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DataRepository repo;
    private EditText entryInput;
    private TextView dateText;
    private TextView wordCounter;
    private CardView todayCard;
    private ImageButton deleteButton;
    private DiaryEntry todaysEntry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repo = DataRepository.getInstance(this);

        initViews();
        loadTodayEntry();
        setupListeners();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTodayEntry();

        // Reset bottom navigation selection when returning to this activity
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    private void initViews() {
        entryInput = findViewById(R.id.entryInput);
        dateText = findViewById(R.id.dateText);
        wordCounter = findViewById(R.id.wordCounter);
        todayCard = findViewById(R.id.todayCard);
        deleteButton = findViewById(R.id.deleteButton);

        // Set today's date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "PT"));
        dateText.setText(sdf.format(new Date()));

        // Word counter
        entryInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWordCount(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateWordCount(String text) {
        if (text.trim().isEmpty()) {
            wordCounter.setText("0 palavras");
        } else {
            int words = text.trim().split("\\s+").length;
            wordCounter.setText(words + (words == 1 ? " palavra" : " palavras"));
        }
    }

    private void loadTodayEntry() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        todaysEntry = repo.getEntryForDay(today);

        if (todaysEntry != null) {
            entryInput.setText(todaysEntry.getText());
            deleteButton.setVisibility(View.VISIBLE);
        } else {
            entryInput.setText("");
            deleteButton.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        Button saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> saveEntry());
        deleteButton.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_history) {
                Intent intent = new Intent(MainActivity.this, ListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void saveEntry() {
        String text = entryInput.getText().toString().trim();

        if (text.isEmpty()) {
            Toast.makeText(this, "Escreve algo primeiro 📝", Toast.LENGTH_SHORT).show();
        } else {
            repo.addOrUpdateEntry(System.currentTimeMillis(), text);
            Toast.makeText(this, "Guardado ✓", Toast.LENGTH_SHORT).show();
            loadTodayEntry();
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar entrada")
                .setMessage("Tens a certeza que queres eliminar a entrada de hoje?")
                .setPositiveButton("Eliminar", (dialog, which) -> deleteEntry())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteEntry() {
        if (todaysEntry != null) {
            repo.deleteEntry(todaysEntry);
            loadTodayEntry();
            Toast.makeText(this, "Entrada eliminada", Toast.LENGTH_SHORT).show();
        }
    }
}