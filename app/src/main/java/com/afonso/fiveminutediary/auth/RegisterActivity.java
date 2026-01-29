package com.afonso.fiveminutediary.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.R;
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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleSignInClient;

    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private Button registerButton;
    private Button googleSignInButton;
    private TextView loginText;
    private ImageButton backButton;
    private ProgressBar loadingProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        initViews();
        setupListeners();
    }

    private void initViews() {
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        registerButton = findViewById(R.id.registerButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        loginText = findViewById(R.id.loginText);
        backButton = findViewById(R.id.backButton);
        loadingProgress = findViewById(R.id.loadingProgress);
    }

    private void setupListeners() {
        registerButton.setOnClickListener(v -> registerWithEmail());
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
        loginText.setOnClickListener(v -> finish());
        backButton.setOnClickListener(v -> finish());
    }

    private void registerWithEmail() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            nameInput.setError("Nome é obrigatório");
            nameInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email é obrigatório");
            emailInput.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Email inválido");
            emailInput.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password é obrigatória");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password deve ter pelo menos 6 caracteres");
            passwordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("As passwords não coincidem");
            confirmPasswordInput.requestFocus();
            return;
        }

        // Show loading
        setLoading(true);

        // Create user with Firebase
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                // Update display name
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();

                                user.updateProfile(profileUpdates)
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    // Send verification email
                                                    sendVerificationEmail(user);
                                                    // Create user document in Firestore
                                                    createUserDocument(user.getUid(), name, email);
                                                }
                                            }
                                        });
                            }
                        } else {
                            setLoading(false);
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : "Erro ao criar conta";
                            Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void sendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this,
                                    "Conta criada! Verifica o teu email.",
                                    Toast.LENGTH_LONG).show();
                            navigateToEmailVerification();
                        } else {
                            Toast.makeText(RegisterActivity.this,
                                    "Erro ao enviar email de verificação",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void createUserDocument(String uid, String name, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("createdAt", System.currentTimeMillis());
        user.put("emailVerified", false);

        db.collection("users")
                .document(uid)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    // User document created successfully
                })
                .addOnFailureListener(e -> {
                    // Failed to create user document (non-critical)
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
                Toast.makeText(this, "Google sign in falhou: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                            if (user != null) {
                                // Check if new user
                                if (task.getResult().getAdditionalUserInfo().isNewUser()) {
                                    // Create user document
                                    createUserDocument(
                                            user.getUid(),
                                            user.getDisplayName(),
                                            user.getEmail()
                                    );
                                }
                                Toast.makeText(RegisterActivity.this, "Bem-vindo!", Toast.LENGTH_SHORT).show();
                                navigateToMain();
                            }
                        } else {
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : "Autenticação falhou";
                            Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingProgress.setVisibility(View.VISIBLE);
            registerButton.setEnabled(false);
            googleSignInButton.setEnabled(false);
        } else {
            loadingProgress.setVisibility(View.GONE);
            registerButton.setEnabled(true);
            googleSignInButton.setEnabled(true);
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(RegisterActivity.this, com.afonso.fiveminutediary.viewmodel.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToEmailVerification() {
        Intent intent = new Intent(RegisterActivity.this, EmailVerificationActivity.class);
        startActivity(intent);
        finish();
    }
}