package com.example.scanbar.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "violations",
        indices = {@androidx.room.Index(value = {"workerRegNo", "date", "type", "docNo"}, unique = true)},
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
    public String plant;
    
    // Safety Audit Enhanced Fields
    public String year;
    public String time;
    public String plantDivision;
    public String tkp;
    public String contractor;
    public String userPlantDivision;
    public String name; // Worker Name snapshot
    public String jobTitle; // Job Title snapshot
    public String amount; // Detailed amount
    public String charge;
    public String damages;
    public String totalAll;
    public String officer;
    public String paymentProofUri;
    public String dataSource;

    public Violation(String workerRegNo, String type, String date, String location, String notes) {
        this.workerRegNo = workerRegNo;
        this.type = type;
        this.date = date;
        this.location = location;
        this.notes = notes;
    }
}