package at.fhj.togethergo.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import at.fhj.togethergo.R;
import at.fhj.togethergo.databinding.ActivityLoginBinding;

/**
 * Login and registration screen.
 * <p>
 * One form serves both modes: the filled button runs the primary action of the current mode, the
 * text button toggles between login and registration. Signed-in users are forwarded to
 * {@link MainActivity} directly.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // google-services.json is not part of the repository (see README). Without it Firebase
        // is not initialized, so show a hint instead of crashing.
        if (FirebaseApp.getApps(this).isEmpty()) {
            Toast.makeText(this, R.string.err_firebase_not_configured, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Skip the form if a user is already signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToMainActivity();
            return;
        }

        binding.btnLogin.setOnClickListener(v -> {
            if (registerMode) {
                registerUser();
            } else {
                loginUser();
            }
        });
        binding.btnRegister.setOnClickListener(v -> setRegisterMode(!registerMode));

        // make sure "back" returns to the login form instead of leaving the app
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (registerMode) {
                    setRegisterMode(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /** Toggles between login and registration mode (name field and button labels). */
    private void setRegisterMode(boolean enabled) {
        registerMode = enabled;
        binding.etName.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.btnLogin.setText(enabled ? R.string.action_register : R.string.action_login);
        binding.btnRegister.setText(enabled ? R.string.action_back_to_login : R.string.action_register);
        if (enabled) {
            binding.etName.requestFocus();
        }
    }

    /** Signs in with email and password and opens {@link MainActivity} on success. */
    private void loginUser() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    binding.loadingBox.getRoot().setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        goToMainActivity();
                    } else {
                        Toast.makeText(this, getString(R.string.err_auth_failed, task.getException().getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Creates a new account after validating the input. */
    private void registerUser() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String name = binding.etName.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
            Toast.makeText(this, getString(R.string.err_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, getString(R.string.err_password_short), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.loadingBox.getRoot().setVisibility(View.VISIBLE);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveUserToFirestore(user, name);
                    } else {
                        binding.loadingBox.getRoot().setVisibility(View.GONE);
                        Toast.makeText(this, getString(R.string.err_registration_failed, task.getException().getMessage()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Creates the Firestore profile for a newly registered user, then opens
     * {@link MainActivity}.
     */
    private void saveUserToFirestore(FirebaseUser user, String name) {
        if (user == null) return;

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("name", name);
        userData.put("level", 1);
        userData.put("currentPoints", 0);
        userData.put("totalQuests", 0);

        db.collection("users").document(user.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    binding.loadingBox.getRoot().setVisibility(View.GONE);
                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    binding.loadingBox.getRoot().setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.err_save_user_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
    }

    private void goToMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }
}
