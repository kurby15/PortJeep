package com.example.portjeep.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
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
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class LogInActivity extends AppCompatActivity {
    private NestedScrollView scrollView;
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private TextView tvError;
    private MaterialButton btnLogin;
    private MaterialButton btnBiometric;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private RecaptchaTasksClient recaptchaTasksClient = null;
    private String recaptchaInitError = null;

    private SharedPreferences encryptedPrefs;
    private static final String PREFS_SETTINGS = "portjeep_settings";
    private static final String KEY_SCREEN_LOCK = "screen_lock_enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log_in);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize reCAPTCHA immediately
        initializeRecaptcha();

        scrollView = findViewById(R.id.login_scroll_view);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        btnLogin = findViewById(R.id.btnLogin);
        btnBiometric = findViewById(R.id.btnBiometric);

        // Setup reCAPTCHA notice links
        TextView tvRecaptchaNotice = findViewById(R.id.tv_recaptcha_notice);
        if (tvRecaptchaNotice != null) {
            tvRecaptchaNotice.setText(Html.fromHtml(getString(R.string.login_recaptcha_notice), Html.FROM_HTML_MODE_LEGACY));
            tvRecaptchaNotice.setMovementMethod(LinkMovementMethod.getInstance());
        }

        setupBiometricAuth();
        setupInputErrorReset();

        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPassword.requestFocus();
                return true;
            }
            return false;
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLogin();
                return true;
            }
            return false;
        });

        if (scrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
                return insets;
            });

            scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect r = new Rect();
                scrollView.getWindowVisibleDisplayFrame(r);
                int keypadHeight = scrollView.getRootView().getHeight() - r.bottom;
                if (keypadHeight > scrollView.getRootView().getHeight() * 0.15) {
                    View focusedView = getCurrentFocus();
                    if (focusedView != null) scrollToView(focusedView);
                }
            });
        }

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword.setOnClickListener(view -> showResetPasswordDialog());
        btnLogin.setOnClickListener(view -> handleLogin());
    }

    private void initializeRecaptcha() {
        String rawKey = BuildConfig.RECAPTCHA_SITE_KEY;
        String siteKey = (rawKey != null) ? rawKey.replace("\"", "").trim() : "";

        if (siteKey.isEmpty() || siteKey.equals("null")) {
            recaptchaInitError = "Site Key missing in local.properties";
            return;
        }

        recaptchaInitError = null;
        Recaptcha.getTasksClient(getApplication(), siteKey)
                .addOnSuccessListener(this, client -> {
                    this.recaptchaTasksClient = client;
                    Log.d("Recaptcha", "Initialization successful");
                })
                .addOnFailureListener(this, e -> {
                    this.recaptchaInitError = e.getMessage();
                    Log.e("Recaptcha", "Initialization failed: " + e.getMessage());
                });
    }

    private void handleLogin() {
        String email = Objects.requireNonNull(etEmail.getText()).toString().trim();
        String password = Objects.requireNonNull(etPassword.getText()).toString().trim();

        clearErrors();

        if (email.isEmpty() || password.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (email.isEmpty()) showError("Please enter your email address.");
            else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) showError("Please enter a valid email address.");
            else showError("Please enter your password.");
            return;
        }

        setLoadingState(true);

        if (recaptchaTasksClient == null) {
            if (recaptchaInitError != null) {
                showError("reCAPTCHA Error: " + recaptchaInitError);
                initializeRecaptcha();
            } else {
                showError("Security check initializing. Please wait a moment.");
            }
            setLoadingState(false);
            return;
        }

        recaptchaTasksClient.executeTask(RecaptchaAction.LOGIN)
                .addOnSuccessListener(this, token -> {
                    performFirebaseLogin(email, password);
                })
                .addOnFailureListener(this, e -> {
                    setLoadingState(false);
                    showError("Security verification failed. Please try again.");
                    Log.e("Recaptcha", "Execution failed: " + e.getMessage());
                });
    }

    private void performFirebaseLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful() && mAuth.getCurrentUser() != null) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        saveCredentials(email, password, user.getUid());
                        validateUserRoleAndProceed(user);
                    } else {
                        setLoadingState(false);
                        String errorMsg = task.getException() != null ? 
                                task.getException().getLocalizedMessage() : "Login failed.";
                        showError(errorMsg);
                    }
                });
    }

    private void validateUserRoleAndProceed(FirebaseUser user) {
        user.getIdToken(true).addOnSuccessListener(result -> {
            Map<String, Object> claims = result.getClaims();
            String role = (String) claims.get("role");
            if (role != null && isAuthorizedRole(role)) {
                setLoadingState(false);
                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                navigateToMain(user.getUid());
            } else {
                fallbackFirestoreRoleCheck(user.getUid());
            }
        }).addOnFailureListener(e -> fallbackFirestoreRoleCheck(user.getUid()));
    }

    private void fallbackFirestoreRoleCheck(String uid) {
        db.collection("File201").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { denyAccess("Access Denied: Profile not found."); return; }
            String posId = doc.getString("position_id");
            if (posId == null) { denyAccess("Access Denied: Incomplete profile."); return; }
            db.collection("Positions").document(posId).get().addOnSuccessListener(posDoc -> {
                String rawTitle = posDoc.getString("title");
                if (rawTitle == null) { denyAccess("Access Denied: Permissions error."); return; }
                String title = CryptoUtils.decrypt(rawTitle, BuildConfig.CRYPTO_SECRET_KEY);
                if (isAuthorizedRole(title)) {
                    setLoadingState(false);
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain(uid);
                } else {
                    denyAccess("Unauthorized Access.");
                }
            }).addOnFailureListener(e -> denyAccess("Verification failed."));
        }).addOnFailureListener(e -> denyAccess("Profile retrieval failed."));
    }

    private boolean isAuthorizedRole(String role) {
        if (role == null) return false;
        String upper = role.toUpperCase().trim();
        return upper.contains("DRIVER") || upper.contains("PUBLIC ASSISTANT") || upper.contains("PAO");
    }

    private void denyAccess(String message) {
        mAuth.signOut();
        setLoadingState(false);
        showError(message);
    }

    private void navigateToMain(String uid) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("USER_UID", uid);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void setLoadingState(boolean isLoading) {
        btnLogin.setEnabled(!isLoading);
        btnLogin.setText(isLoading ? "Signing in..." : "Sign In");
        btnLogin.setAlpha(isLoading ? 0.7f : 1.0f);
    }

    private void clearErrors() {
        tvError.setVisibility(View.GONE);
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPassword != null) tilPassword.setError(null);
    }

    private void scrollToView(View view) {
        if (view == null || scrollView == null) return;
        scrollView.postDelayed(() -> {
            View target = view;
            ViewParent parent = view.getParent();
            while (parent != null && parent instanceof View) {
                if (parent instanceof TextInputLayout) {
                    target = (View) parent;
                    break;
                }
                parent = parent.getParent();
            }
            scrollView.smoothScrollTo(0, Math.max(0, target.getTop() - 100));
        }, 100);
    }

    private void setupBiometricAuth() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            encryptedPrefs = EncryptedSharedPreferences.create(this, "secure_auth_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) { performBiometricLogin(); }
            });
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder().setTitle("Secure Login").setSubtitle("Log in using biometrics")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
            
            checkBiometricAvailability();
            btnBiometric.setOnClickListener(view -> biometricPrompt.authenticate(promptInfo));
        } catch (Exception e) { 
            if (btnBiometric != null) btnBiometric.setVisibility(View.GONE); 
        }
    }

    private void checkBiometricAvailability() {
        if (btnBiometric == null) return;
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        String savedUid = encryptedPrefs != null ? encryptedPrefs.getString("saved_uid", null) : null;
        boolean isLockEnabled = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE).getBoolean(KEY_SCREEN_LOCK + "_" + (savedUid != null ? savedUid : ""), false);
        btnBiometric.setVisibility(canAuth == BiometricManager.BIOMETRIC_SUCCESS && savedUid != null && isLockEnabled ? View.VISIBLE : View.GONE);
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
            encryptedPrefs.edit().putString("saved_email", email).putString("saved_password", password).putString("saved_uid", uid).apply();
            checkBiometricAvailability();
        }
    }

    private void setupInputErrorReset() {
        TextWatcher tw = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearErrors(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etEmail.addTextChangedListener(tw);
        etPassword.addTextChangedListener(tw);
    }

    private void showResetPasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_reset_password, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                .setView(view).setCancelable(true).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        TextInputLayout tilReset = view.findViewById(R.id.til_reset_email);
        TextInputEditText etReset = view.findViewById(R.id.et_reset_email);
        MaterialButton btnSend = view.findViewById(R.id.btn_send_reset);
        String currentEmail = Objects.requireNonNull(etEmail.getText()).toString().trim();
        if (!currentEmail.isEmpty()) etReset.setText(currentEmail);
        view.findViewById(R.id.btn_cancel_reset).setOnClickListener(v -> dialog.dismiss());
        btnSend.setOnClickListener(v -> {
            String inputEmail = Objects.requireNonNull(etReset.getText()).toString().trim();
            if (inputEmail.isEmpty()) tilReset.setError("Email required");
            else if (!Patterns.EMAIL_ADDRESS.matcher(inputEmail).matches()) tilReset.setError("Invalid email");
            else { 
                dialog.dismiss(); 
                mAuth.sendPasswordResetEmail(inputEmail).addOnSuccessListener(aVoid -> Toast.makeText(this, "Reset link sent", Toast.LENGTH_SHORT).show()); 
            }
        });
        dialog.show();
    }
}
