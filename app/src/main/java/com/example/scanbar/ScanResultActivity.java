package com.example.scanbar;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
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
import android.net.Uri;

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
        
        // Load User Role
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
        detailBinding.tvDetailRegNoTop.setText("CODE: " + worker.regNo);
        detailBinding.tvDetailRegNo.setText(worker.regNo);
        detailBinding.tvDetailContractor.setText(worker.contractor);
        detailBinding.tvDetailEventDate.setText(worker.dateOfEvent != null ? worker.dateOfEvent : "-");
        detailBinding.tvDetailPlant.setText(worker.plantDiv != null ? worker.plantDiv : "-");

        if (worker.fineAmount != null && !worker.fineAmount.equals("-") && !worker.fineAmount.isEmpty()) {
            detailBinding.layoutFine.setVisibility(View.VISIBLE);
            detailBinding.tvDetailFineBox.setText(cleanFineAmount(worker.fineAmount));
        } else {
            detailBinding.layoutFine.setVisibility(View.GONE);
        }

        // --- NEW ACTION BUTTON LOGIC ---
        detailBinding.tvDetailViolationBadge.setOnClickListener(v -> showViolationForm(worker));
        detailBinding.tvDetailViolationBadge.setText("TAMBAH PELANGGARAN");
        detailBinding.tvDetailViolationBadge.setBackgroundResource(R.drawable.bg_status_pill); 
        detailBinding.tvDetailViolationBadge.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(this, R.color.alert_terracotta));

        detailBinding.btnAddNote.setVisibility(View.VISIBLE);
        detailBinding.btnAddNote.setOnClickListener(v -> showReprimandDialog(worker));

        detailBinding.tvDetailAccidentBadge.setOnClickListener(v -> showAccidentForm(worker));

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
                detailBinding.tvViolationCount.setText("(" + violationCount + ")");
            } else {
                detailBinding.llViolationSection.setVisibility(View.GONE);
            }

            if (reprimandCount > 0) {
                detailBinding.llReprimandSection.setVisibility(View.VISIBLE);
                detailBinding.tvReprimandCount.setText("(" + reprimandCount + ")");
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
                    if (detailBinding.tvTrainingCount != null) {
                        detailBinding.tvTrainingCount.setText("(" + trainings.size() + ")");
                    }
                    for (Training t : trainings) {
                        addTrainingItemToUi(t);
                    }
                } else {
                    detailBinding.llTrainingSection.setVisibility(View.GONE);
                    if (detailBinding.tvTrainingCount != null) {
                        detailBinding.tvTrainingCount.setText("(0)");
                    }
                }
            }
        });

        AppDatabase.getDatabase(this).workerDao().getAccidentsByWorker(worker.regNo).observe(this, accidents -> {
            if (detailBinding.llAccidentList != null) {
                detailBinding.llAccidentList.removeAllViews();
                if (accidents != null && !accidents.isEmpty()) {
                    detailBinding.llAccidentSection.setVisibility(View.VISIBLE);
                    if (detailBinding.tvAccidentCount != null) {
                        detailBinding.tvAccidentCount.setText("(" + accidents.size() + ")");
                    }
                    for (Accident a : accidents) {
                        addAccidentItemToUi(a);
                    }
                } else {
                    detailBinding.llAccidentSection.setVisibility(View.GONE);
                    if (detailBinding.tvAccidentCount != null) {
                        detailBinding.tvAccidentCount.setText("(0)");
                    }
                }
            }
        });
    }

    private void addAccidentItemToUi(Accident a) {
        View accView = getLayoutInflater().inflate(R.layout.item_accident_detail, detailBinding.llAccidentList, false);
        TextView date = accView.findViewById(R.id.tvAccidentDate);
        TextView severity = accView.findViewById(R.id.tvAccidentSeverity);
        TextView location = accView.findViewById(R.id.tvAccidentLocation);

        date.setText(a.date != null ? a.date : "-");
        severity.setText("Keparahan: " + (a.severity != null ? a.severity : "-"));
        location.setText("Lokasi: " + (a.location != null ? a.location : "-"));

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
        }

        detailsBinding.btnAccidentDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Hapus Data Kecelakaan")
                .setMessage("Apakah Anda yakin ingin menghapus data kecelakaan ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).workerDao().deleteAccident(a);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Data Kecelakaan berhasil dihapus", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
        });

        detailsBinding.btnAccidentClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAccidentForm(Worker worker) {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvAccidentFormTitle.setText("Kecelakaan — " + worker.name);
        dialogBinding.tilWorkerSearch.setVisibility(View.GONE);
        dialogBinding.tvAccidentSelectedWorker.setText("Kontraktor: " + worker.name);
        dialogBinding.tvAccidentSelectedWorker.setVisibility(View.VISIBLE);

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
                Toast.makeText(this, "Tanggal dan kronologis harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                Accident a = new Accident(worker.regNo, date, time, chronology, severity, location);
                AppDatabase.getDatabase(this).workerDao().insertAccident(a);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Data Kecelakaan berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
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
        
        // Handle suffix ",00"
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

    private void addViolationItemToUi(Violation v) {
        View vioView = getLayoutInflater().inflate(R.layout.item_violation_detail, detailBinding.llViolationList, false);
        TextView type = vioView.findViewById(R.id.tvVioDetailType);
        TextView info = vioView.findViewById(R.id.tvVioDetailInfo);
        TextView loc = vioView.findViewById(R.id.tvVioDetailLoc);
        TextView plant = vioView.findViewById(R.id.tvVioDetailPlant);
        TextView notes = vioView.findViewById(R.id.tvVioDetailNotes);

        type.setText(v.type);
        info.setText("Denda: " + cleanFineAmount(v.fine));
        loc.setText("LOKASI: " + (v.location != null ? v.location : "-"));
        plant.setText("PLANT: " + (v.plant != null ? v.plant : "-"));

        if (v.notes != null && !v.notes.isEmpty() && !v.notes.equals("-")) {
            notes.setVisibility(View.VISIBLE);
            notes.setText("Catatan: " + v.notes);
        } else {
            notes.setVisibility(View.GONE);
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

        date.setText(v.date != null ? v.date : "-");
        notes.setText(v.notes != null ? v.notes : "-");
        location.setText("Lokasi: " + (v.location != null ? v.location : "-"));
        inspector.setText("Oleh: " + (v.docNo != null ? v.docNo : "Petugas"));

        repView.setOnClickListener(view -> showViolationDetailDialog(v));
        detailBinding.llReprimandList.addView(repView);
    }

    private void showViolationDetailDialog(Violation v) {
        DialogViolationDetailsBinding detailDialogBinding = DialogViolationDetailsBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(detailDialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        boolean isReprimand = "Teguran (Catatan)".equalsIgnoreCase(v.type);
        if (isReprimand) {
            detailDialogBinding.tvVioDetailTitle.setText("Detail Teguran");
            detailDialogBinding.layoutDetailFine.setVisibility(View.GONE);
        } else {
            detailDialogBinding.tvVioDetailTitle.setText("Detail Pelanggaran");
            detailDialogBinding.layoutDetailFine.setVisibility(View.VISIBLE);
        }

        detailDialogBinding.tvDetailType.setText(v.type);
        detailDialogBinding.tvDetailDate.setText(v.date);
        detailDialogBinding.tvDetailLocation.setText(v.location);
        detailDialogBinding.tvDetailFine.setText(cleanFineAmount(v.fine));
        detailDialogBinding.tvDetailPlant.setText(v.plant);
        detailDialogBinding.tvDetailDocNo.setText(v.docNo);
        detailDialogBinding.tvDetailNotes.setText(v.notes);

        // Role Based Visibility
        if ("admin".equalsIgnoreCase(userRole)) {
            detailDialogBinding.btnEditVio.setVisibility(View.VISIBLE);
            detailDialogBinding.btnDeleteVio.setVisibility(View.VISIBLE);
        }

        detailDialogBinding.btnDeleteVio.setOnClickListener(view -> {
            new AlertDialog.Builder(this)
                .setTitle("Hapus")
                .setMessage("Hapus catatan ini?")
                .setPositiveButton("Ya", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).workerDao().deleteViolation(v);
                        runOnUiThread(() -> dialog.dismiss());
                    });
                })
                .setNegativeButton("Tidak", null)
                .show();
        });

        detailDialogBinding.btnEditVio.setOnClickListener(view -> {
            dialog.dismiss();
            showEditViolationForm(v);
        });

        detailDialogBinding.btnCloseDetail.setOnClickListener(view -> dialog.dismiss());
        dialog.show();
    }

    private void showEditViolationForm(Violation v) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvVioFormTitle.setText("Edit Catatan");
        dialogBinding.etVioTypeManual.setText(v.type);
        dialogBinding.etVioDate.setText(v.date);
        dialogBinding.etVioLocation.setText(v.location);
        dialogBinding.etVioFine.setText(v.fine);
        dialogBinding.etVioPlant.setText(v.plant);
        dialogBinding.etVioDocNo.setText(v.docNo);
        dialogBinding.etInspectorName.setText(v.docNo); // Fallback for reprimands
        dialogBinding.etVioNotes.setText(v.notes);

        dialogBinding.btnVioCancel.setOnClickListener(view -> dialog.dismiss());
        dialogBinding.btnVioSave.setOnClickListener(view -> {
            v.type = dialogBinding.etVioTypeManual.getText().toString();
            v.date = dialogBinding.etVioDate.getText().toString();
            v.location = dialogBinding.etVioLocation.getText().toString();
            v.fine = dialogBinding.etVioFine.getText().toString();
            v.plant = dialogBinding.etVioPlant.getText().toString();
            v.docNo = dialogBinding.etVioDocNo.getText().toString();
            v.notes = dialogBinding.etVioNotes.getText().toString();

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getDatabase(this).workerDao().updateViolation(v);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showViolationForm(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvVioFormTitle.setText("Tambah Pelanggaran \u2014 " + worker.name);
        
        // --- 1. Kolom Tanggal (Robust Numeric Auto-Format & Deletion Fix) ---
        dialogBinding.etVioDate.addTextChangedListener(new TextWatcher() {
            private String current = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals(current)) return;

                String clean = s.toString().replaceAll("[^\\d]", "");
                
                String formatted = "";
                int cl = clean.length();
                if (cl > 0) {
                    if (cl <= 2) {
                        formatted = clean;
                    } else if (cl <= 4) {
                        formatted = clean.substring(0, 2) + "/" + clean.substring(2);
                    } else {
                        formatted = clean.substring(0, 2) + "/" + clean.substring(2, 4) + "/" + clean.substring(4, Math.min(cl, 8));
                    }
                }

                current = formatted;
                dialogBinding.etVioDate.setText(current);
                dialogBinding.etVioDate.setSelection(current.length());
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        // --- 2. Kolom Denda (Fix Zero-Loop with Boolean Flag & Stable Cursor) ---
        dialogBinding.etVioFine.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            private String lastValid = "";

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;

                // Strip suffix ",00" if present to prevent re-parsing zeros
                String text = s.toString();
                if (text.contains(",")) {
                    text = text.substring(0, text.indexOf(","));
                }

                String input = text.replaceAll("[^\\d]", "");
                if (input.isEmpty()) {
                    isFormatting = true;
                    dialogBinding.etVioFine.setText("");
                    isFormatting = false;
                    return;
                }

                isFormatting = true;
                try {
                    long parsed = Long.parseLong(input);
                    String formatted = formatFineDisplay(parsed);
                    
                    dialogBinding.etVioFine.setText(formatted);
                    // Stable cursor: Always before ",00"
                    dialogBinding.etVioFine.setSelection(Math.max(0, formatted.length() - 3));
                } catch (NumberFormatException e) {
                    dialogBinding.etVioFine.setText(lastValid);
                }
                isFormatting = false;
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {
                if (!isFormatting) lastValid = s.toString();
            }
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

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(this, "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                int count = AppDatabase.getDatabase(this).workerDao().getFormalViolationCount(worker.regNo);
                if (count >= 5) {
                    runOnUiThread(() -> Toast.makeText(this, "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show());
                    return;
                }

                Violation violation = new Violation(worker.regNo.trim(), type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = docNo;
                violation.plant = plant;
                AppDatabase.getDatabase(this).workerDao().insertViolation(violation);

                // Update worker's primary fields for immediate UI consistency
                worker.status = "Pelanggaran";
                worker.violationType = type;
                worker.dateOfEvent = date;
                worker.fineAmount = fine;
                worker.plantDiv = plant;
                worker.eventLocation = loc;
                worker.documentNo = docNo;
                AppDatabase.getDatabase(this).workerDao().update(worker);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Pelanggaran berhasil dicatat", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showReprimandDialog(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();
        dialogBinding.tvVioFormTitle.setText("Teguran \u2014 " + worker.name);
        
        // Use the new container to hide specific fields cleanly
        if (dialogBinding.layoutVioSpecificFields != null) {
            dialogBinding.layoutVioSpecificFields.setVisibility(View.GONE);
        }
        
        dialogBinding.etVioTypeManual.setText("Teguran (Catatan)");

        dialogBinding.btnVioCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnVioSave.setOnClickListener(v -> {
            String inspectorName = (dialogBinding.etInspectorName != null && dialogBinding.etInspectorName.getText() != null) ?
                                  dialogBinding.etInspectorName.getText().toString() : "";
            String notes = (dialogBinding.etVioNotes != null && dialogBinding.etVioNotes.getText() != null) ? 
                          dialogBinding.etVioNotes.getText().toString() : "";
            String location = (dialogBinding.etVioLocation != null && dialogBinding.etVioLocation.getText() != null) ? 
                          dialogBinding.etVioLocation.getText().toString() : "-";

            if (inspectorName.isEmpty()) {
                Toast.makeText(this, "Nama Penegur tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            if (notes.isEmpty()) {
                Toast.makeText(this, "Catatan tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            Executors.newSingleThreadExecutor().execute(() -> {
                Violation violation = new Violation(worker.regNo.trim(), "Teguran (Catatan)", today, location, notes);
                violation.fine = "-";
                violation.docNo = inspectorName;
                violation.plant = worker.plantDiv != null ? worker.plantDiv : "-";
                AppDatabase.getDatabase(this).workerDao().insertViolation(violation);

                // Update worker's primary fields for immediate UI consistency
                worker.status = "Pelanggaran"; 
                worker.violationType = "Teguran (Catatan)";
                worker.dateOfEvent = today;
                worker.eventLocation = location;
                worker.documentNo = inspectorName;
                AppDatabase.getDatabase(this).workerDao().update(worker);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Teguran berhasil dicatat", Toast.LENGTH_SHORT).show();
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
        TextView loc = trainView.findViewById(R.id.tvTrainLocation);
        title.setText(t.trainingTitle);
        status.setText(t.passFail != null ? t.passFail.toUpperCase() : "PASS");
        date.setText(t.date != null ? t.date : "-");
        loc.setText(t.trainingLocation != null ? t.trainingLocation : "-");
        if (t.passFail != null && t.passFail.equalsIgnoreCase("FAIL")) {
            status.setBackgroundResource(R.drawable.bg_status_pill_error);
        } else {
            status.setBackgroundResource(R.drawable.bg_status_pill_success);
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
        } else {
            detailsBinding.btnTrainDelete.setVisibility(View.GONE);
        }

        detailsBinding.btnTrainDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Hapus Data Training")
                .setMessage("Apakah Anda yakin ingin menghapus data training ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getDatabase(this).workerDao().deleteTraining(t);
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Data Training berhasil dihapus", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
        });
        detailsBinding.btnTrainDetailClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
