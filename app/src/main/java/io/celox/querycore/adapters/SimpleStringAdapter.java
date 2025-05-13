package io.celox.querycore.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleStringAdapter extends RecyclerView.Adapter<SimpleStringAdapter.StringViewHolder> {
    
    private List<String> items = new ArrayList<>();
    private List<String> filteredItems = new ArrayList<>();
    private final OnItemClickListener listener;
    
    public SimpleStringAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public StringViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new StringViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull StringViewHolder holder, int position) {
        String current = filteredItems.get(position);
        holder.textView.setText(current);
        
        holder.itemView.setOnClickListener(v -> listener.onItemClick(current));
    }
    
    @Override
    public int getItemCount() {
        return filteredItems.size();
    }
    
    public void setItems(List<String> items) {
        this.items = items;
        this.filteredItems = new ArrayList<>(items);
        notifyDataSetChanged();
    }
    
    public void filter(String query) {
        if (query == null || query.isEmpty()) {
            filteredItems = new ArrayList<>(items);
        } else {
            filteredItems = items.stream()
                    .filter(item -> item.toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }
    
    static class StringViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        
        public StringViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
    
    public interface OnItemClickListener {
        void onItemClick(String item);
    }
}