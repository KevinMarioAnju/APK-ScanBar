package com.example.scanbar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.scanbar.data.Worker;
import com.example.scanbar.databinding.ItemWorkerBinding;
import java.util.ArrayList;
import java.util.List;

public class WorkerAdapter extends RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder> {
    private List<Worker> workers = new ArrayList<>();
    private OnWorkerActionListener listener;

    public interface OnWorkerActionListener {
        void onEdit(Worker worker);
        void onDelete(Worker worker);
        void onViolation(Worker worker);
        void onDetail(Worker worker);
    }

    public WorkerAdapter(OnWorkerActionListener listener) {
        this.listener = listener;
    }

    public void setWorkers(List<Worker> workers) {
        this.workers = workers;
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
        Worker worker = workers.get(position);
        holder.binding.tvRegNo.setText("REG NO: " + worker.regNo);
        holder.binding.tvName.setText(worker.name);
        holder.binding.tvContractor.setText(worker.contractor);
        holder.binding.tvPosition.setText(worker.position);
        
        // Status binding with modern UI logic
        if (worker.status != null && (worker.status.equalsIgnoreCase("Pelanggaran") || worker.status.contains("1") || worker.status.contains("PELANGGARAN"))) {
            holder.binding.tvStatus.setText("ADA PELANGGARAN");
            holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pill_error);
        } else {
            holder.binding.tvStatus.setText("BERSIH");
            holder.binding.tvStatus.setBackgroundResource(R.drawable.bg_status_pill_success);
        }
        
        // Set root click for details
        holder.binding.getRoot().setOnClickListener(v -> listener.onDetail(worker));
        
        holder.binding.btnEdit.setOnClickListener(v -> listener.onEdit(worker));
        holder.binding.btnDelete.setOnClickListener(v -> listener.onDelete(worker));
    }

    @Override
    public int getItemCount() {
        return workers.size();
    }

    static class WorkerViewHolder extends RecyclerView.ViewHolder {
        ItemWorkerBinding binding;
        WorkerViewHolder(ItemWorkerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}