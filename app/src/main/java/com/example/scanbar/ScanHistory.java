package com.example.scanbar;

public class ScanHistory {
    public String regNo;
    public String name;
    public String contractor;
    public String time;
    public boolean isFound;

    public ScanHistory(String regNo, String name, String contractor, String time, boolean isFound) {
        this.regNo = regNo;
        this.name = name;
        this.contractor = contractor;
        this.time = time;
        this.isFound = isFound;
    }
}