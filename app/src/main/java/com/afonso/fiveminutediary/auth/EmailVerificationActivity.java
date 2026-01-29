package com.afonso.fiveminutediary.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.viewmodel.MainActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class EmailVerificationActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private TextView emailText;
    private Button resendButton;
    private Button continueButton;
    private TextView logoutText;
    private ProgressBar loadingProgress;

    private Handler verificationCheckHandler;
    private Runnable verificationCheckRunnable;
    private boolean isCheckingVerification = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            navigateToLogin();
            return;
        }

        initViews();
        setupListeners();
        displayEmail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startVerificationCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVerificationCheck();
    }

    private void initViews() {
        emailText = findViewById(R.id.emailText);
        resendButton = findViewById(R.id.resendButton);
        continueButton = findViewById(R.id.continueButton);
        logoutText = findViewById(R.id.logoutText);
        loadingProgress = findViewById(R.id.loadingProgress);
    }

    private void setupListeners() {
        resendButton.setOnClickListener(v -> resendVerificationEmail());
        continueButton.setOnClickListener(v -> checkEmailVerification());
        logoutText.setOnClickListener(v -> logout());
    }

    private void displayEmail() {
        if (currentUser != null && currentUser.getEmail() != null) {
            emailText.setText("Enviámos um link de verificação para\n" + currentUser.getEmail());
        }
    }

    private void resendVerificationEmail() {
        if (currentUser == null) return;

        setLoading(true);

        currentUser.sendEmailVerification()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            Toast.makeText(EmailVerificationActivity.this,
                                    "Email reenviado!",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            String errorMessage = task.getException() != null ?
                                    task.getException().getMessage() : "Erro ao reenviar email";
                            Toast.makeText(EmailVerificationActivity.this,
                                    errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkEmailVerification() {
        if (currentUser == null) return;

        setLoading(true);

        currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                setLoading(false);

                if (task.isSuccessful()) {
                    currentUser = auth.getCurrentUser();

                    if (currentUser != null && currentUser.isEmailVerified()) {
                        Toast.makeText(EmailVerificationActivity.this,
                                "Email verificado com sucesso!",
                                Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    } else {
                        Toast.makeText(EmailVerificationActivity.this,
                                "Email ainda não verificado. Por favor verifica a tua caixa de entrada.",
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(EmailVerificationActivity.this,
                            "Erro ao verificar. Tenta novamente.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void startVerificationCheck() {
        if (verificationCheckHandler == null) {
            verificationCheckHandler = new Handler(Looper.getMainLooper());
        }

        verificationCheckRunnable = () -> {
            if (!isCheckingVerification && currentUser != null) {
                isCheckingVerification = true;

                currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        isCheckingVerification = false;

                        if (task.isSuccessful()) {
                            currentUser = auth.getCurrentUser();

                            if (currentUser != null && currentUser.isEmailVerified()) {
                                Toast.makeText(EmailVerificationActivity.this,
                                        "Email verificado!",
                                        Toast.LENGTH_SHORT).show();
                                navigateToMain();
                                return;
                            }
                        }

                        // CORREÇÃO: Usar verificationCheckRunnable em vez de this
                        // Check again in 5 seconds
                        if (verificationCheckHandler != null && verificationCheckRunnable != null) {
                            verificationCheckHandler.postDelayed(verificationCheckRunnable, 5000);
                        }
                    }
                });
            }
        };

        verificationCheckHandler.postDelayed(verificationCheckRunnable, 5000);
    }

    private void stopVerificationCheck() {
        if (verificationCheckHandler != null && verificationCheckRunnable != null) {
            verificationCheckHandler.removeCallbacks(verificationCheckRunnable);
        }
    }

    private void logout() {
        auth.signOut();
        navigateToLogin();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loadingProgress.setVisibility(View.VISIBLE);
            resendButton.setEnabled(false);
            continueButton.setEnabled(false);
        } else {
            loadingProgress.setVisibility(View.GONE);
            resendButton.setEnabled(true);
            continueButton.setEnabled(true);
        }
    }

    private void navigateToMain() {
        stopVerificationCheck();
        Intent intent = new Intent(EmailVerificationActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        stopVerificationCheck();
        Intent intent = new Intent(EmailVerificationActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVerificationCheck();
    }
}