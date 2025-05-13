package io.celox.querycore.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.celox.querycore.R;
import io.celox.querycore.data.ConnectionEntity;

public class ConnectionAdapter extends RecyclerView.Adapter<ConnectionAdapter.ConnectionViewHolder> {
    
    private List<ConnectionEntity> connections = new ArrayList<>();
    private final OnConnectionListener listener;
    
    public ConnectionAdapter(OnConnectionListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ConnectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_connection, parent, false);
        return new ConnectionViewHolder(itemView);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ConnectionViewHolder holder, int position) {
        ConnectionEntity current = connections.get(position);
        holder.textViewName.setText(current.getName());
        holder.textViewType.setText(current.getType());
        
        String details = String.format("%s:%d/%s", 
                current.getHost(), 
                current.getPort(), 
                current.getDatabase());
        holder.textViewDetails.setText(details);
        
        // Set click listeners
        holder.buttonConnect.setOnClickListener(v -> listener.onConnectClick(current));
        holder.buttonEdit.setOnClickListener(v -> listener.onEditClick(current));
    }
    
    @Override
    public int getItemCount() {
        return connections.size();
    }
    
    public void setConnections(List<ConnectionEntity> connections) {
        this.connections = connections;
        notifyDataSetChanged();
    }
    
    static class ConnectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewName;
        private final TextView textViewType;
        private final TextView textViewDetails;
        private final Button buttonConnect;
        private final Button buttonEdit;
        
        public ConnectionViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.text_view_connection_name);
            textViewType = itemView.findViewById(R.id.text_view_connection_type);
            textViewDetails = itemView.findViewById(R.id.text_view_connection_details);
            buttonConnect = itemView.findViewById(R.id.button_connect);
            buttonEdit = itemView.findViewById(R.id.button_edit);
        }
    }
    
    public interface OnConnectionListener {
        void onConnectClick(ConnectionEntity connection);
        void onEditClick(ConnectionEntity connection);
    }
}