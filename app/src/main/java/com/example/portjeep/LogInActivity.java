package com.example.portjeep;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

public class LogInActivity extends AppCompatActivity {
    private EditText etEmail, etPassword;
    private TextView tvError, tvForgotPassword;
    private Button btnLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log_in);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);

        // FORGOT PASSWORD BUTTON
        tvForgotPassword.setOnClickListener(view ->
                Toast.makeText(LogInActivity.this, "Under maintenance", Toast.LENGTH_SHORT).show()
        );

        // LOGIN BUTTON
        btnLogin.setOnClickListener(view -> handleLogin());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Auto-login check with role validation
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            setLoadingState(true);
            validateUserRoleAndProceed(currentUser, true);
        }
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Reset inputs to default border state first
        etEmail.setBackgroundResource(R.drawable.bg_pill_input);
        etPassword.setBackgroundResource(R.drawable.bg_pill_input);
        tvError.setVisibility(View.GONE);

        // Separate field validation checks
        if (email.isEmpty() && password.isEmpty()) {
            etEmail.setBackgroundResource(R.drawable.bg_pill_input_error);
            etPassword.setBackgroundResource(R.drawable.bg_pill_input_error);
            showError("Please enter your email and password.");
            return;
        }

        if (email.isEmpty()) {
            etEmail.setBackgroundResource(R.drawable.bg_pill_input_error);
            showError("Please enter your email address.");
            return;
        }

        if (password.isEmpty()) {
            etPassword.setBackgroundResource(R.drawable.bg_pill_input_error);
            showError("Please enter your password.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setBackgroundResource(R.drawable.bg_pill_input_error);
            showError("Please enter a valid email address.");
            return;
        }

        setLoadingState(true);

        // 1. Authenticate with Firebase Auth
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        validateUserRoleAndProceed(user, false);
                    } else {
                        setLoadingState(false);
                        // Highlight both fields on general authentication failure
                        etEmail.setBackgroundResource(R.drawable.bg_pill_input_error);
                        etPassword.setBackgroundResource(R.drawable.bg_pill_input_error);

                        String errorMsg = task.getException() != null ?
                                task.getException().getLocalizedMessage() : "Authentication failed. Please check your credentials.";
                        showError(errorMsg);
                    }
                });
    }

    private void validateUserRoleAndProceed(FirebaseUser user, boolean isAutoLogin) {
        // First, check Firebase Auth Custom Claims as recommended by your friend
        user.getIdToken(true)
                .addOnSuccessListener(result -> {
                    Map<String, Object> claims = result.getClaims();
                    String role = claims.get("role") != null ? String.valueOf(claims.get("role")) : null;

                    if (role != null && !role.trim().isEmpty()) {
                        // Custom claim found, evaluate it directly
                        if (isAuthorizedRole(role)) {
                            setLoadingState(false);
                            if (!isAutoLogin) {
                                Toast.makeText(LogInActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                            }
                            navigateToMain(user.getUid());
                        } else {
                            denyAccess("Unauthorized Access\nPlease contact your administrator.");
                        }
                    } else {
                        // Fallback: If custom claims aren't set up yet, fallback to checking Firestore
                        Log.w("AuthWarning", "Custom claim 'role' not found. Falling back to Firestore lookup.");
                        fallbackFirestoreRoleCheck(user.getUid(), isAutoLogin);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AuthError", "Failed to fetch ID token, falling back to Firestore", e);
                    fallbackFirestoreRoleCheck(user.getUid(), isAutoLogin);
                });
    }

    private void fallbackFirestoreRoleCheck(String uid, boolean isAutoLogin) {
        db.collection("File201")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        denyAccess("Access Denied: Your account profile could not be found. Please contact the administrator.");
                        return;
                    }

                    Object posIdObj = documentSnapshot.get("position_id");
                    String positionId = posIdObj != null ? String.valueOf(posIdObj) : null;

                    if (positionId == null || positionId.trim().isEmpty()) {
                        denyAccess("Access Denied: Your account setup is incomplete. Please contact support.");
                        return;
                    }

                    db.collection("Positions")
                            .document(positionId)
                            .get()
                            .addOnSuccessListener(posDoc -> {
                                if (!posDoc.exists()) {
                                    denyAccess("Access Denied: We could not verify your account permissions. Please contact support.");
                                    return;
                                }

                                String rawTitle = null;
                                Map<String, Object> data = posDoc.getData();

                                if (data != null && !data.isEmpty()) {
                                    String[] commonKeys = {"title", "name", "position", "position_name", "role", "description", "Title", "Name", "Position"};
                                    for (String key : commonKeys) {
                                        if (data.containsKey(key) && data.get(key) instanceof String) {
                                            rawTitle = (String) data.get(key);
                                            break;
                                        }
                                    }

                                    if (rawTitle == null) {
                                        for (Object val : data.values()) {
                                            if (val instanceof String && !((String) val).trim().isEmpty()) {
                                                rawTitle = (String) val;
                                                break;
                                            }
                                        }
                                    }
                                }

                                if (rawTitle == null) {
                                    denyAccess("Access Denied: There was an issue verifying your account details. Please contact the administrator.");
                                    return;
                                }

                                String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                                String positionTitle = CryptoUtils.decrypt(rawTitle, secretKey);

                                if (isAuthorizedRole(positionTitle)) {
                                    setLoadingState(false);
                                    if (!isAutoLogin) {
                                        Toast.makeText(LogInActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                                    }
                                    navigateToMain(uid);
                                } else {
                                    denyAccess("Unauthorized Access\nPlease contact your administrator.");
                                }
                            })
                            .addOnFailureListener(e -> denyAccess("Authentication Error: Unable to verify account permissions. Please try again later."));
                })
                .addOnFailureListener(e -> denyAccess("Authentication Error: Unable to retrieve account profile. Please try again later."));
    }

    private boolean isAuthorizedRole(String roleTitle) {
        if (roleTitle == null || roleTitle.trim().isEmpty()) {
            return false;
        }

        String upper = roleTitle.toUpperCase().trim();
        return upper.contains("DRIVER") || upper.contains("PUBLIC ASSISTANT OFFICER") || upper.contains("PAO");
    }

    private void denyAccess(String message) {
        mAuth.signOut(); // Revoke session
        setLoadingState(false);
        showError(message);
    }

    private void navigateToMain(String uid) {
        Intent intent = new Intent(LogInActivity.this, MainActivity.class);
        intent.putExtra("USER_UID", uid);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");
            btnLogin.setAlpha(0.7f);
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign In");
            btnLogin.setAlpha(1.0f);
        }
    }
}