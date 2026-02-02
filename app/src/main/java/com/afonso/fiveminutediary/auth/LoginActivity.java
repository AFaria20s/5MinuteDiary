package com.afonso.fiveminutediary.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.viewmodel.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private Button loginButton;
    private Button googleSignInButton;
    private TextView signUpText;
    private TextView forgotPasswordText;
    private ProgressBar loadingProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        setupListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified() || currentUser.getProviderData().toString().contains("google.com")) {
                navigateToMain();
            } else {
                navigateToEmailVerification();
            }
        }
    }

    private void initViews() {
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        signUpText = findViewById(R.id.signUpText);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        loadingProgress = findViewById(R.id.loadingProgress);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> loginWithEmail());
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
        signUpText.setOnClickListener(v -> navigateToRegister());
        forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
    }

    private void loginWithEmail() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(email)) {
            emailInput.setError(getString(R.string.email_required));
            emailInput.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.email_invalid));
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError(getString(R.string.password_required));
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError(getString(R.string.password_min_length));
            passwordInput.requestFocus();
            return;
        }

        // Show loading
        setLoading(true);

        // Sign in with Firebase
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                if (user.isEmailVerified()) {
                                    Toast.makeText(LoginActivity.this, getString(R.string.welcome), Toast.LENGTH_SHORT).show();
                                    navigateToMain();
                                } else {
                                    Toast.makeText(LoginActivity.this, getString(R.string.verify_email_first), Toast.LENGTH_LONG).show();
                                    navigateToEmailVerification();
                                }
                            }
                        } else {
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : getString(R.string.login_error);
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this,
                        String.format(getString(R.string.google_signin_failed), e.getMessage()),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        setLoading(true);

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();

                            // Save Google name to profile
                            if (user != null) {
                                String displayName = user.getDisplayName();
                                String email = user.getEmail();

                                android.util.Log.d("LoginActivity", "Google name: " + displayName);
                                android.util.Log.d("LoginActivity", "Google email: " + email);

                                // Save the name to profile
                                saveGoogleNameToProfile(displayName);
                            }

                            Toast.makeText(LoginActivity.this, getString(R.string.welcome), Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        } else {
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : getString(R.string.auth_failed);
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Save Google account name to user profile
     */
    private void saveGoogleNameToProfile(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return;
        }

        com.afonso.fiveminutediary.data.DataRepository repo =
                com.afonso.fiveminutediary.data.DataRepository.getInstance(this);

        repo.getOrCreateUserProfile(profile -> {
            // Only update if name is still empty
            if (profile.getUserName() == null || profile.getUserName().isEmpty()) {
                profile.setUserName(displayName);
                repo.updateUserProfile(profile, task -> {
                    android.util.Log.d("LoginActivity", "Profile updated with Google name");
                });
            }
        });
    }

    private void handleForgotPassword() {
        String email = emailInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError(getString(R.string.enter_email));
            emailInput.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.email_invalid));
            emailInput.requestFocus();
            return;
        }

        setLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this,
                                    String.format(getString(R.string.password_reset_sent), email),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : getString(R.string.password_reset_error);
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingProgress.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            googleSignInButton.setEnabled(false);
        } else {
            loadingProgress.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            googleSignInButton.setEnabled(true);
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
        startActivity(intent);
    }

    private void navigateToEmailVerification() {
        Intent intent = new Intent(LoginActivity.this, EmailVerificationActivity.class);
        startActivity(intent);
    }
}