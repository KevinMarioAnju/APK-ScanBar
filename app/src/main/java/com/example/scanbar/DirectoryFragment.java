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
import com.example.scanbar.databinding.DialogAdvancedExportBinding;
import com.example.scanbar.databinding.DialogSelectExportDataBinding;
import com.example.scanbar.databinding.ItemSelectExportBinding;
import android.graphics.pdf.PdfDocument;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import org.apache.poi.ss.usermodel.DataFormatter;
import android.widget.CheckBox;

import org.apache.poi.ss.usermodel.DataFormatter;

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
            binding.btnAddWorkerModern.setVisibility(View.VISIBLE);
            binding.btnExport.setVisibility(View.VISIBLE);
            binding.btnImport.setVisibility(View.VISIBLE);
        } else {
            binding.btnAddWorkerModern.setVisibility(View.VISIBLE); // Let inspector add reports
            binding.btnExport.setVisibility(View.GONE);
            binding.btnImport.setVisibility(View.GONE);
        }
    }

    private <T> void observeOnce(LiveData<T> liveData, java.util.function.Consumer<T> callback) {
        liveData.observe(getViewLifecycleOwner(), new androidx.lifecycle.Observer<T>() {
            @Override public void onChanged(T t) {
                if (t != null) {
                    callback.accept(t);
                    liveData.removeObserver(this);
                }
            }
        });
    }

    private void showAdvancedExportDialog() {
        DialogAdvancedExportBinding exportBinding = DialogAdvancedExportBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(exportBinding.getRoot()).create();

        // Setup Dropdown Kategori Filter
        String[] categories = {"Semua", "Bersih", "Pelanggaran", "Teguran", "Training", "Kecelakaan"};
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, categories);
        exportBinding.actvExportCategory.setAdapter(catAdapter);

        // State untuk data spesifik yang terpilih
        final List<Worker> selectedSpecificWorkers = new ArrayList<>();

        exportBinding.rgExportScope.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isFiltered = (checkedId == R.id.rbExportFiltered);
            exportBinding.tvFilterDropdownLabel.setVisibility(isFiltered ? View.VISIBLE : View.GONE);
            exportBinding.tilExportCategory.setVisibility(isFiltered ? View.VISIBLE : View.GONE);
        });

        exportBinding.etExportLimit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String val = s.toString().trim();
                exportBinding.btnSelectSpecificData.setVisibility(val.isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        exportBinding.btnSelectSpecificData.setOnClickListener(v -> {
            int scopeId = exportBinding.rgExportScope.getCheckedRadioButtonId();
            String limitStr = exportBinding.etExportLimit.getText().toString();
            int limit = limitStr.isEmpty() ? 0 : Integer.parseInt(limitStr);
            
            if (scopeId == R.id.rbExportAll) {
                observeOnce(workerDao.getAllWorkers(), workers -> showSelectDataDialog(workers, limit, selectedSpecificWorkers));
            } else if (scopeId == R.id.rbExportFiltered) {
                String cat = exportBinding.actvExportCategory.getText().toString();
                observeOnce(getFilteredWorkers(cat), workers -> showSelectDataDialog(workers, limit, selectedSpecificWorkers));
            } else {
                observeOnce(workerDao.getWorkersBySource("Input di HP"), workers -> showSelectDataDialog(workers, limit, selectedSpecificWorkers));
            }
        });

        exportBinding.btnExportCancel.setOnClickListener(v -> dialog.dismiss());
        exportBinding.btnExportExecute.setOnClickListener(v -> {
            int scopeId = exportBinding.rgExportScope.getCheckedRadioButtonId();
            int formatId = exportBinding.rgExportFormat.getCheckedRadioButtonId();
            String limitStr = exportBinding.etExportLimit.getText().toString();
            int limit = limitStr.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(limitStr);
            String format = (formatId == R.id.rbExportXLS) ? "XLS" : (formatId == R.id.rbExportPDF ? "PDF" : "CSV");

            Executors.newSingleThreadExecutor().execute(() -> {
                List<Worker> exportWorkers;
                String catName;

                if (!selectedSpecificWorkers.isEmpty()) {
                    exportWorkers = new ArrayList<>(selectedSpecificWorkers);
                    catName = "CustomSelected";
                } else if (scopeId == R.id.rbExportAll) {
                    exportWorkers = workerDao.getAllWorkersSync();
                    catName = "SemuaData";
                } else if (scopeId == R.id.rbExportFiltered) {
                    String cat = exportBinding.actvExportCategory.getText().toString();
                    catName = cat;
                    switch (cat) {
                        case "Bersih": exportWorkers = workerDao.getCleanWorkersSync(); break;
                        case "Pelanggaran": exportWorkers = workerDao.getWorkersWithViolationsOnlySync(); break;
                        case "Kecelakaan": exportWorkers = workerDao.getWorkersWithAccidentsOnlySync(); break;
                        case "Training": exportWorkers = workerDao.getWorkersWithTrainingsOnlySync(); break;
                        default: exportWorkers = workerDao.getAllWorkersSync();
                    }
                } else {
                    exportWorkers = workerDao.getWorkersBySourceSync("Input di HP");
                    catName = "InputHP";
                }

                if (exportWorkers == null || exportWorkers.isEmpty()) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Tidak ada data untuk diexport", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Fetch full history synchronously
                List<Violation> allVios = workerDao.getAllViolationsSync();
                List<Training> allTrainings = workerDao.getAllTrainingsSync();
                List<Accident> allAccidents = workerDao.getAllAccidentsSync();

                // Filter history based on selected workers (Robust matching)
                java.util.Set<String> targetIds = new java.util.HashSet<>();
                for (Worker w : exportWorkers) if (w.regNo != null) targetIds.add(w.regNo.trim().toLowerCase());

                List<Violation> filteredVios = new ArrayList<>();
                for (Violation vi : allVios) {
                    if (vi.workerRegNo != null && targetIds.contains(vi.workerRegNo.trim().toLowerCase())) filteredVios.add(vi);
                }

                List<Training> filteredTrainings = new ArrayList<>();
                for (Training tr : allTrainings) {
                    if (tr.workerRegNo != null && targetIds.contains(tr.workerRegNo.trim().toLowerCase())) filteredTrainings.add(tr);
                }

                List<Accident> filteredAccidents = new ArrayList<>();
                for (Accident ac : allAccidents) {
                    if (ac.workerRegNo != null && targetIds.contains(ac.workerRegNo.trim().toLowerCase())) filteredAccidents.add(ac);
                }

                getActivity().runOnUiThread(() -> {
                    performExportData(exportWorkers, filteredVios, filteredTrainings, filteredAccidents, format, catName, limit);
                });
            });
            dialog.dismiss();
        });
        dialog.show();
    }

    private void fetchFullHistoryAndExport(List<Worker> workers, String format, String catName, int limit) {
        observeOnce(workerDao.getAllViolations(), violations -> {
            observeOnce(workerDao.getAllTrainings(), trainings -> {
                observeOnce(workerDao.getAllAccidents(), accidents -> {
                    // Filter history to only include records for the selected workers
                    // Use a Set of lower-case, trimmed Reg Nos for robust matching
                    java.util.Set<String> targetRegNos = new java.util.HashSet<>();
                    for (Worker w : workers) {
                        if (w.regNo != null) targetRegNos.add(w.regNo.trim().toLowerCase());
                    }

                    List<Violation> filteredVios = new ArrayList<>();
                    if (violations != null) {
                        for (Violation v : violations) {
                            if (v.workerRegNo != null && targetRegNos.contains(v.workerRegNo.trim().toLowerCase())) {
                                filteredVios.add(v);
                            }
                        }
                    }

                    List<Training> filteredTrainings = new ArrayList<>();
                    if (trainings != null) {
                        for (Training t : trainings) {
                            if (t.workerRegNo != null && targetRegNos.contains(t.workerRegNo.trim().toLowerCase())) {
                                filteredTrainings.add(t);
                            }
                        }
                    }

                    List<Accident> filteredAccidents = new ArrayList<>();
                    if (accidents != null) {
                        for (Accident a : accidents) {
                            if (a.workerRegNo != null && targetRegNos.contains(a.workerRegNo.trim().toLowerCase())) {
                                filteredAccidents.add(a);
                            }
                        }
                    }

                    performExportData(workers, filteredVios, filteredTrainings, filteredAccidents, format, catName, limit);
                });
            });
        });
    }

    private void performExportData(List<Worker> workers, List<Violation> violations, List<Training> trainings, List<Accident> accidents, String format, String catName, int limit) {
        currentExportSession = new ExportSession(workers, violations, trainings, accidents, format, catName, limit);
        
        String mimeType;
        String ext;
        if (format.equals("XLS")) {
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            ext = ".xlsx";
        } else if (format.equals("PDF")) {
            mimeType = "application/pdf";
            ext = ".pdf";
        } else {
            mimeType = "text/csv";
            ext = ".csv";
        }

        String dateStr = new java.text.SimpleDateFormat("ddMMyyyy", java.util.Locale.getDefault()).format(new java.util.Date());
        String fileName = "laporan_" + catName.replace(" ", "") + "_" + dateStr + ext;

        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(android.content.Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, 202);
    }

    private static class ExportSession {
        List<Worker> workers;
        List<Violation> violations;
        List<Training> trainings;
        List<Accident> accidents;
        String format;
        String categoryName;
        int limit;

        ExportSession(List<Worker> w, List<Violation> v, List<Training> t, List<Accident> a, String f, String cat, int lim) {
            this.workers = w != null && w.size() > lim ? w.subList(0, lim) : w;
            this.violations = v; // Do not limit history, export all for selected workers
            this.trainings = t;
            this.accidents = a;
            this.format = f;
            this.categoryName = cat;
            this.limit = lim;
        }
    }

    private ExportSession currentExportSession;

    private void generateXlsFileComplex(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (java.io.OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                Workbook workbook = new XSSFWorkbook();
                
                // Sheet 1: Workers
                if (currentExportSession.workers != null && !currentExportSession.workers.isEmpty()) {
                    Sheet sheet = workbook.createSheet("Pekerja");
                    Row header = sheet.createRow(0);
                    // 19 Fields for Workers (Added Catatan as snapshot)
                    String[] heads = {"Reg. No", "Name", "Contractor", "Position", "Status", "Contractor Code", "Gender", "Birth Date", "WSP Exp Date", "Age", "Event Date", "Vio Type", "Fine", "Plant", "Location", "Doc No", "Inspector", "Sumber", "Catatan Snapshot"};
                    for (int i = 0; i < heads.length; i++) header.createCell(i).setCellValue(heads[i]);
                    int rowIdx = 1;
                    for (Worker w : currentExportSession.workers) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(w.regNo);
                        row.createCell(1).setCellValue(w.name);
                        row.createCell(2).setCellValue(w.contractor);
                        row.createCell(3).setCellValue(w.position);
                        row.createCell(4).setCellValue(w.status);
                        row.createCell(5).setCellValue(w.contractorCode);
                        row.createCell(6).setCellValue(w.gender);
                        row.createCell(7).setCellValue(w.birthDate);
                        row.createCell(8).setCellValue(w.wspExpDate);
                        row.createCell(9).setCellValue(w.age);
                        row.createCell(10).setCellValue(w.dateOfEvent);
                        row.createCell(11).setCellValue(w.violationType);
                        row.createCell(12).setCellValue(w.fineAmount);
                        row.createCell(13).setCellValue(w.plantDiv);
                        row.createCell(14).setCellValue(w.eventLocation);
                        row.createCell(15).setCellValue(w.documentNo);
                        row.createCell(16).setCellValue(w.inspectorName);
                        row.createCell(17).setCellValue(w.dataSource);
                        row.createCell(18).setCellValue("-"); // Placeholder or actual last note if we tracked it in worker
                    }
                }
                
                // Sheet 2: Violations
                if (currentExportSession.violations != null && !currentExportSession.violations.isEmpty()) {
                    Sheet sheet = workbook.createSheet("Pelanggaran");
                    Row header = sheet.createRow(0);
                    // 19 Fields for Violations (Added Catatan)
                    String[] heads = {"Date", "Year", "Time", "Plant/Division", "TKP", "Contractor", "User Plant/Division", "Name", "Reg. NO", "Job Title", "Amount", "Type", "Charge", "Damages", "Total All", "Doc No.", "Officer", "Sumber", "Catatan"};
                    for (int i = 0; i < heads.length; i++) header.createCell(i).setCellValue(heads[i]);
                    int rowIdx = 1;
                    for (Violation v : currentExportSession.violations) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(v.date);
                        row.createCell(1).setCellValue(v.year);
                        row.createCell(2).setCellValue(v.time);
                        row.createCell(3).setCellValue(v.plant);
                        row.createCell(4).setCellValue(v.location);
                        row.createCell(5).setCellValue(v.contractor);
                        row.createCell(6).setCellValue(v.userPlantDivision);
                        row.createCell(7).setCellValue(v.name);
                        row.createCell(8).setCellValue(v.workerRegNo);
                        row.createCell(9).setCellValue(v.jobTitle);
                        row.createCell(10).setCellValue(v.fine);
                        row.createCell(11).setCellValue(v.type);
                        row.createCell(12).setCellValue(v.charge);
                        row.createCell(13).setCellValue(v.damages);
                        row.createCell(14).setCellValue(v.totalAll);
                        row.createCell(15).setCellValue(v.docNo);
                        row.createCell(16).setCellValue(v.officer);
                        row.createCell(17).setCellValue(v.dataSource);
                        row.createCell(18).setCellValue(v.notes);
                    }
                }
                
                // Sheet 3: Trainings
                if (currentExportSession.trainings != null && !currentExportSession.trainings.isEmpty()) {
                    Sheet sheet = workbook.createSheet("Training");
                    Row header = sheet.createRow(0);
                    String[] heads = {"Id", "Name", "Training Code", "Training Title", "Date", "Time", "Training Hours", "Training Location", "Pass/Fail"};
                    for (int i = 0; i < heads.length; i++) header.createCell(i).setCellValue(heads[i]);
                    int rowIdx = 1;
                    for (Training t : currentExportSession.trainings) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(t.workerRegNo);
                        row.createCell(1).setCellValue(t.workerName);
                        row.createCell(2).setCellValue(t.trainingCode);
                        row.createCell(3).setCellValue(t.trainingTitle);
                        row.createCell(4).setCellValue(t.date);
                        row.createCell(5).setCellValue(t.time);
                        row.createCell(6).setCellValue(t.trainingHours);
                        row.createCell(7).setCellValue(t.trainingLocation);
                        row.createCell(8).setCellValue(t.passFail);
                    }
                }
                
                // Sheet 4: Accidents
                if (currentExportSession.accidents != null && !currentExportSession.accidents.isEmpty()) {
                    Sheet sheet = workbook.createSheet("Kecelakaan");
                    Row header = sheet.createRow(0);
                    String[] heads = {"Id", "Tanggal Kecelakaan", "Jam Kecelakaan", "Kronologis Kecelakaan", "Keparahan", "Lokasi Kecelakaan"};
                    for (int i = 0; i < heads.length; i++) header.createCell(i).setCellValue(heads[i]);
                    int rowIdx = 1;
                    for (Accident a : currentExportSession.accidents) {
                        Row row = sheet.createRow(rowIdx++);
                        row.createCell(0).setCellValue(a.workerRegNo);
                        row.createCell(1).setCellValue(a.date);
                        row.createCell(2).setCellValue(a.time);
                        row.createCell(3).setCellValue(a.chronology);
                        row.createCell(4).setCellValue(a.severity);
                        row.createCell(5).setCellValue(a.location);
                    }
                }
                
                workbook.write(os);
                workbook.close();
                os.flush();
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Export Excel Berhasil", Toast.LENGTH_SHORT).show();
                    currentExportSession = null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Export Excel Gagal", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void generateCsvFileComplex(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (java.io.OutputStream os = getContext().getContentResolver().openOutputStream(uri);
                 java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(os)) {

                StringBuilder csv = new StringBuilder();
                if (currentExportSession.workers != null) {
                    csv.append("Reg No,Nama,Status,Kontraktor,Jabatan,Tgl Kejadian,Jenis,Denda,Plant,Lokasi,Doc No,Penegur,Sumber\n");
                    for (Worker w : currentExportSession.workers) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                w.regNo, w.name, w.status, w.contractor, w.position,
                                w.dateOfEvent, w.violationType, w.fineAmount, w.plantDiv,
                                w.eventLocation, w.documentNo, w.inspectorName, w.dataSource));
                    }
                } else if (currentExportSession.violations != null) {
                    csv.append("Date,Year,Time,Plant,TKP,Contractor,Name,Reg NO,Amount,Type,Doc No,Officer\n");
                    for (Violation v : currentExportSession.violations) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                                v.date, v.year, v.time, v.plant, v.location, v.contractor,
                                v.name, v.workerRegNo, v.fine, v.type, v.docNo, v.officer));
                    }
                } else if (currentExportSession.trainings != null) {
                    csv.append("Reg No,Name,Title,Date,Time,Hours,Location,Result\n");
                    for (Training t : currentExportSession.trainings) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                                t.workerRegNo, t.workerName, t.trainingTitle, t.date, t.time,
                                t.trainingHours, t.trainingLocation, t.passFail));
                    }
                } else if (currentExportSession.accidents != null) {
                    csv.append("Reg No,Date,Time,Chronology,Severity,Location\n");
                    for (Accident a : currentExportSession.accidents) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                                a.workerRegNo, a.date, a.time, a.chronology, a.severity, a.location));
                    }
                }

                osw.write(csv.toString());
                osw.flush();
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Export CSV Berhasil", Toast.LENGTH_SHORT).show();
                    currentExportSession = null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Export CSV Gagal", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void generatePdfFileComplex(Uri uri) {
        Executors.newSingleThreadExecutor().execute(() -> {
            PdfDocument document = new PdfDocument();
            try {
                // Determine category for title and styling
                String catTitle = currentExportSession.categoryName;
                
                // PDF Settings
                int pageWidth = 842; // A4 Landscape
                int pageHeight = 595;
                int margin = 40;
                
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();
                Paint paint = new Paint();
                Paint titlePaint = new Paint();
                
                titlePaint.setTextSize(20);
                titlePaint.setFakeBoldText(true);
                titlePaint.setColor(Color.BLACK);
                canvas.drawText("Laporan Data " + catTitle, margin, margin + 20, titlePaint);
                
                paint.setTextSize(10);
                paint.setColor(Color.BLACK);
                
                // Table Configuration
                String[] headers;
                List<String[]> dataRows = new ArrayList<>();
                
                if (currentExportSession.workers != null) {
                    headers = new String[]{"Reg No", "Nama", "Contractor", "Position", "Status", "Plant", "Location"};
                    for (Worker w : currentExportSession.workers) {
                        dataRows.add(new String[]{w.regNo, w.name, w.contractor, w.position, w.status, w.plantDiv, w.eventLocation});
                    }
                } else if (currentExportSession.violations != null) {
                    headers = new String[]{"Date", "Reg NO", "Name", "Type", "Location", "Fine", "Officer"};
                    for (Violation v : currentExportSession.violations) {
                        dataRows.add(new String[]{v.date, v.workerRegNo, v.name, v.type, v.location, v.fine, v.officer});
                    }
                } else if (currentExportSession.trainings != null) {
                    headers = new String[]{"Reg No", "Name", "Title", "Date", "Time", "Location", "Result"};
                    for (Training t : currentExportSession.trainings) {
                        dataRows.add(new String[]{t.workerRegNo, t.workerName, t.trainingTitle, t.date, t.time, t.trainingLocation, t.passFail});
                    }
                } else if (currentExportSession.accidents != null) {
                    headers = new String[]{"Reg No", "Date", "Time", "Severity", "Location", "Chronology"};
                    for (Accident a : currentExportSession.accidents) {
                        dataRows.add(new String[]{a.workerRegNo, a.date, a.time, a.severity, a.location, a.chronology});
                    }
                } else {
                    headers = new String[]{"Data Kosong"};
                }
                
                // Draw Table
                float totalWidth = pageWidth - (2 * margin);
                float[] colWidths = new float[headers.length];
                for (int i = 0; i < headers.length; i++) colWidths[i] = totalWidth / headers.length;
                
                int y = margin + 60;
                paint.setFakeBoldText(true);
                float curX = margin;
                for (int i = 0; i < headers.length; i++) {
                    canvas.drawText(headers[i], curX, y, paint);
                    curX += colWidths[i];
                }
                
                canvas.drawLine(margin, y + 5, pageWidth - margin, y + 5, paint);
                y += 25;
                paint.setFakeBoldText(false);
                
                for (String[] row : dataRows) {
                    if (y > pageHeight - margin) {
                        document.finishPage(page);
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin + 20;
                    }
                    
                    curX = margin;
                    for (int i = 0; i < row.length; i++) {
                        String text = (row[i] != null) ? row[i] : "-";
                        float maxWidth = colWidths[i] - 10;
                        if (paint.measureText(text) > maxWidth) {
                            text = text.substring(0, Math.min(text.length(), 15)) + "...";
                        }
                        canvas.drawText(text, curX, y, paint);
                        curX += colWidths[i];
                    }
                    y += 20;
                }
                
                document.finishPage(page);
                
                try (java.io.OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        document.writeTo(os);
                        os.flush();
                    }
                }
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Export PDF Berhasil", Toast.LENGTH_SHORT).show();
                    currentExportSession = null;
                });
            } catch (Exception e) {
                e.printStackTrace();
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Export PDF Gagal", Toast.LENGTH_SHORT).show());
            } finally {
                document.close();
            }
        });
    }

    private LiveData<List<Worker>> getFilteredWorkers(String category) {
        switch (category) {
            case "Bersih": return workerDao.getCleanWorkers();
            case "Pelanggaran": return workerDao.getWorkersWithViolations();
            case "Teguran": return workerDao.getWorkersWithReprimands();
            case "Training": return workerDao.getWorkersWithTrainingsOnly();
            case "Kecelakaan": return workerDao.getWorkersWithAccidentsOnly();
            default: return workerDao.getAllWorkers();
        }
    }

    private void showSelectDataDialog(List<Worker> allPossible, int limit, List<Worker> outSelected) {
        DialogSelectExportDataBinding selectBinding = DialogSelectExportDataBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(selectBinding.getRoot()).create();

        final List<Worker> currentSelection = new ArrayList<>();
        final List<Worker> displayedWorkers = new ArrayList<>(allPossible);

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(ItemSelectExportBinding.inflate(getLayoutInflater(), parent, false).getRoot()) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ItemSelectExportBinding b = ItemSelectExportBinding.bind(holder.itemView);
                Worker w = displayedWorkers.get(position);
                b.tvSelectName.setText(w.name);
                b.tvSelectRegNo.setText("Reg No: " + w.regNo);
                
                b.cbExportSelect.setOnCheckedChangeListener(null);
                b.cbExportSelect.setChecked(currentSelection.contains(w));
                b.cbExportSelect.setOnCheckedChangeListener((v, isChecked) -> {
                    if (isChecked) {
                        if (currentSelection.size() >= limit) {
                            v.setChecked(false);
                            Toast.makeText(getContext(), "Limit " + limit + " data tercapai", Toast.LENGTH_SHORT).show();
                        } else {
                            currentSelection.add(w);
                        }
                    } else {
                        currentSelection.remove(w);
                    }
                    selectBinding.tvSelectCountInfo.setText("Terpilih: " + currentSelection.size() + " / " + limit + " data");
                });
            }
            @Override public int getItemCount() { return displayedWorkers.size(); }
        };

        selectBinding.rvSelectExport.setLayoutManager(new LinearLayoutManager(getContext()));
        selectBinding.rvSelectExport.setAdapter(adapter);

        // Logic Search di dalam dialog
        selectBinding.etSelectSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();
                displayedWorkers.clear();
                if (query.isEmpty()) {
                    displayedWorkers.addAll(allPossible);
                } else {
                    for (Worker w : allPossible) {
                        if (w.name.toLowerCase().contains(query) || w.regNo.toLowerCase().contains(query)) {
                            displayedWorkers.add(w);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        selectBinding.tvSelectCountInfo.setText("Terpilih: 0 / " + limit + " data");
        selectBinding.btnSelectCancel.setOnClickListener(v -> dialog.dismiss());
        selectBinding.btnSelectConfirm.setOnClickListener(v -> {
            outSelected.clear();
            outSelected.addAll(currentSelection);
            dialog.dismiss();
            Toast.makeText(getContext(), outSelected.size() + " data dipilih", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
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

        // --- Date Format Logic ---
        setupDateFormat(dialogBinding.etAccidentDate);

        // --- Time Format Logic ---
        setupTimeFormat(dialogBinding.etAccidentTime);

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
                int count = workerDao.getAccidentCount(worker.regNo);
                if (count >= 3) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Batas maksimal 3 kecelakaan tercapai", Toast.LENGTH_LONG).show());
                    return;
                }
                Accident a = new Accident(worker.regNo, date, time, chronology, severity, location);
                a.dataSource = "Input di HP";
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

        // --- Date Format Logic ---
        setupDateFormat(dialogBinding.etAccidentDate);

        // --- Time Format Logic ---
        setupTimeFormat(dialogBinding.etAccidentTime);

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
                int count = workerDao.getAccidentCount(selectedWorker[0].regNo);
                if (count >= 3) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Batas maksimal 3 kecelakaan tercapai", Toast.LENGTH_LONG).show());
                    return;
                }
                Accident a = new Accident(selectedWorker[0].regNo, date, time, chronology, severity, location);
                a.dataSource = "Input di HP";
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

        // --- Date Format Logic ---
        setupDateFormat(dialogBinding.etTrainingDate);

        // --- Time Format Logic ---
        setupTimeFormat(dialogBinding.etTrainingTime);
        setupTimeFormat(dialogBinding.etTrainingEndTime);

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
                t.dataSource = "Input di HP";
                
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
        } else if (requestCode == 202 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && currentExportSession != null) {
                if (currentExportSession.format.equals("XLS")) {
                    generateXlsFileComplex(uri);
                } else if (currentExportSession.format.equals("PDF")) {
                    generatePdfFileComplex(uri);
                } else {
                    generateCsvFileComplex(uri);
                }
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
        binding.btnAddWorkerModern.setOnClickListener(v -> showAddMenu(v));
        binding.btnExport.setOnClickListener(v -> showAdvancedExportDialog());
        binding.btnImport.setOnClickListener(v -> openFilePicker());

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
        showAdvancedExportDialog();
    }


    private void showWorkerDialog(@Nullable Worker worker) {
        DialogWorkerFormBinding dialogBinding = DialogWorkerFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        
        AlertDialog dialog = builder.create();

        if (worker != null) {
            dialogBinding.tvFormTitle.setText("Edit Kontraktor — " + worker.name);
            dialogBinding.etRegNo.setText(worker.regNo);
            dialogBinding.etName.setText(worker.name);
            dialogBinding.etContractor.setText(worker.contractor);
            dialogBinding.etPosition.setText(worker.position);
            dialogBinding.etWorkerPlant.setText(worker.plantDiv);
            
            dialogBinding.btnSave.setText("Simpan Perubahan");
        }

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialogBinding.btnSave.setOnClickListener(v -> {
            String regNo = dialogBinding.etRegNo.getText().toString();
            String name = dialogBinding.etName.getText().toString();
            String contractor = dialogBinding.etContractor.getText().toString();
            String position = dialogBinding.etPosition.getText().toString();
            String workerPlant = dialogBinding.etWorkerPlant.getText().toString();
            
            if (regNo.isEmpty() || name.isEmpty()) return;

            Executors.newSingleThreadExecutor().execute(() -> {
                Worker targetWorker = worker;
                if (targetWorker == null) {
                    targetWorker = new Worker(regNo, name, contractor, position, "Bersih");
                    targetWorker.dataSource = "Input di HP";
                    targetWorker.plantDiv = workerPlant;
                } else {
                    targetWorker.regNo = regNo;
                    targetWorker.name = name;
                    targetWorker.contractor = contractor;
                    targetWorker.position = position;
                    targetWorker.plantDiv = workerPlant;
                    targetWorker.dataSource = "Input di HP";
                }
                
                if (worker == null) {
                    workerDao.insert(targetWorker);
                } else {
                    workerDao.update(targetWorker);
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
            vioBinding.etVioDocNo.setText(violation.docNo);
            vioBinding.etVioInspector.setText(violation.officer);
            vioBinding.etVioLocation.setText(violation.location);

            // Update terminology based on existing type
            boolean isReprimand = violation.type != null && violation.type.toLowerCase().contains("teguran");
            if (vioBinding.tilItemVioInspector != null) {
                vioBinding.tilItemVioInspector.setHint(isReprimand ? "Nama Penegur" : "Nama Petugas");
            }
        }

        // Dynamic update as user types
        vioBinding.etVioType.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isRep = s.toString().toLowerCase().contains("teguran");
                if (vioBinding.tilItemVioInspector != null) {
                    vioBinding.tilItemVioInspector.setHint(isRep ? "Nama Penegur" : "Nama Petugas");
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
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
        if (dialogBinding.tilInspectorName != null) {
            dialogBinding.tilInspectorName.setHint("Nama Petugas");
        }
        
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
        setupDateFormat(dialogBinding.etVioDate);

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
            String docNo = dialogBinding.etVioDocNo.getText().toString();
            String inspector = dialogBinding.etInspectorName.getText().toString();

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(getContext(), "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                boolean isTeguran = type.toLowerCase().contains("teguran");
                if (!isTeguran) {
                    int count = workerDao.getFormalViolationCount(selectedWorker[0].regNo);
                    if (count >= 5) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show());
                        return;
                    }
                }

                Violation violation = new Violation(selectedWorker[0].regNo.trim(), type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = docNo;
                violation.officer = inspector; 
                violation.plant = plant;
                violation.dataSource = "Input di HP";
                workerDao.insertViolation(violation);

                // Update worker's primary fields for immediate UI consistency
                if (selectedWorker[0].status == null || !selectedWorker[0].status.equalsIgnoreCase("Pelanggaran")) {
                    selectedWorker[0].status = isTeguran ? "Teguran" : "Pelanggaran";
                }
                selectedWorker[0].violationType = type;
                selectedWorker[0].dateOfEvent = date;
                selectedWorker[0].fineAmount = fine;
                if (plant != null && !plant.isEmpty() && !plant.equals("-")) {
                    selectedWorker[0].plantDiv = plant;
                }
                selectedWorker[0].eventLocation = loc;
                selectedWorker[0].documentNo = docNo;
                selectedWorker[0].inspectorName = inspector;
                selectedWorker[0].dataSource = "Input di HP";
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
        if (dialogBinding.tilInspectorName != null) {
            dialogBinding.tilInspectorName.setHint("Nama Petugas");
        }
        
        // Hide worker search since worker is already known
        dialogBinding.llWorkerSearchContainer.setVisibility(View.GONE);
        dialogBinding.tvSelectedWorker.setText("Kontraktor: " + worker.name);
        dialogBinding.tvSelectedWorker.setVisibility(View.VISIBLE);

        // --- 1. Kolom Tanggal ---
        setupDateFormat(dialogBinding.etVioDate);

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
            String docNo = dialogBinding.etVioDocNo.getText().toString();
            String inspector = dialogBinding.etInspectorName.getText().toString();

            if (type.isEmpty() || date.isEmpty() || loc.isEmpty()) {
                Toast.makeText(getContext(), "Jenis, Tanggal, dan Lokasi wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                boolean isTeguran = type.toLowerCase().contains("teguran");
                if (!isTeguran) {
                    int count = workerDao.getFormalViolationCount(worker.regNo);
                    if (count >= 5) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Batas maksimal 5 pelanggaran tercapai", Toast.LENGTH_LONG).show());
                        return;
                    }
                }

                Violation violation = new Violation(worker.regNo.trim(), type, date, loc, notes);
                violation.fine = fine;
                violation.docNo = docNo;
                violation.officer = inspector; 
                violation.plant = plant;
                violation.dataSource = "Input di HP";
                workerDao.insertViolation(violation);

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
            String docNo = (dialogBinding.etVioDocNo != null && dialogBinding.etVioDocNo.getText() != null) ?
                          dialogBinding.etVioDocNo.getText().toString() : "-";
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
                violation.docNo = docNo.isEmpty() ? "-" : docNo;
                violation.officer = inspectorName;
                violation.plant = worker.plantDiv != null ? worker.plantDiv : "-";
                violation.dataSource = "Input di HP";
                workerDao.insertViolation(violation);
                
                // Ensure worker status is updated to trigger refresh
                if (worker.status == null || !worker.status.equalsIgnoreCase("Pelanggaran")) {
                    worker.status = "Teguran";
                }
                worker.violationType = "Teguran (Catatan)";
                worker.dateOfEvent = today;
                worker.eventLocation = location;
                worker.documentNo = docNo.isEmpty() ? "-" : docNo;
                worker.inspectorName = inspectorName;
                worker.dataSource = "Input di HP";
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
        sheetBinding.tvSheetPositionGrid.setText(worker.position != null ? worker.position : "-");
        sheetBinding.tvSheetContractor.setText(worker.contractor != null ? worker.contractor : "-");
        sheetBinding.tvSheetPlantDiv.setText(worker.plantDiv != null ? worker.plantDiv : "-");
        sheetBinding.tvSheetDataSource.setText(worker.dataSource != null ? worker.dataSource : "-");

        sheetBinding.tvSheetViolationBadge.setOnClickListener(v -> showViolationDialog(worker));
        sheetBinding.tvSheetAccidentBadge.setOnClickListener(v -> {
            dialog.dismiss();
            showAccidentDialogForWorker(worker);
        });

        sheetBinding.btnAddNoteSheet.setOnClickListener(v -> {
            dialog.dismiss();
            showReprimandDialogFromSheet(worker);
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
                sheetBinding.tvSheetViolationCount.setText(String.valueOf(violationCount));
            } else {
                sheetBinding.llViolationSection.setVisibility(View.GONE);
            }

            if (reprimandCount > 0) {
                sheetBinding.llReprimandSection.setVisibility(View.VISIBLE);
                sheetBinding.tvSheetReprimandCount.setText(String.valueOf(reprimandCount));
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
                sheetBinding.tvSheetAccidentCount.setText(String.valueOf(accidents.size()));
                for (Accident a : accidents) {
                    addAccidentItemToUi(sheetBinding.llAccidentList, a);
                }
            } else {
                sheetBinding.llAccidentSection.setVisibility(View.GONE);
                sheetBinding.tvSheetAccidentCount.setText("0");
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
        severity.setText(a.severity != null ? a.severity : "-");
        location.setText(a.location != null ? a.location : "-");

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
            detailsBinding.btnEditAccident.setVisibility(View.VISIBLE);
        }

        detailsBinding.btnEditAccident.setOnClickListener(v -> {
            dialog.dismiss();
            showEditAccidentForm(a);
        });

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

    private void showEditTrainingForm(Training t) {
        com.example.scanbar.databinding.DialogTrainingFormBinding dialogBinding = 
                com.example.scanbar.databinding.DialogTrainingFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        // Setup UI for edit mode
        dialogBinding.tvSelectedWorker.setVisibility(View.VISIBLE);
        dialogBinding.tvSelectedWorker.setText("Pekerja: " + t.workerRegNo);

        // Hide search components
        dialogBinding.etWorkerSearch.setVisibility(View.GONE);
        dialogBinding.rvWorkerSearch.setVisibility(View.GONE);
        // Find and hide the "Cari & Pilih Kontraktor" label (it's the first TextView before search box)
        ViewGroup parent = (ViewGroup) dialogBinding.etWorkerSearch.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child instanceof TextView && ((TextView) child).getText().toString().contains("Cari")) {
                    child.setVisibility(View.GONE);
                    break;
                }
            }
        }

        // Setup Pass/Fail Spinner
        String[] options = {"PASS", "FAIL"};
        ArrayAdapter<String> pfAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, options);
        pfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.spinnerPassFail.setAdapter(pfAdapter);

        // Pre-fill values
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

            if (t.trainingTitle.isEmpty()) {
                Toast.makeText(getContext(), "Judul training harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                workerDao.updateTraining(t);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Data Training berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showEditAccidentForm(Accident a) {
        DialogAccidentFormBinding dialogBinding = DialogAccidentFormBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogBinding.getRoot());
        AlertDialog dialog = builder.create();

        dialogBinding.tvAccidentFormTitle.setText("Edit Kecelakaan");
        dialogBinding.tilWorkerSearch.setVisibility(View.GONE);
        dialogBinding.tvAccidentSelectedWorker.setVisibility(View.VISIBLE);
        dialogBinding.tvAccidentSelectedWorker.setText("Pekerja: " + a.workerRegNo);

        String[] options = {"LTI", "MTI", "First Aid", "Near Hit", "Property Damage"};
        ArrayAdapter<String> sevAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, options);
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

            if (a.date.isEmpty() || a.chronology.isEmpty()) {
                Toast.makeText(getContext(), "Tanggal dan kronologis harus diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                workerDao.updateAccident(a);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Data Kecelakaan berhasil diperbarui", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
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

    private void addViolationItemToUi(ViewGroup container, Violation v) {
        View vioView = getLayoutInflater().inflate(R.layout.item_violation_detail, container, false);
        TextView type = vioView.findViewById(R.id.tvVioDetailType);
        TextView date = vioView.findViewById(R.id.tvVioDetailDate);
        TextView info = vioView.findViewById(R.id.tvVioDetailInfo);
        TextView loc = vioView.findViewById(R.id.tvVioDetailLoc);
        TextView plant = vioView.findViewById(R.id.tvVioDetailPlant);
        TextView docNo = vioView.findViewById(R.id.tvVioDetailDocNo);
        TextView inspector = vioView.findViewById(R.id.tvVioDetailInspector);
        TextView notes = vioView.findViewById(R.id.tvVioDetailNotes);

        type.setText(v.type);
        if (date != null) date.setText(v.date != null ? v.date : "-");
        info.setText(cleanFineAmount(v.fine));
        loc.setText(v.location != null ? v.location : "-");
        plant.setText(v.plant != null ? v.plant : "-");
        docNo.setText(v.docNo != null ? v.docNo : "-");
        inspector.setText(v.officer != null ? v.officer : "-");

        View noteContainer = vioView.findViewById(R.id.llVioNoteContainer);
        if (v.notes != null && !v.notes.trim().isEmpty() && !v.notes.equals("-")) {
            if (noteContainer != null) noteContainer.setVisibility(View.VISIBLE);
            notes.setText(v.notes);
        } else {
            if (noteContainer != null) noteContainer.setVisibility(View.GONE);
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
        location.setText(v.location != null ? v.location : "-");
        
        View noteContainer = repView.findViewById(R.id.llRepNoteContainer);
        if (v.notes != null && !v.notes.trim().isEmpty() && !v.notes.equals("-")) {
            if (noteContainer != null) noteContainer.setVisibility(View.VISIBLE);
            notes.setText(v.notes);
        } else {
            if (noteContainer != null) noteContainer.setVisibility(View.GONE);
        }
        inspector.setText(v.officer != null ? v.officer : "-");

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
                DataFormatter formatter = new DataFormatter();
                
                // --- NEW INTEGRATED IMPORT LOGIC ---
                // Detect if multiple sheets exist for a full backup restore
                Sheet workerSheet = workbook.getSheet("Pekerja");
                Sheet violationSheet = workbook.getSheet("Pelanggaran");
                Sheet trainingSheet = workbook.getSheet("Training");
                Sheet accidentSheet = workbook.getSheet("Kecelakaan");

                if (workerSheet != null) {
                    // This is a full backup file
                    importFullBackup(workbook, formatter);
                } else {
                    // This is a single-category export file or legacy file
                    Row header = sheet.getRow(0);
                    if (header == null) return;
                    
                    String header1 = formatter.formatCellValue(header.getCell(1)).toLowerCase();
                    String header2 = formatter.formatCellValue(header.getCell(2)).toLowerCase();
                    
                    if (header1.contains("worker name")) {
                        importWorkers(sheet, formatter);
                    } else if (header1.contains("year")) {
                        importViolations(sheet, formatter);
                    } else if (header2.contains("training code")) {
                        importTrainings(sheet, formatter);
                    } else if (header1.contains("tanggal kecelakaan")) {
                        importAccidents(sheet, formatter);
                    } else {
                        importLegacyWorkers(sheet, formatter);
                    }
                }
                workbook.close();
            } catch (Exception e) {
                android.util.Log.e("DirectoryFragment", "Error reading Excel file", e);
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "Gagal membaca file Excel: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void importFullBackup(Workbook workbook, DataFormatter df) {
        List<Worker> workers = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        List<Training> trainings = new ArrayList<>();
        List<Accident> accidents = new ArrayList<>();

        // Helper to find sheet case-insensitively
        autoFindAndReadSheets(workbook, df, workers, violations, trainings, accidents);

        getActivity().runOnUiThread(() -> showFullBackupPreview(workers, violations, trainings, accidents));
    }

    private void autoFindAndReadSheets(Workbook wb, DataFormatter df, List<Worker> workers, List<Violation> violations, List<Training> trainings, List<Accident> accidents) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet s = wb.getSheetAt(i);
            String name = s.getSheetName().toLowerCase();
            if (name.contains("pekerja")) {
                for (Row row : s) {
                    if (row.getRowNum() == 0) continue;
                    String regNo = df.formatCellValue(row.getCell(0)).trim();
                    if (regNo.isEmpty()) continue;
                    Worker w = new Worker(regNo, df.formatCellValue(row.getCell(1)), df.formatCellValue(row.getCell(2)),
                                         df.formatCellValue(row.getCell(3)), df.formatCellValue(row.getCell(4)));
                    w.contractorCode = df.formatCellValue(row.getCell(5));
                    w.gender = df.formatCellValue(row.getCell(6));
                    w.birthDate = df.formatCellValue(row.getCell(7));
                    w.wspExpDate = df.formatCellValue(row.getCell(8));
                    w.age = df.formatCellValue(row.getCell(9));
                    w.dateOfEvent = df.formatCellValue(row.getCell(10));
                    w.violationType = df.formatCellValue(row.getCell(11));
                    w.fineAmount = df.formatCellValue(row.getCell(12));
                    w.plantDiv = df.formatCellValue(row.getCell(13));
                    w.eventLocation = df.formatCellValue(row.getCell(14));
                    w.documentNo = df.formatCellValue(row.getCell(15));
                    w.inspectorName = df.formatCellValue(row.getCell(16));
                    w.dataSource = df.formatCellValue(row.getCell(17));
                    workers.add(w);
                }
            } else if (name.contains("pelanggaran")) {
                for (Row row : s) {
                    if (row.getRowNum() == 0) continue;
                    String regNo = df.formatCellValue(row.getCell(8)).trim();
                    if (regNo.isEmpty()) continue;
                    Violation v = new Violation(regNo, df.formatCellValue(row.getCell(11)), 
                                               df.formatCellValue(row.getCell(0)), df.formatCellValue(row.getCell(4)), 
                                               df.formatCellValue(row.getCell(18))); // index 18 = Catatan
                    v.year = df.formatCellValue(row.getCell(1));
                    v.time = df.formatCellValue(row.getCell(2));
                    v.plant = df.formatCellValue(row.getCell(3));
                    v.contractor = df.formatCellValue(row.getCell(5));
                    v.userPlantDivision = df.formatCellValue(row.getCell(6));
                    v.name = df.formatCellValue(row.getCell(7));
                    v.jobTitle = df.formatCellValue(row.getCell(9));
                    v.fine = df.formatCellValue(row.getCell(10));
                    v.charge = df.formatCellValue(row.getCell(12));
                    v.damages = df.formatCellValue(row.getCell(13));
                    v.totalAll = df.formatCellValue(row.getCell(14));
                    v.docNo = df.formatCellValue(row.getCell(15));
                    v.officer = df.formatCellValue(row.getCell(16));
                    v.dataSource = df.formatCellValue(row.getCell(17));
                    violations.add(v);
                }
            } else if (name.contains("training")) {
                for (Row row : s) {
                    if (row.getRowNum() == 0) continue;
                    String regNo = df.formatCellValue(row.getCell(0)).trim();
                    if (regNo.isEmpty()) continue;
                    Training t = new Training(regNo, df.formatCellValue(row.getCell(3)), df.formatCellValue(row.getCell(4)));
                    t.workerName = df.formatCellValue(row.getCell(1));
                    t.trainingCode = df.formatCellValue(row.getCell(2));
                    t.time = df.formatCellValue(row.getCell(5));
                    t.trainingHours = df.formatCellValue(row.getCell(6));
                    t.trainingLocation = df.formatCellValue(row.getCell(7));
                    t.passFail = df.formatCellValue(row.getCell(8));
                    trainings.add(t);
                }
            } else if (name.contains("kecelakaan")) {
                for (Row row : s) {
                    if (row.getRowNum() == 0) continue;
                    String regNo = df.formatCellValue(row.getCell(0)).trim();
                    if (regNo.isEmpty()) continue;
                    Accident a = new Accident(regNo, df.formatCellValue(row.getCell(1)), df.formatCellValue(row.getCell(2)),
                                             df.formatCellValue(row.getCell(3)), df.formatCellValue(row.getCell(4)), df.formatCellValue(row.getCell(5)));
                    accidents.add(a);
                }
            }
        }
    }

    private void showFullBackupPreview(List<Worker> workers, List<Violation> violations, List<Training> trainings, List<Accident> accidents) {
        com.example.scanbar.databinding.DialogImportPreviewBinding b = 
                com.example.scanbar.databinding.DialogImportPreviewBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(b.getRoot()).create();

        b.tvImportCountInfo.setText(String.format("Laporan Backup Lengkap:\n• %d Pekerja\n• %d Pelanggaran\n• %d Training\n• %d Kecelakaan", 
                workers.size(), violations.size(), trainings.size(), accidents.size()));

        b.rvImportPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        // Show worker list as preview
        b.rvImportPreview.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Worker w = workers.get(position);
                TextView name = holder.itemView.findViewById(R.id.tvMiniName);
                TextView reg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                name.setText(w.name);
                reg.setText(w.regNo);
            }
            @Override public int getItemCount() { return workers.size(); }
        });

        b.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
        b.btnImportConfirm.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                for (Worker w : workers) {
                    workerDao.insert(w);
                    // Determine status based on presence of formal violations (case-insensitive)
                    boolean hasVio = false;
                    for (Violation vio : violations) {
                        if (vio.workerRegNo.trim().equalsIgnoreCase(w.regNo.trim())) {
                            if (vio.type != null && !vio.type.toLowerCase().contains("teguran")) {
                                hasVio = true;
                                break;
                            }
                        }
                    }
                    if (hasVio) {
                        w.status = "Pelanggaran";
                        workerDao.update(w);
                    }
                }
                for (Violation vio : violations) workerDao.insertViolation(vio);
                for (Training t : trainings) workerDao.insertTraining(t);
                for (Accident a : accidents) {
                    a.dataSource = "Imported";
                    workerDao.insertAccident(a);
                }

                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Backup Berhasil Direstore", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void importWorkers(Sheet sheet, DataFormatter df) {
        List<Worker> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String regNo = df.formatCellValue(row.getCell(0));
            if (regNo.isEmpty()) continue;
            Worker w = new Worker(regNo, df.formatCellValue(row.getCell(1)), df.formatCellValue(row.getCell(3)),
                                 df.formatCellValue(row.getCell(4)), "Bersih");
            w.contractorCode = df.formatCellValue(row.getCell(2));
            w.wspExpDate = df.formatCellValue(row.getCell(5));
            w.birthDate = df.formatCellValue(row.getCell(6));
            w.age = df.formatCellValue(row.getCell(7));
            w.gender = df.formatCellValue(row.getCell(8));
            w.dataSource = df.formatCellValue(row.getCell(9));
            list.add(w);
        }
        getActivity().runOnUiThread(() -> showImportPreviewDialog(list, "Pekerja"));
    }

    private void importViolations(Sheet sheet, DataFormatter df) {
        List<Violation> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String regNo = df.formatCellValue(row.getCell(8));
            if (regNo.isEmpty()) continue;
            Violation v = new Violation(regNo, df.formatCellValue(row.getCell(11)), 
                                       df.formatCellValue(row.getCell(0)), df.formatCellValue(row.getCell(4)), "-");
            v.year = df.formatCellValue(row.getCell(1));
            v.time = df.formatCellValue(row.getCell(2));
            v.plant = df.formatCellValue(row.getCell(3));
            v.contractor = df.formatCellValue(row.getCell(5));
            v.userPlantDivision = df.formatCellValue(row.getCell(6));
            v.name = df.formatCellValue(row.getCell(7));
            v.jobTitle = df.formatCellValue(row.getCell(9));
            v.fine = df.formatCellValue(row.getCell(10));
            v.charge = df.formatCellValue(row.getCell(12));
            v.damages = df.formatCellValue(row.getCell(13));
            v.totalAll = df.formatCellValue(row.getCell(14));
            v.docNo = df.formatCellValue(row.getCell(15));
            v.officer = df.formatCellValue(row.getCell(16));
            v.dataSource = df.formatCellValue(row.getCell(17));
            list.add(v);
        }
        getActivity().runOnUiThread(() -> showViolationPreviewDialog(list));
    }

    private void importTrainings(Sheet sheet, DataFormatter df) {
        List<Training> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String regNo = df.formatCellValue(row.getCell(0));
            if (regNo.isEmpty()) continue;
            Training t = new Training(regNo, df.formatCellValue(row.getCell(3)), df.formatCellValue(row.getCell(4)));
            t.workerName = df.formatCellValue(row.getCell(1));
            t.trainingCode = df.formatCellValue(row.getCell(2));
            t.time = df.formatCellValue(row.getCell(5));
            t.trainingHours = df.formatCellValue(row.getCell(6));
            t.trainingLocation = df.formatCellValue(row.getCell(7));
            t.passFail = df.formatCellValue(row.getCell(8));
            list.add(t);
        }
        getActivity().runOnUiThread(() -> showTrainingPreviewDialog(list));
    }

    private void importAccidents(Sheet sheet, DataFormatter df) {
        List<Accident> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String regNo = df.formatCellValue(row.getCell(0));
            if (regNo.isEmpty()) continue;
            Accident a = new Accident(regNo, df.formatCellValue(row.getCell(1)), df.formatCellValue(row.getCell(2)),
                                     df.formatCellValue(row.getCell(3)), df.formatCellValue(row.getCell(4)), df.formatCellValue(row.getCell(5)));
            list.add(a);
        }
        getActivity().runOnUiThread(() -> showAccidentPreviewDialog(list));
    }

    private void importLegacyWorkers(Sheet sheet, DataFormatter df) {
        List<Worker> list = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            String regNo = df.formatCellValue(row.getCell(0));
            String name = df.formatCellValue(row.getCell(1));
            if (regNo.isEmpty() || name.isEmpty() || regNo.equals("-")) continue;
            Worker w = new Worker(regNo, name, df.formatCellValue(row.getCell(3)), 
                                 df.formatCellValue(row.getCell(4)), df.formatCellValue(row.getCell(2)));
            w.dateOfEvent = df.formatCellValue(row.getCell(5));
            w.violationType = df.formatCellValue(row.getCell(6));
            w.fineAmount = df.formatCellValue(row.getCell(7));
            w.plantDiv = df.formatCellValue(row.getCell(8));
            w.eventLocation = df.formatCellValue(row.getCell(9));
            w.documentNo = df.formatCellValue(row.getCell(10));
            w.inspectorName = df.formatCellValue(row.getCell(11));
            w.dataSource = df.formatCellValue(row.getCell(12));
            list.add(w);
        }
        getActivity().runOnUiThread(() -> showImportPreviewDialog(list, "Pekerja (Legacy)"));
    }

    private void showImportPreviewDialog(List<Worker> workers, String type) {
        com.example.scanbar.databinding.DialogImportPreviewBinding previewBinding = 
                com.example.scanbar.databinding.DialogImportPreviewBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(previewBinding.getRoot()).create();

        previewBinding.tvImportCountInfo.setText("Ditemukan: " + workers.size() + " data " + type);

        previewBinding.rvImportPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        previewBinding.rvImportPreview.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Worker w = workers.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMiniName);
                TextView tvReg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                tvName.setText(w.name);
                tvReg.setText(w.regNo + (w.status != null ? " (" + w.status + ")" : ""));
            }
            @Override public int getItemCount() { return workers.size(); }
        });

        previewBinding.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
        previewBinding.btnImportConfirm.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                for (Worker w : workers) {
                    workerDao.insert(w);
                    // Legacy sync for violations if any
                    if (w.status != null && (w.status.equalsIgnoreCase("Pelanggaran") || (w.violationType != null && !w.violationType.equals("-")))) {
                        Violation vio = new Violation(w.regNo, w.violationType, w.dateOfEvent, w.eventLocation, "-");
                        vio.fine = w.fineAmount; vio.plant = w.plantDiv; vio.docNo = w.documentNo;
                        vio.officer = w.inspectorName; vio.dataSource = w.dataSource;
                        workerDao.insertViolation(vio);
                    }
                }
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Berhasil mengimpor " + workers.size() + " data pekerja", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showViolationPreviewDialog(List<Violation> list) {
        com.example.scanbar.databinding.DialogImportPreviewBinding previewBinding = 
                com.example.scanbar.databinding.DialogImportPreviewBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(previewBinding.getRoot()).create();
        previewBinding.tvImportCountInfo.setText("Ditemukan: " + list.size() + " riwayat pelanggaran");

        previewBinding.rvImportPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        previewBinding.rvImportPreview.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Violation v = list.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMiniName);
                TextView tvReg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                tvName.setText(v.type);
                tvReg.setText("Reg: " + v.workerRegNo + " | Tgl: " + v.date);
            }
            @Override public int getItemCount() { return list.size(); }
        });

        previewBinding.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
        previewBinding.btnImportConfirm.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                for (Violation vio : list) {
                    workerDao.insertViolation(vio);
                    Worker w = workerDao.getWorkerByRegNo(vio.workerRegNo);
                    if (w != null) {
                        boolean isTeguran = vio.type != null && vio.type.toLowerCase().contains("teguran");
                        if (w.status == null || !w.status.equalsIgnoreCase("Pelanggaran")) {
                            w.status = isTeguran ? "Teguran" : "Pelanggaran";
                        }
                        workerDao.update(w);
                    }
                }
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Berhasil mengimpor " + list.size() + " riwayat pelanggaran", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                });
            });
        });
        dialog.show();
    }

    private void showTrainingPreviewDialog(List<Training> list) {
        com.example.scanbar.databinding.DialogImportPreviewBinding previewBinding = 
                com.example.scanbar.databinding.DialogImportPreviewBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(previewBinding.getRoot()).create();
        previewBinding.tvImportCountInfo.setText("Ditemukan: " + list.size() + " riwayat training");

        previewBinding.rvImportPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        previewBinding.rvImportPreview.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Training t = list.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMiniName);
                TextView tvReg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                tvName.setText(t.trainingTitle);
                tvReg.setText("Reg: " + t.workerRegNo + " | Tgl: " + t.date);
            }
            @Override public int getItemCount() { return list.size(); }
        });

        previewBinding.btnImportConfirm.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                for (Training t : list) workerDao.insertTraining(t);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Import training berhasil", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        previewBinding.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showAccidentPreviewDialog(List<Accident> list) {
        com.example.scanbar.databinding.DialogImportPreviewBinding previewBinding = 
                com.example.scanbar.databinding.DialogImportPreviewBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setView(previewBinding.getRoot()).create();
        previewBinding.tvImportCountInfo.setText("Ditemukan: " + list.size() + " riwayat kecelakaan");

        previewBinding.rvImportPreview.setLayoutManager(new LinearLayoutManager(getContext()));
        previewBinding.rvImportPreview.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_worker_minimal, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Accident a = list.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvMiniName);
                TextView tvReg = holder.itemView.findViewById(R.id.tvMiniRegNo);
                tvName.setText("Kecelakaan (" + a.severity + ")");
                tvReg.setText("Reg: " + a.workerRegNo + " | Tgl: " + a.date);
            }
            @Override public int getItemCount() { return list.size(); }
        });

        previewBinding.btnImportConfirm.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                for (Accident a : list) workerDao.insertAccident(a);
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Import kecelakaan berhasil", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });
        });
        previewBinding.btnImportCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
