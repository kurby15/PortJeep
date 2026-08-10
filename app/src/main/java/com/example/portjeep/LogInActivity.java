package com.example.portjeep;

import android.content.Intent;
import android.os.Bundle;
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
            validateUserRoleAndProceed(currentUser.getUid(), true);
        }
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.");
            return;
        }

        tvError.setVisibility(View.GONE);
        setLoadingState(true);

        // 1. Authenticate with Firebase Auth
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        validateUserRoleAndProceed(user.getUid(), false);
                    } else {
                        setLoadingState(false);
                        String errorMsg = task.getException() != null ?
                                task.getException().getLocalizedMessage() : "Authentication failed.";
                        showError(errorMsg);
                    }
                });
    }

    private void validateUserRoleAndProceed(String uid, boolean isAutoLogin) {
        // 2. Fetch profile from File201
        db.collection("File201")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        denyAccess("Access Denied: No record found in File201 for this account.");
                        return;
                    }

                    // SAFE RETRIEVAL: position_id might be a Number in Firestore
                    Object posIdObj = documentSnapshot.get("position_id");
                    String positionId = posIdObj != null ? String.valueOf(posIdObj) : null;

                    if (positionId == null || positionId.trim().isEmpty()) {
                        denyAccess("Access Denied: 'position_id' field is missing in your account.");
                        return;
                    }

                    // 3. Resolve Position document from 'Positions' collection
                    db.collection("Positions")
                            .document(positionId)
                            .get()
                            .addOnSuccessListener(posDoc -> {
                                if (!posDoc.exists()) {
                                    denyAccess("Access Denied: Position ID '" + positionId + "' not found in Positions collection.");
                                    return;
                                }

                                String rawTitle = null;
                                Map<String, Object> data = posDoc.getData();

                                if (data != null && !data.isEmpty()) {
                                    // Try common position field keys
                                    String[] commonKeys = {"title", "name", "position", "position_name", "role", "description", "Title", "Name", "Position"};
                                    for (String key : commonKeys) {
                                        if (data.containsKey(key) && data.get(key) instanceof String) {
                                            rawTitle = (String) data.get(key);
                                            break;
                                        }
                                    }

                                    // Fallback to first non-empty string value in document
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
                                    denyAccess("Access Denied: Could not read position title field.");
                                    return;
                                }

                                String secretKey = BuildConfig.CRYPTO_SECRET_KEY;
                                String positionTitle = CryptoUtils.decrypt(rawTitle, secretKey);

                                // 4. Validate Driver or PAO role
                                if (isAuthorizedRole(positionTitle)) {
                                    setLoadingState(false);
                                    if (!isAutoLogin) {
                                        Toast.makeText(LogInActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                                    }
                                    navigateToMain(uid);
                                } else {
                                    denyAccess("Access Denied: Only Drivers and PAOs can access this app (Found: " + positionTitle + ").");
                                }
                            })
                            .addOnFailureListener(e -> denyAccess("Positions Check Failed: " + e.getLocalizedMessage()));
                })
                .addOnFailureListener(e -> denyAccess("File201 Check Failed: " + e.getLocalizedMessage()));
    }

    private boolean isAuthorizedRole(String positionTitle) {
        if (positionTitle == null || positionTitle.trim().isEmpty()) {
            return false;
        }

        String upper = positionTitle.toUpperCase().trim();
        return upper.contains("DRIVER") || upper.contains("PUBLIC ASSISTANT") || upper.contains("PAO");
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
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign in");
        }
    }
}