package io.celox.querycore.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.celox.querycore.R;
import io.celox.querycore.adapters.TableDataAdapter;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class QueryResultsFragment extends Fragment {
    
    private DatabaseViewModel databaseViewModel;
    
    private TextView textViewQuery;
    private TextView textViewResultInfo;
    private RecyclerView recyclerView;
    private Button buttonExport;
    private ProgressBar progressBar;
    private TextView emptyView;
    
    private TableDataAdapter adapter;
    private String query;
    private List<Map<String, Object>> queryResults;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_query_results, container, false);
        
        // Initialize views
        textViewQuery = view.findViewById(R.id.text_view_query);
        textViewResultInfo = view.findViewById(R.id.text_view_result_info);
        recyclerView = view.findViewById(R.id.recycler_view_results);
        buttonExport = view.findViewById(R.id.button_export);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.text_view_empty);
        
        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TableDataAdapter();
        recyclerView.setAdapter(adapter);
        
        // Set up export button
        buttonExport.setOnClickListener(v -> exportResults());
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModel
        databaseViewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
        
        // Get arguments
        if (getArguments() != null) {
            query = getArguments().getString("query", "");
        }
        
        // Update UI
        textViewQuery.setText(query);
        
        // Observe query results
        databaseViewModel.getQueryResults().observe(getViewLifecycleOwner(), results -> {
            if (results != null && !results.isEmpty()) {
                queryResults = results;
                adapter.setData(results);
                textViewResultInfo.setText(String.format("%d rows returned", results.size()));
                hideLoading();
            } else {
                showEmpty();
            }
        });
    }
    
    private void exportResults() {
        if (queryResults == null || queryResults.isEmpty()) {
            Toast.makeText(requireContext(), "No results to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Generate a CSV file
            StringBuilder csvData = new StringBuilder();
            
            // Add header row
            if (!queryResults.isEmpty()) {
                Map<String, Object> firstRow = queryResults.get(0);
                boolean isFirst = true;
                for (String column : firstRow.keySet()) {
                    if (!isFirst) {
                        csvData.append(",");
                    }
                    csvData.append("\"").append(column).append("\"");
                    isFirst = false;
                }
                csvData.append("\n");
            }
            
            // Add data rows
            for (Map<String, Object> row : queryResults) {
                boolean isFirst = true;
                for (Object value : row.values()) {
                    if (!isFirst) {
                        csvData.append(",");
                    }
                    if (value != null) {
                        csvData.append("\"").append(value.toString().replace("\"", "\"\"")).append("\"");
                    } else {
                        csvData.append("\"\"");
                    }
                    isFirst = false;
                }
                csvData.append("\n");
            }
            
            // Save the file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String fileName = "query_results_" + sdf.format(new Date()) + ".csv";
            
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            
            Uri uri = requireContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    os.write(csvData.toString().getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(requireContext(), "Results exported to " + fileName, Toast.LENGTH_LONG).show();
                }
            } else {
                throw new IOException("Failed to create file");
            }
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }
    
    private void showEmpty() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        textViewResultInfo.setText("0 rows returned");
    }
}