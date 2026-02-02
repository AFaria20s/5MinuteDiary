package com.afonso.fiveminutediary.viewmodel;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.UserProfile;
import com.afonso.fiveminutediary.util.LocaleManager;
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

public class ProfileActivity extends BaseActivity {

    private DataRepository repo;
    private UserProfile userProfile;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;

    private EditText nameInput;
    private TextView journeyDaysText;
    private TextView totalEntriesText;
    private TextView streakText;
    private TextView currentLanguageText;
    private CardView premiumStatsCard;
    private CardView languageCard;
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
        setupLanguageSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Start realtime listener for profile
        startRealtimeProfileUpdates();

        // Reload stats
        loadProfileData();

        // Update language display
        updateLanguageDisplay();

        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfileField("lastOpenedTimestamp", System.currentTimeMillis(), null);
        }

        updateBottomNavSelection();
    }

    @Override
    protected int getNavigationMenuItemId() {
        return R.id.nav_profile;
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
        currentLanguageText = findViewById(R.id.currentLanguageText);
        premiumStatsCard = findViewById(R.id.premiumStatsCard);
        languageCard = findViewById(R.id.languageCard);
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

    private void startRealtimeProfileUpdates() {
        repo.startProfileListener(profile -> {
            runOnUiThread(() -> {
                userProfile = profile;
                if (userProfile != null && userProfile.getUserName() != null) {
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

        long daysSinceFirstUse = TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - userProfile.getFirstUseTimestamp()
        );

        journeyDaysText.setText(getResources().getQuantityString(
                R.plurals.journey_days,
                (int) daysSinceFirstUse,
                (int) daysSinceFirstUse
        ));

        repo.getEntryCount(count -> {
            runOnUiThread(() -> {
                totalEntriesText.setText(String.valueOf(count));
            });
        });

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
            repo.updateUserProfileField("userName", name, task -> {});
        }
    }

    private void setupPremiumCard() {
        premiumStatsCard.setOnClickListener(v -> {
            Toast.makeText(this, R.string.premium_toast, Toast.LENGTH_SHORT).show();
        });
    }

    // ==================== LANGUAGE SELECTOR ====================

    private void setupLanguageSelector() {
        languageCard.setOnClickListener(v -> showLanguageDialog());
        updateLanguageDisplay();
    }

    private void updateLanguageDisplay() {
        String currentLang = LocaleManager.getCurrentLanguageName(this);
        currentLanguageText.setText(currentLang);
    }

    private void showLanguageDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_language_selector, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.languageRadioGroup);

        String currentLanguage = LocaleManager.getLanguage(this);

        // Add radio buttons for each language
        LocaleManager.Language[] languages = LocaleManager.getAvailableLanguages();
        for (LocaleManager.Language lang : languages) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(lang.nativeName + " (" + lang.name + ")");
            radioButton.setTextSize(16);
            radioButton.setPadding(16, 16, 16, 16);
            radioButton.setTag(lang.code);

            if (lang.code.equals(currentLanguage)) {
                radioButton.setChecked(true);
            }

            radioGroup.addView(radioButton);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    int selectedId = radioGroup.getCheckedRadioButtonId();
                    if (selectedId != -1) {
                        RadioButton selectedButton = dialogView.findViewById(selectedId);
                        String selectedLang = (String) selectedButton.getTag();

                        if (!selectedLang.equals(currentLanguage)) {
                            changeLanguage(selectedLang);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }

    private void changeLanguage(String languageCode) {
        // Save the language preference
        LocaleManager.setLanguage(this, languageCode);

        // Show confirmation
        Toast.makeText(this, R.string.language_changed, Toast.LENGTH_SHORT).show();

        // Recreate all activities to apply new language
        recreateApp();
    }

    private void recreateApp() {
        // Restart the app to apply language to all activities
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ==================== LOGOUT & DELETE ====================

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> performLogout())
                .setNegativeButton(R.string.cancel_button, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void performLogout() {
        repo.clearCache();
        auth.signOut();

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showDeleteAccountDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        boolean isGoogleAccount = false;
        for (int i = 0; i < user.getProviderData().size(); i++) {
            if (user.getProviderData().get(i).getProviderId().equals("google.com")) {
                isGoogleAccount = true;
                break;
            }
        }

        if (isGoogleAccount) {
            showDeleteConfirmationForGoogle();
        } else {
            showPasswordConfirmationDialog();
        }
    }

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

    private void showPasswordConfirmationDialog() {
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

                button.setEnabled(false);
                passwordInput.setEnabled(false);
                reauthenticateAndDelete(password, dialog);
            });
        });

        dialog.show();
    }

    private void reauthenticateAndDelete(String password, AlertDialog dialog) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, R.string.error_getting_user, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            dialog.dismiss();
                            showFinalDeleteConfirmation();
                        } else {
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
                                repo.clearCache();
                                googleSignInClient.signOut();

                                Toast.makeText(ProfileActivity.this,
                                        R.string.delete_account_success,
                                        Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(ProfileActivity.this,
                                        com.afonso.fiveminutediary.auth.LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
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
}