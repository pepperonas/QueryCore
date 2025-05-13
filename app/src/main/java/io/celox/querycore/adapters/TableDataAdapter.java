package io.celox.querycore.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.celox.querycore.R;

public class TableDataAdapter extends RecyclerView.Adapter<TableDataAdapter.TableDataViewHolder> {
    
    private List<Map<String, Object>> data = new ArrayList<>();
    private Map<String, String> structure = new LinkedHashMap<>();
    private List<String> columns = new ArrayList<>();
    
    @NonNull
    @Override
    public TableDataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_table_row, parent, false);
        return new TableDataViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TableDataViewHolder holder, int position) {
        Map<String, Object> rowData = data.get(position);
        
        // Clear existing views
        holder.tableRow.removeAllViews();
        
        // Add row number column
        TextView rowNumberView = new TextView(holder.itemView.getContext());
        rowNumberView.setPadding(16, 8, 16, 8);
        rowNumberView.setText(String.valueOf(position + 1));
        holder.tableRow.addView(rowNumberView);
        
        // Add data columns
        for (String column : columns) {
            TextView textView = new TextView(holder.itemView.getContext());
            textView.setPadding(16, 8, 16, 8);
            
            Object value = rowData.get(column);
            textView.setText(value != null ? value.toString() : "null");
            
            holder.tableRow.addView(textView);
        }
    }
    
    @Override
    public int getItemCount() {
        return data.size();
    }
    
    public void setData(List<Map<String, Object>> data) {
        this.data = data;
        
        // Update columns if needed
        if (data != null && !data.isEmpty() && (columns == null || columns.isEmpty())) {
            columns = new ArrayList<>(data.get(0).keySet());
        }
        
        notifyDataSetChanged();
    }
    
    public void setStructure(Map<String, String> structure) {
        this.structure = structure;
        
        // Update columns based on structure
        if (structure != null && !structure.isEmpty()) {
            columns = new ArrayList<>(structure.keySet());
        }
        
        notifyDataSetChanged();
    }
    
    static class TableDataViewHolder extends RecyclerView.ViewHolder {
        private final TableRow tableRow;
        
        public TableDataViewHolder(@NonNull View itemView) {
            super(itemView);
            tableRow = itemView.findViewById(R.id.table_row);
        }
    }
}