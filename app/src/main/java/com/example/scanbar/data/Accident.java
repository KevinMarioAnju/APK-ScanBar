package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "accidents",
        indices = {@androidx.room.Index(value = {"workerRegNo", "date", "time"}, unique = true)},
        foreignKeys = @ForeignKey(entity = Worker.class,
                parentColumns = "regNo",
                childColumns = "workerRegNo",
                onDelete = ForeignKey.CASCADE))
public class Accident {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String workerRegNo;
    public String date;           // Tanggal Kecelakaan
    public String time;           // Jam Kecelakaan
    public String chronology;     // Kronologis Kecelakaan
    public String severity;       // Keparahan (LTI, MTI, etc)
    public String location;       // Lokasi Kecelakaan
    public String dataSource;     // Add this

    public Accident(String workerRegNo, String date, String time, String chronology, String severity, String location) {
        this.workerRegNo = workerRegNo;
        this.date = date;
        this.time = time;
        this.chronology = chronology;
        this.severity = severity;
        this.location = location;
    }
}