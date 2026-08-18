package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "violations",
        foreignKeys = @ForeignKey(entity = Worker.class,
                parentColumns = "regNo",
                childColumns = "workerRegNo",
                onDelete = ForeignKey.CASCADE))
public class Violation {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String workerRegNo;
    public String type;
    public String date;
    public String location;
    public String notes;
    public String docNo;
    public String fine;

    public Violation(String workerRegNo, String type, String date, String location, String notes) {
        this.workerRegNo = workerRegNo;
        this.type = type;
        this.date = date;
        this.location = location;
        this.notes = notes;
    }
}