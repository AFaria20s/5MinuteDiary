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

        // VERIFICAR AUTENTICAÇÃO
        checkAuthentication();

        setContentView(R.layout.activity_profile);

        repo = DataRepository.getInstance(this);
        auth = FirebaseAuth.getInstance();

        // Configure Google Sign In para logout
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

        // Iniciar listener em tempo real para profile
        startRealtimeProfileUpdates();

        // Recarregar stats
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

        // Parar listener
        repo.stopProfileListener();

        // Auto-save name
        saveName();

        if (userProfile != null) {
            userProfile.setLastOpenedTimestamp(System.currentTimeMillis());
            repo.updateUserProfileField("lastOpenedTimestamp", System.currentTimeMillis(), null);
        }
    }

    /**
     * Verifica se o utilizador está autenticado
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
     * Iniciar updates em tempo real do perfil
     */
    private void startRealtimeProfileUpdates() {
        repo.startProfileListener(profile -> {
            runOnUiThread(() -> {
                userProfile = profile;
                if (userProfile != null && userProfile.getUserName() != null) {
                    // Só atualizar se o campo não está focado
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

        if (daysSinceFirstUse == 0) {
            journeyDaysText.setText("Hoje");
        } else if (daysSinceFirstUse == 1) {
            journeyDaysText.setText("Há 1 dia");
        } else {
            journeyDaysText.setText("Há " + daysSinceFirstUse + " dias");
        }

        // Total entries (com cache)
        repo.getEntryCount(count -> {
            runOnUiThread(() -> {
                totalEntriesText.setText(String.valueOf(count));
            });
        });

        // Streak (com cache)
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

            // Atualizar apenas o campo "userName" para eficiência
            repo.updateUserProfileField("userName", name, task -> {
                // Opcional: mostrar feedback
            });
        }
    }

    private void setupPremiumCard() {
        premiumStatsCard.setOnClickListener(v -> {
            Toast.makeText(this, "Funcionalidade premium - Em breve!", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Mostrar diálogo de confirmação de logout
     */
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Terminar sessão")
                .setMessage("Tens a certeza que queres sair?")
                .setPositiveButton("Sair", (dialog, which) -> performLogout())
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Executar logout
     */
    private void performLogout() {
        // Limpar cache do repository
        repo.clearCache();

        // Sign out from Firebase
        auth.signOut();

        // Sign out from Google (se fez login com Google)
        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Navigate to login
            Intent intent = new Intent(this, com.afonso.fiveminutediary.auth.LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Mostrar diálogo de confirmação de eliminação de conta
     */
    private void showDeleteAccountDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Verificar se é conta Google ou Email/Password
        boolean isGoogleAccount = false;
        for (int i = 0; i < user.getProviderData().size(); i++) {
            if (user.getProviderData().get(i).getProviderId().equals("google.com")) {
                isGoogleAccount = true;
                break;
            }
        }

        if (isGoogleAccount) {
            // Para conta Google, não precisa de password
            showDeleteConfirmationForGoogle();
        } else {
            // Para conta Email/Password, pedir password
            showPasswordConfirmationDialog();
        }
    }

    /**
     * Confirmação de eliminação para contas Google
     */
    private void showDeleteConfirmationForGoogle() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Conta")
                .setMessage("Esta ação é PERMANENTE e IRREVERSÍVEL.\n\n" +
                        "• Todas as tuas entradas serão eliminadas\n" +
                        "• O teu perfil será apagado\n" +
                        "• Não será possível recuperar os dados\n\n" +
                        "Tens a certeza absoluta?")
                .setPositiveButton("Sim, eliminar PERMANENTEMENTE", (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Diálogo de confirmação com password
     */
    private void showPasswordConfirmationDialog() {
        // Criar layout customizado para o diálogo
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_confirmation, null);
        EditText passwordInput = dialogView.findViewById(R.id.passwordConfirmInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Eliminar Conta")
                .setMessage("Para eliminar a tua conta, confirma a tua password.")
                .setView(dialogView)
                .setPositiveButton("Eliminar", null) // Null para controlar manualmente
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(view -> {
                String password = passwordInput.getText().toString().trim();

                if (TextUtils.isEmpty(password)) {
                    passwordInput.setError("Password é obrigatória");
                    passwordInput.requestFocus();
                    return;
                }

                // Desabilitar botão durante verificação
                button.setEnabled(false);
                passwordInput.setEnabled(false);

                // Re-autenticar e eliminar
                reauthenticateAndDelete(password, dialog);
            });
        });

        dialog.show();
    }

    /**
     * Re-autenticar utilizador e eliminar conta
     */
    private void reauthenticateAndDelete(String password, AlertDialog dialog) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Erro ao obter utilizador", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            return;
        }

        // Criar credencial com email e password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        // Re-autenticar
        user.reauthenticate(credential)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Password correta, mostrar confirmação final
                            dialog.dismiss();
                            showFinalDeleteConfirmation();
                        } else {
                            // Password incorreta
                            dialog.dismiss();
                            new AlertDialog.Builder(ProfileActivity.this)
                                    .setTitle("Password incorreta")
                                    .setMessage("A password que inseriste está incorreta. Tenta novamente.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    }
                });
    }

    /**
     * Confirmação final antes de eliminar
     */
    private void showFinalDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("ÚLTIMA CONFIRMAÇÃO")
                .setMessage("Tens a CERTEZA ABSOLUTA que queres eliminar a tua conta?\n\n" +
                        "• Todas as entradas serão eliminadas\n" +
                        "• O teu perfil será apagado\n" +
                        "• Esta ação é IRREVERSÍVEL\n\n" +
                        "Não será possível recuperar os dados.")
                .setPositiveButton("SIM, ELIMINAR TUDO", (dialog, which) -> {
                    performAccountDeletion();
                })
                .setNegativeButton("Não, voltar atrás", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Executar eliminação da conta
     */
    private void performAccountDeletion() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("A eliminar conta...")
                .setMessage("Por favor aguarda.")
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
                                // Limpar cache
                                repo.clearCache();

                                // Sign out do Google se necessário
                                googleSignInClient.signOut();

                                // Mostrar mensagem e voltar ao login
                                Toast.makeText(ProfileActivity.this,
                                        "Conta eliminada com sucesso",
                                        Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(ProfileActivity.this,
                                        com.afonso.fiveminutediary.auth.LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                // Erro ao eliminar conta
                                String errorMessage = task.getException() != null ?
                                        task.getException().getMessage() : "Erro desconhecido";

                                new AlertDialog.Builder(ProfileActivity.this)
                                        .setTitle("Erro ao eliminar conta")
                                        .setMessage("Não foi possível eliminar a conta:\n\n" + errorMessage +
                                                "\n\nPor favor tenta fazer logout e login novamente antes de eliminar.")
                                        .setPositiveButton("OK", null)
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