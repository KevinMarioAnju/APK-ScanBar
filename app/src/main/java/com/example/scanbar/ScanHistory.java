package com.example.scanbar;

public class ScanHistory {
    public String regNo;
    public String name;
    public String time;
    public boolean isFound;

    public ScanHistory(String regNo, String name, String time, boolean isFound) {
        this.regNo = regNo;
        this.name = name;
        this.time = time;
        this.isFound = isFound;
    }
}