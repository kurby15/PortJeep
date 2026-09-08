package com.example.portjeep.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.portjeep.BuildConfig;
import com.example.portjeep.MainActivity;
import com.example.portjeep.R;
import com.example.portjeep.utils.CryptoUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.Executor;

public class LogInActivity extends AppCompatActivity {
    private NestedScrollView scrollView;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private TextView tvError, tvForgotPassword;
    private MaterialButton btnLogin;
    private MaterialButton btnBiometric;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Biometric components
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private SharedPreferences encryptedPrefs;

    private static final String PREFS_SETTINGS = "portjeep_settings";
    private static final String KEY_SCREEN_LOCK = "screen_lock_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log_in);

        // Initialize Firebase instances
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Fix: Use the correct ID for the NestedScrollView to avoid ClassCastException
        scrollView = findViewById(R.id.login_scroll_view);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnBiometric = findViewById(R.id.btnBiometric);

        setupBiometricAuth();
        setupInputErrorReset();

        // Fix for "Next" button on keyboard in Email field
        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPassword.requestFocus();
                return true;
            }
            return false;
        });

        // "Done" button on keyboard in Password field
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLogin();
                return true;
            }
            return false;
        });

        // Dynamically adjust padding for EdgeToEdge + IME (Keyboard)
        if (scrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
                return insets;
            });

            // Automatically scroll to reveal active input when keyboard pops up
            scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    Rect r = new Rect();
                    scrollView.getWindowVisibleDisplayFrame(r);
                    int screenHeight = scrollView.getRootView().getHeight();
                    int keypadHeight = screenHeight - r.bottom;

                    if (keypadHeight > screenHeight * 0.15) {
                        View focusedView = getCurrentFocus();
                        if (focusedView != null) {
                            scrollToView(focusedView);
                        }
                    }
                }
            });
        }

        // FORGOT PASSWORD BUTTON
        tvForgotPassword.setOnClickListener(view -> showResetPasswordDialog());

        // LOGIN BUTTON
        btnLogin.setOnClickListener(view -> handleLogin());

        // BIOMETRIC BUTTON
        btnBiometric.setOnClickListener(view -> biometricPrompt.authenticate(promptInfo));
    }

    private void scrollToView(View view) {
        if (view == null || scrollView == null) return;
        scrollView.postDelayed(() -> {
            View target = view;
            // Find the TextInputLayout parent to scroll the entire field into view
            ViewParent parent = view.getParent();
            while (parent != null && parent instanceof View) {
                if (parent instanceof TextInputLayout) {
                    target = (View) parent;
                    break;
                }
                parent = parent.getParent();
            }
            
            // Scroll to the top of the field with a comfortable offset
            int targetTop = target.getTop();
            scrollView.smoothScrollTo(0, Math.max(0, targetTop - 100));
        }, 100);
    }

    private void setupBiometricAuth() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    this,
                    "secure_auth_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            executor = ContextCompat.getMainExecutor(this);
            biometricPrompt = new BiometricPrompt(LogInActivity.this,
                    executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    performBiometricLogin();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Secure Login")
                    .setSubtitle("Log in using biometrics or device lock")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            checkBiometricAvailability();

        } catch (GeneralSecurityException | IOException e) {
            Log.e("BiometricSetup", "Error initializing encrypted preferences", e);
            btnBiometric.setVisibility(View.GONE);
        }
    }

    private void checkBiometricAvailability() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        String savedEmail = encryptedPrefs != null ? encryptedPrefs.getString("saved_email", null) : null;
        String savedPassword = encryptedPrefs != null ? encryptedPrefs.getString("saved_password", null) : null;
        String savedUid = encryptedPrefs != null ? encryptedPrefs.getString("saved_uid", null) : null;

        SharedPreferences settingsPrefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        boolean isScreenLockEnabled = false;
        if (savedUid != null) {
            isScreenLockEnabled = settingsPrefs.getBoolean(KEY_SCREEN_LOCK + "_" + savedUid, false);
        }

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && savedEmail != null && savedPassword != null && isScreenLockEnabled) {
            btnBiometric.setVisibility(View.VISIBLE);
        } else {
            btnBiometric.setVisibility(View.GONE);
        }
    }

    private void performBiometricLogin() {
        String email = encryptedPrefs.getString("saved_email", "");
        String password = encryptedPrefs.getString("saved_password", "");

        if (!email.isEmpty() && !password.isEmpty()) {
            etEmail.setText(email);
            etPassword.setText(password);
            handleLogin();
        }
    }

    private void saveCredentials(String email, String password, String uid) {
        if (encryptedPrefs != null) {
            encryptedPrefs.edit()
                    .putString("saved_email", email)
                    .putString("saved_password", password)
                    .putString("saved_uid", uid)
                    .apply();
            checkBiometricAvailability();
        }
    }

    private void showResetPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_password, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextInputLayout tilResetEmail = dialogView.findViewById(R.id.til_reset_email);
        TextInputEditText etResetEmail = dialogView.findViewById(R.id.et_reset_email);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_reset);
        MaterialButton btnSend = dialogView.findViewById(R.id.btn_send_reset);

        String currentEmail = etEmail.getText().toString().trim();
        if (!currentEmail.isEmpty()) {
            etResetEmail.setText(currentEmail);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSend.setOnClickListener(v -> {
            String inputEmail = etResetEmail != null && etResetEmail.getText() != null ? etResetEmail.getText().toString().trim() : "";

            if (inputEmail.isEmpty()) {
                tilResetEmail.setError("Email address is required");
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) {
                tilResetEmail.setError("Please enter a valid email address");
                return;
            }

            tilResetEmail.setError(null);
            dialog.dismiss();
            sendPasswordResetEmail(inputEmail);
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(LogInActivity.this, "Password reset link sent to " + email, Toast.LENGTH_LONG).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(LogInActivity.this, "Failed: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void setupInputErrorReset() {
        TextWatcher errorClearWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearErrors();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        View.OnFocusChangeListener focusClearListener = (v, hasFocus) -> {
            if (hasFocus) {
                clearErrors();
                scrollToView(v);
            }
        };

        etEmail.addTextChangedListener(errorClearWatcher);
        etPassword.addTextChangedListener(errorClearWatcher);

        etEmail.setOnFocusChangeListener(focusClearListener);
        etPassword.setOnFocusChangeListener(focusClearListener);
    }

    private void clearErrors() {
        // Clear global error banner if visible
        if (tvError.getVisibility() == View.VISIBLE) {
            tvError.setVisibility(View.GONE);
            tvError.setText("");
        }

        // Reset TextInputLayout states
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPassword != null) tilPassword.setError(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkBiometricAvailability();
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        clearErrors();

        if (email.isEmpty() && password.isEmpty()) {
            showError("Please enter your email and password.");
            tilEmail.setError(" ");
            tilPassword.setError(" ");
            return;
        }

        if (email.isEmpty()) {
            showError("Please enter your email address.");
            tilEmail.setError(" ");
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password.");
            tilPassword.setError(" ");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.");
            tilEmail.setError(" ");
            return;
        }

        setLoadingState(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveCredentials(email, password, user.getUid());
                        validateUserRoleAndProceed(user, false);
                    } else {
                        setLoadingState(false);
                        String errorMsg = task.getException() != null ?
                                task.getException().getLocalizedMessage() : "Authentication failed. Please check your credentials.";
                        showError(errorMsg);
                        tilEmail.setError(" ");
                        tilPassword.setError(" ");
                    }
                });
    }

    private void validateUserRoleAndProceed(FirebaseUser user, boolean isAutoLogin) {
        user.getIdToken(true)
                .addOnSuccessListener(result -> {
                    Map<String, Object> claims = result.getClaims();
                    String role = claims.get("role") != null ? String.valueOf(claims.get("role")) : null;

                    if (role != null && !role.trim().isEmpty() && isAuthorizedRole(role)) {
                        setLoadingState(false);
                        if (!isAutoLogin) {
                            Toast.makeText(LogInActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        }
                        navigateToMain(user.getUid());
                    } else {
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
                        denyAccess("Access Denied: Your account setup is incomplete. Please contact administrator.");
                        return;
                    }

                    db.collection("Positions")
                            .document(positionId)
                            .get()
                            .addOnSuccessListener(posDoc -> {
                                if (!posDoc.exists()) {
                                    denyAccess("Access Denied: We could not verify your account permissions. Please contact administrator.");
                                    return;
                                }

                                String rawTitle = null;
                                Map<String, Object> data = posDoc.getData();

                                if (data != null && !data.isEmpty()) {
                                    String[] commonKeys = {"title", "name", "position", "position_name", "role", "description"};
                                    for (String key : commonKeys) {
                                        if (data.containsKey(key) && data.get(key) instanceof String) {
                                            rawTitle = (String) data.get(key);
                                            break;
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
                                    denyAccess("Unauthorized Access\nContact administrator for more information.");
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
        return upper.contains("DRIVER") || upper.contains("PUBLIC ASSISTANT") || upper.contains("PAO");
    }

    private void denyAccess(String message) {
        mAuth.signOut();
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
