package com.example.scanbar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.scanbar.databinding.ActivityLoginBinding;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (username.equals("admin") && password.equals("admin123")) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("ROLE", "admin");
                startActivity(intent);
                finish();
            } else if (username.equals("inspektur") && password.equals("inspektur123")) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("ROLE", "inspektur");
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Login Gagal! Cek Username/Password", Toast.LENGTH_SHORT).show();
            }
        });
    }
}