package com.example.scanbar.data;

import androidx.room.Embedded;

public class WorkerWithStats {
    @Embedded
    public Worker worker;
    
    public int violationCount;
    public int reprimandCount;
    public int trainingCount;
    public int accidentCount;
}