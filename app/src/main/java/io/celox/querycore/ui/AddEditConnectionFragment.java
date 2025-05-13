package io.celox.querycore.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;

import io.celox.querycore.R;
import io.celox.querycore.data.ConnectionEntity;
import io.celox.querycore.models.ConnectionInfo;
import io.celox.querycore.viewmodel.ConnectionViewModel;
import io.celox.querycore.viewmodel.DatabaseViewModel;

public class AddEditConnectionFragment extends Fragment {
    
    private ConnectionViewModel connectionViewModel;
    private DatabaseViewModel databaseViewModel;
    
    private TextInputEditText editTextName;
    private AutoCompleteTextView dropdownType;
    private TextInputEditText editTextHost;
    private TextInputEditText editTextPort;
    private TextInputEditText editTextDatabase;
    private TextInputEditText editTextUsername;
    private TextInputEditText editTextPassword;
    private Button buttonTest;
    private Button buttonSave;
    private Button buttonDelete;
    
    private int connectionId = -1;
    private ConnectionEntity currentConnection;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_edit_connection, container, false);
        
        // Initialize views
        editTextName = view.findViewById(R.id.edit_text_connection_name);
        dropdownType = view.findViewById(R.id.dropdown_connection_type);
        editTextHost = view.findViewById(R.id.edit_text_host);
        editTextPort = view.findViewById(R.id.edit_text_port);
        editTextDatabase = view.findViewById(R.id.edit_text_database);
        editTextUsername = view.findViewById(R.id.edit_text_username);
        editTextPassword = view.findViewById(R.id.edit_text_password);
        buttonTest = view.findViewById(R.id.button_test_connection);
        buttonSave = view.findViewById(R.id.button_save);
        buttonDelete = view.findViewById(R.id.button_delete);
        
        // Set up database type dropdown
        String[] databaseTypes = new String[]{
                getString(R.string.type_mysql),
                getString(R.string.type_mariadb),
                getString(R.string.type_mongodb)
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                databaseTypes
        );
        
        dropdownType.setAdapter(adapter);
        
        // Set up default ports
        dropdownType.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = parent.getItemAtPosition(position).toString();
            if (selected.equals(getString(R.string.type_mysql)) || selected.equals(getString(R.string.type_mariadb))) {
                editTextPort.setText("3306");
            } else if (selected.equals(getString(R.string.type_mongodb))) {
                editTextPort.setText("27017");
            }
        });
        
        // Set click listeners
        buttonTest.setOnClickListener(v -> testConnection());
        buttonSave.setOnClickListener(v -> saveConnection());
        buttonDelete.setOnClickListener(v -> deleteConnection());
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize ViewModels
        connectionViewModel = new ViewModelProvider(requireActivity()).get(ConnectionViewModel.class);
        databaseViewModel = new ViewModelProvider(requireActivity()).get(DatabaseViewModel.class);
        
        // Get arguments
        if (getArguments() != null) {
            connectionId = getArguments().getInt("connectionId", -1);
        }
        
        // If editing an existing connection
        if (connectionId != -1) {
            requireActivity().setTitle(R.string.edit_connection);
            buttonDelete.setVisibility(View.VISIBLE);
            
            connectionViewModel.getConnectionById(connectionId).observe(getViewLifecycleOwner(), connection -> {
                if (connection != null) {
                    currentConnection = connection;
                    populateFields(connection);
                }
            });
        } else {
            requireActivity().setTitle(R.string.add_connection);
            buttonDelete.setVisibility(View.GONE);
            
            // Set default values for a new connection
            dropdownType.setText(getString(R.string.type_mysql), false);
            editTextHost.setText("localhost");
            editTextPort.setText("3306");
        }
        
        // Observe database connection status
        databaseViewModel.getIsConnected().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected) {
                Toast.makeText(requireContext(), R.string.connection_successful, Toast.LENGTH_SHORT).show();
            }
        });
        
        databaseViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void populateFields(ConnectionEntity connection) {
        editTextName.setText(connection.getName());
        dropdownType.setText(connection.getType(), false);
        editTextHost.setText(connection.getHost());
        editTextPort.setText(String.valueOf(connection.getPort()));
        editTextDatabase.setText(connection.getDatabase());
        editTextUsername.setText(connection.getUsername());
        editTextPassword.setText(connection.getPassword());
    }
    
    private void testConnection() {
        if (validateInputs()) {
            ConnectionInfo connectionInfo = getConnectionInfoFromInputs();
            databaseViewModel.connect(connectionInfo);
        }
    }
    
    private void saveConnection() {
        if (validateInputs()) {
            ConnectionEntity connection = getConnectionEntityFromInputs();
            
            if (connectionId != -1) {
                // Update existing connection
                connection.setId(connectionId);
                connectionViewModel.update(connection);
            } else {
                // Add new connection
                connectionViewModel.insert(connection);
            }
            
            Navigation.findNavController(requireView()).navigateUp();
        }
    }
    
    private void deleteConnection() {
        if (currentConnection != null) {
            connectionViewModel.delete(currentConnection);
            Navigation.findNavController(requireView()).navigateUp();
        }
    }
    
    private boolean validateInputs() {
        boolean isValid = true;
        
        if (editTextName.getText().toString().trim().isEmpty()) {
            editTextName.setError("Name is required");
            isValid = false;
        }
        
        if (dropdownType.getText().toString().trim().isEmpty()) {
            dropdownType.setError("Database type is required");
            isValid = false;
        }
        
        if (editTextHost.getText().toString().trim().isEmpty()) {
            editTextHost.setError("Host is required");
            isValid = false;
        }
        
        if (editTextPort.getText().toString().trim().isEmpty()) {
            editTextPort.setError("Port is required");
            isValid = false;
        }
        
        return isValid;
    }
    
    private ConnectionInfo getConnectionInfoFromInputs() {
        String name = editTextName.getText().toString().trim();
        String typeString = dropdownType.getText().toString().trim();
        String host = editTextHost.getText().toString().trim();
        int port = Integer.parseInt(editTextPort.getText().toString().trim());
        String database = editTextDatabase.getText().toString().trim();
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        
        ConnectionInfo.DatabaseType type;
        if (typeString.equals(getString(R.string.type_mysql))) {
            type = ConnectionInfo.DatabaseType.MYSQL;
        } else if (typeString.equals(getString(R.string.type_mariadb))) {
            type = ConnectionInfo.DatabaseType.MARIADB;
        } else {
            type = ConnectionInfo.DatabaseType.MONGODB;
        }
        
        return new ConnectionInfo(name, type, host, port, database, username, password);
    }
    
    private ConnectionEntity getConnectionEntityFromInputs() {
        String name = editTextName.getText().toString().trim();
        String typeString = dropdownType.getText().toString().trim();
        String host = editTextHost.getText().toString().trim();
        int port = Integer.parseInt(editTextPort.getText().toString().trim());
        String database = editTextDatabase.getText().toString().trim();
        String username = editTextUsername.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        
        String type;
        if (typeString.equals(getString(R.string.type_mysql))) {
            type = ConnectionInfo.DatabaseType.MYSQL.name();
        } else if (typeString.equals(getString(R.string.type_mariadb))) {
            type = ConnectionInfo.DatabaseType.MARIADB.name();
        } else {
            type = ConnectionInfo.DatabaseType.MONGODB.name();
        }
        
        return new ConnectionEntity(name, type, host, port, database, username, password);
    }
}