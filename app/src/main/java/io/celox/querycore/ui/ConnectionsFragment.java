package io.celox.querycore.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import io.celox.querycore.R;
import io.celox.querycore.adapters.ConnectionAdapter;
import io.celox.querycore.data.ConnectionEntity;
import io.celox.querycore.models.ConnectionInfo;
import io.celox.querycore.viewmodel.ConnectionViewModel;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class ConnectionsFragment extends Fragment implements ConnectionAdapter.OnConnectionListener {

    private ConnectionViewModel connectionViewModel;
    private DatabaseViewModel databaseViewModel;
    private ConnectionAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connections, container, false);
        
        // Initialize views
        recyclerView = view.findViewById(R.id.recycler_view_connections);
        emptyView = view.findViewById(R.id.text_view_empty);
        FloatingActionButton fabAddConnection = view.findViewById(R.id.fab_add_connection);
        
        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ConnectionAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // Set click listener for FAB
        fabAddConnection.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(
                        R.id.action_connectionsFragment_to_addEditConnectionFragment
                )
        );
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModels
        connectionViewModel = new ViewModelProvider(requireActivity()).get(ConnectionViewModel.class);
        databaseViewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
        
        // Observe connections
        connectionViewModel.getAllConnections().observe(getViewLifecycleOwner(), connections -> {
            adapter.setConnections(connections);
            
            if (connections == null || connections.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }
    
    @Override
    public void onConnectClick(ConnectionEntity connection) {
        // Connect to database using the database view model
        ConnectionInfo connectionInfo = connection.toConnectionInfo();
        databaseViewModel.connect(connectionInfo);
        
        // Navigate to database browser with bundle
        Bundle args = new Bundle();
        args.putInt("connectionId", connection.getId());
        Navigation.findNavController(requireView()).navigate(
                R.id.action_connectionsFragment_to_databaseBrowserFragment, args
        );
    }
    
    @Override
    public void onEditClick(ConnectionEntity connection) {
        // Navigate to edit connection screen with bundle
        Bundle args = new Bundle();
        args.putInt("connectionId", connection.getId());
        Navigation.findNavController(requireView()).navigate(
                R.id.action_connectionsFragment_to_addEditConnectionFragment, args
        );
    }
}