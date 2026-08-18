package com.example.scanbar.data;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

@Database(entities = {Worker.class, Violation.class}, version = 4)
public abstract class AppDatabase extends RoomDatabase {
    public abstract WorkerDao workerDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "scanbar_db")
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    Log.d("AppDatabase", "Database onCreate triggered");
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        prePopulateDatabase(context, getDatabase(context).workerDao());
                                    });
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    Log.d("AppDatabase", "Database onOpen triggered");
                                    // Check if data exists, if not, populate
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        WorkerDao dao = getDatabase(context).workerDao();
                                        // We can't use getWorkerCount().getValue() here because it's LiveData and we're not on UI thread with observer
                                        // Let's use a simple synchronous count query if we had one, but we can just attempt population if it's the first run
                                        // or better, add a sync count method to DAO.
                                        if (dao.getSyncWorkerCount() == 0) {
                                            prePopulateDatabase(context, dao);
                                        }
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void prePopulateDatabase(Context context, WorkerDao dao) {
        try {
            InputStream is = context.getAssets().open("database/data_worker_with_violation.json");
            
            // Optimization for large files: Use a BufferedReader and a more efficient parser if needed,
            // but for now, let's keep the transaction optimization.
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            
            String json = new String(buffer, StandardCharsets.UTF_8).trim();
            // If the JSON is a sequence of objects like {..},{..}, wrap it in []
            if (!json.startsWith("[")) {
                json = "[" + json + "]";
            }
            
            JSONArray array = new JSONArray(json);
            
            // WRAP IN TRANSACTION FOR SPEED AND STABILITY
            INSTANCE.runInTransaction(() -> {
                for (int i = 0; i < array.length(); i++) {
                    try {
                        processJsonObject(array.getJSONObject(i), dao);
                    } catch (Exception e) {
                        Log.e("AppDatabase", "Error processing object at " + i, e);
                    }
                }
                Log.d("AppDatabase", "Transaction completed: " + array.length() + " items imported.");
            });

            Log.d("AppDatabase", "Database pre-population background process finished.");
        } catch (Exception e) {
            Log.e("AppDatabase", "CRITICAL ERROR during pre-population", e);
        }
    }

    private static void processJsonObject(JSONObject obj, WorkerDao dao) {
        String regNo = obj.optString("regNo", 
                       obj.optString("RegNo", 
                       obj.optString("no_reg", 
                       obj.optString("ID Pekerja (Reg. No)", ""))));
        
        String name = obj.optString("name", 
                      obj.optString("Name", 
                      obj.optString("nama", 
                      obj.optString("Nama Pekerja", ""))));
        
        String contractor = obj.optString("contractor", 
                            obj.optString("Contractor", 
                            obj.optString("kontraktor", 
                            obj.optString("Nama Kontraktor", ""))));
        
        String position = obj.optString("position", 
                          obj.optString("Position", 
                          obj.optString("jabatan", 
                          obj.optString("Jabatan", ""))));
        
        String status = obj.optString("status", 
                        obj.optString("Status", 
                        obj.optString("Status Pelanggaran", "Bersih")));

        // Normalize status: if it contains "1" or is not "Bersih", mark as "Pelanggaran"
        if (status.equals("1") || (!status.equalsIgnoreCase("Bersih") && !status.equals("-"))) {
            status = "Pelanggaran";
        } else {
            status = "Bersih";
        }

        if (!regNo.isEmpty() || !name.isEmpty()) {
            Worker worker = new Worker(regNo, name, contractor, position, status);
            
            // Map additional fields from user's JSON if present
            worker.contractorCode = obj.optString("Kode Kontraktor", "");
            worker.gender = obj.optString("Gender", obj.optString("GENDER / USIA", ""));
            worker.birthDate = obj.optString("Tanggal Lahir", obj.optString("TGL LAHIR", ""));
            worker.wspExpDate = obj.optString("WSP EXPIRING DATE", "");
            
            // Map new fields from the latest JSON schema
            worker.dateOfEvent = obj.optString("Tanggal Kejadian", "-");
            worker.violationType = obj.optString("Jenis Pelanggaran", "-");
            worker.fineAmount = obj.optString("Denda (Rp)", "-");
            worker.plantDiv = obj.optString("Plant/Divisi", "-");
            worker.eventLocation = obj.optString("Lokasi Kejadian", "-");
            worker.documentNo = obj.optString("No. Dokumen", "-");
            
            dao.insert(worker);

            // If there's a violation in the status, create a sample violation entry
            if (!status.equalsIgnoreCase("Bersih") && !status.equals("-")) {
                String type = worker.violationType;
                String date = worker.dateOfEvent;
                String loc = worker.eventLocation;
                String notes = obj.optString("Catatan", "-");
                
                Violation v = new Violation(regNo, type, date, loc, notes);
                v.docNo = worker.documentNo;
                v.fine = worker.fineAmount;
                dao.insertViolation(v);
            }
        }
    }
}