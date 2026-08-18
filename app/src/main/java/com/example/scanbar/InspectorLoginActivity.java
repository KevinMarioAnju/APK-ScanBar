package com.example.scanbar;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.scanbar.databinding.ActivityInspectorLoginBinding;

public class InspectorLoginActivity extends AppCompatActivity {
    private ActivityInspectorLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInspectorLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> {
            String username = binding.etUsername.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Silakan isi username dan password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Kredensial untuk Admin
            if (username.equals("admin") && password.equals("admin123")) {
                Toast.makeText(this, "Login Berhasil sebagai Admin", Toast.LENGTH_SHORT).show();
                goToMain("admin");
            } 
            // Kredensial untuk Inspektur
            else if (username.equals("inspektur") && password.equals("inspektur123")) {
                Toast.makeText(this, "Login Berhasil sebagai Inspektur", Toast.LENGTH_SHORT).show();
                goToMain("inspektur");
            } 
            else {
                Toast.makeText(this, "Username atau Password salah", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void goToMain(String role) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("ROLE", role);
        startActivity(intent);
        finish();
    }
}