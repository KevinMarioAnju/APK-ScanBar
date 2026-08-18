package com.example.scanbar;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.scanbar.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle Role Based UI
        String role = getIntent().getStringExtra("ROLE");
        if ("inspektur".equals(role)) {
            binding.btnNavDirectory.setVisibility(View.GONE);
        }

        // Default Fragment
        loadFragment(new ScanFragment());

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
        binding.btnNavScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                isScanActive ? getColor(R.color.accent_teal) : android.graphics.Color.TRANSPARENT));
        binding.btnNavScan.setTextColor(isScanActive ? getColor(R.color.white) : getColor(R.color.text_medium));
        
        binding.btnNavDirectory.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                !isScanActive ? getColor(R.color.accent_teal) : android.graphics.Color.TRANSPARENT));
        binding.btnNavDirectory.setTextColor(!isScanActive ? getColor(R.color.white) : getColor(R.color.text_medium));
    }
}