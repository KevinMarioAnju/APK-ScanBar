package com.example.scanbar;

import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.scanbar.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force Light Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle Role Based UI
        String role = getIntent().getStringExtra("ROLE");
        String nickname = getIntent().getStringExtra("NICKNAME");
        if ("inspektur".equals(role)) {
            binding.bottomNavCard.setVisibility(View.GONE);
            binding.tvGreeting.setText("Halo, " + (nickname != null ? nickname : "Inspektur"));
            binding.btnManageAccounts.setVisibility(View.GONE);
        } else {
            binding.tvGreeting.setText("Halo, " + (nickname != null ? nickname : "Admin Safety"));
            binding.btnManageAccounts.setVisibility(View.VISIBLE);
        }

        // Default Fragment
        loadFragment(new ScanFragment());
        updateNavUI(true);

        binding.btnManageAccounts.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminManagementActivity.class);
            startActivity(intent);
        });

        binding.btnLogout.setOnClickListener(v -> showLogoutDialog());

        binding.btnNavScan.setOnClickListener(v -> {
            loadFragment(new ScanFragment());
            updateNavUI(true);
        });

        binding.btnNavDirectory.setOnClickListener(v -> {
            loadFragment(new DirectoryFragment());
            updateNavUI(false);
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void updateNavUI(boolean isScanActive) {
        int accentColor = androidx.core.content.ContextCompat.getColor(this, R.color.accent_teal);
        int whiteColor = android.graphics.Color.WHITE;
        int textHigh = androidx.core.content.ContextCompat.getColor(this, R.color.text_high);

        if (isScanActive) {
            // Scan Active
            binding.btnNavScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
            binding.btnNavScan.setTextColor(whiteColor);
            binding.btnNavScan.setIconTint(android.content.res.ColorStateList.valueOf(whiteColor));
            binding.btnNavScan.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
            
            // Directory Inactive
            binding.btnNavDirectory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnNavDirectory.setTextColor(textHigh);
            binding.btnNavDirectory.setIconTint(android.content.res.ColorStateList.valueOf(textHigh));
            binding.btnNavDirectory.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        } else {
            // Directory Active
            binding.btnNavDirectory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
            binding.btnNavDirectory.setTextColor(whiteColor);
            binding.btnNavDirectory.setIconTint(android.content.res.ColorStateList.valueOf(whiteColor));
            binding.btnNavDirectory.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start();
            
            // Scan Inactive
            binding.btnNavScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnNavScan.setTextColor(textHigh);
            binding.btnNavScan.setIconTint(android.content.res.ColorStateList.valueOf(textHigh));
            binding.btnNavScan.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
        }
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Apakah Anda yakin ingin keluar?")
                .setPositiveButton("Ya", (dialog, which) -> performLogout())
                .setNegativeButton("Tidak", null)
                .show();
    }

    private void performLogout() {
        Intent intent = new Intent(this, InspectorLoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}