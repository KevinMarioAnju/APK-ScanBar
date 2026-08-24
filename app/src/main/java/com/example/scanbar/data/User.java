package com.example.scanbar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @NonNull
    public String username;
    
    @NonNull
    public String password;

    public String nickname;
    
    @NonNull
    public String role; // "admin" or "inspektur"

    public User(@NonNull String username, @NonNull String password, @NonNull String role, String nickname) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.nickname = nickname;
    }
}