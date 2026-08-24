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
import androidx.recyclerview.widget.RecyclerView;
import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.Worker;
import com.example.scanbar.data.WorkerDao;
import com.example.scanbar.data.WorkerWithStats;
import android.widget.Spinner;
import android.widget.TextView;
import com.example.scanbar.data.Training;
import com.example.scanbar.data.Violation;
import com.example.scanbar.databinding.DialogTrainingDetailsBinding;
import com.example.scanbar.databinding.DialogViolationFormBinding;
import com.example.scanbar.databinding.LayoutWorkerDetailsSheetBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.example.scanbar.databinding.DialogWorkerFormBinding;
import com.example.scanbar.databinding.FragmentDirectoryBinding;
import com.example.scanbar.databinding.ItemViolationFormBinding;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import com.example.scanbar.databinding.DialogViolationDetailsBinding;
import android.net.Uri;

import com.example.scanbar.data.Accident;
import com.example.scanbar.databinding.DialogAccidentDetailsBinding;
import com.example.scanbar.databinding.DialogAccidentFormBinding;
import com.example.scanbar.databinding.ItemAccidentDetailBinding;

public class DirectoryFragment extends Fragment implements WorkerAdapter.OnWorkerActionListener {
    private FragmentDirectoryBinding binding;
    private WorkerAdapter adapter;
    private WorkerDao workerDao;
    private LiveData<List<WorkerWithStats>> currentLiveData;
    private String userRole = "inspektur";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentDirectoryBinding.inflate(inflater, container, false);
        
        // Load User Role
        if (getContext() != null) {
            userRole = getContext().getSharedPreferences("ScanBarSession", android.content.Context.MODE_PRIVATE).getString("ROLE", "inspektur");
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        workerDao = AppDatabase.getDatabase(getContext()).workerDao();
        setupRecyclerView();
        setupModernFilters();
        setupSearch();
        observeWorkers(workerDao.getAllWorkersWithStats());

        if ("admin".equalsIgnoreCase(userRole)) {
            binding.btnAddWorker.setVisibility(View.VISIBLE);
            binding.btnExport.setVisibility(View.VISIBLE);
            binding.btnImport.setVisibility(View.VISIBLE);
        } else {
            binding.btnAddWorker.setVisibility(View.VISIBLE); // Let inspector add reports
            binding.btnExport.setVisibility(View.GONE);
            binding.btnImport.setVisibility(View.GONE);
        }

        binding.btnAddWorker.setOnClickListener(v -> showAddMenu(v));
        binding.btnExport.setOnClickListener(this::showExportMenu);
        binding.btnImport.setOnClickListener(v -> openFilePicker());
    }

    private void showAddMenu(View v) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenu().add("Tambah Kontraktor");
        popup.getMenu().add("Tambah Pelanggaran");
        popup.getMenu().add("Tambah Training");
        popup.getMenu().add("Tambah Kecelakaan");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Tambah Kontraktor")) {
                showWorkerDialog(null);
            } else if (item.getTitle().equals("Tambah Pelanggaran")) {
                showViolationSearchDialog();
            } else if (item.getTitle().equals("Tambah Training")) {
                showTrainingDialog();
            } else {
                showAccidentDialog();
            }
            return true;
        });
        popup.show();
    }

    private void showAccidentDialogForWorker(Worker worker) {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvAccidentFormTitle.setText("Kecelakaan — " + worker.name);
        dialogBinding.tilWorkerSearch.setVisibility(View.GONE);
        dialogBinding.tvAccidentSelectedWorker.setText("Kontraktor: " + worker.name);
        dialogBinding.tvAccidentSelectedWorker.setVisibility(View.VISIBLE);

        String[] options = {"LTI", "MTI", "First Aid", "Near Hit", "Property Damage"};
        ArrayAdapter<String> sevAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, options);
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
                Toast.makeText(getContext(), "Tanggal dan kronologis harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                Accident a = new Accident(worker.regNo, date, time, chronology, severity, location);
                workerDao.insertAccident(a);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Data Kecelakaan berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    showWorkerDetailsSheet(worker); // Refresh sheet
                });
            });
        });
        dialog.show();
    }

    private void showAccidentDialog() {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        final Worker[] selectedWorker = {null};

        // Setup Severity Spinner
        String[] options = {"LTI", "MTI", "First Aid", "Near Hit", "Property Damage"};
        ArrayAdapter<String> sevAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, options);
        sevAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerAccidentSeverity.setAdapter(sevAdapter);

        // Worker Search Logic
        dialogBinding.etAccidentWorkerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    workerDao.searchWorkers(query).observe(getViewLifecycleOwner(), workers -> {
                        if (workers != null && !workers.isEmpty()) {
                            dialogBinding.rvAccidentWorkerSearch.setVisibility(View.VISIBLE);
                            setupMinimalWorkerList(dialogBinding.rvAccidentWorkerSearch, workers, worker -> {
                                selectedWorker[0] = worker;
                                dialogBinding.tvAccidentSelectedWorker.setText("Kontraktor Terpilih: " + worker.name);
                                dialogBinding.tvAccidentSelectedWorker.setVisibility(View.VISIBLE);
                                dialogBinding.rvAccidentWorkerSearch.setVisibility(View.GONE);
                                dialogBinding.etAccidentWorkerSearch.setText(worker.name);
                            });
                        } else {
                            dialogBinding.rvAccidentWorkerSearch.setVisibility(View.GONE);
                        }
                    });
                } else {
                    dialogBinding.rvAccidentWorkerSearch.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogBinding.btnAccidentCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnAccidentSave.setOnClickListener(v -> {
            if (selectedWorker[0] == null) {
                Toast.makeText(getContext(), "Pilih kontraktor terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }

            String date = dialogBinding.etAccidentDate.getText().toString();
            String time = dialogBinding.etAccidentTime.getText().toString();
            String location = dialogBinding.etAccidentLocation.getText().toString();
            String severity = dialogBinding.spinnerAccidentSeverity.getSelectedItem().toString();
            String chronology = dialogBinding.etAccidentChronology.getText().toString();

            if (date.isEmpty() || chronology.isEmpty()) {
                Toast.makeText(getContext(), "Tanggal dan kronologis harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                Accident a = new Accident(selectedWorker[0].regNo, date, time, chronology, severity, location);
                workerDao.insertAccident(a);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Data Kecelakaan berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });

        dialog.show();
    }

    private void showTrainingDialog() {
        com.example.scanbar.databinding.DialogTrainingFormBinding dialogBinding = 
                com.example.scanbar.databinding.DialogTrainingFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        final Worker[] selectedWorker = {null};

        // Setup Pass/Fail Spinner
        String[] options = {"PASS", "FAIL"};
        ArrayAdapter<String> pfAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, options);
        pfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerPassFail.setAdapter(pfAdapter);

        // Worker Search Logic
        dialogBinding.etWorkerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    workerDao.searchWorkers(query).observe(getViewLifecycleOwner(), workers -> {
                        if (workers != null && !workers.isEmpty()) {
                            dialogBinding.rvWorkerSearch.setVisibility(View.VISIBLE);
                            setupMinimalWorkerList(dialogBinding.rvWorkerSearch, workers, worker -> {
                                selectedWorker[0] = worker;
                                dialogBinding.tvSelectedWorker.setText("Kontraktor Terpilih: " + worker.name);
                                dialogBinding.tvSelectedWorker.setVisibility(View.VISIBLE);
                                dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                                dialogBinding.etWorkerSearch.setText(worker.name);
                            });
                        } else {
                            dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                        }
                    });
                } else {
                    dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogBinding.btnTrainingCancel.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnTrainingSave.setOnClickListener(v -> {
            if (selectedWorker[0] == null) {
                Toast.makeText(getContext(), "Pilih kontraktor terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }

            String title = dialogBinding.etTrainingTitle.getText().toString();
            String date = dialogBinding.etTrainingDate.getText().toString();
            String startTime = dialogBinding.etTrainingTime.getText().toString();
            String endTime = dialogBinding.etTrainingEndTime.getText().toString();
            String location = dialogBinding.etTrainingLocation.getText().toString();
            String result = dialogBinding.spinnerPassFail.getSelectedItem().toString();

            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Judul training harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                com.example.scanbar.data.Training t = new com.example.scanbar.data.Training(selectedWorker[0].regNo, title, date);
                t.time = startTime;
                t.endTime = endTime;
                t.trainingLocation = location;
                t.passFail = result;
                
                // Calculate duration if possible
                t.trainingHours = calculateDuration(startTime, endTime);
                
                workerDao.insertTraining(t);
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Data Training berhasil disimpan", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
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
                if (diff < 0) diff += 24 * 60 * 60 * 1000; // Handle overnight
                double hours = (double) diff / (1000 * 60 * 60);
                return String.format(Locale.getDefault(), "%.1f", hours);
            }
        } catch (Exception e) {}
        return "-";
    }

    private void setupMinimalWorkerList(RecyclerView rv, List<Worker> workers, java.util.function.Consumer<Worker> onSelect) {
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Worker w = workers.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMiniName);
                TextView tvReg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                tvName.setText(w.name);
                tvReg.setText(w.regNo);
                holder.itemView.setOnClickListener(v -> onSelect.accept(w));
            }
            @Override public int getItemCount() { return workers.size(); }
        });
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
        adapter = new WorkerAdapter(this, userRole);
        binding.rvWorkers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvWorkers.setAdapter(adapter);
        
        // Performance optimization for large lists
        binding.rvWorkers.setHasFixedSize(true);
        binding.rvWorkers.setItemViewCacheSize(20);
    }

    private void setupModernFilters() {
        // Now using PopupMenu triggered by the 'Aktif' button
        binding.btnFilterStatus.setOnClickListener(v -> {
            PopupMenu filterMenu = new PopupMenu(getContext(), v);
            filterMenu.getMenu().add("Semua");
            filterMenu.getMenu().add("Bersih");
            filterMenu.getMenu().add("Pelanggaran");
            filterMenu.getMenu().add("Teguran");
            filterMenu.getMenu().add("Training");
            filterMenu.getMenu().add("Kecelakaan");

            filterMenu.setOnMenuItemClickListener(item -> {
                String selected = item.getTitle().toString();
                binding.tvFilterLabel.setText(selected);
                applyFilters();
                return true;
            });
            filterMenu.show();
        });

        // Initialize with default text
        binding.tvFilterLabel.setText("Semua");
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
        String selectedFilter = binding.tvFilterLabel.getText().toString();

        if (currentLiveData != null) {
            currentLiveData.removeObservers(getViewLifecycleOwner());
        }

        if (!query.isEmpty()) {
            observeWorkers(workerDao.searchWorkersWithStats(query));
        } else {
            if ("Pelanggaran".equals(selectedFilter)) {
                observeWorkers(workerDao.getWorkersWithViolationsWithStats());
            } else if ("Teguran".equals(selectedFilter)) {
                observeWorkers(workerDao.getWorkersWithReprimandsWithStats());
            } else if ("Training".equals(selectedFilter)) {
                observeWorkers(workerDao.getWorkersWithTrainingsWithStats());
            } else if ("Kecelakaan".equals(selectedFilter)) {
                observeWorkers(workerDao.getWorkersWithAccidentsWithStats());
            } else if ("Bersih".equals(selectedFilter)) {
                observeWorkers(workerDao.getCleanWorkersWithStats());
            } else {
                observeWorkers(workerDao.getAllWorkersWithStats());
            }
        }
    }

    private void observeWorkers(LiveData<List<WorkerWithStats>> liveData) {
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
            dialogBinding.etGlobalDocNo.setText(worker.documentNo);

            // Load additional violations
            Executors.newSingleThreadExecutor().execute(() -> {
                List<Violation> violations = workerDao.getViolationsSync(worker.regNo);
                getActivity().runOnUiThread(() -> {
                    for (Violation v : violations) {
                        // Avoid displaying the violation that is already shown in the main card
                        // (Usually the first one if it matches the worker's direct violation fields)
                        boolean isDuplicate = v.type != null && v.type.equals(worker.violationType) && 
                                            v.date != null && v.date.equals(worker.dateOfEvent);
                        
                        if (!isDuplicate) {
                            addViolationFieldBlock(dialogBinding.layoutAdditionalViolations, v);
                        }
                    }
                });
            });
            
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
                    dialogBinding.layoutAdditionalViolations.setVisibility(View.GONE);
                    dialogBinding.tilGlobalDocNo.setVisibility(View.GONE);
                } else {
                    dialogBinding.layoutViolationFields.setVisibility(View.VISIBLE);
                    dialogBinding.layoutAdditionalViolations.setVisibility(View.VISIBLE);
                    dialogBinding.tilGlobalDocNo.setVisibility(View.VISIBLE);
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
            
            // Collect main violation fields
            String eventDate = dialogBinding.etEventDate.getText().toString();
            String vioType = dialogBinding.etVioType.getText().toString();
            String fine = dialogBinding.etFine.getText().toString();
            String plant = dialogBinding.etPlant.getText().toString();
            String location = dialogBinding.etLocation.getText().toString();
            String docNo = dialogBinding.etGlobalDocNo.getText().toString();

            if (regNo.isEmpty() || name.isEmpty()) return;

            // Collect additional violations FROM UI THREAD
            List<Violation> additionalViolations = new ArrayList<>();
            int formalViolationCount = 0;
            
            // Check main violation fields first
            boolean hasMainViolation = !vioType.isEmpty() && !vioType.equals("-");
            if (hasMainViolation) {
                if (!vioType.toLowerCase().contains("teguran")) {
                    formalViolationCount++;
                }
                Violation mainVio = new Violation(regNo, vioType, eventDate, location, "");
                mainVio.fine = fine;
                mainVio.plant = plant;
                mainVio.docNo = docNo;
                additionalViolations.add(mainVio);
            }

            int childCount = dialogBinding.layoutAdditionalViolations.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = dialogBinding.layoutAdditionalViolations.getChildAt(i);
                ItemViolationFormBinding vioBinding = ItemViolationFormBinding.bind(child);
                
                String vType = vioBinding.etVioType.getText().toString();
                if (vType != null && !vType.isEmpty() && !vType.equals("-")) {
                    if (!vType.toLowerCase().contains("teguran")) {
                        formalViolationCount++;
                    }

                    Violation violationItem = new Violation(regNo, 
                        vType,
                        vioBinding.etVioDate.getText().toString(),
                        vioBinding.etVioLocation.getText().toString(),
                        "" // notes
                    );
                    violationItem.fine = vioBinding.etVioFine.getText().toString();
                    violationItem.plant = vioBinding.etVioPlant.getText().toString();
                    violationItem.docNo = docNo; // Use global doc no
                    additionalViolations.add(violationItem);
                }
            }

            if (formalViolationCount > 5) {
                Toast.makeText(getContext(), "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show();
                return;
            }

            // Determine final status based on whether ANY violation/reprimand exists
            String finalStatus = (additionalViolations.isEmpty()) ? "Bersih" : "Pelanggaran";

            Executors.newSingleThreadExecutor().execute(() -> {
                Worker targetWorker = worker;
                if (targetWorker == null) {
                    targetWorker = new Worker(regNo, name, contractor, position, finalStatus);
                } else {
                    targetWorker.regNo = regNo;
                    targetWorker.name = name;
                    targetWorker.contractor = contractor;
                    targetWorker.position = position;
                    targetWorker.status = finalStatus;
                }
                
                targetWorker.dateOfEvent = eventDate;
                targetWorker.violationType = vioType;
                targetWorker.fineAmount = fine;
                targetWorker.plantDiv = plant;
                targetWorker.eventLocation = location;
                targetWorker.documentNo = docNo;

                if (worker == null) {
                    workerDao.insert(targetWorker);
                } else {
                    workerDao.update(targetWorker);
                }

                // Handle additional violations
                workerDao.deleteViolationsByWorker(regNo);
                for (Violation addVio : additionalViolations) {
                    workerDao.insertViolation(addVio);
                }
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), worker == null ? "Kontraktor berhasil ditambahkan" : "Data Kontraktor berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });

        dialog.show();
    }

    private void addViolationFieldBlock(ViewGroup container, @Nullable Violation violation) {
        ItemViolationFormBinding vioBinding = ItemViolationFormBinding.inflate(getLayoutInflater(), container, false);
        
        if (violation != null) {
            vioBinding.etVioDate.setText(violation.date);
            vioBinding.etVioType.setText(violation.type);
            vioBinding.etVioFine.setText(violation.fine);
            vioBinding.etVioPlant.setText(violation.plant);
            vioBinding.etVioLocation.setText(violation.location);
        }
        
        vioBinding.btnRemoveViolation.setOnClickListener(v -> container.removeView(vioBinding.getRoot()));
        
        container.addView(vioBinding.getRoot());
    }

    private void showViolationSearchDialog() {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        final Worker[] selectedWorker = {null};

        dialogBinding.tvVioFormTitle.setText("Tambah Pelanggaran");
        
        // Show worker search UI in violation form
        dialogBinding.llWorkerSearchContainer.setVisibility(View.VISIBLE);
        dialogBinding.etWorkerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    workerDao.searchWorkers(query).observe(getViewLifecycleOwner(), workers -> {
                        if (workers != null && !workers.isEmpty()) {
                            dialogBinding.rvWorkerSearch.setVisibility(View.VISIBLE);
                            setupMinimalWorkerList(dialogBinding.rvWorkerSearch, workers, worker -> {
                                selectedWorker[0] = worker;
                                dialogBinding.tvSelectedWorker.setText("Kontraktor: " + worker.name);
                                dialogBinding.tvSelectedWorker.setVisibility(View.VISIBLE);
                                dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                                dialogBinding.etWorkerSearch.setText(worker.name);
                            });
                        } else {
                            dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                        }
                    });
                } else {
                    dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- Date Format Logic ---
        dialogBinding.etVioDate.addTextChangedListener(new TextWatcher() {
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
                dialogBinding.etVioDate.setText(current);
                dialogBinding.etVioDate.setSelection(current.length());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- Fine Format Logic ---
        dialogBinding.etVioFine.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                String text = s.toString();
                if (text.contains(",")) text = text.substring(0, text.indexOf(","));
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
                    dialogBinding.etVioFine.setSelection(Math.max(0, formatted.length() - 3));
                } catch (Exception e) {}
                isFormatting = false;
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogBinding.btnVioCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnVioSave.setOnClickListener(v -> {
            if (selectedWorker[0] == null) {
                Toast.makeText(getContext(), "Pilih kontraktor terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }
            String type = dialogBinding.etVioTypeManual.getText().toString().trim();
            String date = dialogBinding.etVioDate.getText().toString();
            String loc = dialogBinding.etVioLocation.getText().toString();
            String notes = dialogBinding.etVioNotes.getText().toString();
            String fine = dialogBinding.etVioFine.getText().toString();
            String plant = dialogBinding.etVioPlant.getText().toString();
            String inspector = dialogBinding.etInspectorName.getText().toString();

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(getContext(), "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                Violation violation = new Violation(selectedWorker[0].regNo.trim(), type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = inspector;
                violation.plant = plant;
                workerDao.insertViolation(violation);

                // Update worker's primary fields for immediate UI consistency
                selectedWorker[0].status = "Pelanggaran";
                selectedWorker[0].violationType = type;
                selectedWorker[0].dateOfEvent = date;
                selectedWorker[0].fineAmount = fine;
                selectedWorker[0].plantDiv = plant;
                selectedWorker[0].eventLocation = loc;
                selectedWorker[0].documentNo = inspector;
                workerDao.update(selectedWorker[0]);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Pelanggaran berhasil dicatat", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showViolationDialog(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvVioFormTitle.setText("Pelanggaran — " + worker.name);
        
        // Hide worker search since worker is already known
        dialogBinding.llWorkerSearchContainer.setVisibility(View.GONE);
        dialogBinding.tvSelectedWorker.setText("Kontraktor: " + worker.name);
        dialogBinding.tvSelectedWorker.setVisibility(View.VISIBLE);

        // --- 1. Kolom Tanggal ---
        dialogBinding.etVioDate.addTextChangedListener(new TextWatcher() {
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
                dialogBinding.etVioDate.setText(current);
                dialogBinding.etVioDate.setSelection(current.length());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- 2. Kolom Denda ---
        dialogBinding.etVioFine.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormatting) return;
                String text = s.toString();
                if (text.contains(",")) text = text.substring(0, text.indexOf(","));
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
                    dialogBinding.etVioFine.setSelection(Math.max(0, formatted.length() - 3));
                } catch (Exception e) {}
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
            String inspector = dialogBinding.etInspectorName.getText().toString();

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(getContext(), "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                int count = workerDao.getFormalViolationCount(worker.regNo);
                if (count >= 5) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show());
                    return;
                }

                Violation violation = new Violation(worker.regNo.trim(), type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = inspector;
                violation.plant = plant;
                workerDao.insertViolation(violation);

                // Update worker's primary fields for immediate UI consistency
                worker.status = "Pelanggaran";
                worker.violationType = type;
                worker.dateOfEvent = date;
                worker.fineAmount = fine;
                worker.plantDiv = plant;
                worker.eventLocation = loc;
                worker.documentNo = inspector;
                workerDao.update(worker);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Pelanggaran berhasil dicatat", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });

        dialog.show();
    }

    private void showReprimandDialogFromSheet(Worker worker) {
        DialogViolationFormBinding dialogBinding = DialogViolationFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();
        dialogBinding.tvVioFormTitle.setText("Teguran — " + worker.name);
        
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
                Toast.makeText(getContext(), "Nama Penegur tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            if (notes.isEmpty()) {
                Toast.makeText(getContext(), "Catatan tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
            Executors.newSingleThreadExecutor().execute(() -> {
                Violation violation = new Violation(worker.regNo.trim(), "Teguran (Catatan)", today, location, notes);
                violation.fine = "-";
                violation.docNo = inspectorName;
                violation.plant = worker.plantDiv != null ? worker.plantDiv : "-";
                workerDao.insertViolation(violation);
                
                // Ensure worker status is updated to trigger refresh
                worker.status = "Pelanggaran"; 
                worker.violationType = "Teguran (Catatan)";
                worker.dateOfEvent = today;
                worker.eventLocation = location;
                worker.documentNo = inspectorName;
                workerDao.update(worker);
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Teguran berhasil dicatat", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
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
        sheetBinding.tvSheetRegNoGrid.setText(worker.regNo);
        sheetBinding.tvSheetPosition.setText(worker.position != null ? worker.position : "-");
        sheetBinding.tvSheetContractor.setText(worker.contractor != null ? worker.contractor : "-");
        sheetBinding.tvSheetDocNo.setText(worker.documentNo != null ? worker.documentNo : "-");

        sheetBinding.tvSheetViolationBadge.setOnClickListener(v -> showViolationDialog(worker));
        sheetBinding.tvSheetViolationBadge.setText("TAMBAH PELANGGARAN");
        sheetBinding.tvSheetViolationBadge.setBackgroundResource(R.drawable.bg_status_pill); 
        sheetBinding.tvSheetViolationBadge.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(getContext(), R.color.alert_terracotta));

        sheetBinding.btnAddNoteSheet.setOnClickListener(v -> showReprimandDialogFromSheet(worker));
        sheetBinding.tvSheetAccidentBadge.setOnClickListener(v -> {
            dialog.dismiss();
            showAccidentDialogForWorker(worker);
        });

        // Role Based Visibility for deletion in details
        boolean isAdmin = "admin".equalsIgnoreCase(userRole);

        // Load Violations
        boolean hasDirectViolation = worker.status != null && 
            (worker.status.equalsIgnoreCase("Pelanggaran") || worker.status.contains("PELANGGARAN"));

        workerDao.getViolationsByWorker(worker.regNo).observe(getViewLifecycleOwner(), violations -> {
            sheetBinding.llViolationList.removeAllViews();
            sheetBinding.llReprimandList.removeAllViews();
            
            long totalFine = 0;
            int violationCount = 0;
            int reprimandCount = 0;

            // Process existing records
            for (com.example.scanbar.data.Violation v : violations) {
                if (v.type != null && v.type.toLowerCase().contains("teguran")) {
                    addReprimandItemToUi(sheetBinding.llReprimandList, v);
                    reprimandCount++;
                } else {
                    addViolationItemToUi(sheetBinding.llViolationList, v);
                    violationCount++;
                    
                    if (v.fine != null && !v.fine.isEmpty()) {
                        try {
                            String digitsOnly = v.fine.replaceAll("[^0-9]", "");
                            if (digitsOnly.endsWith("00") && v.fine.contains(",")) {
                                digitsOnly = digitsOnly.substring(0, digitsOnly.length() - 2);
                            }
                            if (!digitsOnly.isEmpty()) totalFine += Long.parseLong(digitsOnly);
                        } catch (Exception e) {}
                    }
                }
            }

            // Update UI Sections
            if (violationCount > 0) {
                sheetBinding.llViolationSection.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetViolationCount.setText("(" + violationCount + ")");
            } else {
                sheetBinding.llViolationSection.setVisibility(View.GONE);
            }

            if (reprimandCount > 0) {
                sheetBinding.llReprimandSection.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetReprimandCount.setText("(" + reprimandCount + ")");
            } else {
                sheetBinding.llReprimandSection.setVisibility(View.GONE);
            }

            if (totalFine > 0) {
                sheetBinding.layoutFine.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetFine.setText(String.format(java.util.Locale.getDefault(), "Rp %,d", totalFine).replace(',', '.'));
            } else {
                sheetBinding.layoutFine.setVisibility(View.GONE);
            }
        });

        // Load Trainings
        workerDao.getTrainingsByWorker(worker.regNo).observe(getViewLifecycleOwner(), trainings -> {
            sheetBinding.llTrainingList.removeAllViews();
            if (trainings != null && !trainings.isEmpty()) {
                sheetBinding.llTrainingSection.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetTrainingCount.setText("(" + trainings.size() + ")");
                for (com.example.scanbar.data.Training t : trainings) {
                    addTrainingItemToUi(sheetBinding.llTrainingList, t);
                }
            } else {
                sheetBinding.llTrainingSection.setVisibility(View.GONE);
                sheetBinding.tvSheetTrainingCount.setText("(0)");
            }
        });

        // Load Accidents
        workerDao.getAccidentsByWorker(worker.regNo).observe(getViewLifecycleOwner(), accidents -> {
            sheetBinding.llAccidentList.removeAllViews();
            if (accidents != null && !accidents.isEmpty()) {
                sheetBinding.llAccidentSection.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetAccidentCount.setText("(" + accidents.size() + ")");
                for (Accident a : accidents) {
                    addAccidentItemToUi(sheetBinding.llAccidentList, a);
                }
            } else {
                sheetBinding.llAccidentSection.setVisibility(View.GONE);
                sheetBinding.tvSheetAccidentCount.setText("(0)");
            }
        });

        dialog.show();
    }

    private void addAccidentItemToUi(ViewGroup container, Accident a) {
        View accView = getLayoutInflater().inflate(R.layout.item_accident_detail, container, false);
        TextView date = accView.findViewById(R.id.tvAccidentDate);
        TextView severity = accView.findViewById(R.id.tvAccidentSeverity);
        TextView location = accView.findViewById(R.id.tvAccidentLocation);

        date.setText(a.date != null ? a.date : "-");
        severity.setText("Keparahan: " + (a.severity != null ? a.severity : "-"));
        location.setText("Lokasi: " + (a.location != null ? a.location : "-"));

        accView.setOnClickListener(v -> showAccidentDetailsDialog(a));
        container.addView(accView);
    }

    private void showAccidentDetailsDialog(Accident a) {
        DialogAccidentDetailsBinding detailsBinding = DialogAccidentDetailsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(detailsBinding.getRoot()).create();

        detailsBinding.tvAccidentDetailSeverity.setText(a.severity != null ? a.severity : "-");
        detailsBinding.tvAccidentDetailDate.setText(a.date != null ? a.date : "-");
        detailsBinding.tvAccidentDetailTime.setText(a.time != null ? a.time : "-");
        detailsBinding.tvAccidentDetailLocation.setText(a.location != null ? a.location : "-");
        detailsBinding.tvAccidentDetailChronology.setText(a.chronology != null ? a.chronology : "-");

        if ("admin".equalsIgnoreCase(userRole)) {
            detailsBinding.btnAccidentDelete.setVisibility(View.VISIBLE);
        }

        detailsBinding.btnAccidentDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                .setTitle("Hapus Data Kecelakaan")
                .setMessage("Apakah Anda yakin ingin menghapus data kecelakaan ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        workerDao.deleteAccident(a);
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Data Kecelakaan berhasil dihapus", Toast.LENGTH_SHORT).show();
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

    private void addTrainingItemToUi(ViewGroup container, com.example.scanbar.data.Training t) {
        View trainView = getLayoutInflater().inflate(R.layout.item_training_detail, container, false);
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

        container.addView(trainView);
    }

    private void showTrainingDetailsDialog(Training t) {
        DialogTrainingDetailsBinding detailsBinding = DialogTrainingDetailsBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(detailsBinding.getRoot()).create();

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
            new AlertDialog.Builder(getContext())
                .setTitle("Hapus Data Training")
                .setMessage("Apakah Anda yakin ingin menghapus data training ini?")
                .setPositiveButton("Hapus", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        workerDao.deleteTraining(t);
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Data Training berhasil dihapus", Toast.LENGTH_SHORT).show();
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

    private void addViolationItemToUi(ViewGroup container, Violation v) {
        View vioView = getLayoutInflater().inflate(R.layout.item_violation_detail, container, false);
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
        container.addView(vioView);
    }

    private void addReprimandItemToUi(ViewGroup container, Violation v) {
        View repView = getLayoutInflater().inflate(R.layout.item_reprimand_detail, container, false);
        TextView date = repView.findViewById(R.id.tvRepDetailDate);
        TextView notes = repView.findViewById(R.id.tvRepDetailNotes);
        TextView location = repView.findViewById(R.id.tvRepDetailLocation);
        TextView inspector = repView.findViewById(R.id.tvRepDetailInspector);

        date.setText(v.date != null ? v.date : "-");
        notes.setText(v.notes != null ? v.notes : "-");
        location.setText("Lokasi: " + (v.location != null ? v.location : "-"));
        inspector.setText("Oleh: " + (v.docNo != null ? v.docNo : "Petugas"));

        repView.setOnClickListener(view -> showViolationDetailDialog(v));
        container.addView(repView);
    }

    private void showViolationDetailDialog(Violation v) {
        DialogViolationDetailsBinding detailDialogBinding = DialogViolationDetailsBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
            new AlertDialog.Builder(getContext())
                .setTitle("Hapus")
                .setMessage("Hapus catatan ini?")
                .setPositiveButton("Ya", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        workerDao.deleteViolation(v);
                        getActivity().runOnUiThread(() -> dialog.dismiss());
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
                workerDao.updateViolation(v);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Berhasil diperbarui", Toast.LENGTH_SHORT).show();
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
        
        // Remove existing currency symbols and separators to reformat
        String digitsOnly = clean.replaceAll("[^0-9]", "");
        
        // Handle suffix ",00"
        if (digitsOnly.endsWith("00") && clean.contains(",")) {
            digitsOnly = digitsOnly.substring(0, digitsOnly.length() - 2);
        }

        if (digitsOnly.isEmpty()) return "Rp -";
        
        try {
            long amount = Long.parseLong(digitsOnly);
            return String.format(java.util.Locale.getDefault(), "Rp %,d", amount).replace(',', '.');
        } catch (Exception e) {
            if (!clean.startsWith("Rp")) {
                clean = "Rp " + clean;
            }
            return clean.replace(',', '.');
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void readExcelFile(Uri uri) {
        if (getContext() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                if (inputStream == null) return;
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