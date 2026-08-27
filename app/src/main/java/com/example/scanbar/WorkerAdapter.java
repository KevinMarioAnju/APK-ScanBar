package com.example.scanbar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.scanbar.data.Worker;
import com.example.scanbar.data.WorkerWithStats;
import com.example.scanbar.databinding.ItemWorkerBinding;
import java.util.ArrayList;
import java.util.List;

public class WorkerAdapter extends RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder> {
    private List<WorkerWithStats> workerStats = new ArrayList<>();
    private OnWorkerActionListener listener;
    private String userRole = "inspektur";

    public interface OnWorkerActionListener {
        void onEdit(Worker worker);
        void onDelete(Worker worker);
        void onViolation(Worker worker);
        void onDetail(Worker worker);
    }

    public WorkerAdapter(OnWorkerActionListener listener, String userRole) {
        this.listener = listener;
        this.userRole = userRole;
    }

    public void setWorkers(List<WorkerWithStats> workers) {
        this.workerStats = workers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WorkerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWorkerBinding binding = ItemWorkerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new WorkerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull WorkerViewHolder holder, int position) {
        WorkerWithStats stats = workerStats.get(position);
        Worker worker = stats.worker;
        
        holder.binding.tvRegNo.setText("REG NO: " + worker.regNo);
        holder.binding.tvName.setText(worker.name);
        holder.binding.tvContractor.setText(worker.contractor);
        holder.binding.tvPosition.setText(worker.position);
        
        // --- MULTI-STATUS BADGE LOGIC (Refined UX) ---
        boolean hasCriticalInfo = false;

        // 1. Violation Check
        if (stats.violationCount > 0) {
            holder.binding.tvStatus.setText(stats.violationCount + " Pelanggaran");
            holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pill_violation);
            holder.binding.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.semantic_error_text));
            holder.binding.tvStatus.setVisibility(View.VISIBLE);
            hasCriticalInfo = true;
        } else {
            holder.binding.tvStatus.setVisibility(View.GONE);
        }

        // 2. Reprimand Check
        if (stats.reprimandCount > 0) {
            holder.binding.tvStatusReprimand.setText(stats.reprimandCount + " Teguran");
            holder.binding.tvStatusReprimand.setBackgroundResource(R.drawable.bg_status_pill_reprimand);
            holder.binding.tvStatusReprimand.setTextColor(holder.itemView.getContext().getColor(R.color.semantic_warning_text));
            holder.binding.tvStatusReprimand.setVisibility(View.VISIBLE);
            hasCriticalInfo = true;
        } else {
            holder.binding.tvStatusReprimand.setVisibility(View.GONE);
        }

        // 3. Accident Check
        if (stats.accidentCount > 0) {
            holder.binding.tvStatusAccident.setText(stats.accidentCount + " Kecelakaan");
            holder.binding.tvStatusAccident.setBackgroundResource(R.drawable.bg_status_pill_accident);
            holder.binding.tvStatusAccident.setTextColor(holder.itemView.getContext().getColor(R.color.semantic_error_text));
            holder.binding.tvStatusAccident.setVisibility(View.VISIBLE);
            hasCriticalInfo = true;
        } else {
            holder.binding.tvStatusAccident.setVisibility(View.GONE);
        }

        // 4. Clean Status
        if (!hasCriticalInfo) {
            holder.binding.tvStatusClean.setVisibility(View.VISIBLE);
            holder.binding.tvStatusClean.setTextColor(holder.itemView.getContext().getColor(R.color.semantic_success_text));
        } else {
            holder.binding.tvStatusClean.setVisibility(View.GONE);
        }

        // 3. Training Check
        if (stats.trainingCount > 0) {
            holder.binding.tvStatusTraining.setText(stats.trainingCount + " TRAINING");
            holder.binding.tvStatusTraining.setBackgroundResource(R.drawable.bg_status_pill_info);
            holder.binding.tvStatusTraining.setTextColor(Color.parseColor("#00B894"));
            holder.binding.tvStatusTraining.setVisibility(View.VISIBLE);
        } else {
            holder.binding.tvStatusTraining.setVisibility(View.GONE);
        }
        
        // Set root click for details
        holder.binding.getRoot().setOnClickListener(v -> listener.onDetail(worker));
        
        if ("admin".equalsIgnoreCase(userRole)) {
            holder.binding.btnEdit.setVisibility(View.VISIBLE);
            holder.binding.btnDelete.setVisibility(View.VISIBLE);
        } else {
            holder.binding.btnEdit.setVisibility(View.GONE);
            holder.binding.btnDelete.setVisibility(View.GONE);
        }

        holder.binding.btnEdit.setOnClickListener(v -> listener.onEdit(worker));
        holder.binding.btnDelete.setOnClickListener(v -> listener.onDelete(worker));
    }

    @Override
    public int getItemCount() {
        return workerStats.size();
    }

    static class WorkerViewHolder extends RecyclerView.ViewHolder {
        ItemWorkerBinding binding;
        WorkerViewHolder(ItemWorkerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}