package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "trainings",
        foreignKeys = @ForeignKey(entity = Worker.class,
                parentColumns = "regNo",
                childColumns = "workerRegNo",
                onDelete = ForeignKey.CASCADE))
public class Training {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String workerRegNo;
    public String trainingTitle;
    public String date;
    public String time;
    public String endTime;
    public String trainingHours;
    public String trainingLocation;
    public String passFail;

    public Training(String workerRegNo, String trainingTitle, String date) {
        this.workerRegNo = workerRegNo;
        this.trainingTitle = trainingTitle;
        this.date = date;
    }
}