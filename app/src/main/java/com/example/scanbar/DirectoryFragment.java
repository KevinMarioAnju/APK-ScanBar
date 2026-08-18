package com.example.scanbar;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.Worker;
import com.example.scanbar.data.WorkerDao;
import android.widget.Spinner;
import android.widget.Toast;
import com.example.scanbar.data.Violation;
import com.example.scanbar.databinding.DialogViolationFormBinding;
import com.example.scanbar.databinding.LayoutWorkerDetailsSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.example.scanbar.databinding.DialogWorkerFormBinding;
import com.example.scanbar.databinding.FragmentDirectoryBinding;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

public class DirectoryFragment extends Fragment implements WorkerAdapter.OnWorkerActionListener {
    private FragmentDirectoryBinding binding;
    private WorkerAdapter adapter;
    private WorkerDao workerDao;
    private LiveData<List<Worker>> currentLiveData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDirectoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        workerDao = AppDatabase.getDatabase(getContext()).workerDao();
        setupRecyclerView();
        setupModernFilters();
        setupSearch();
        observeWorkers(workerDao.getAllWorkers());

        binding.btnAddWorker.setOnClickListener(v -> showWorkerDialog(null));
        binding.btnExport.setOnClickListener(this::showExportMenu);
        binding.btnImport.setOnClickListener(v -> openFilePicker());
    }

    private void openFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimetypes = {"application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"};
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimetypes);
        startActivityForResult(intent, 101);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                readExcelFile(uri);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new WorkerAdapter(this);
        binding.rvWorkers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvWorkers.setAdapter(adapter);
        
        // Performance optimization for large lists
        binding.rvWorkers.setHasFixedSize(true);
        binding.rvWorkers.setItemViewCacheSize(20);
    }

    private void setupModernFilters() {
        binding.chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            applyFilters();
        });
    }

    private void setupFilters() {
        // Keep this for backward compatibility or remove if not needed
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            applyFilters();
            return true;
        });
    }

    private void applyFilters() {
        String query = binding.etSearch.getText().toString().trim();
        int checkedId = binding.chipGroupFilter.getCheckedChipId();

        if (currentLiveData != null) {
            currentLiveData.removeObservers(getViewLifecycleOwner());
        }

        if (!query.isEmpty()) {
            observeWorkers(workerDao.searchWorkers(query));
        } else {
            if (checkedId == R.id.chipViolation) {
                observeWorkers(workerDao.getWorkersWithViolations());
            } else if (checkedId == R.id.chipClean) {
                observeWorkers(workerDao.getCleanWorkers());
            } else {
                observeWorkers(workerDao.getAllWorkers());
            }
        }
    }

    private void observeWorkers(LiveData<List<Worker>> liveData) {
        currentLiveData = liveData;
        currentLiveData.observe(getViewLifecycleOwner(), workers -> {
            adapter.setWorkers(workers);
            binding.tvResultCount.setText(String.format(java.util.Locale.getDefault(), "%,d hasil", workers.size()).replace(',', '.'));
        });
    }

    private void showExportMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add("Export as PDF");
        popup.getMenu().add("Export as XLS");
        popup.getMenu().add("Export as CSV");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.contains("CSV")) {
                exportToCSV();
            } else {
                Toast.makeText(getContext(), "Fitur " + title + " sedang disiapkan...", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    private void exportToCSV() {
        workerDao.getAllWorkers().observe(getViewLifecycleOwner(), workers -> {
            if (workers == null || workers.isEmpty()) return;
            
            StringBuilder csv = new StringBuilder();
            csv.append("ID Pekerja (Reg. No),Nama Pekerja,Status Pelanggaran,Nama Kontraktor,Jabatan,Tanggal Kejadian,Jenis Pelanggaran,Denda (Rp),Plant/Divisi,Lokasi Kejadian,No. Dokumen\n");
            for (Worker w : workers) {
                csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n", 
                    w.regNo, w.name, w.status, w.contractor, w.position, 
                    w.dateOfEvent, w.violationType, w.fineAmount, w.plantDiv, 
                    w.eventLocation, w.documentNo));
            }

            try {
                java.io.File file = new java.io.File(getContext().getExternalFilesDir(null), "Direktori_Pekerja.csv");
                java.io.FileWriter writer = new java.io.FileWriter(file);
                writer.write(csv.toString());
                writer.close();
                Toast.makeText(getContext(), "Berhasil: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Gagal ekspor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWorkerDialog(@Nullable Worker worker) {
        DialogWorkerFormBinding dialogBinding = DialogWorkerFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        
        AlertDialog dialog = builder.create();

        // Setup Spinner Status
        String[] statuses = {"Bersih", "Pelanggaran"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, statuses);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerStatus.setAdapter(statusAdapter);

        if (worker != null) {
            dialogBinding.tvFormTitle.setText("Edit Kontraktor — " + worker.name);
            dialogBinding.etRegNo.setText(worker.regNo);
            dialogBinding.etName.setText(worker.name);
            dialogBinding.etContractor.setText(worker.contractor);
            dialogBinding.etPosition.setText(worker.position);
            
            // Set status spinner
            int statusPos = 0;
            if (worker.status != null && (worker.status.equalsIgnoreCase("Pelanggaran") || worker.status.contains("1") || worker.status.contains("PELANGGARAN"))) {
                statusPos = 1;
            }
            dialogBinding.spinnerStatus.setSelection(statusPos);
            
            dialogBinding.etEventDate.setText(worker.dateOfEvent);
            dialogBinding.etVioType.setText(worker.violationType);
            dialogBinding.etFine.setText(worker.fineAmount);
            dialogBinding.etPlant.setText(worker.plantDiv);
            dialogBinding.etLocation.setText(worker.eventLocation);
            dialogBinding.etDocNo.setText(worker.documentNo);

            
            dialogBinding.btnSave.setText("Simpan Perubahan");
        }

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Toggle violation fields visibility based on status
        dialogBinding.spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                if (selected.equalsIgnoreCase("Bersih")) {
                    dialogBinding.layoutViolationFields.setVisibility(View.GONE);
                } else {
                    dialogBinding.layoutViolationFields.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        dialogBinding.btnSave.setOnClickListener(v -> {
            String regNo = dialogBinding.etRegNo.getText().toString();
            String name = dialogBinding.etName.getText().toString();
            String contractor = dialogBinding.etContractor.getText().toString();
            String position = dialogBinding.etPosition.getText().toString();
            String status = dialogBinding.spinnerStatus.getSelectedItem().toString();
            String eventDate = dialogBinding.etEventDate.getText().toString();
            String vioType = dialogBinding.etVioType.getText().toString();
            String fine = dialogBinding.etFine.getText().toString();
            String plant = dialogBinding.etPlant.getText().toString();
            String location = dialogBinding.etLocation.getText().toString();
            String docNo = dialogBinding.etDocNo.getText().toString();

            if (regNo.isEmpty() || name.isEmpty()) return;

            Executors.newSingleThreadExecutor().execute(() -> {
                if (worker == null) {
                    Worker newWorker = new Worker(regNo, name, contractor, position, status);
                    newWorker.dateOfEvent = eventDate;
                    newWorker.violationType = vioType;
                    newWorker.fineAmount = fine;
                    newWorker.plantDiv = plant;
                    newWorker.eventLocation = location;
                    newWorker.documentNo = docNo;
                    workerDao.insert(newWorker);
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Kontraktor berhasil ditambahkan", Toast.LENGTH_SHORT).show());
                } else {
                    worker.regNo = regNo;
                    worker.name = name;
                    worker.contractor = contractor;
                    worker.position = position;
                    worker.status = status;
                    worker.dateOfEvent = eventDate;
                    worker.violationType = vioType;
                    worker.fineAmount = fine;
                    worker.plantDiv = plant;
                    worker.eventLocation = location;
                    worker.documentNo = docNo;
                    workerDao.update(worker);
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Data Kontraktor berhasil diperbarui", Toast.LENGTH_SHORT).show());
                }
            });
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showViolationDialog(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvVioFormTitle.setText("Pelanggaran — " + worker.name);
        
        String[] types = {"Merokok di tempat yang tidak diizinkan", "Tidak menggunakan APD", "Pelanggaran Safety Lainnya"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, types);
        dialogBinding.spinnerVioType.setAdapter(typeAdapter);

        dialogBinding.btnVioClose.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnVioCancel.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnVioSave.setOnClickListener(v -> {
            String type = dialogBinding.spinnerVioType.getSelectedItem().toString();
            String date = dialogBinding.etVioDate.getText().toString();
            String loc = dialogBinding.etVioLocation.getText().toString();
            String notes = dialogBinding.etVioNotes.getText().toString();

            Executors.newSingleThreadExecutor().execute(() -> {
                Violation violation = new Violation(worker.regNo, type, date, loc, notes);
                workerDao.insertViolation(violation);
                
                worker.status = "Pelanggaran"; // Mark worker as having violation
                workerDao.update(worker);
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Pelanggaran berhasil dicatat", Toast.LENGTH_SHORT).show());
            });
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onEdit(Worker worker) {
        showWorkerDialog(worker);
    }

    @Override
    public void onDelete(Worker worker) {
        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hapus Kontraktor")
                .setMessage("Hapus data Kontraktor dengan No. Reg " + worker.regNo + "? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("OK", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        workerDao.delete(worker);
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Data Kontraktor berhasil dihapus", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onViolation(Worker worker) {
        showViolationDialog(worker);
    }

    @Override
    public void onDetail(Worker worker) {
        showWorkerDetailsSheet(worker);
    }

    private void showWorkerDetailsSheet(Worker worker) {
        LayoutWorkerDetailsSheetBinding sheetBinding = LayoutWorkerDetailsSheetBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.tvSheetName.setText(worker.name);
        sheetBinding.tvSheetRegNo.setText("Reg No: " + worker.regNo);
        sheetBinding.tvSheetPosition.setText(worker.position != null ? worker.position : "-");
        sheetBinding.tvSheetContractor.setText(worker.contractor != null ? worker.contractor : "-");
        sheetBinding.tvSheetDate.setText(worker.dateOfEvent != null ? worker.dateOfEvent : "-");
        sheetBinding.tvSheetVioType.setText(worker.violationType != null ? worker.violationType : "-");
        sheetBinding.tvSheetPlant.setText(worker.plantDiv != null ? worker.plantDiv : "-");
        sheetBinding.tvSheetLocation.setText(worker.eventLocation != null ? worker.eventLocation : "-");
        sheetBinding.tvSheetDocNo.setText(worker.documentNo != null ? worker.documentNo : "-");
        
        if (worker.fineAmount != null && !worker.fineAmount.equals("-") && !worker.fineAmount.isEmpty()) {
            sheetBinding.layoutFine.setVisibility(View.VISIBLE);
            sheetBinding.tvSheetFine.setText(cleanFineAmount(worker.fineAmount));
        } else {
            sheetBinding.layoutFine.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private String cleanFineAmount(String fine) {
        if (fine == null || fine.isEmpty() || fine.equals("-")) return "Rp -";
        String clean = fine.replace("-", "").trim();
        if (!clean.startsWith("Rp")) {
            clean = "Rp " + clean;
        }
        return clean;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void readExcelFile(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                Workbook workbook = new XSSFWorkbook(inputStream);
                Sheet sheet = workbook.getSheetAt(0);
                
                int importedCount = 0;
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // Skip header

                    // Map columns based on common order or headers
                    String regNo = getCellValue(row, 0);
                    String name = getCellValue(row, 1);
                    String status = getCellValue(row, 2);
                    String contractor = getCellValue(row, 3);
                    String position = getCellValue(row, 4);
                    
                    if (regNo.isEmpty() && name.isEmpty()) continue;

                    Worker worker = new Worker(regNo, name, contractor, position, status);
                    
                    // Map additional fields if available in Excel columns 5-11
                    worker.dateOfEvent = getCellValue(row, 5);
                    worker.violationType = getCellValue(row, 6);
                    worker.fineAmount = getCellValue(row, 7);
                    worker.plantDiv = getCellValue(row, 8);
                    worker.eventLocation = getCellValue(row, 9);
                    worker.documentNo = getCellValue(row, 10);

                    workerDao.insert(worker);
                    importedCount++;
                }
                
                workbook.close();
                int finalCount = importedCount;
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Berhasil mengimpor " + finalCount + " data", Toast.LENGTH_LONG).show());
                
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "Gagal membaca file Excel", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String getCellValue(Row row, int cellIndex) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(cellIndex);
        if (cell == null) return "-";
        
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "-";
        }
    }
}