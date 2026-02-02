package com.afonso.fiveminutediary.viewmodel;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.UserProfile;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

public class ProfileActivity extends AppCompatActivity {

    private DataRepository repo;
    private UserProfile userProfile;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;

    private EditText nameInput;
    private TextView journeyDaysText;
    private TextView totalEntriesText;
    private TextView streakText;
    private CardView premiumStatsCard;
    private Button logoutButton;
    private Button deleteAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check authentication
        checkAuthentication();

        setContentView(R.layout.activity_profile);

        repo = DataRepository.getInstance(this);
        auth = FirebaseAuth.getInstance();

        // Configure Google Sign In for logout
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        loadOrCreateProfile();
        setupBottomNavigation();
        setupPremiumCard();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start realtime listener for profile
        startRealtimeProfileUpdates();

        // Reload stats
        loadProfileData();

        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfileField("lastOpenedTimestamp", System.currentTimeMillis(), null);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop listener
        repo.stopProfileListener();

        // Auto-save name
        saveName();

        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfileField("lastOpenedTimestamp", System.currentTimeMillis(), null);
        }
    }

    /**
     * Check if user is authenticated
     */
    private void checkAuthentication() {
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void initViews() {
        nameInput = findViewById(R.id.nameInput);
        journeyDaysText = findViewById(R.id.journeyDaysText);
        totalEntriesText = findViewById(R.id.totalEntriesText);
        streakText = findViewById(R.id.streakText);
        premiumStatsCard = findViewById(R.id.premiumStatsCard);
        logoutButton = findViewById(R.id.logoutButton);
        deleteAccountButton = findViewById(R.id.deleteAccountButton);

        nameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveName();
            }
        });

        logoutButton.setOnClickListener(v -> showLogoutDialog());
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
    }

    /**
     * Start realtime updates for profile
     */
    private void startRealtimeProfileUpdates() {
        repo.startProfileListener(profile -> {
            runOnUiThread(() -> {
                userProfile = profile;
                if (userProfile != null && userProfile.getUserName() != null) {
                    // Only update if field is not focused
                    if (!nameInput.hasFocus()) {
                        nameInput.setText(userProfile.getUserName());
                    }
                }
            });
        });
    }

    private void loadOrCreateProfile() {
        repo.getOrCreateUserProfile(profile -> {
            runOnUiThread(() -> {
                userProfile = profile;
                if (userProfile.getUserName() != null && !userProfile.getUserName().isEmpty()) {
                    nameInput.setText(userProfile.getUserName());
                }
                loadProfileData();
            });
        });
    }

    private void loadProfileData() {
        if (userProfile == null) return;

        // Journey days
        long daysSinceFirstUse = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - userProfile.getFirstUseTimestamp()
        );

        // Use proper plurals
        journeyDaysText.setText(getResources().getQuantityString(
                R.plurals.journey_days,
                (int) daysSinceFirstUse,
                (int) daysSinceFirstUse
        ));

        // Total entries (with cache)
        repo.getEntryCount(count -> {
            runOnUiThread(() -> {
                totalEntriesText.setText(String.valueOf(count));
            });
        });

        // Streak (with cache)
        repo.calculateStreak(streak -> {
            runOnUiThread(() -> {
                streakText.setText(String.valueOf(streak));
            });
        });
    }

    private void saveName() {
        String name = nameInput.getText().toString().trim();
        if (userProfile != null && !name.equals(userProfile.getUserName())) {
            userProfile.setUserName(name);

            // Update only the "userName" field for efficiency
            repo.updateUserProfileField("userName", name, task -> {
                // Optional: show feedback
            });
        }
    }

    private void setupPremiumCard() {
        premiumStatsCard.setOnClickListener(v -> {
            Toast.makeText(this, R.string.premium_toast, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Show logout confirmation dialog
     */
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> performLogout())
                .setNegativeButton(R.string.cancel_button, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Perform logout
     */
    private void performLogout() {
        // Clear repository cache
        repo.clearCache();

        // Sign out from Firebase
        auth.signOut();

        // Sign out from Google (if logged in with Google)
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Navigate to login
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Show account deletion confirmation dialog
     */
    private void showDeleteAccountDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Check if it's a Google account or Email/Password
        boolean isGoogleAccount = false;
        for (int i = 0; i < user.getProviderData().size(); i++) {
            if (user.getProviderData().get(i).getProviderId().equals("google.com")) {
                isGoogleAccount = true;
                break;
            }
        }

        if (isGoogleAccount) {
            // For Google account, no password needed
            showDeleteConfirmationForGoogle();
        } else {
            // For Email/Password account, ask for password
            showPasswordConfirmationDialog();
        }
    }

    /**
     * Deletion confirmation for Google accounts
     */
    private void showDeleteConfirmationForGoogle() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message_google)
                .setPositiveButton(R.string.delete_account_confirm, (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton(R.string.cancel_button, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Password confirmation dialog
     */
    private void showPasswordConfirmationDialog() {
        // Create custom layout for dialog
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_confirmation, null);
        EditText passwordInput = dialogView.findViewById(R.id.passwordConfirmInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_password_prompt)
                .setView(dialogView)
                .setPositiveButton(R.string.delete_account_button, null)
                .setNegativeButton(R.string.cancel_button, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String password = passwordInput.getText().toString().trim();

                if (TextUtils.isEmpty(password)) {
                    passwordInput.setError(getString(R.string.password_required));
                    passwordInput.requestFocus();
                    return;
                }

                // Disable button during verification
                button.setEnabled(false);
                passwordInput.setEnabled(false);

                // Re-authenticate and delete
                reauthenticateAndDelete(password, dialog);
            });
        });

        dialog.show();
    }

    /**
     * Re-authenticate user and delete account
     */
    private void reauthenticateAndDelete(String password, AlertDialog dialog) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, R.string.error_getting_user, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        // Create credential with email and password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        // Re-authenticate
        user.reauthenticate(credential)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Password correct, show final confirmation
                            dialog.dismiss();
                            showFinalDeleteConfirmation();
                        } else {
                            // Password incorrect
                            dialog.dismiss();
                            new AlertDialog.Builder(ProfileActivity.this)
                                    .setTitle(R.string.password_incorrect_title)
                                    .setMessage(R.string.password_incorrect_message)
                                    .setPositiveButton(R.string.ok, null)
                                    .show();
                        }
                    }
                });
    }

    /**
     * Final confirmation before deletion
     */
    private void showFinalDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_final_title)
                .setMessage(R.string.delete_account_final_message)
                .setPositiveButton(R.string.delete_account_final_confirm, (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton(R.string.delete_account_final_cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Perform account deletion
     */
    private void performAccountDeletion() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_progress)
                .setMessage(R.string.delete_account_wait)
                .setCancelable(false)
                .create();
        loadingDialog.show();

        repo.deleteAllEntries(task1 -> {
            user.delete()
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            loadingDialog.dismiss();

                            if (task.isSuccessful()) {
                                // Clear cache
                                repo.clearCache();

                                // Sign out from Google if necessary
                                googleSignInClient.signOut();

                                // Show message and return to login
                                Toast.makeText(ProfileActivity.this,
                                        R.string.delete_account_success,
                                        Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(ProfileActivity.this,
                                        com.afonso.fiveminutediary.auth.LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                // Error deleting account
                                String errorMessage = task.getException() != null ?
                                        task.getException().getMessage() : getString(R.string.unknown_error);

                                new AlertDialog.Builder(ProfileActivity.this)
                                        .setTitle(R.string.delete_account_error_title)
                                        .setMessage(String.format(getString(R.string.delete_account_error_message), errorMessage))
                                        .setPositiveButton(R.string.ok, null)
                                        .show();
                            }
                        }
                    });
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
}