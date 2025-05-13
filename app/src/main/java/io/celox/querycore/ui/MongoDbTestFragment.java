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
import io.celox.querycore.viewmodel.DatabaseViewModel;

/**
 * Fragment for testing MongoDB connections with the correct settings
 */
public class MongoDbTestFragment extends Fragment {
    private static final String TAG = "MongoDbTestFragment";
    
    private EditText hostEditText;
    private EditText portEditText;
    private EditText databaseEditText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button connectButton;
    private Button diagnosticsButton;
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
        // Inflate the layout for this fragment - you'll need to create this layout
        View view = inflater.inflate(R.layout.fragment_mongo_db_test, container, false);
        
        // Initialize UI components
        hostEditText = view.findViewById(R.id.editTextHost);
        portEditText = view.findViewById(R.id.editTextPort);
        databaseEditText = view.findViewById(R.id.editTextDatabase);
        usernameEditText = view.findViewById(R.id.editTextUsername);
        passwordEditText = view.findViewById(R.id.editTextPassword);
        connectButton = view.findViewById(R.id.buttonConnect);
        diagnosticsButton = view.findViewById(R.id.buttonDiagnostics);
        resultTextView = view.findViewById(R.id.textViewResult);
        
        // Set default values
        hostEditText.setText("69.62.121.168");
        portEditText.setText("27017"); // Default MongoDB port
        databaseEditText.setText("admin");
        usernameEditText.setText("mongoAdmin");
        // Leave password empty in the UI - user will enter it
        
        // Set up listeners
        connectButton.setOnClickListener(v -> connectToMongoDB());
        diagnosticsButton.setOnClickListener(v -> runDiagnostics());
        
        // Observe connection result
        viewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected) {
                resultTextView.append("\nConnected successfully to MongoDB!");
                diagnosticsButton.setEnabled(true);
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
    
    private void connectToMongoDB() {
        String host = hostEditText.getText().toString();
        int port;
        try {
            port = Integer.parseInt(portEditText.getText().toString());
        } catch (NumberFormatException e) {
            port = 27017; // Default MongoDB port
        }
        String database = databaseEditText.getText().toString();
        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        
        // Create connection info
        ConnectionInfo connectionInfo = new ConnectionInfo(
                "MongoDB Test",
                ConnectionInfo.DatabaseType.MONGODB,
                host,
                port,
                database,
                username,
                password
        );
        
        // Clear previous results
        resultTextView.setText("Connecting to MongoDB at " + host + ":" + port + "...");
        
        // Connect using the ViewModel
        viewModel.connect(connectionInfo);
    }
    
    private void runDiagnostics() {
        resultTextView.append("\nRunning MongoDB diagnostics...");
        viewModel.runMongoDbDiagnostics();
    }
}