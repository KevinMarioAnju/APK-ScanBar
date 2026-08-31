package com.example.scanbar;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.Training;
import com.example.scanbar.data.Violation;
import com.example.scanbar.data.Worker;
import com.example.scanbar.databinding.DialogTrainingDetailsBinding;
import com.example.scanbar.databinding.DialogViolationFormBinding;
import com.example.scanbar.databinding.LayoutScanResultPageBinding;
import com.example.scanbar.databinding.LayoutWorkerDetailsBinding;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import com.example.scanbar.databinding.DialogViolationDetailsBinding;
import com.example.scanbar.data.Accident;
import com.example.scanbar.databinding.DialogAccidentDetailsBinding;
import com.example.scanbar.databinding.DialogAccidentFormBinding;

public class ScanResultActivity extends AppCompatActivity {
    private LayoutScanResultPageBinding binding;
    private LayoutWorkerDetailsBinding detailBinding;
    private String regNo;
    private String userRole = "inspektur";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        getWindow().setBackgroundDrawableResource(android.R.color.white);
        
        userRole = getSharedPreferences("ScanBarSession", MODE_PRIVATE).getString("ROLE", "inspektur");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(Color.WHITE);
            getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        
        binding = LayoutScanResultPageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        detailBinding = LayoutWorkerDetailsBinding.bind(binding.layoutWorkerDetailsPage.getRoot());

        regNo = getIntent().getStringExtra("REG_NO");
        if (regNo == null || regNo.isEmpty()) {
            Toast.makeText(this, "Data tidak valid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupButtons();
        loadWorkerData();
    }

    private void setupButtons() {
        binding.btnBackToScan.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });
    }

    private void loadWorkerData() {
        AppDatabase.getDatabase(this).workerDao().getWorkerLiveDataByRegNo(regNo).observe(this, worker -> {
            if (worker != null) {
                showWorkerDetails(worker);
            } else {
                Toast.makeText(this, "Kontraktor Tidak Ditemukan", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showWorkerDetails(Worker worker) {
        detailBinding.tvDetailName.setText(worker.name);
        detailBinding.tvDetailPosition.setText(worker.position);
        detailBinding.tvDetailPositionGrid.setText(worker.position != null ? worker.position : "-");
        detailBinding.tvDetailRegNoTop.setText("CODE: " + worker.regNo);
        detailBinding.tvDetailRegNo.setText(worker.regNo);
        detailBinding.tvDetailContractor.setText(worker.contractor);
        detailBinding.tvDetailPlantDiv.setText(worker.plantDiv != null ? worker.plantDiv : "-");
        detailBinding.tvDetailDataSource.setText(worker.dataSource != null ? worker.dataSource : "-");

        if (worker.fineAmount != null && !worker.fineAmount.equals("-") && !worker.fineAmount.isEmpty()) {
            detailBinding.layoutFine.setVisibility(View.VISIBLE);
            detailBinding.tvDetailFineBox.setText(cleanFineAmount(worker.fineAmount));
        } else {
            detailBinding.layoutFine.setVisibility(View.GONE);
        }

        // Action Buttons Setup
        detailBinding.tvDetailViolationBadge.setOnClickListener(v -> showViolationForm(worker));
        detailBinding.tvDetailAccidentBadge.setOnClickListener(v -> showAccidentForm(worker));
        detailBinding.tvDetailTrainingBadge.setOnClickListener(v -> showTrainingForm(worker));
        detailBinding.btnAddNote.setOnClickListener(v -> showReprimandDialog(worker));

        AppDatabase.getDatabase(this).workerDao().getViolationsByWorker(worker.regNo).observe(this, violations -> {
            detailBinding.llViolationList.removeAllViews();
            detailBinding.llReprimandList.removeAllViews();
            
            long totalFine = 0;
            int violationCount = 0;
            int reprimandCount = 0;

            for (Violation v : violations) {
                if (v.type != null && v.type.toLowerCase().contains("teguran")) {
                    addReprimandItemToUi(v);
                    reprimandCount++;
                } else {
                    addViolationItemToUi(v);
                    violationCount++;
                    if (v.fine != null && !v.fine.isEmpty()) {
                        try {
                            String clean = v.fine.replaceAll("[^0-9]", "");
                            if (clean.endsWith("00") && v.fine.contains(",")) {
                                clean = clean.substring(0, clean.length() - 2);
                            }
                            if (!clean.isEmpty()) totalFine += Long.parseLong(clean);
                        } catch (Exception e) {}
                    }
                }
            }

            if (violationCount > 0) {
                detailBinding.llViolationSection.setVisibility(View.VISIBLE);
                detailBinding.tvViolationCount.setText(String.valueOf(violationCount));
            } else {
                detailBinding.llViolationSection.setVisibility(View.GONE);
            }

            if (reprimandCount > 0) {
                detailBinding.llReprimandSection.setVisibility(View.VISIBLE);
                detailBinding.tvReprimandCount.setText(String.valueOf(reprimandCount));
            } else {
                detailBinding.llReprimandSection.setVisibility(View.GONE);
            }

            if (totalFine > 0) {
                detailBinding.layoutFine.setVisibility(View.VISIBLE);
                detailBinding.tvDetailFineBox.setText(formatFineDisplay(totalFine));
            } else {
                detailBinding.layoutFine.setVisibility(View.GONE);
            }
        });

        AppDatabase.getDatabase(this).workerDao().getTrainingsByWorker(worker.regNo).observe(this, trainings -> {
            if (detailBinding.llTrainingList != null) {
                detailBinding.llTrainingList.removeAllViews();
                if (trainings != null && !trainings.isEmpty()) {
                    detailBinding.llTrainingSection.setVisibility(View.VISIBLE);
                    detailBinding.tvTrainingCount.setText(String.valueOf(trainings.size()));
                    for (Training t : trainings) {
                        addTrainingItemToUi(t);
                    }
                } else {
                    detailBinding.llTrainingSection.setVisibility(View.GONE);
                    detailBinding.tvTrainingCount.setText("0");
                }
            }
        });

        AppDatabase.getDatabase(this).workerDao().getAccidentsByWorker(worker.regNo).observe(this, accidents -> {
            if (detailBinding.llAccidentList != null) {
                detailBinding.llAccidentList.removeAllViews();
                if (accidents != null && !accidents.isEmpty()) {
                    detailBinding.llAccidentSection.setVisibility(View.VISIBLE);
                    detailBinding.tvAccidentCount.setText(String.valueOf(accidents.size()));
                    for (Accident a : accidents) {
                        addAccidentItemToUi(a);
                    }
                } else {
                    detailBinding.llAccidentSection.setVisibility(View.GONE);
                    detailBinding.tvAccidentCount.setText("0");
                }
            }
        });
    }

    private String formatFineDisplay(long amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("###,###", symbols);
        return "Rp. " + formatter.format(amount) + ",00";
    }

    private String cleanFineAmount(String fine) {
        if (fine == null || fine.isEmpty() || fine.equals("-")) return "Rp -";
        String clean = fine.replace("-", "").trim();
        String digitsOnly = clean.replaceAll("[^0-9]", "");
        if (digitsOnly.endsWith("00") && clean.contains(",")) {
            digitsOnly = digitsOnly.substring(0, digitsOnly.length() - 2);
        }
        if (digitsOnly.isEmpty()) return "Rp -";
        try {
            long amount = Long.parseLong(digitsOnly);
            return formatFineDisplay(amount);
        } catch (Exception e) {
            return fine;
        }
    }

    private void showViolationForm(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvVioFormTitle.setText("Tambah Pelanggaran — " + worker.name);
        if (dialogBinding.tilInspectorName != null) {
            dialogBinding.tilInspectorName.setHint("Nama Petugas");
        }
        
        // --- 1. Kolom Tanggal (Numeric, Auto-Format DD/MM/YYYY, Backspace Fix) ---
        dialogBinding.etVioDate.addTextChangedListener(new TextWatcher() {
            private String current = "";
            private boolean isDeleting = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { isDeleting = count > after; }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                if (isDeleting && s.length() > 0 && (s.charAt(s.length() - 1) == '/')) {
                    if (clean.length() > 0) clean = clean.substring(0, clean.length() - 1);
                }
                String formatted = "";
                int cl = clean.length();
                if (cl > 0) {
                    if (cl <= 2) formatted = clean;
                    else if (cl <= 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2);
                    else formatted = clean.substring(0, 2) + "/" + clean.substring(2, 4) + "/" + clean.substring(4, Math.min(cl, 8));
                }
                current = formatted;
                dialogBinding.etVioDate.setText(current);
                dialogBinding.etVioDate.setSelection(current.length());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- 2. Kolom Denda (Numeric, State-Guard Anti-Loop, Rp. ###.###,00) ---
        dialogBinding.etVioFine.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                String text = s.toString();
                if (text.contains(",")) text = text.substring(0, text.indexOf(","));
                String input = text.replaceAll("[^\\d]", "");
                isFormatting = true;
                if (input.isEmpty()) {
                    dialogBinding.etVioFine.setText("");
                } else {
                    try {
                        long parsed = Long.parseLong(input);
                        String formatted = formatFineDisplay(parsed);
                        dialogBinding.etVioFine.setText(formatted);
                        dialogBinding.etVioFine.setSelection(Math.max(0, formatted.length() - 3));
                    } catch (Exception e) { dialogBinding.etVioFine.setText(""); }
                }
                isFormatting = false;
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogBinding.btnVioCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnVioSave.setOnClickListener(v -> {
            String type = dialogBinding.etVioTypeManual.getText().toString().trim();
            String date = dialogBinding.etVioDate.getText().toString();
            String loc = dialogBinding.etVioLocation.getText().toString();
            String notes = dialogBinding.etVioNotes.getText().toString();
            String fine = dialogBinding.etVioFine.getText().toString();
            String plant = dialogBinding.etVioPlant.getText().toString();
            String docNo = dialogBinding.etVioDocNo.getText().toString();
            String inspector = dialogBinding.etInspectorName.getText().toString();

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(this, "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                boolean isTeguran = type.toLowerCase().contains("teguran");
                if (!isTeguran) {
                    int count = AppDatabase.getDatabase(this).workerDao().getFormalViolationCount(worker.regNo);
                    if (count >= 5) {
                        runOnUiThread(() -> Toast.makeText(this, "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show());
                        return;
                    }
                }
                
                Violation violation = new Violation(worker.regNo, type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = docNo;
                violation.plant = plant;
                violation.officer = inspector;
                violation.dataSource = "Input di HP";
                AppDatabase.getDatabase(this).workerDao().insertViolation(violation);
                
                // Update worker's primary fields for immediate UI consistency
                if (worker.status == null || !worker.status.equalsIgnoreCase("Pelanggaran")) {
                    worker.status = isTeguran ? "Teguran" : "Pelanggaran";
                }
                worker.violationType = type;
                worker.dateOfEvent = date;
                worker.fineAmount = fine;
                if (plant != null && !plant.isEmpty() && !plant.equals("-")) {
                    worker.plantDiv = plant;
                }
                worker.eventLocation = loc;
                worker.documentNo = docNo;
                worker.inspectorName = inspector;
                worker.dataSource = "Input di HP";
                AppDatabase.getDatabase(this).workerDao().update(worker);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Berhasil dicatat", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showAccidentForm(Worker worker) {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvAccidentFormTitle.setText("Tambah Kecelakaan — " + worker.name);
        dialogBinding.tilWorkerSearch.setVisibility(View.GONE);

        String[] options = {"LTI", "MTI", "First Aid", "Near Hit", "Property Damage"};
        ArrayAdapter<String> sevAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        sevAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerAccidentSeverity.setAdapter(sevAdapter);

        dialogBinding.btnAccidentCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnAccidentSave.setOnClickListener(v -> {
            String date = dialogBinding.etAccidentDate.getText().toString();
            String time = dialogBinding.etAccidentTime.getText().toString();
            String location = dialogBinding.etAccidentLocation.getText().toString();
            String severity = dialogBinding.spinnerAccidentSeverity.getSelectedItem().toString();
            String chronology = dialogBinding.etAccidentChronology.getText().toString();

            if (date.isEmpty() || chronology.isEmpty()) {
                Toast.makeText(this, "Tanggal dan kronologis wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                int count = AppDatabase.getDatabase(this).workerDao().getAccidentCount(worker.regNo);
                if (count >= 3) {
                    runOnUiThread(() -> Toast.makeText(this, "Batas maksimal 3 kecelakaan tercapai", Toast.LENGTH_LONG).show());
                    return;
                }
                Accident a = new Accident(worker.regNo, date, time, chronology, severity, location);
                AppDatabase.getDatabase(this).workerDao().insertAccident(a);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void addViolationItemToUi(Violation v) {
        View vioView = getLayoutInflater().inflate(R.layout.item_violation_detail, detailBinding.llViolationList, false);
        TextView type = vioView.findViewById(R.id.tvVioDetailType);
        TextView date = vioView.findViewById(R.id.tvVioDetailDate);
        TextView info = vioView.findViewById(R.id.tvVioDetailInfo);
        TextView loc = vioView.findViewById(R.id.tvVioDetailLoc);
        TextView plant = vioView.findViewById(R.id.tvVioDetailPlant);
        TextView docNo = vioView.findViewById(R.id.tvVioDetailDocNo);
        TextView inspector = vioView.findViewById(R.id.tvVioDetailInspector);
        TextView notes = vioView.findViewById(R.id.tvVioDetailNotes);
        View noteContainer = vioView.findViewById(R.id.llVioNoteContainer);

        type.setText(v.type);
        if (date != null) date.setText(v.date != null ? v.date : "-");
        info.setText(cleanFineAmount(v.fine));
        loc.setText(v.location != null ? v.location : "-");
        if (plant != null) plant.setText(v.plant != null ? v.plant : "-");
        if (docNo != null) docNo.setText(v.docNo != null ? v.docNo : "-");
        if (inspector != null) inspector.setText(v.officer != null ? v.officer : "-");

        if (v.notes != null && !v.notes.trim().isEmpty() && !v.notes.equals("-")) {
            if (noteContainer != null) noteContainer.setVisibility(View.VISIBLE);
            notes.setText(v.notes);
        } else {
            if (noteContainer != null) noteContainer.setVisibility(View.GONE);
        }

        vioView.setOnClickListener(view -> showViolationDetailDialog(v));
        detailBinding.llViolationList.addView(vioView);
    }

    private void addReprimandItemToUi(Violation v) {
        View repView = getLayoutInflater().inflate(R.layout.item_reprimand_detail, detailBinding.llReprimandList, false);
        TextView date = repView.findViewById(R.id.tvRepDetailDate);
        TextView notes = repView.findViewById(R.id.tvRepDetailNotes);
        TextView location = repView.findViewById(R.id.tvRepDetailLocation);
        TextView inspector = repView.findViewById(R.id.tvRepDetailInspector);
        View noteContainer = repView.findViewById(R.id.llRepNoteContainer);

        if (date != null) date.setText(v.date != null ? v.date : "-");
        if (location != null) location.setText(v.location != null ? v.location : "-");
        if (inspector != null) inspector.setText(v.officer != null ? v.officer : "-");

        if (v.notes != null && !v.notes.trim().isEmpty() && !v.notes.equals("-")) {
            if (noteContainer != null) noteContainer.setVisibility(View.VISIBLE);
            notes.setText(v.notes);
        } else {
            if (noteContainer != null) noteContainer.setVisibility(View.GONE);
        }

        repView.setOnClickListener(view -> showViolationDetailDialog(v));
        detailBinding.llReprimandList.addView(repView);
    }

    private void showViolationDetailDialog(Violation v) {
        DialogViolationDetailsBinding detailDialogBinding = DialogViolationDetailsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(detailDialogBinding.getRoot()).create();

        boolean isReprimand = "Teguran (Catatan)".equalsIgnoreCase(v.type);
        if (isReprimand) {
            detailDialogBinding.tvVioDetailTitle.setText("Detail Teguran");
            detailDialogBinding.tvDetailOfficerLabel.setText("NAMA PENEGUR");
            detailDialogBinding.layoutDetailFine.setVisibility(View.GONE);
        } else {
            detailDialogBinding.tvVioDetailTitle.setText("Detail Pelanggaran");
            detailDialogBinding.tvDetailOfficerLabel.setText("NAMA PETUGAS");
            detailDialogBinding.layoutDetailFine.setVisibility(View.VISIBLE);
        }

        detailDialogBinding.tvDetailType.setText(v.type);
        detailDialogBinding.tvDetailDate.setText(v.date);
        detailDialogBinding.tvDetailLocation.setText(v.location);
        detailDialogBinding.tvDetailFine.setText(cleanFineAmount(v.fine));
        detailDialogBinding.tvDetailPlant.setText(v.plant);
        detailDialogBinding.tvDetailDocNo.setText(v.docNo != null ? v.docNo : "-");
        detailDialogBinding.tvDetailOfficer.setText(v.officer != null ? v.officer : "-");
        detailDialogBinding.tvDetailNotes.setText(v.notes);

        detailDialogBinding.btnCloseDetail.setOnClickListener(view -> dialog.dismiss());
        if ("admin".equalsIgnoreCase(userRole)) {
            detailDialogBinding.btnDeleteVio.setVisibility(View.VISIBLE);
            detailDialogBinding.btnDeleteVio.setOnClickListener(view -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getDatabase(this).workerDao().deleteViolation(v);
                    runOnUiThread(() -> dialog.dismiss());
                });
            });
        }
        dialog.show();
    }

    private void showReprimandDialog(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogBinding.getRoot()).create();
        dialogBinding.tvVioFormTitle.setText("Teguran — " + worker.name);
        if (dialogBinding.tilInspectorName != null) {
            dialogBinding.tilInspectorName.setHint("Nama Penegur");
        }
        
        // Hide specific fields but keep No. Dokumen visible
        if (dialogBinding.etVioTypeManual != null) ((View)dialogBinding.etVioTypeManual.getParent().getParent()).setVisibility(View.GONE);
        if (dialogBinding.etVioDate != null) ((View)dialogBinding.etVioDate.getParent().getParent()).setVisibility(View.GONE);
        if (dialogBinding.etVioFine != null) ((View)dialogBinding.etVioFine.getParent().getParent()).setVisibility(View.GONE);
        if (dialogBinding.etVioPlant != null) ((View)dialogBinding.etVioPlant.getParent().getParent()).setVisibility(View.GONE);
        
        dialogBinding.etVioTypeManual.setText("Teguran (Catatan)");
        dialogBinding.btnVioCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnVioSave.setOnClickListener(v -> {
            String docNo = dialogBinding.etVioDocNo.getText().toString();
            String notes = dialogBinding.etVioNotes.getText().toString();
            String inspectorName = dialogBinding.etInspectorName.getText().toString();
            String location = dialogBinding.etVioLocation.getText().toString();
            
            if (notes.isEmpty() || inspectorName.isEmpty()) {
                Toast.makeText(this, "Harap isi Catatan dan Nama Penegur", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            
            Executors.newSingleThreadExecutor().execute(() -> {
                Violation violation = new Violation(worker.regNo, "Teguran (Catatan)", today, location.isEmpty() ? "-" : location, notes);
                violation.fine = "-";
                violation.docNo = docNo.isEmpty() ? "-" : docNo;
                violation.officer = inspectorName;
                violation.plant = worker.plantDiv != null ? worker.plantDiv : "-";
                violation.dataSource = "Input di HP";
                AppDatabase.getDatabase(this).workerDao().insertViolation(violation);
                
                // Update worker
                if (worker.status == null || !worker.status.equalsIgnoreCase("Pelanggaran")) {
                    worker.status = "Teguran";
                }
                worker.violationType = "Teguran (Catatan)";
                worker.dateOfEvent = today;
                worker.eventLocation = location.isEmpty() ? "-" : location;
                worker.documentNo = docNo.isEmpty() ? "-" : docNo;
                worker.inspectorName = inspectorName;
                worker.dataSource = "Input di HP";
                AppDatabase.getDatabase(this).workerDao().update(worker);
                
                runOnUiThread(() -> { 
                    Toast.makeText(this, "Berhasil dicatat", Toast.LENGTH_SHORT).show(); 
                    dialog.dismiss(); 
                });
            });
        });
        dialog.show();
    }

    private void showTrainingForm(Worker worker) {
        com.example.scanbar.databinding.DialogTrainingFormBinding dialogBinding = 
                com.example.scanbar.databinding.DialogTrainingFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        // UI Prep
        dialogBinding.etWorkerSearch.setVisibility(View.GONE);
        dialogBinding.rvWorkerSearch.setVisibility(View.GONE);

        String[] options = {"PASS", "FAIL"};
        ArrayAdapter<String> pfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        pfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerPassFail.setAdapter(pfAdapter);

        dialogBinding.btnTrainingCancel.setOnClickListener(v -> dialog.dismiss());
        
        // Use existing helpers from ScanResultActivity or DirectoryFragment pattern
        setupDateFormat(dialogBinding.etTrainingDate);
        setupTimeFormat(dialogBinding.etTrainingTime);
        setupTimeFormat(dialogBinding.etTrainingEndTime);

        dialogBinding.btnTrainingSave.setOnClickListener(v -> {
            String title = dialogBinding.etTrainingTitle.getText().toString();
            String date = dialogBinding.etTrainingDate.getText().toString();
            String start = dialogBinding.etTrainingTime.getText().toString();
            String end = dialogBinding.etTrainingEndTime.getText().toString();
            String loc = dialogBinding.etTrainingLocation.getText().toString();
            String result = dialogBinding.spinnerPassFail.getSelectedItem().toString();

            if (title.isEmpty()) {
                Toast.makeText(this, "Judul training harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                com.example.scanbar.data.Training t = new com.example.scanbar.data.Training(worker.regNo, title, date);
                t.time = start;
                t.endTime = end;
                t.trainingLocation = loc;
                t.passFail = result;
                t.dataSource = "Input di HP";
                
                // Duration calculation logic
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    Date dStart = sdf.parse(start);
                    Date dEnd = sdf.parse(end);
                    if (dStart != null && dEnd != null) {
                        long diff = dEnd.getTime() - dStart.getTime();
                        if (diff < 0) diff += 24 * 60 * 60 * 1000;
                        double hours = (double) diff / (1000 * 60 * 60);
                        t.trainingHours = String.format(Locale.getDefault(), "%.1f", hours);
                    }
                } catch (Exception e) { t.trainingHours = "-"; }

                AppDatabase.getDatabase(this).workerDao().insertTraining(t);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Data Training berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void addTrainingItemToUi(Training t) {
        View trainView = getLayoutInflater().inflate(R.layout.item_training_detail, detailBinding.llTrainingList, false);
        TextView title = trainView.findViewById(R.id.tvTrainTitle);
        TextView status = trainView.findViewById(R.id.tvTrainStatus);
        TextView date = trainView.findViewById(R.id.tvTrainDate);
        TextView code = trainView.findViewById(R.id.tvTrainCode);
        TextView hours = trainView.findViewById(R.id.tvTrainHours);
        TextView loc = trainView.findViewById(R.id.tvTrainLocation);

        title.setText(t.trainingTitle);
        status.setText(t.passFail != null ? t.passFail.toUpperCase() : "PASS");
        date.setText(t.date != null ? t.date : "-");
        code.setText(t.trainingCode != null ? t.trainingCode : "-");
        hours.setText(t.trainingHours != null ? t.trainingHours : "-");
        loc.setText(t.trainingLocation != null ? t.trainingLocation : "-");

        if (t.passFail != null && t.passFail.equalsIgnoreCase("FAIL")) {
            status.setBackgroundResource(R.drawable.bg_status_pill_violation);
        } else {
            status.setBackgroundResource(R.drawable.bg_status_pill_info);
        }

        trainView.setOnClickListener(v -> showTrainingDetailsDialog(t));
        detailBinding.llTrainingList.addView(trainView);
    }

    private void showTrainingDetailsDialog(Training t) {
        DialogTrainingDetailsBinding detailsBinding = DialogTrainingDetailsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(detailsBinding.getRoot()).create();
        detailsBinding.tvTrainTitleInfo.setText(t.trainingTitle);
        detailsBinding.tvTrainDateInfo.setText(t.date != null ? t.date : "-");
        detailsBinding.tvTrainTimeInfo.setText(t.time != null ? t.time : "-");
        detailsBinding.tvTrainEndTimeInfo.setText(t.endTime != null ? t.endTime : "-");
        detailsBinding.tvTrainHoursInfo.setText(t.trainingHours != null ? t.trainingHours : "-");
        detailsBinding.tvTrainLocInfo.setText(t.trainingLocation != null ? t.trainingLocation : "-");
        detailsBinding.tvTrainResultInfo.setText(t.passFail != null ? t.passFail : "-");

        if ("admin".equalsIgnoreCase(userRole)) {
            detailsBinding.btnTrainDelete.setVisibility(View.VISIBLE);
            detailsBinding.btnEditTrain.setVisibility(View.VISIBLE);
        } else {
            detailsBinding.btnTrainDelete.setVisibility(View.GONE);
            detailsBinding.btnEditTrain.setVisibility(View.GONE);
        }

        detailsBinding.btnEditTrain.setOnClickListener(v -> {
            dialog.dismiss();
            showEditTrainingForm(t);
        });

        detailsBinding.btnTrainDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Hapus Data Training")
                .setMessage("Apakah Anda yakin ingin menghapus data training ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).workerDao().deleteTraining(t);
                        runOnUiThread(() -> dialog.dismiss());
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
        });

        detailsBinding.btnTrainDetailClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditTrainingForm(Training t) {
        com.example.scanbar.databinding.DialogTrainingFormBinding dialogBinding = 
                com.example.scanbar.databinding.DialogTrainingFormBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogBinding.getRoot()).create();

        dialogBinding.etWorkerSearch.setVisibility(View.GONE);
        dialogBinding.rvWorkerSearch.setVisibility(View.GONE);

        String[] options = {"PASS", "FAIL"};
        ArrayAdapter<String> pfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        pfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerPassFail.setAdapter(pfAdapter);

        dialogBinding.etTrainingTitle.setText(t.trainingTitle);
        dialogBinding.etTrainingDate.setText(t.date);
        dialogBinding.etTrainingTime.setText(t.time);
        dialogBinding.etTrainingEndTime.setText(t.endTime);
        dialogBinding.etTrainingLocation.setText(t.trainingLocation);
        if (t.passFail != null) {
            int pos = t.passFail.equalsIgnoreCase("PASS") ? 0 : 1;
            dialogBinding.spinnerPassFail.setSelection(pos);
        }

        setupDateFormat(dialogBinding.etTrainingDate);
        setupTimeFormat(dialogBinding.etTrainingTime);
        setupTimeFormat(dialogBinding.etTrainingEndTime);

        dialogBinding.btnTrainingCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnTrainingSave.setOnClickListener(v -> {
            t.trainingTitle = dialogBinding.etTrainingTitle.getText().toString();
            t.date = dialogBinding.etTrainingDate.getText().toString();
            t.time = dialogBinding.etTrainingTime.getText().toString();
            t.endTime = dialogBinding.etTrainingEndTime.getText().toString();
            t.trainingLocation = dialogBinding.etTrainingLocation.getText().toString();
            t.passFail = dialogBinding.spinnerPassFail.getSelectedItem().toString();
            t.trainingHours = calculateDuration(t.time, t.endTime);

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getDatabase(this).workerDao().updateTraining(t);
                runOnUiThread(() -> dialog.dismiss());
            });
        });
        dialog.show();
    }

    private String calculateDuration(String start, String end) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH.mm", Locale.getDefault());
            Date dateStart = sdf.parse(start.replace(":", "."));
            Date dateEnd = sdf.parse(end.replace(":", "."));
            if (dateStart != null && dateEnd != null) {
                long diff = dateEnd.getTime() - dateStart.getTime();
                if (diff < 0) diff += 24 * 60 * 60 * 1000;
                double hours = (double) diff / (1000 * 60 * 60);
                return String.format(Locale.getDefault(), "%.1f", hours);
            }
        } catch (Exception e) {}
        return "-";
    }

    private void addAccidentItemToUi(Accident a) {
        View accView = getLayoutInflater().inflate(R.layout.item_accident_detail, detailBinding.llAccidentList, false);
        TextView date = accView.findViewById(R.id.tvAccidentDate);
        TextView severity = accView.findViewById(R.id.tvAccidentSeverity);
        TextView location = accView.findViewById(R.id.tvAccidentLocation);

        date.setText(a.date != null ? a.date : "-");
        severity.setText(a.severity != null ? a.severity : "-");
        location.setText(a.location != null ? a.location : "-");

        accView.setOnClickListener(v -> showAccidentDetailsDialog(a));
        detailBinding.llAccidentList.addView(accView);
    }

    private void showAccidentDetailsDialog(Accident a) {
        DialogAccidentDetailsBinding detailsBinding = DialogAccidentDetailsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(detailsBinding.getRoot()).create();
        detailsBinding.tvAccidentDetailSeverity.setText(a.severity != null ? a.severity : "-");
        detailsBinding.tvAccidentDetailDate.setText(a.date != null ? a.date : "-");
        detailsBinding.tvAccidentDetailTime.setText(a.time != null ? a.time : "-");
        detailsBinding.tvAccidentDetailLocation.setText(a.location != null ? a.location : "-");
        detailsBinding.tvAccidentDetailChronology.setText(a.chronology != null ? a.chronology : "-");

        if ("admin".equalsIgnoreCase(userRole)) {
            detailsBinding.btnAccidentDelete.setVisibility(View.VISIBLE);
            detailsBinding.btnEditAccident.setVisibility(View.VISIBLE);
        }

        detailsBinding.btnEditAccident.setOnClickListener(v -> {
            dialog.dismiss();
            showEditAccidentForm(a);
        });

        detailsBinding.btnAccidentDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Hapus Data Kecelakaan")
                .setMessage("Apakah Anda yakin ingin menghapus data kecelakaan ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).workerDao().deleteAccident(a);
                        runOnUiThread(() -> dialog.dismiss());
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
        });

        detailsBinding.btnAccidentClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditAccidentForm(Accident a) {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogBinding.getRoot()).create();

        dialogBinding.tvAccidentFormTitle.setText("Edit Kecelakaan");
        dialogBinding.tilWorkerSearch.setVisibility(View.GONE);

        String[] options = {"LTI", "MTI", "First Aid", "Near Hit", "Property Damage"};
        ArrayAdapter<String> sevAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        sevAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerAccidentSeverity.setAdapter(sevAdapter);

        dialogBinding.etAccidentDate.setText(a.date);
        dialogBinding.etAccidentTime.setText(a.time);
        dialogBinding.etAccidentLocation.setText(a.location);
        dialogBinding.etAccidentChronology.setText(a.chronology);
        if (a.severity != null) {
            for (int i = 0; i < options.length; i++) {
                if (a.severity.equalsIgnoreCase(options[i])) {
                    dialogBinding.spinnerAccidentSeverity.setSelection(i);
                    break;
                }
            }
        }

        setupDateFormat(dialogBinding.etAccidentDate);
        setupTimeFormat(dialogBinding.etAccidentTime);

        dialogBinding.btnAccidentCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnAccidentSave.setOnClickListener(v -> {
            a.date = dialogBinding.etAccidentDate.getText().toString();
            a.time = dialogBinding.etAccidentTime.getText().toString();
            a.location = dialogBinding.etAccidentLocation.getText().toString();
            a.severity = dialogBinding.spinnerAccidentSeverity.getSelectedItem().toString();
            a.chronology = dialogBinding.etAccidentChronology.getText().toString();

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getDatabase(this).workerDao().updateAccident(a);
                runOnUiThread(() -> dialog.dismiss());
            });
        });
        dialog.show();
    }

    private void setupDateFormat(android.widget.EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                String formatted = "";
                int cl = clean.length();
                if (cl > 0) {
                    if (cl <= 2) formatted = clean;
                    else if (cl <= 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2);
                    else formatted = clean.substring(0, 2) + "/" + clean.substring(2, 4) + "/" + clean.substring(4, Math.min(cl, 8));
                }
                current = formatted;
                et.setText(current);
                et.setSelection(current.length());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupTimeFormat(android.widget.EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                String formatted = "";
                int cl = clean.length();
                if (cl > 0) {
                    if (cl <= 2) formatted = clean;
                    else formatted = clean.substring(0, 2) + ":" + clean.substring(2, Math.min(cl, 4));
                }
                current = formatted;
                et.setText(current);
                et.setSelection(current.length());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }
}
