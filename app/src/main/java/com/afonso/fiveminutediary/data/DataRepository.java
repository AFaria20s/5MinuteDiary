package com.afonso.fiveminutediary.data;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Complete and robust DataRepository with offline persistence and retry logic
 */
public class DataRepository {

    private static final String TAG = "DataRepository";
    private static final String COLLECTION_ENTRIES = "diary_entries";
    private static final String COLLECTION_PROFILES = "user_profiles";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private static DataRepository instance;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Cache
    private List<DiaryEntry> cachedEntries = new ArrayList<>();
    private UserProfile cachedProfile = null;
    private boolean entriesCacheValid = false;
    private boolean profileCacheValid = false;

    // Listeners
    private ListenerRegistration entriesListener = null;
    private ListenerRegistration profileListener = null;

    private final Object saveLock = new Object();
    private boolean isSaving = false;

    private DataRepository(Context context) {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        enableOfflinePersistence();
    }

    public static synchronized DataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DataRepository(context.getApplicationContext());
        }
        return instance;
    }

    private void enableOfflinePersistence() {
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
            Log.d(TAG, "Offline persistence enabled");
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable offline persistence", e);
        }
    }

    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void clearCache() {
        cachedEntries.clear();
        cachedProfile = null;
        entriesCacheValid = false;
        profileCacheValid = false;

        if (entriesListener != null) {
            entriesListener.remove();
            entriesListener = null;
        }
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }

        Log.d(TAG, "Cache cleared");
    }

    // ========== DIARY ENTRIES ==========

    /**
     * Save or update today's entry with retry logic
     */
    public void saveOrUpdateTodayEntry(String text, String formatting, OnCompleteListener<Void> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (listener != null) listener.onComplete(null);
            return;
        }

        synchronized (saveLock) {
            if (isSaving) {
                // Já há um save em andamento — ignora esta chamada
                if (listener != null) listener.onComplete(null);
                return;
            }
            isSaving = true;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        getEntryForDay(today, existingEntry -> {
            if (existingEntry != null) {
                existingEntry.setText(text);
                existingEntry.setFormatting(formatting);
                existingEntry.setTimestamp(System.currentTimeMillis());
                updateEntryWithRetry(existingEntry, 0, task -> {
                    synchronized (saveLock) { isSaving = false; }
                    if (listener != null) listener.onComplete(task);
                });
            } else {
                DiaryEntry newEntry = new DiaryEntry(null, userId, System.currentTimeMillis(), text, null, formatting);
                addEntryWithRetry(newEntry, 0, task -> {
                    synchronized (saveLock) { isSaving = false; }
                    if (listener != null) listener.onComplete(task);
                });
            }
        });
    }

    private void addEntryWithRetry(DiaryEntry entry, int retryCount, OnCompleteListener<Void> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (listener != null) listener.onComplete(null);
            return;
        }

        entry.setUserId(userId);
        if (entry.getTimestamp() == 0) {
            entry.setTimestamp(System.currentTimeMillis());
        }

        db.collection(COLLECTION_ENTRIES)
                .add(entry.toMap())
                .addOnSuccessListener(documentReference -> {
                    entry.setId(documentReference.getId());
                    cachedEntries.add(0, entry);
                    entriesCacheValid = true;
                    Log.d(TAG, "Entry added: " + entry.getId());
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add entry (attempt " + (retryCount + 1) + ")", e);
                    if (retryCount < MAX_RETRIES) {
                        // Retry after delay
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> addEntryWithRetry(entry, retryCount + 1, listener), RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "Failed to add entry after " + MAX_RETRIES + " attempts");
                        if (listener != null) listener.onComplete(null);
                    }
                });
    }

    private void updateEntryWithRetry(DiaryEntry entry, int retryCount, OnCompleteListener<Void> listener) {
        if (entry.getId() == null) {
            Log.e(TAG, "Entry ID is null");
            if (listener != null) listener.onComplete(null);
            return;
        }

        db.collection(COLLECTION_ENTRIES)
                .document(entry.getId())
                .set(entry.toMap(), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    updateCachedEntry(entry);
                    Log.d(TAG, "Entry updated: " + entry.getId());
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update entry (attempt " + (retryCount + 1) + ")", e);
                    if (retryCount < MAX_RETRIES) {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> updateEntryWithRetry(entry, retryCount + 1, listener), RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "Failed to update entry after " + MAX_RETRIES + " attempts");
                        if (listener != null) listener.onComplete(null);
                    }
                });
    }

    private void updateCachedEntry(DiaryEntry entry) {
        for (int i = 0; i < cachedEntries.size(); i++) {
            if (cachedEntries.get(i).getId() != null &&
                    cachedEntries.get(i).getId().equals(entry.getId())) {
                cachedEntries.set(i, entry);
                return;
            }
        }
    }

    /**
     * Update existing entry (public method for DetailActivity, etc)
     */
    public void updateEntry(DiaryEntry entry, OnCompleteListener<Void> listener) {
        updateEntryWithRetry(entry, 0, listener);
    }

    /**
     * Get all entries with cache
     */
    public void getEntries(OnSuccessListener<List<DiaryEntry>> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            listener.onSuccess(new ArrayList<>());
            return;
        }

        // Return cache if valid
        if (entriesCacheValid && !cachedEntries.isEmpty()) {
            Log.d(TAG, "Returning cached entries: " + cachedEntries.size());
            listener.onSuccess(new ArrayList<>(cachedEntries));
            return;
        }

        // Fetch from Firestore
        db.collection(COLLECTION_ENTRIES)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DiaryEntry> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        DiaryEntry entry = document.toObject(DiaryEntry.class);
                        entry.setId(document.getId());
                        entries.add(entry);
                    }
                    cachedEntries = entries;
                    entriesCacheValid = true;
                    Log.d(TAG, "Loaded " + entries.size() + " entries");
                    listener.onSuccess(entries);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting entries", e);
                    listener.onSuccess(new ArrayList<>(cachedEntries));
                });
    }

    /**
     * Get entry for specific day
     */
    public void getEntryForDay(String day, OnSuccessListener<DiaryEntry> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            listener.onSuccess(null);
            return;
        }

        // Check cache first
        if (entriesCacheValid) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                for (DiaryEntry entry : cachedEntries) {
                    String entryDay = sdf.format(new Date(entry.getTimestamp()));
                    if (entryDay.equals(day)) {
                        Log.d(TAG, "Entry found in cache for " + day);
                        listener.onSuccess(entry);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking cache", e);
            }
        }

        // Fetch from Firestore
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            cal.setTime(sdf.parse(day));
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date", e);
            listener.onSuccess(null);
            return;
        }

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfDay = cal.getTimeInMillis();

        db.collection(COLLECTION_ENTRIES)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot document = queryDocumentSnapshots.getDocuments().get(0);
                        DiaryEntry entry = document.toObject(DiaryEntry.class);
                        if (entry != null) {
                            entry.setId(document.getId());
                        }
                        listener.onSuccess(entry);
                    } else {
                        listener.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting entry for day", e);
                    listener.onSuccess(null);
                });
    }

    /**
     * Delete entry
     */
    public void deleteEntry(DiaryEntry entry, OnCompleteListener<Void> listener) {
        if (entry.getId() == null) {
            Log.e(TAG, "Entry ID is null, cannot delete");
            if (listener != null) listener.onComplete(null);
            return;
        }

        db.collection(COLLECTION_ENTRIES)
                .document(entry.getId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    cachedEntries.remove(entry);
                    entriesCacheValid = true; // Cache still valid after removal
                    Log.d(TAG, "Entry deleted: " + entry.getId());
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting entry", e);
                    if (listener != null) listener.onComplete(null);
                });
    }

    /**
     * Real-time listener for entries
     */
    public void startEntriesListener(OnSuccessListener<List<DiaryEntry>> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in, cannot start listener");
            return;
        }

        if (entriesListener != null) {
            entriesListener.remove();
        }

        entriesListener = db.collection(COLLECTION_ENTRIES)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((queryDocumentSnapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed", error);
                        return;
                    }

                    if (queryDocumentSnapshots != null) {
                        List<DiaryEntry> entries = new ArrayList<>();
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            DiaryEntry entry = document.toObject(DiaryEntry.class);
                            entry.setId(document.getId());
                            entries.add(entry);
                        }
                        cachedEntries = entries;
                        entriesCacheValid = true;
                        Log.d(TAG, "Real-time update: " + entries.size() + " entries");
                        listener.onSuccess(entries);
                    }
                });
    }

    public void stopEntriesListener() {
        if (entriesListener != null) {
            entriesListener.remove();
            entriesListener = null;
            Log.d(TAG, "Entries listener stopped");
        }
    }

    /**
     * Calculate current streak
     */
    public void calculateStreak(OnSuccessListener<Integer> listener) {
        getEntries(allEntries -> {
            if (allEntries.isEmpty()) {
                listener.onSuccess(0);
                return;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long todayStart = calendar.getTimeInMillis();

            // Check if we have entry for today
            boolean hasToday = false;
            for (DiaryEntry entry : allEntries) {
                if (entry.getTimestamp() >= todayStart) {
                    hasToday = true;
                    break;
                }
            }

            // Start from today or yesterday
            if (!hasToday) {
                calendar.add(Calendar.DAY_OF_YEAR, -1);
            }

            int streak = 0;

            // Count backwards
            for (int i = 0; i < 365; i++) {
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);

                long dayStart = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                long dayEnd = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_YEAR, -1);

                boolean foundEntry = false;
                for (DiaryEntry entry : allEntries) {
                    if (entry.getTimestamp() >= dayStart && entry.getTimestamp() < dayEnd) {
                        foundEntry = true;
                        break;
                    }
                }

                if (foundEntry) {
                    streak++;
                    calendar.add(Calendar.DAY_OF_YEAR, -1);
                } else {
                    break;
                }
            }

            Log.d(TAG, "Calculated streak: " + streak);
            listener.onSuccess(streak);
        });
    }

    /**
     * Get entry count
     */
    public void getEntryCount(OnSuccessListener<Integer> listener) {
        if (entriesCacheValid) {
            listener.onSuccess(cachedEntries.size());
            return;
        }

        String userId = getCurrentUserId();
        if (userId == null) {
            listener.onSuccess(0);
            return;
        }

        db.collection(COLLECTION_ENTRIES)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    Log.d(TAG, "Entry count: " + count);
                    listener.onSuccess(count);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting entry count", e);
                    listener.onSuccess(cachedEntries.size());
                });
    }

    /**
     * Delete all entries (for account deletion)
     */
    public void deleteAllEntries(OnCompleteListener<Void> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (listener != null) listener.onComplete(null);
            return;
        }

        db.collection(COLLECTION_ENTRIES)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    if (count == 0) {
                        Log.d(TAG, "No entries to delete");
                        if (listener != null) listener.onComplete(null);
                        return;
                    }

                    // Delete all documents
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        document.getReference().delete();
                    }

                    cachedEntries.clear();
                    entriesCacheValid = false;

                    Log.d(TAG, "Deleted " + count + " entries");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting all entries", e);
                    if (listener != null) listener.onComplete(null);
                });
    }

    // ========== USER PROFILE ==========

    /**
     * Get user profile with cache
     */
    public void getUserProfile(OnSuccessListener<UserProfile> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            listener.onSuccess(null);
            return;
        }

        if (profileCacheValid && cachedProfile != null) {
            Log.d(TAG, "Returning cached profile");
            listener.onSuccess(cachedProfile);
            return;
        }

        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserProfile profile = documentSnapshot.toObject(UserProfile.class);
                        if (profile != null) {
                            profile.setId(documentSnapshot.getId());
                            cachedProfile = profile;
                            profileCacheValid = true;
                            Log.d(TAG, "Profile loaded from Firestore");
                        }
                        listener.onSuccess(profile);
                    } else {
                        Log.d(TAG, "Profile not found");
                        listener.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user profile", e);
                    listener.onSuccess(cachedProfile);
                });
    }

    /**
     * Update user profile
     */
    public void updateUserProfile(UserProfile profile, OnCompleteListener<Void> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (listener != null) listener.onComplete(null);
            return;
        }

        profile.setId(userId);

        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .set(profile.toMap(), SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    cachedProfile = profile;
                    profileCacheValid = true;
                    Log.d(TAG, "Profile updated");
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating profile", e);
                    if (listener != null) listener.onComplete(null);
                });
    }

    /**
     * Update single profile field
     */
    public void updateUserProfileField(String field, Object value, OnCompleteListener<Void> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            if (listener != null) listener.onComplete(null);
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(field, value);

        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    profileCacheValid = false; // Invalidate to force refresh
                    Log.d(TAG, "Profile field updated: " + field);
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating profile field", e);
                    if (listener != null) listener.onComplete(null);
                });
    }

    /**
     * Get or create user profile
     */
    public void getOrCreateUserProfile(OnSuccessListener<UserProfile> listener) {
        getUserProfile(profile -> {
            if (profile == null) {
                UserProfile newProfile = new UserProfile(getCurrentUserId());
                updateUserProfile(newProfile, task -> {
                    Log.d(TAG, "New profile created");
                    listener.onSuccess(newProfile);
                });
            } else {
                listener.onSuccess(profile);
            }
        });
    }

    /**
     * Real-time listener for profile
     */
    public void startProfileListener(OnSuccessListener<UserProfile> listener) {
        String userId = getCurrentUserId();
        if (userId == null) {
            Log.e(TAG, "User not logged in");
            return;
        }

        if (profileListener != null) {
            profileListener.remove();
        }

        profileListener = db.collection(COLLECTION_PROFILES)
                .document(userId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Profile listen failed", error);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        UserProfile profile = documentSnapshot.toObject(UserProfile.class);
                        if (profile != null) {
                            profile.setId(documentSnapshot.getId());
                            cachedProfile = profile;
                            profileCacheValid = true;
                            Log.d(TAG, "Profile real-time update");
                            listener.onSuccess(profile);
                        }
                    }
                });
    }

    public void stopProfileListener() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
            Log.d(TAG, "Profile listener stopped");
        }
    }
}