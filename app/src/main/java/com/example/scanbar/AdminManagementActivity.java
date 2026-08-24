package com.example.scanbar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.User;
import com.example.scanbar.databinding.ActivityAdminManagementBinding;
import java.util.concurrent.Executors;

public class AdminManagementActivity extends AppCompatActivity {
    private ActivityAdminManagementBinding binding;
    private UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new UserAdapter(this::showDeleteConfirmation);
        binding.rvUserList.setLayoutManager(new LinearLayoutManager(this));
        binding.rvUserList.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnAddInspector.setOnClickListener(v -> showAddUserDialog());

        loadUsers();
    }

    private void loadUsers() {
        Executors.newSingleThreadExecutor().execute(() -> {
            var users = AppDatabase.getDatabase(this).userDao().getAllInspectors();
            runOnUiThread(() -> adapter.setUsers(users));
        });
    }

    private void showAddUserDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_user, null);
        EditText etNickname = dialogView.findViewById(R.id.etNickname);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);

        new AlertDialog.Builder(this)
                .setTitle("Tambah Inspektur")
                .setView(dialogView)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String nickname = etNickname.getText().toString().trim();
                    String username = etUsername.getText().toString().trim();
                    String password = etPassword.getText().toString().trim();
                    if (!username.isEmpty() && !password.isEmpty()) {
                        saveUser(new User(username, password, "inspektur", nickname.isEmpty() ? username : nickname));
                    } else {
                        Toast.makeText(this, "Harap isi semua bidang", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void saveUser(User user) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getDatabase(this).userDao().insert(user);
            runOnUiThread(() -> {
                Toast.makeText(this, "Akun Inspektur \"" + user.nickname + "\" berhasil dibuat", Toast.LENGTH_SHORT).show();
                loadUsers();
            });
        });
    }

    private void showDeleteConfirmation(User user) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Akun")
                .setMessage("Apakah Anda yakin ingin menghapus akun " + user.username + "?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).userDao().delete(user);
                        loadUsers();
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}