package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "reprimands",
        foreignKeys = @ForeignKey(entity = Worker.class,
                parentColumns = "regNo",
                childColumns = "workerRegNo",
                onDelete = ForeignKey.CASCADE))
public class Reprimand {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String workerRegNo;
    public String date;
    public String location;
    public String notes;
    public String officer;
    public String dataSource;

    public Reprimand(String workerRegNo, String date, String location, String notes, String officer) {
        this.workerRegNo = workerRegNo;
        this.date = date;
        this.location = location;
        this.notes = notes;
        this.officer = officer;
    }
}