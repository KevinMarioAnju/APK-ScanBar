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
        if (isScanActive) {
            binding.btnNavScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent_teal)));
            binding.btnNavScan.setTextColor(getColor(R.color.white));
            binding.btnNavScan.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.white)));

            binding.btnNavDirectory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnNavDirectory.setTextColor(getColor(R.color.text_medium));
            binding.btnNavDirectory.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.text_medium)));

            // Shift indicator line to the left (under Scan)
            binding.navIndicator.animate().translationX(-60).setDuration(200).start();
        } else {
            binding.btnNavDirectory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent_teal)));
            binding.btnNavDirectory.setTextColor(getColor(R.color.white));
            binding.btnNavDirectory.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.white)));

            binding.btnNavScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            binding.btnNavScan.setTextColor(getColor(R.color.text_medium));
            binding.btnNavScan.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.text_medium)));

            // Shift indicator line to the right (under Directory)
            binding.navIndicator.animate().translationX(60).setDuration(200).start();
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