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
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insert(Worker worker);

    @Update
    void update(Worker worker);

    @Delete
    void delete(Worker worker);

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') as violationCount, " +
            "(SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getAllWorkersWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') as violationCount, " +
            "(SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) as reprimandCount, " +
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
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') as violationCount, " +
            "(SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithViolationsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') as violationCount, " +
            "(SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') = 0 " +
            "AND (SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) = 0 " +
            "AND (SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) = 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getCleanWorkersWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) NOT LIKE '%teguran%') as violationCount, " +
            "(SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT (SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo) AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo))) > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithReprimandsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo)) as violationCount, " +
            "(SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo)) as reprimandCount, " +
            "(SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) as trainingCount, " +
            "(SELECT COUNT(*) FROM accidents a WHERE LOWER(a.workerRegNo) = LOWER(w.regNo)) as accidentCount " +
            "FROM workers w WHERE (SELECT COUNT(*) FROM trainings t WHERE LOWER(t.workerRegNo) = LOWER(w.regNo)) > 0 " +
            "ORDER BY w.id DESC")
    LiveData<List<WorkerWithStats>> getWorkersWithTrainingsWithStats();

    @Query("SELECT w.*, " +
            "(SELECT COUNT(*) FROM violations v WHERE LOWER(v.workerRegNo) = LOWER(w.regNo)) as violationCount, " +
            "(SELECT COUNT(*) FROM reprimands r WHERE LOWER(r.workerRegNo) = LOWER(w.regNo)) as reprimandCount, " +
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

    @Query("SELECT DISTINCT w.* FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE v.workerRegNo = w.regNo AND LOWER(v.type) NOT LIKE '%teguran%') > 0 ORDER BY w.id DESC")
    LiveData<List<Worker>> getWorkersWithViolations();

    @Query("SELECT DISTINCT w.* FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE v.workerRegNo = w.regNo AND LOWER(v.type) NOT LIKE '%teguran%') = 0 " +
            "AND (SELECT (SELECT COUNT(*) FROM violations v WHERE v.workerRegNo = w.regNo AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE r.workerRegNo = w.regNo)) = 0 " +
            "AND (SELECT COUNT(*) FROM accidents a WHERE a.workerRegNo = w.regNo) = 0 ORDER BY w.id DESC")
    LiveData<List<Worker>> getCleanWorkers();

    @Query("SELECT DISTINCT w.* FROM workers w WHERE (SELECT (SELECT COUNT(*) FROM violations v WHERE v.workerRegNo = w.regNo AND LOWER(v.type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands r WHERE r.workerRegNo = w.regNo)) > 0 ORDER BY w.id DESC")
    LiveData<List<Worker>> getWorkersWithReprimands();

    @Query("SELECT DISTINCT w.* FROM workers w INNER JOIN trainings t ON w.regNo = t.workerRegNo")
    LiveData<List<Worker>> getWorkersWithTrainingsOnly();

    @Query("SELECT DISTINCT w.* FROM workers w INNER JOIN accidents a ON w.regNo = a.workerRegNo")
    LiveData<List<Worker>> getWorkersWithAccidentsOnly();

    @Query("SELECT * FROM workers WHERE dataSource = :source ORDER BY id DESC")
    LiveData<List<Worker>> getWorkersBySource(String source);

    @Query("SELECT w.* FROM workers w WHERE (SELECT COUNT(*) FROM violations v WHERE v.workerRegNo = w.regNo) = 0 AND (SELECT COUNT(*) FROM reprimands r WHERE r.workerRegNo = w.regNo) = 0 AND (SELECT COUNT(*) FROM accidents a WHERE a.workerRegNo = w.regNo) = 0 ORDER BY w.id DESC")
    List<Worker> getCleanWorkersSync();

    @Query("SELECT * FROM violations ORDER BY date DESC")
    LiveData<List<Violation>> getAllViolations();

    @Query("SELECT * FROM violations ORDER BY date DESC")
    List<Violation> getAllViolationsSync();

    @Query("SELECT * FROM reprimands ORDER BY date DESC")
    List<Reprimand> getAllReprimandsSync();

    @Query("SELECT * FROM trainings ORDER BY date DESC")
    LiveData<List<Training>> getAllTrainings();

    @Query("SELECT * FROM trainings ORDER BY date DESC")
    List<Training> getAllTrainingsSync();

    @Query("SELECT * FROM accidents ORDER BY date DESC")
    LiveData<List<Accident>> getAllAccidents();

    @Query("SELECT * FROM accidents ORDER BY date DESC")
    List<Accident> getAllAccidentsSync();

    @Query("SELECT * FROM workers ORDER BY id DESC")
    List<Worker> getAllWorkersSync();

    @Query("SELECT * FROM workers WHERE dataSource = :source ORDER BY id DESC")
    List<Worker> getWorkersBySourceSync(String source);

    @Query("SELECT w.* FROM workers w INNER JOIN violations v ON w.regNo = v.workerRegNo")
    List<Worker> getWorkersWithViolationsOnlySync();

    @Query("SELECT w.* FROM workers w INNER JOIN accidents a ON w.regNo = a.workerRegNo")
    List<Worker> getWorkersWithAccidentsOnlySync();

    @Query("SELECT w.* FROM workers w INNER JOIN trainings t ON w.regNo = t.workerRegNo")
    List<Worker> getWorkersWithTrainingsOnlySync();

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

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertViolation(Violation violation);

    @Update
    void updateViolation(Violation violation);

    @Delete
    void deleteViolation(Violation violation);

    @Query("DELETE FROM violations WHERE workerRegNo = :regNo")
    void deleteViolationsByWorker(String regNo);

    @Query("SELECT * FROM reprimands WHERE workerRegNo = :regNo ORDER BY date DESC")
    LiveData<List<Reprimand>> getReprimandsByWorker(String regNo);

    @Query("SELECT * FROM reprimands WHERE workerRegNo = :regNo ORDER BY date DESC")
    List<Reprimand> getReprimandsSync(String regNo);

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertReprimand(Reprimand reprimand);

    @Update
    void updateReprimand(Reprimand reprimand);

    @Delete
    void deleteReprimand(Reprimand reprimand);

    @Query("DELETE FROM reprimands WHERE workerRegNo = :regNo")
    void deleteReprimandsByWorker(String regNo);

    @Query("SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo")
    int getViolationCount(String regNo);

    @Query("SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo AND LOWER(type) NOT LIKE '%teguran%'")
    int getFormalViolationCount(String regNo);

    @Query("SELECT (SELECT COUNT(*) FROM violations WHERE workerRegNo = :regNo AND LOWER(type) LIKE '%teguran%') + (SELECT COUNT(*) FROM reprimands WHERE workerRegNo = :regNo)")
    int getReprimandCount(String regNo);

    @Query("SELECT COUNT(*) FROM violations")
    LiveData<Integer> getTotalViolationCount();

    @Query("SELECT * FROM trainings WHERE workerRegNo = :regNo ORDER BY date DESC")
    LiveData<List<Training>> getTrainingsByWorker(String regNo);

    @Query("SELECT * FROM trainings WHERE workerRegNo = :regNo ORDER BY date DESC")
    List<Training> getTrainingsSync(String regNo);

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertTraining(Training training);

    @Update
    void updateTraining(Training training);

    @Delete
    void deleteTraining(Training training);

    @Query("DELETE FROM trainings WHERE workerRegNo = :regNo")
    void deleteTrainingsByWorker(String regNo);

    @Query("SELECT * FROM accidents WHERE workerRegNo = :regNo ORDER BY date DESC, time DESC")
    LiveData<List<Accident>> getAccidentsByWorker(String regNo);

    @Query("SELECT COUNT(*) FROM accidents WHERE workerRegNo = :regNo")
    int getAccidentCount(String regNo);

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    void insertAccident(Accident accident);

    @Update
    void updateAccident(Accident accident);

    @Delete
    void deleteAccident(Accident accident);

    @Query("DELETE FROM accidents WHERE workerRegNo = :regNo")
    void deleteAccidentsByWorker(String regNo);

    @Query("DELETE FROM workers WHERE dataSource = 'Master Data'")
    void deleteMasterWorkers();

    @Query("DELETE FROM violations WHERE dataSource = 'Master Data'")
    void deleteMasterViolations();

    @Query("DELETE FROM reprimands WHERE dataSource = 'Master Data'")
    void deleteMasterReprimands();

    @Query("DELETE FROM trainings WHERE dataSource = 'Master Data'")
    void deleteMasterTrainings();

    @Query("DELETE FROM accidents WHERE dataSource = 'Master Data'")
    void deleteMasterAccidents();

    @Query("DELETE FROM accidents")
    void deleteAllAccidents();

    @Query("DELETE FROM trainings")
    void deleteAllTrainings();

    @Query("DELETE FROM violations")
    void deleteAllViolations();

    @Query("DELETE FROM reprimands")
    void deleteAllReprimands();

    @Query("DELETE FROM workers")
    void deleteAllWorkers();
}