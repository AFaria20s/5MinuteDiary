package com.afonso.fiveminutediary.viewmodel;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.adapter.DiaryAdapter;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ListActivity extends BaseActivity implements DiaryAdapter.OnEntryClickListener {

    private RecyclerView recyclerView;
    private DiaryAdapter adapter;
    private DataRepository repo;
    private LinearLayout emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check authentication
        checkAuthentication();

        setContentView(R.layout.activity_list);

        repo = DataRepository.getInstance(this);

        initViews();
        loadEntries();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start realtime listener
        startRealtimeUpdates();

        updateBottomNavSelection();
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_history;
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop listener to save resources
        repo.stopEntriesListener();
    }

    /**
     * Check if user is authenticated
     */
    private void checkAuthentication() {
        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DiaryAdapter(this, this);
        recyclerView.setAdapter(adapter);
    }

    /**
     * Start realtime updates
     */
    private void startRealtimeUpdates() {
        repo.startEntriesListener(entries -> {
            runOnUiThread(() -> {
                updateUI(entries);
            });
        });
    }

    /**
     * Load entries (initial)
     */
    private void loadEntries() {
        repo.getEntries(entries -> {
            runOnUiThread(() -> {
                updateUI(entries);
            });
        });
    }

    /**
     * Update UI with entries
     */
    private void updateUI(java.util.List<DiaryEntry> entries) {
        if (entries.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter.setEntries(entries);
        }
    }

    @Override
    public void onEntryClick(DiaryEntry entry) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("entry", entry);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(DiaryEntry entry) {
        repo.deleteEntry(entry, task -> {
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this,
                        getString(R.string.entry_deleted_toast),
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }
}