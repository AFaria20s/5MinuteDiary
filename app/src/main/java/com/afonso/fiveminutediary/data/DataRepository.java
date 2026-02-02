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
 * Singleton that handles all Firestore read/write operations for diary entries
 * and user profiles. Includes an in-memory cache to reduce network calls,
 * automatic retry on write failures, and real-time listeners.
 */
public class DataRepository {

    private static final String TAG = "DataRepository";

    /** Firestore collection name for diary entries. */
    private static final String COLLECTION_ENTRIES = "diary_entries";

    /** Firestore collection name for user profiles. */
    private static final String COLLECTION_PROFILES = "user_profiles";

    /** Maximum number of automatic retry attempts on write failure. */
    private static final int MAX_RETRIES = 3;

    /** Delay in ms between each retry attempt. */
    private static final long RETRY_DELAY_MS = 1000;

    private static DataRepository instance;
    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // ─── Cache ───────────────────────────────────────────────────────────────

    /** Local copy of the current user's entries, sorted newest first. */
    private List<DiaryEntry> cachedEntries = new ArrayList<>();

    /** Local copy of the current user's profile. Null if not yet loaded. */
    private UserProfile cachedProfile = null;

    /** True when cachedEntries holds valid data and can be returned directly. */
    private boolean entriesCacheValid = false;

    /** True when cachedProfile holds valid data. Set to false after a single-field update to force a refresh. */
    private boolean profileCacheValid = false;

    // ─── Real-time listeners ─────────────────────────────────────────────────

    /** Registration handle for the entries snapshot listener. Null when inactive. */
    private ListenerRegistration entriesListener = null;

    /** Registration handle for the profile snapshot listener. Null when inactive. */
    private ListenerRegistration profileListener = null;

    // ─── Write lock ──────────────────────────────────────────────────────────

    /**
     * Lock object used to prevent two saveOrUpdateTodayEntry calls from running
     * at the same time, which would otherwise create duplicate entries.
     */
    private final Object saveLock = new Object();

    /** True while a save operation is in progress. Guarded by saveLock. */
    private boolean isSaving = false;

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Private constructor. Initialises Firestore, Auth, and enables offline persistence.
     *
     * @param context application context (avoids memory leaks in the singleton)
     */
    private DataRepository(Context context) {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        enableOfflinePersistence();
    }

    /**
     * Returns the singleton instance, creating it if needed.
     *
     * @param context any context; internally uses getApplicationContext()
     * @return the single DataRepository instance
     */
    public static synchronized DataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DataRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ─── Configuration ───────────────────────────────────────────────────────

    /**
     * Enables Firestore offline persistence with unlimited cache size.
     * All reads and writes work without a network connection and sync automatically when online.
     */
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

    /**
     * Returns the UID of the currently signed-in Firebase user, or null if none.
     */
    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // ─── Cache management ────────────────────────────────────────────────────

    /**
     * Clears all cached data and removes any active real-time listeners.
     * Should be called on logout to avoid leftover data from the previous user.
     */
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
     * Saves or updates today's diary entry.
     *
     * <p>Checks whether an entry already exists for today. If it does, updates it;
     * otherwise creates a new one. Uses saveLock to prevent duplicate entries when
     * auto-save and manual save fire at the same time.</p>
     *
     * @param text        plain text content of the entry
     * @param formatting  serialised formatting data (from TextFormattingSerializer); can be null
     * @param listener    callback when the operation completes; can be null
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
                // A save is already in progress — skip this call
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

    /**
     * Adds a new entry to Firestore with automatic retry on failure.
     * On success, inserts the entry at the beginning of the local cache.
     *
     * @param entry      the entry to add; its id will be set by Firestore after success
     * @param retryCount current attempt number (0 on first call)
     * @param listener   callback when done; can be null
     */
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
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(() -> addEntryWithRetry(entry, retryCount + 1, listener), RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "Failed to add entry after " + MAX_RETRIES + " attempts");
                        if (listener != null) listener.onComplete(null);
                    }
                });
    }

    /**
     * Updates an existing entry in Firestore with automatic retry on failure.
     * Uses merge so only the provided fields are written.
     * On success, updates the matching entry in the local cache.
     *
     * @param entry      the entry with updated data; must have a valid id
     * @param retryCount current attempt number (0 on first call)
     * @param listener   callback when done; can be null
     */
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

    /**
     * Replaces the cached copy of an entry with the given updated version,
     * matched by id. Does nothing if the entry is not in the cache.
     */
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
     * Public method to update an existing entry. Used by detail/edit activities.
     *
     * @param entry    the entry with updated data; must have a valid id
     * @param listener callback when done; can be null
     */
    public void updateEntry(DiaryEntry entry, OnCompleteListener<Void> listener) {
        updateEntryWithRetry(entry, 0, listener);
    }

    /**
     * Returns all diary entries for the current user, sorted newest first.
     * Returns from cache if available; otherwise fetches from Firestore and updates the cache.
     * On network failure, falls back to the current cache contents.
     *
     * @param listener callback that receives the list of entries (empty list if none)
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
     * Returns the diary entry for a specific day, or null if none exists.
     *
     * <p>First checks the local cache. If not found there (or cache is invalid),
     * queries Firestore using a timestamp range covering the full day (00:00:00 to 23:59:59).</p>
     *
     * @param day      the target day in "yyyy-MM-dd" format
     * @param listener callback that receives the entry, or null if not found
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
     * Deletes a single entry from Firestore and removes it from the local cache.
     * The cache remains valid after removal.
     *
     * @param entry    the entry to delete; must have a valid id
     * @param listener callback when done; can be null
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
                    entriesCacheValid = true;
                    Log.d(TAG, "Entry deleted: " + entry.getId());
                    if (listener != null) listener.onComplete(null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting entry", e);
                    if (listener != null) listener.onComplete(null);
                });
    }

    // ─── Real-time listeners ─────────────────────────────────────────────────

    /**
     * Starts a real-time listener on the entries collection.
     * The callback fires automatically whenever entries are added, updated, or deleted.
     * Replaces any previously active entries listener.
     *
     * @param listener callback that receives the full updated entry list on each change
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

    /**
     * Removes the active entries real-time listener, if one exists.
     * Call this when the listening activity or component is destroyed.
     */
    public void stopEntriesListener() {
        if (entriesListener != null) {
            entriesListener.remove();
            entriesListener = null;
            Log.d(TAG, "Entries listener stopped");
        }
    }

    // ─── Streak ──────────────────────────────────────────────────────────────

    /**
     * Calculates the current consecutive-day streak.
     *
     * <p>If today already has an entry, the count starts from today.
     * If not, it starts from yesterday (so the streak isn't broken simply
     * because the user hasn't written yet today). Counts backwards day by day
     * until a gap is found, up to a maximum of 365 days.</p>
     *
     * @param listener callback that receives the streak count (0 if no entries exist)
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

    // ─── Entry count ─────────────────────────────────────────────────────────

    /**
     * Returns the total number of entries for the current user.
     * Uses the cache size if available; otherwise queries Firestore.
     * Falls back to cache size on network failure.
     *
     * @param listener callback that receives the entry count
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
     * Deletes all entries for the current user. Used during account deletion.
     *
     * <p>Note: deletions are fired individually per document and are not atomic.
     * If it fails midway, some documents may already have been deleted.</p>
     *
     * @param listener callback when done; can be null
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
     * Fetches the current user's profile from Firestore.
     * Returns from cache if available. On network failure, falls back to the cached profile.
     *
     * @param listener callback that receives the UserProfile, or null if it doesn't exist
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
     * Creates or updates the full user profile in Firestore using merge.
     * The profile's id is automatically set to the current user's UID.
     *
     * @param profile  the profile to save
     * @param listener callback when done; can be null
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
     * Updates a single field on the user's profile document.
     * More efficient than updateUserProfile when only one value changes.
     * Invalidates the profile cache so the next read fetches fresh data.
     *
     * @param field    the Firestore field name (e.g. "displayName")
     * @param value    the new value for that field
     * @param listener callback when done; can be null
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
     * Returns the user's profile, creating a new one with default values if it doesn't exist yet.
     * Useful on first login or when the profile hasn't been initialised.
     *
     * @param listener callback that receives the existing or newly created profile (never null)
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
     * Starts a real-time listener on the user's profile document.
     * The callback fires automatically whenever the profile is updated.
     * Replaces any previously active profile listener.
     *
     * @param listener callback that receives the updated profile on each change
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

    /**
     * Removes the active profile real-time listener, if one exists.
     * Call this when the listening activity or component is destroyed.
     */
    public void stopProfileListener() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
            Log.d(TAG, "Profile listener stopped");
        }
    }
}