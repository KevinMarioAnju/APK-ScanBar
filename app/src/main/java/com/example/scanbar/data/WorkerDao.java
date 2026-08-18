package com.example.scanbar.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface WorkerDao {
    @Insert
    void insert(Worker worker);

    @Update
    void update(Worker worker);

    @Delete
    void delete(Worker worker);

    @Query("SELECT * FROM workers ORDER BY id DESC")
    LiveData<List<Worker>> getAllWorkers();

    @Query("SELECT * FROM workers WHERE " +
            "name LIKE '%' || :query || '%' OR " +
            "regNo LIKE '%' || :query || '%' OR " +
            "contractor LIKE '%' || :query || '%' OR " +
            "position LIKE '%' || :query || '%' " +
            "ORDER BY id DESC")
    LiveData<List<Worker>> searchWorkers(String query);

    @Query("SELECT * FROM workers WHERE status != 'Bersih' AND status != 'BERSIH' ORDER BY id DESC")
    LiveData<List<Worker>> getWorkersWithViolations();

    @Query("SELECT * FROM workers WHERE status = 'Bersih' OR status = 'BERSIH' ORDER BY id DESC")
    LiveData<List<Worker>> getCleanWorkers();

    @Query("SELECT * FROM workers WHERE regNo = :regNo LIMIT 1")
    Worker getWorkerByRegNo(String regNo);

    @Query("SELECT * FROM workers WHERE regNo = :regNo LIMIT 1")
    LiveData<Worker> getWorkerLiveDataByRegNo(String regNo);
    
    @Query("SELECT COUNT(*) FROM workers")
    LiveData<Integer> getWorkerCount();

    @Query("SELECT COUNT(*) FROM workers")
    int getSyncWorkerCount();

    @Query("SELECT * FROM violations WHERE workerRegNo = :regNo ORDER BY date DESC")
    LiveData<List<Violation>> getViolationsByWorker(String regNo);

    @Insert
    void insertViolation(Violation violation);

    @Query("SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo")
    int getViolationCount(String regNo);

    @Query("SELECT COUNT(*) FROM violations")
    LiveData<Integer> getTotalViolationCount();
}