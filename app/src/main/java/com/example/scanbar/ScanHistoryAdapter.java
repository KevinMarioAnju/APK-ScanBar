package com.example.scanbar;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.scanbar.databinding.ItemScanHistoryBinding;
import java.util.ArrayList;
import java.util.List;

public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {
    private List<ScanHistory> historyList = new ArrayList<>();

    public void addEntry(ScanHistory entry) {
        historyList.add(0, entry);
        notifyItemInserted(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemScanHistoryBinding binding = ItemScanHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanHistory item = historyList.get(position);
        holder.binding.tvHistoryName.setText(item.name != null ? item.name : item.regNo);
        holder.binding.tvHistoryTime.setText(item.time);
        
        if (item.isFound) {
            holder.binding.tvHistoryStatus.setText("DITEMUKAN");
            holder.binding.tvHistoryStatus.setTextColor(Color.parseColor("#4CAF50"));
            holder.binding.tvHistoryStatus.getBackground().setTint(Color.parseColor("#003D1B"));
        } else {
            holder.binding.tvHistoryStatus.setText("TIDAK ADA");
            holder.binding.tvHistoryStatus.setTextColor(Color.parseColor("#F44336"));
            holder.binding.tvHistoryStatus.getBackground().setTint(Color.parseColor("#3D0000"));
        }
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemScanHistoryBinding binding;
        ViewHolder(ItemScanHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}