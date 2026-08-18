package com.example.scanbar.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "workers", indices = {@Index(value = {"regNo"}, unique = true)})
public class Worker {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @NonNull
    public String regNo;
    public String name;
    public String contractor;
    public String position;
    public String status; 
    
    // Additional fields from original schema
    public String contractorCode;
    public String gender;
    public String birthDate;
    public String wspExpDate;

    // New fields from JSON "Direktori Pekerja"
    public String dateOfEvent;
    public String violationType;
    public String fineAmount;
    public String plantDiv;
    public String eventLocation;
    public String documentNo;

    public Worker(@NonNull String regNo, String name, String contractor, String position, String status) {
        this.regNo = regNo;
        this.name = name;
        this.contractor = contractor;
        this.position = position;
        this.status = status;
    }
}