package io.celox.querycore.ui;

import android.os.Bundle;
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
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;

import io.celox.querycore.R;
import io.celox.querycore.models.ConnectionInfo;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class QueryFragment extends Fragment {
    
    private DatabaseViewModel databaseViewModel;
    
    private TextView textViewDatabaseName;
    private TextInputEditText editTextQuery;
    private Button buttonExecute;
    private Button buttonClear;
    private ProgressBar progressBar;
    
    private String databaseName;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_query, container, false);
        
        // Initialize views
        textViewDatabaseName = view.findViewById(R.id.text_view_database_name);
        editTextQuery = view.findViewById(R.id.edit_text_query);
        buttonExecute = view.findViewById(R.id.button_execute);
        buttonClear = view.findViewById(R.id.button_clear);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // Set up button click listeners
        buttonExecute.setOnClickListener(v -> executeQuery());
        buttonClear.setOnClickListener(v -> editTextQuery.setText(""));
        
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
        }
        
        // Update UI
        textViewDatabaseName.setText(String.format("Database: %s", databaseName));
        
        // Set default query text based on database type
        ConnectionInfo connectionInfo = databaseViewModel.getCurrentConnection().getValue();
        if (connectionInfo != null) {
            switch (connectionInfo.getType()) {
                case MYSQL:
                case MARIADB:
                    editTextQuery.setText("SELECT * FROM table_name LIMIT 100;");
                    break;
                case MONGODB:
                    editTextQuery.setText("{ \"collection\": \"collection_name\", \"find\": {} }");
                    break;
            }
        }
        
        // Observe errors
        databaseViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                hideLoading();
            }
        });
        
        // Observe query results
        databaseViewModel.getQueryResults().observe(getViewLifecycleOwner(), results -> {
            if (results != null) {
                hideLoading();
                // Navigate to results fragment
                Bundle args = new Bundle();
                args.putString("query", editTextQuery.getText().toString());
                Navigation.findNavController(requireView()).navigate(
                        R.id.action_queryFragment_to_queryResultsFragment, args
                );
            }
        });
    }
    
    private void executeQuery() {
        String query = editTextQuery.getText().toString().trim();
        
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a query", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading();
        databaseViewModel.executeQuery(query);
    }
    
    private void showLoading() {
        progressBar.setVisibility(View.VISIBLE);
        buttonExecute.setEnabled(false);
        buttonClear.setEnabled(false);
    }
    
    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
        buttonExecute.setEnabled(true);
        buttonClear.setEnabled(true);
    }
}