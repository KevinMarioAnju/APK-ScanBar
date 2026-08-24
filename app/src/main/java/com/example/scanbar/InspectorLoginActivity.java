package com.example.scanbar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.User;
import com.example.scanbar.databinding.ActivityInspectorLoginBinding;
import java.util.concurrent.Executors;

public class InspectorLoginActivity extends AppCompatActivity {
    private ActivityInspectorLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInspectorLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        seedDefaultUsers();

        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Silakan isi username dan password", Toast.LENGTH_SHORT).show();
                return;
            }

            performLogin(username, password);
        });
    }

    private void seedDefaultUsers() {
        Executors.newSingleThreadExecutor().execute(() -> {
            var userDao = AppDatabase.getDatabase(this).userDao();
            
            // Check specifically for admin
            if (userDao.getUserByUsername("admin") == null) {
                userDao.insert(new User("admin", "admin123", "admin", "Admin Safety"));
            }
            
            // Check specifically for default inspector
            if (userDao.getUserByUsername("inspektur") == null) {
                userDao.insert(new User("inspektur", "inspektur123", "inspektur", "Inspektur Lapangan"));
            }
        });
    }

    private void performLogin(String username, String password) {
        Executors.newSingleThreadExecutor().execute(() -> {
            var user = AppDatabase.getDatabase(this).userDao().getUser(username, password);
            runOnUiThread(() -> {
                if (user != null) {
                    // Save Role to SharedPreferences for global access
                    getSharedPreferences("ScanBarSession", MODE_PRIVATE)
                            .edit()
                            .putString("ROLE", user.role)
                            .putString("NICKNAME", user.nickname)
                            .apply();

                    Toast.makeText(this, "Login Berhasil sebagai " + (user.nickname != null ? user.nickname : user.role), Toast.LENGTH_SHORT).show();
                    goToMain(user.role, user.nickname);
                } else {
                    Toast.makeText(this, "Username atau Password salah", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void goToMain(String role, String nickname) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("ROLE", role);
        intent.putExtra("NICKNAME", nickname);
        startActivity(intent);
        finish();
    }
}