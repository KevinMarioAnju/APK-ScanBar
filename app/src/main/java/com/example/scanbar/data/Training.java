package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "trainings",
        indices = {@androidx.room.Index(value = {"workerRegNo", "trainingTitle", "date"}, unique = true)},
        foreignKeys = @ForeignKey(entity = Worker.class,
                parentColumns = "regNo",
                childColumns = "workerRegNo",
                onDelete = ForeignKey.CASCADE))
public class Training {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String workerRegNo;
    public String workerName; // Add this
    public String trainingCode; // Add this
    public String trainingTitle;
    public String date;
    public String time;
    public String endTime;
    public String trainingHours;
    public String trainingLocation;
    public String passFail;
    public String dataSource; // Add this

    public Training(String workerRegNo, String trainingTitle, String date) {
        this.workerRegNo = workerRegNo;
        this.trainingTitle = trainingTitle;
        this.date = date;
    }
}