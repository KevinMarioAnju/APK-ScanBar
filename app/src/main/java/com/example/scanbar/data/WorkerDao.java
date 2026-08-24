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

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getAllWorkersWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE " +
            "w.name LIKE '%' || :query || '%' OR " +
            "w.regNo LIKE '%' || :query || '%' OR " +
            "w.contractor LIKE '%' || :query || '%' OR " +
            "w.position LIKE '%' || :query || '%' " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> searchWorkersWithStats(String query);

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithViolationsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') = 0 " +
            "AND (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') = 0 " +
            "AND (SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) = 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getCleanWorkersWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithReprimandsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithTrainingsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type NOT LIKE '%Teguran%') as violationCount, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND v.type LIKE '%Teguran%') as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithAccidentsWithStats();

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

    @Query("SELECT DISTINCT w.* FROM workers w INNER JOIN violations v ON w.regNo = v.workerRegNo WHERE v.type LIKE '%Teguran%'")
    LiveData<List<Worker>> getWorkersWithReprimands();

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

    @Query("SELECT * FROM violations WHERE workerRegNo = :regNo ORDER BY date DESC")
    List<Violation> getViolationsSync(String regNo);

    @Insert
    void insertViolation(Violation violation);

    @Update
    void updateViolation(Violation violation);

    @Delete
    void deleteViolation(Violation violation);

    @Query("DELETE FROM violations WHERE workerRegNo = :regNo")
    void deleteViolationsByWorker(String regNo);

    @Query("SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo")
    int getViolationCount(String regNo);

    @Query("SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo AND type NOT LIKE '%Teguran%'")
    int getFormalViolationCount(String regNo);

    @Query("SELECT COUNT(*) FROM violations")
    LiveData<Integer> getTotalViolationCount();

    @Query("SELECT * FROM trainings WHERE workerRegNo = :regNo ORDER BY date DESC")
    LiveData<List<Training>> getTrainingsByWorker(String regNo);

    @Query("SELECT * FROM trainings WHERE workerRegNo = :regNo ORDER BY date DESC")
    List<Training> getTrainingsSync(String regNo);

    @Insert
    void insertTraining(Training training);

    @Update
    void updateTraining(Training training);

    @Delete
    void deleteTraining(Training training);

    @Query("DELETE FROM trainings WHERE workerRegNo = :regNo")
    void deleteTrainingsByWorker(String regNo);

    @Query("SELECT * FROM accidents WHERE workerRegNo = :regNo ORDER BY date DESC, time DESC")
    LiveData<List<Accident>> getAccidentsByWorker(String regNo);

    @Insert
    void insertAccident(Accident accident);

    @Update
    void updateAccident(Accident accident);

    @Delete
    void deleteAccident(Accident accident);

    @Query("DELETE FROM accidents WHERE workerRegNo = :regNo")
    void deleteAccidentsByWorker(String regNo);

    @Query("DELETE FROM accidents")
    void deleteAllAccidents();

    @Query("DELETE FROM trainings")
    void deleteAllTrainings();

    @Query("DELETE FROM violations")
    void deleteAllViolations();

    @Query("DELETE FROM workers")
    void deleteAllWorkers();
}