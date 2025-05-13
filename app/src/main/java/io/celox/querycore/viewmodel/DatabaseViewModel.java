package io.celox.querycore.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.celox.querycore.database.DatabaseService;
import io.celox.querycore.database.MongoDbDatabaseService;
import io.celox.querycore.database.MySqlDatabaseService;
import io.celox.querycore.models.ConnectionInfo;

public class DatabaseViewModel extends ViewModel {
    
    private DatabaseService databaseService;
    private ExecutorService executorService;
    
    private MutableLiveData<ConnectionInfo> currentConnection = new MutableLiveData<>();
    private MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<List<String>> databases = new MutableLiveData<>();
    private MutableLiveData<List<String>> tables = new MutableLiveData<>();
    private MutableLiveData<List<Map<String, Object>>> queryResults = new MutableLiveData<>();
    private MutableLiveData<Map<String, String>> tableStructure = new MutableLiveData<>();
    
    public DatabaseViewModel() {
        // Ensure we have enough threads for database operations
        executorService = Executors.newFixedThreadPool(4);
    }
    
    public void connect(ConnectionInfo connectionInfo) {
        executorService.execute(() -> {
            try {
                // Disconnect from previous connection if any
                if (databaseService != null && databaseService.isConnected()) {
                    databaseService.disconnect();
                }
                
                // Create appropriate database service
                switch (connectionInfo.getType()) {
                    case MYSQL:
                    case MARIADB:
                        databaseService = new MySqlDatabaseService();
                        break;
                    case MONGODB:
                        databaseService = new MongoDbDatabaseService();
                        Log.d("DatabaseViewModel", "Using MongoDB driver");
                        break;
                }
                
                // Connect to database
                databaseService.connect(connectionInfo);
                
                // Update LiveData
                currentConnection.postValue(connectionInfo);
                isConnected.postValue(true);
                errorMessage.postValue(null);
                
                // Load databases
                loadDatabases();
                
            } catch (Exception e) {
                isConnected.postValue(false);
                
                // Provide more detailed error information
                String errorMsg = "Connection failed: " + e.getMessage();
                
                // Log the exception with stacktrace
                Log.e("DatabaseViewModel", errorMsg, e);
                
                // Check for common MongoDB errors
                String exceptionMessage = e.getMessage() != null ? e.getMessage() : "Unknown error";
                if (exceptionMessage.contains("NoClassDefFoundError")) {
                    errorMsg = "Connection failed: MongoDB driver issue. Please check app configuration.";
                } else if (exceptionMessage.contains("Connection refused")) {
                    errorMsg = "Connection failed: MongoDB server refused connection. Check server address and port.";
                } else if (exceptionMessage.contains("timeout")) {
                    errorMsg = "Connection failed: Connection timeout. Check server availability.";
                } else if (exceptionMessage.contains("authentication")) {
                    errorMsg = "Connection failed: Authentication failed. Check username and password.";
                }
                
                errorMessage.postValue(errorMsg);
            }
        });
    }
    
    public void disconnect() {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    databaseService.disconnect();
                }
                isConnected.postValue(false);
                currentConnection.postValue(null);
                errorMessage.postValue(null);
            } catch (Exception e) {
                errorMessage.postValue("Disconnect failed: " + e.getMessage());
            }
        });
    }
    
    public void loadDatabases() {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    Log.d("DatabaseViewModel", "Loading databases from " + 
                          (currentConnection.getValue() != null ? currentConnection.getValue().getHost() : "unknown host"));
                    
                    List<String> dbs = databaseService.getDatabases();
                    
                    if (dbs != null && !dbs.isEmpty()) {
                        Log.d("DatabaseViewModel", "Successfully loaded " + dbs.size() + " databases");
                        databases.postValue(dbs);
                        errorMessage.postValue(null);
                    } else {
                        Log.w("DatabaseViewModel", "No databases found or access denied");
                        databases.postValue(new ArrayList<>());
                        errorMessage.postValue("No databases found or access denied");
                    }
                } else {
                    Log.w("DatabaseViewModel", "Cannot load databases: Not connected");
                    errorMessage.postValue("Cannot load databases: Not connected");
                }
            } catch (Exception e) {
                String errorMsg = "Failed to load databases: " + e.getMessage();
                Log.e("DatabaseViewModel", errorMsg, e);
                
                // Check for common error types
                Throwable rootCause = getRootCause(e);
                if (rootCause instanceof NoClassDefFoundError) {
                    NoClassDefFoundError noClassError = (NoClassDefFoundError) rootCause;
                    String missingClass = noClassError.getMessage();
                    
                    if (missingClass.contains("javax/security/sasl")) {
                        errorMsg = "Failed to load databases: SASL authentication not supported. Try a different authentication method.";
                        Log.e("DatabaseViewModel", "SASL authentication error: " + missingClass, noClassError);
                    } else {
                        errorMsg = "Failed to load databases: Missing class: " + missingClass;
                        Log.e("DatabaseViewModel", "NoClassDefFoundError details: " + missingClass, noClassError);
                    }
                } else if (rootCause instanceof ClassNotFoundException) {
                    String missingClass = rootCause.getMessage();
                    errorMsg = "Failed to load databases: Class not found: " + missingClass;
                    Log.e("DatabaseViewModel", "ClassNotFoundException details: " + missingClass, rootCause);
                } else if (rootCause instanceof SecurityException) {
                    errorMsg = "Failed to load databases: Security error: " + rootCause.getMessage();
                    Log.e("DatabaseViewModel", "SecurityException details", rootCause);
                }
                
                errorMessage.postValue(errorMsg);
            }
        });
    }
    
    public void loadTables(String database) {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    List<String> tableList = databaseService.getTables(database);
                    tables.postValue(tableList);
                    errorMessage.postValue(null);
                }
            } catch (Exception e) {
                errorMessage.postValue("Failed to load tables: " + e.getMessage());
            }
        });
    }
    
    public void executeQuery(String query) {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    List<Map<String, Object>> results = databaseService.executeQuery(query);
                    queryResults.postValue(results);
                    errorMessage.postValue(null);
                }
            } catch (Exception e) {
                errorMessage.postValue("Query execution failed: " + e.getMessage());
            }
        });
    }
    
    public void executeUpdate(String query) {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    int rowsAffected = databaseService.executeUpdate(query);
                    errorMessage.postValue("Update successful. Rows affected: " + rowsAffected);
                }
            } catch (Exception e) {
                errorMessage.postValue("Update failed: " + e.getMessage());
            }
        });
    }
    
    public void loadTableStructure(String table) {
        executorService.execute(() -> {
            try {
                if (databaseService != null && databaseService.isConnected()) {
                    Map<String, String> structure = databaseService.getTableStructure(table);
                    tableStructure.postValue(structure);
                    errorMessage.postValue(null);
                }
            } catch (Exception e) {
                errorMessage.postValue("Failed to load table structure: " + e.getMessage());
            }
        });
    }
    
    // LiveData getters
    public LiveData<ConnectionInfo> getCurrentConnection() {
        return currentConnection;
    }
    
    public LiveData<Boolean> getIsConnected() {
        return isConnected;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<List<String>> getDatabases() {
        return databases;
    }
    
    public LiveData<List<String>> getTables() {
        return tables;
    }
    
    public LiveData<List<Map<String, Object>>> getQueryResults() {
        return queryResults;
    }
    
    public LiveData<Map<String, String>> getTableStructure() {
        return tableStructure;
    }
    
    /**
     * Helper method to get the root cause of an exception
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Override
    protected void onCleared() {
        executorService.shutdown();
        try {
            if (databaseService != null && databaseService.isConnected()) {
                databaseService.disconnect();
            }
        } catch (Exception e) {
            errorMessage.postValue("Error disconnecting: " + e.getMessage());
        }
        super.onCleared();
    }
}