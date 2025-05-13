package io.celox.querycore.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.celox.querycore.R;
import io.celox.querycore.adapters.TableDataAdapter;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class TableViewFragment extends Fragment {
    
    private DatabaseViewModel databaseViewModel;
    
    private TextView textViewTableName;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyView;
    
    private TableDataAdapter adapter;
    private String databaseName;
    private String tableName;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_table_view, container, false);
        
        // Initialize views
        textViewTableName = view.findViewById(R.id.text_view_table_name);
        recyclerView = view.findViewById(R.id.recycler_view_table);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.text_view_empty);
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TableDataAdapter();
        recyclerView.setAdapter(adapter);
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModel
        databaseViewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
        
        // Get arguments
        if (getArguments() != null) {
            databaseName = getArguments().getString("databaseName", "");
            tableName = getArguments().getString("tableName", "");
        }
        
        // Update UI
        textViewTableName.setText(tableName);
        requireActivity().setTitle(String.format("%s - %s", databaseName, tableName));
        
        // Load table data
        showLoading();
        try {
            String query = "";
            if (databaseViewModel.getCurrentConnection().getValue().getType() == io.celox.querycore.models.ConnectionInfo.DatabaseType.MONGODB) {
                // MongoDB query
                query = String.format("{ \"collection\": \"%s\", \"find\": {} }", tableName);
            } else {
                // SQL query
                query = String.format("SELECT * FROM %s LIMIT 100", tableName);
            }
            databaseViewModel.executeQuery(query);
        } catch (Exception e) {
            showEmpty("Error loading table data: " + e.getMessage());
        }
        
        // Observe table structure
        databaseViewModel.getTableStructure().observe(getViewLifecycleOwner(), structure -> {
            if (structure != null && !structure.isEmpty()) {
                adapter.setStructure(structure);
            }
        });
        
        // Observe query results
        databaseViewModel.getQueryResults().observe(getViewLifecycleOwner(), results -> {
            if (results != null && !results.isEmpty()) {
                adapter.setData(results);
                hideLoading();
            } else {
                showEmpty("No data available");
            }
        });
        
        // Observe errors
        databaseViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                showEmpty("Error loading data");
            }
        });
        
        // Load table structure
        databaseViewModel.loadTableStructure(tableName);
    }
    
    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }
    
    private void showEmpty(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setText(message);
    }
}