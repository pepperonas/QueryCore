package io.celox.querycore.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import io.celox.querycore.R;
import io.celox.querycore.models.ConnectionInfo;
import io.celox.querycore.utils.DatabaseTestUtils;
import io.celox.querycore.viewmodel.DatabaseViewModel;

/**
 * Fragment for testing MySQL connections with correct settings
 */
public class MySqlTestFragment extends Fragment {
    private static final String TAG = "MySqlTestFragment";
    
    private EditText hostEditText;
    private EditText portEditText;
    private EditText databaseEditText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button connectButton;
    private Button testTableButton;
    private TextView resultTextView;
    
    private DatabaseViewModel viewModel;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_mysql_test, container, false);
        
        // Initialize UI components
        hostEditText = view.findViewById(R.id.editTextHost);
        portEditText = view.findViewById(R.id.editTextPort);
        databaseEditText = view.findViewById(R.id.editTextDatabase);
        usernameEditText = view.findViewById(R.id.editTextUsername);
        passwordEditText = view.findViewById(R.id.editTextPassword);
        connectButton = view.findViewById(R.id.buttonConnect);
        testTableButton = view.findViewById(R.id.buttonTestTable);
        resultTextView = view.findViewById(R.id.textViewResult);
        
        // Set default values
        hostEditText.setText("69.62.121.168");
        portEditText.setText("3306"); // Default MySQL port
        databaseEditText.setText("test_db");
        usernameEditText.setText("root");
        // Leave password empty in the UI - user will enter it
        
        // Set up listeners
        connectButton.setOnClickListener(v -> connectToMySQL());
        testTableButton.setOnClickListener(v -> createTestTable());
        
        // Observe connection result
        viewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected) {
                resultTextView.append("\nConnected successfully to MySQL!");
                testTableButton.setEnabled(true);
            } else {
                testTableButton.setEnabled(false);
            }
        });
        
        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                resultTextView.append("\nError: " + errorMsg);
            }
        });
        
        // Observe diagnostic info
        viewModel.getDiagnosticInfo().observe(getViewLifecycleOwner(), info -> {
            if (info != null && !info.isEmpty()) {
                resultTextView.append("\n\n" + info);
            }
        });
        
        return view;
    }
    
    private void connectToMySQL() {
        String host = hostEditText.getText().toString();
        int port;
        try {
            port = Integer.parseInt(portEditText.getText().toString());
        } catch (NumberFormatException e) {
            port = 3306; // Default MySQL port
        }
        String database = databaseEditText.getText().toString();
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        
        // Log connection details without showing the full password
        Log.d(TAG, "Connecting to MySQL - Host: " + host + ", Port: " + port + ", DB: " + database);
        Log.d(TAG, "Username: " + username + ", Password provided: " + (password != null && !password.isEmpty()));
        
        if (password == null || password.isEmpty()) {
            resultTextView.setText("Error: Password cannot be empty");
            return;
        }
        
        // Create connection info with specific MySQL database type (not MariaDB)
        ConnectionInfo connectionInfo = new ConnectionInfo(
                "MySQL Test",
                ConnectionInfo.DatabaseType.MYSQL, // Explicitly using MySQL driver, not MariaDB
                host,
                port,
                database,
                username,
                password
        );
        
        // Clear previous results
        resultTextView.setText("Connecting to MySQL at " + host + ":" + port + "...");
        
        // Connect using the ViewModel
        viewModel.connect(connectionInfo);
    }
    
    private void createTestTable() {
        String host = hostEditText.getText().toString();
        int port;
        try {
            port = Integer.parseInt(portEditText.getText().toString());
        } catch (NumberFormatException e) {
            port = 3306; // Default MySQL port
        }
        String database = databaseEditText.getText().toString();
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        
        // Log connection details without showing the full password
        Log.d(TAG, "Creating test table - Host: " + host + ", Port: " + port + ", DB: " + database);
        Log.d(TAG, "Username: " + username + ", Password provided: " + (password != null && !password.isEmpty()));
        
        if (password == null || password.isEmpty()) {
            resultTextView.append("\nError: Password cannot be empty");
            return;
        }
        
        resultTextView.append("\nCreating test table...");

        final int _port = port;

        // Use a background thread to avoid blocking the UI
        new Thread(() -> {
            try {
                final String result = DatabaseTestUtils.createMySqlTestTable(
                        host, _port, database, username, password);
                
                // Update UI on the main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        resultTextView.append("\n" + result);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating test table: " + e.getMessage(), e);
                
                // Update UI on the main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        resultTextView.append("\nError creating test table: " + e.getMessage());
                    });
                }
            }
        }).start();
    }
}