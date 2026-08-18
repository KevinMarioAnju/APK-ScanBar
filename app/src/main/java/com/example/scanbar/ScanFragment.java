package com.example.scanbar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.scanbar.data.AppDatabase;
import com.example.scanbar.data.Worker;
import com.example.scanbar.databinding.FragmentScanBinding;
import com.example.scanbar.databinding.LayoutWorkerDetailsBinding;
import android.graphics.Color;
import android.widget.TextView;
import com.example.scanbar.data.Violation;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import android.annotation.SuppressLint;
import android.util.Log;

import com.google.mlkit.vision.barcode.BarcodeScannerOptions;

public class ScanFragment extends Fragment {
    private FragmentScanBinding binding;
    private LayoutWorkerDetailsBinding detailBinding;
    private ScanHistoryAdapter historyAdapter;
    private BarcodeScanner scanner;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String lastScannedCode = "";
    private long lastScanTimestamp = 0;
    private androidx.lifecycle.LiveData<Worker> currentWorkerLiveData;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        toggleCamera();
                    } else {
                        Toast.makeText(getContext(), "Izin kamera diperlukan untuk memindai barcode card", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        detailBinding = LayoutWorkerDetailsBinding.bind(binding.layoutWorkerDetails.getRoot());
        
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        scanner = BarcodeScanning.getClient(options);
        
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupHistoryList();

        binding.btnActivateCamera.setOnClickListener(v -> checkPermissionAndToggleCamera());

        binding.etRegNo.setOnEditorActionListener((v, actionId, event) -> {
            String regNo = binding.etRegNo.getText().toString();
            if (!regNo.isEmpty()) {
                searchWorker(regNo);
                binding.etRegNo.setText(""); 
            }
            return true;
        });
    }

    private void checkPermissionAndToggleCamera() {
        if (getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            toggleCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void toggleCamera() {
        if (binding.previewView.getVisibility() == View.GONE) {
            binding.previewView.setVisibility(View.VISIBLE);
            startCamera();
            binding.btnActivateCamera.setText("Matikan Kamera");
        } else {
            stopCamera();
        }
    }

    private void stopCamera() {
        try {
            if (getContext() != null) {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
                cameraProvider.unbindAll();
            }
        } catch (Exception e) {
            Log.e("ScanFragment", "Error stopping camera", e);
        }
        if (binding != null) {
            binding.previewView.setVisibility(View.GONE);
            binding.btnActivateCamera.setText("Aktifkan Kamera");
        }
    }

    private void startCamera() {
        if (getContext() == null) return;
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                Log.e("ScanFragment", "Camera initialization failed", e);
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        if (getContext() == null || binding == null) return;
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(getContext()), imageProxy -> {
            @SuppressLint("UnsafeOptInUsageError")
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
            
            if (image != null) {
                scanner.process(image)
                        .addOnSuccessListener(barcodes -> {
                            long now = System.currentTimeMillis();
                            for (Barcode barcode : barcodes) {
                                String value = barcode.getRawValue();
                                if (value != null && !value.isEmpty()) {
                                    if (!value.equals(lastScannedCode) || (now - lastScanTimestamp > 2000)) {
                                        lastScannedCode = value;
                                        lastScanTimestamp = now;
                                        searchWorker(value);
                                    }
                                }
                            }
                        })
                        .addOnCompleteListener(task -> imageProxy.close());
            } else {
                imageProxy.close();
            }
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private void setupHistoryList() {
        historyAdapter = new ScanHistoryAdapter();
        binding.rvScanHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvScanHistory.setAdapter(historyAdapter);
    }

    private void searchWorker(String regNo) {
        String cleanRegNo = regNo.trim();
        if (currentWorkerLiveData != null) {
            currentWorkerLiveData.removeObservers(getViewLifecycleOwner());
        }

        currentWorkerLiveData = AppDatabase.getDatabase(getContext()).workerDao().getWorkerLiveDataByRegNo(cleanRegNo);
        
        currentWorkerLiveData.observe(getViewLifecycleOwner(), worker -> {
            if (worker != null) {
                showWorkerDetails(worker);
            } else {
                hideWorkerDetails();
                Toast.makeText(getContext(), "Kontraktor Tidak Ditemukan", Toast.LENGTH_SHORT).show();
            }
        });

        // Still add to history on the initial search trigger
        Executors.newSingleThreadExecutor().execute(() -> {
            Worker worker = AppDatabase.getDatabase(getContext()).workerDao().getWorkerByRegNo(cleanRegNo);
            getActivity().runOnUiThread(() -> {
                String currentTime = new SimpleDateFormat("HH.mm.ss", Locale.getDefault()).format(new Date());
                if (worker != null) {
                    historyAdapter.addEntry(new ScanHistory(cleanRegNo, worker.name, currentTime, true));
                } else {
                    historyAdapter.addEntry(new ScanHistory(cleanRegNo, null, currentTime, false));
                }
            });
        });
    }

    private void showWorkerDetails(Worker worker) {
        binding.flWorkerDetailsContainer.setVisibility(View.VISIBLE);

        detailBinding.tvDetailName.setText(worker.name);
        detailBinding.tvDetailPosition.setText(worker.position);
        detailBinding.tvDetailRegNoTop.setText("CODE: " + worker.regNo);
        detailBinding.tvDetailRegNo.setText(worker.regNo);
        detailBinding.tvDetailContractor.setText(worker.contractor);
        detailBinding.tvDetailEventDate.setText(worker.dateOfEvent != null ? worker.dateOfEvent : "-");
        detailBinding.tvDetailPlant.setText(worker.plantDiv != null ? worker.plantDiv : "-");

        // Clean and Show Fine Box
        if (worker.fineAmount != null && !worker.fineAmount.equals("-") && !worker.fineAmount.isEmpty()) {
            detailBinding.layoutFine.setVisibility(View.VISIBLE);
            detailBinding.tvDetailFineBox.setText(cleanFineAmount(worker.fineAmount));
        } else {
            detailBinding.layoutFine.setVisibility(View.GONE);
        }

        // Toggle Violation Section Visibility on Badge Click
        detailBinding.tvDetailViolationBadge.setOnClickListener(v -> {
            if (detailBinding.llViolationSection.getVisibility() == View.VISIBLE) {
                detailBinding.llViolationSection.setVisibility(View.GONE);
            } else {
                detailBinding.llViolationSection.setVisibility(View.VISIBLE);
            }
        });

        // Hide violation section by default
        detailBinding.llViolationSection.setVisibility(View.GONE);
        
        // Cek status pelanggaran langsung dari object worker (dari Edit Dialog / Excel)
        boolean hasDirectViolation = worker.status != null && 
            (worker.status.equalsIgnoreCase("Pelanggaran") || worker.status.contains("PELANGGARAN"));

        // Fetch and show violations
        AppDatabase.getDatabase(getContext()).workerDao().getViolationsByWorker(worker.regNo).observe(getViewLifecycleOwner(), violations -> {
            detailBinding.llViolationList.removeAllViews();
            int tableCount = violations.size();
            
            // Update Badge
            if (hasDirectViolation || tableCount > 0) {
                String badgeText = "ADA PELANGGARAN";
                if (tableCount > 0) badgeText += " (" + tableCount + ")";
                detailBinding.tvDetailViolationBadge.setText(badgeText);
                detailBinding.tvDetailViolationBadge.setBackgroundResource(R.drawable.bg_status_pill_error);
                detailBinding.tvDetailViolationBadge.setTextColor(Color.WHITE);
            } else {
                detailBinding.tvDetailViolationBadge.setText("BERSIH");
                detailBinding.tvDetailViolationBadge.setBackgroundResource(R.drawable.bg_status_pill_success);
                detailBinding.tvDetailViolationBadge.setTextColor(Color.WHITE);
            }

            // Tampilkan data pelanggaran dari Worker Object (Excel/Edit)
            if (hasDirectViolation && worker.violationType != null && !worker.violationType.isEmpty() && !worker.violationType.equals("-")) {
                addViolationItemToUi(worker.violationType, worker.dateOfEvent, 
                    worker.eventLocation, worker.documentNo, cleanFineAmount(worker.fineAmount), "");
            }

            // Tampilkan data pelanggaran dari tabel Violation
            for (Violation v : violations) {
                addViolationItemToUi(v.type, v.date, v.location, v.docNo, cleanFineAmount(v.fine), "Safety");
            }
        });
    }

    private String cleanFineAmount(String fine) {
        if (fine == null || fine.isEmpty() || fine.equals("-")) return "Rp -";
        String clean = fine.replace("-", "").trim();
        if (!clean.startsWith("Rp")) {
            clean = "Rp " + clean;
        }
        return clean;
    }

    private void addViolationItemToUi(String typeStr, String dateStr, String locStr, String docStr, String fineStr, String extraInfo) {
        View vioView = getLayoutInflater().inflate(R.layout.item_violation_detail, detailBinding.llViolationList, false);
        TextView type = vioView.findViewById(R.id.tvVioDetailType);
        TextView date = vioView.findViewById(R.id.tvVioDetailDate);
        TextView info = vioView.findViewById(R.id.tvVioDetailInfo);
        TextView loc = vioView.findViewById(R.id.tvVioDetailLoc);
        TextView doc = vioView.findViewById(R.id.tvVioDetailDoc);

        type.setText(typeStr);
        date.setText(dateStr != null ? dateStr : "-");
        info.setText((fineStr != null ? fineStr : "") + "  " + (extraInfo != null ? extraInfo : ""));
        loc.setText("LOKASI: " + (locStr != null ? locStr : "-"));
        doc.setText("NO. DOK: " + (docStr != null ? docStr : "-"));
        
        detailBinding.llViolationList.addView(vioView);
    }

    private void hideWorkerDetails() {
        binding.flWorkerDetailsContainer.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}