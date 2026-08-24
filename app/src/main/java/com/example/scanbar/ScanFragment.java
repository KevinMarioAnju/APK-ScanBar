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
import android.content.Intent;
import com.example.scanbar.databinding.DialogViolationFormBinding;

public class ScanFragment extends Fragment {
    private FragmentScanBinding binding;
    private ScanHistoryAdapter historyAdapter;
    private BarcodeScanner scanner;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private String lastScannedCode = "";
    private long lastScanTimestamp = 0;

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
        
        Executors.newSingleThreadExecutor().execute(() -> {
            Worker worker = AppDatabase.getDatabase(getContext()).workerDao().getWorkerByRegNo(cleanRegNo);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    String currentTime = new SimpleDateFormat("HH.mm.ss", Locale.getDefault()).format(new Date());
                    if (worker != null) {
                        historyAdapter.addEntry(new ScanHistory(cleanRegNo, worker.name, worker.contractor, currentTime, true));
                        showWorkerDetails(worker);
                    } else {
                        historyAdapter.addEntry(new ScanHistory(cleanRegNo, null, null, currentTime, false));
                        Toast.makeText(getContext(), "Kontraktor Tidak Ditemukan", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showWorkerDetails(Worker worker) {
        stopCamera();
        Intent intent = new Intent(getContext(), ScanResultActivity.class);
        intent.putExtra("REG_NO", worker.regNo);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}