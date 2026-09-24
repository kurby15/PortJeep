package com.example.portjeep.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.portjeep.MainActivity;
import com.example.portjeep.R;
import com.example.portjeep.auth.LogInActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashLoading extends AppCompatActivity {
    private final Handler handler = new Handler();
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            // User is already logged in, navigate directly to MainActivity without showing splash screen
            Intent intent = new Intent(SplashLoading.this, MainActivity.class);
            intent.putExtra("USER_UID", currentUser.getUid());
            startActivity(intent);
            finish();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_loading);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        runnable = () -> {
            // No user logged in, navigate to LogInActivity
            startActivity(new Intent(SplashLoading.this, LogInActivity.class));
            finish();
        };

        // Delay for splash screen when user is not logged in
        handler.postDelayed(runnable, 7100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}
