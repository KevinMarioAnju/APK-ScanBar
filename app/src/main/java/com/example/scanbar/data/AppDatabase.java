package com.example.scanbar.data;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.security.MessageDigest;
import java.util.concurrent.Executors;

@Database(entities = {Worker.class, Violation.class, User.class, Training.class, Accident.class, Reprimand.class}, version = 25)
public abstract class AppDatabase extends RoomDatabase {
    public abstract WorkerDao workerDao();
    public abstract UserDao userDao();

    private static final String WORKER_ASSET_PATH = "database/Master_Data_Kontraktor.json";
    private static final String ASSET_PREFS_NAME = "scanbar_asset_state";
    private static final String LAST_WORKER_ASSET_HASH = "last_worker_asset_hash";

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "scanbar_master_db")
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
                                    Log.d("AppDatabase", "DATABASE OPENED - Checking data...");
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        WorkerDao dao = getDatabase(context).workerDao();
                                        refreshWorkersIfAssetChanged(context, dao);
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void refreshWorkersIfAssetChanged(Context context, WorkerDao dao) {
        try {
            String currentHash = hashAsset(context, WORKER_ASSET_PATH);
            SharedPreferences prefs = context.getSharedPreferences(ASSET_PREFS_NAME, Context.MODE_PRIVATE);
            String lastHash = prefs.getString(LAST_WORKER_ASSET_HASH, null);

            // FORCE re-population if the database is empty (e.g. after migration)
            if (dao.getSyncWorkerCount() == 0) {
                Log.d("AppDatabase", "Database is empty. Forced population started.");
                prePopulateDatabase(context, dao);
                return;
            }

            if (currentHash.equals(lastHash)) {
                Log.d("AppDatabase", "Worker asset unchanged; skipping refresh.");
                return;
            }

            Log.d("AppDatabase", "Worker asset changed. Refreshing Master Data while preserving local data.");
            dao.deleteMasterViolations();
            dao.deleteMasterReprimands();
            dao.deleteMasterTrainings();
            dao.deleteMasterAccidents();
            dao.deleteMasterWorkers();
            prePopulateDatabase(context, dao);
            prefs.edit().putString(LAST_WORKER_ASSET_HASH, currentHash).apply();
        } catch (Exception e) {
            Log.e("AppDatabase", "Failed to refresh workers from asset", e);
        }
    }

    private static String hashAsset(Context context, String assetPath) throws Exception {
        try (InputStream is = context.getAssets().open(assetPath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }

            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    private static void prePopulateDatabase(Context context, WorkerDao dao) {
        try {
            Log.d("AppDatabase", "POPULATION START");
            InputStream is = context.getAssets().open(WORKER_ASSET_PATH);

            java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
            String json = scanner.hasNext() ? scanner.next() : "";
            is.close();

            if (json.isEmpty()) {
                Log.e("AppDatabase", "JSON file is empty!");
                return;
            }

            Log.d("AppDatabase", "JSON String loaded, length: " + json.length());
            json = json.trim();

            if (!json.startsWith("[")) {
                Log.d("AppDatabase", "Formatting non-standard JSON...");
                json = json.replaceAll("\\}\\s*\\{", "},{");
                if (!json.startsWith("[")) json = "[" + json;
                if (!json.endsWith("]")) json = json + "]";
            }

            json = json.replace(",]", "]");

            JSONArray array = new JSONArray(json);
            Log.d("AppDatabase", "JSONArray parsed: " + array.length() + " items");

            INSTANCE.runInTransaction(() -> {
                int count = 0;
                for (int i = 0; i < array.length(); i++) {
                    try {
                        JSONObject obj = array.getJSONObject(i);
                        processJsonObject(obj, dao);
                        count++;
                    } catch (Exception e) {
                        Log.e("AppDatabase", "Error at index " + i, e);
                    }
                }
                Log.d("AppDatabase", "Import finished: " + count + " workers.");
            });

            SharedPreferences prefs = context.getSharedPreferences(ASSET_PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(LAST_WORKER_ASSET_HASH, hashAsset(context, WORKER_ASSET_PATH)).apply();

        } catch (Exception e) {
            Log.e("AppDatabase", "CRITICAL POPULATION ERROR", e);
        }
    }

    private static void processJsonObject(JSONObject obj, WorkerDao dao) {
        // Mapping with support for Indonesian and English keys
        String regNo = obj.optString("ID Pekerja (Reg. No)",
                obj.optString("Reg No.",
                        obj.optString("regNo",
                                obj.optString("RegNo", "")))).trim();

        String name = obj.optString("Nama Pekerja",
                obj.optString("Worker Name",
                        obj.optString("name",
                                obj.optString("Name", ""))));

        String contractor = obj.optString("Nama Kontraktor",
                obj.optString("Contractor Name",
                        obj.optString("contractor",
                                obj.optString("Contractor", ""))));

        String position = obj.optString("Jabatan",
                obj.optString("Job Title",
                        obj.optString("position",
                                obj.optString("Position", ""))));

        String statusJson = obj.optString("Status Pelanggaran",
                obj.optString("Status",
                        obj.optString("status", "BERSIH")));

        String finalStatus = "Bersih";
        if (statusJson.equalsIgnoreCase("ADA PELANGGARAN") ||
                statusJson.equalsIgnoreCase("Pelanggaran") ||
                statusJson.equals("1") ||
                (!statusJson.equalsIgnoreCase("Bersih") && !statusJson.equalsIgnoreCase("BERSIH") && !statusJson.isEmpty() && !statusJson.equals("-"))) {
            finalStatus = "Pelanggaran";
        }

        if (!regNo.isEmpty() || !name.isEmpty()) {
            Worker worker = new Worker(regNo, name, contractor, position, finalStatus);
            worker.dataSource = "Master Data";

            worker.contractorCode = obj.optString("Contractor Code", obj.optString("Kode Kontraktor", ""));
            worker.gender = obj.optString("Gender", obj.optString("GENDER / USIA", ""));
            worker.birthDate = obj.optString("Birth Date", obj.optString("Tanggal Lahir", obj.optString("Birth Date", "")));
            worker.wspExpDate = obj.optString("WSP EXPIRING DATE", obj.optString("WSP Expiring Date", ""));

            worker.dateOfEvent = obj.optString("Tanggal Kejadian",
                    obj.optString("Violation Date",
                            obj.optString("Violation Date", "-")));
            worker.violationType = obj.optString("Jenis Pelanggaran",
                    obj.optString("Violation Type", "-"));
            worker.fineAmount = obj.optString("Denda (Rp)",
                    obj.optString("Violation Amount", "-"));
            worker.plantDiv = obj.optString("Plant/Divisi",
                    obj.optString("Plant/Division", "-"));
            worker.eventLocation = obj.optString("Lokasi Kejadian",
                    obj.optString("TKP", "-"));
            worker.documentNo = obj.optString("No. Dokumen",
                    obj.optString("Violation Doc No.", "-"));

            dao.insert(worker);

            if (finalStatus.equals("Pelanggaran")) {
                Violation v = new Violation(regNo, worker.violationType, worker.dateOfEvent, worker.eventLocation, "-");
                v.docNo = worker.documentNo;
                v.fine = worker.fineAmount;
                v.plant = worker.plantDiv;
                v.dataSource = "Master Data";
                
                // Enhanced fields
                v.year = obj.optString("Violation Year", "");
                v.time = obj.optString("Violation Time", "");
                v.tkp = worker.eventLocation;
                v.officer = obj.optString("Officer", "-");
                v.amount = worker.fineAmount;
                v.charge = obj.optString("Violation Charge", "-");
                v.damages = obj.optString("Violation Damages", "-");
                v.totalAll = obj.optString("Violation Total All", "-");
                v.name = name;
                v.jobTitle = position;
                v.contractor = contractor;
                
                dao.insertViolation(v);
            }

            // --- REPRIMAND DATA IMPORT ---
            String reprimandNote = obj.optString("Catatan Teguran", 
                                  obj.optString("Reprimand Note", 
                                  obj.optString("reprimandNote", "")));
            if (!reprimandNote.isEmpty() && !reprimandNote.equals("null")) {
                Reprimand r = new Reprimand(regNo, 
                                           obj.optString("Tanggal Teguran", obj.optString("Reprimand Date", worker.dateOfEvent)),
                                           obj.optString("Lokasi Teguran", obj.optString("Reprimand Location", worker.eventLocation)),
                                           reprimandNote,
                                           obj.optString("Penegur", obj.optString("Reprimand Officer", "-")));
                r.dataSource = "Master Data";
                dao.insertReprimand(r);
            }

            // --- TRAINING DATA IMPORT ---
            String trainingTitle = obj.optString("Training Title", "");
            if (!trainingTitle.isEmpty() && !trainingTitle.equals("null")) {
                Training t = new Training(regNo, trainingTitle, obj.optString("Training Date", "-"));
                t.time = obj.optString("Training Time", "-");
                t.endTime = obj.optString("Training End Time", "-");
                t.trainingHours = obj.optString("Traning Hours", "-");
                t.trainingLocation = obj.optString("Training Location", "-");
                t.passFail = obj.optString("Pass/Fail", "-");
                
                dao.insertTraining(t);
            }
        }
    }
}