package io.celox.querycore.database;

import android.util.Log;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.celox.querycore.models.ConnectionInfo;

public class MySqlDatabaseService implements DatabaseService {
    
    private static final String TAG = "MySqlDatabaseService";
    private String connectionTrackingId;
    
    static {
        // Configure system-wide MariaDB/MySQL driver properties
        System.setProperty("org.mariadb.jdbc.logging.level", "DEBUG");
        System.setProperty("org.mariadb.jdbc.logging.enable", "true");
        
        // Disable DNS SRV lookup
        System.setProperty("org.mariadb.jdbc.enabledSslProtocolSuites", "TLSv1,TLSv1.1,TLSv1.2");
        
        // Sensible timeouts
        System.setProperty("org.mariadb.jdbc.connectTimeout", "20000");
        System.setProperty("org.mariadb.jdbc.socketTimeout", "30000");
        
        // Reduce idle connection lifetime for mobile
        System.setProperty("org.mariadb.jdbc.idleTimeout", "60000");
        
        Log.i("MySqlDatabaseService", "MySQL/MariaDB JDBC driver system properties configured");
    }
    
    private Connection connection;
    private ConnectionInfo connectionInfo;
    
    /**
     * Detects specific error types from MySQL/MariaDB exceptions and provides user-friendly error messages
     * @param e The exception to analyze
     * @return A user-friendly error message, or null if no specific error type is detected
     */
    private String detectSpecificError(SQLException e) {
        if (e == null) return null;
        
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();
        String message = e.getMessage();
        
        Log.d(TAG, "SQL error details - Error code: " + errorCode + ", SQLState: " + sqlState);
        
        // MySQL error codes: https://dev.mysql.com/doc/mysql-errors/8.0/en/server-error-reference.html
        // MariaDB error codes: https://mariadb.com/kb/en/mariadb-error-codes/
        
        // Authentication errors
        if (errorCode == 1045 || "28000".equals(sqlState)) {
            return "Authentication failed: Invalid username or password. Please check your credentials and try again.";
        }
        
        // Host-based permissions
        if (errorCode == 1130 || "HY000".equals(sqlState) && message.contains("host")) {
            return "Access denied: Your client IP address is not allowed to connect to this MySQL server. " +
                   "Please contact your database administrator to grant access from your current location.";
        }
        
        // Connection refused - common networking issue
        if (errorCode == 0 && message.contains("Connection refused")) {
            return "Connection refused: The database server actively refused the connection. " +
                   "This usually means the server is not running or a firewall is blocking the connection. " +
                   "Please verify the server is running and accessible from your current network.";
        }
        
        // Unknown database
        if (errorCode == 1049 || ("42000".equals(sqlState) && message.contains("Unknown database"))) {
            return "Unknown database: The specified database does not exist. " +
                   "Please check that you've entered the correct database name and that it exists on the server.";
        }
        
        // Connection timeout
        if (message.contains("Communication link failure") || message.contains("Socket timeout") || 
            message.contains("Read timed out")) {
            return "Connection timeout: The connection to the database server timed out. " +
                   "This could be due to network issues, high server load, or firewall restrictions. " +
                   "Please try again or check your network connection.";
        }
        
        // Wrong port (potentially MongoDB)
        if (connectionInfo.getPort() == 27017) {
            return "Connection error: You're trying to connect to port 27017, which is typically used for MongoDB, " +
                   "not MySQL/MariaDB. MySQL/MariaDB typically uses port 3306. Please check your connection settings.";
        }
        
        return null;
    }
    
    @Override
    public void connect(ConnectionInfo connectionInfo) throws Exception {
        // Start connection tracking
        connectionTrackingId = ConnectionTracker.getInstance().trackConnectionStart(connectionInfo, "MySQL/MariaDB");
        
        // Log connection attempt with detailed information
        Log.i(TAG, "-------------- MYSQL/MARIADB CONNECTION ATTEMPT --------------");
        Log.i(TAG, "Connection ID: " + connectionTrackingId);
        Log.i(TAG, "Host: " + connectionInfo.getHost());
        Log.i(TAG, "Port: " + connectionInfo.getPort());
        Log.i(TAG, "Database: " + connectionInfo.getDatabase());
        Log.i(TAG, "Username: " + connectionInfo.getUsername());
        Log.i(TAG, "Auth: " + (connectionInfo.getUsername() != null && !connectionInfo.getUsername().isEmpty() ? "Yes" : "No"));
        Log.i(TAG, "Android API Level: " + android.os.Build.VERSION.SDK_INT);
        Log.i(TAG, "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
        
        // Log system properties
        Log.d(TAG, "System Properties:");
        Log.d(TAG, " - java.vm.version: " + System.getProperty("java.vm.version", "unknown"));
        Log.d(TAG, " - java.version: " + System.getProperty("java.version", "unknown"));
        Log.d(TAG, " - os.name: " + System.getProperty("os.name", "unknown"));
        Log.d(TAG, " - os.arch: " + System.getProperty("os.arch", "unknown"));
        
        // Validate port number - MySQL typically uses 3306
        int port = connectionInfo.getPort();
        if (port == 27017) {
            Log.w(TAG, "Warning: Port 27017 is typically used for MongoDB, not MySQL/MariaDB");
            String errorMsg = "Error: Port 27017 is typically used for MongoDB, not MySQL/MariaDB. " +
                  "Please check your connection settings. MySQL/MariaDB typically uses port 3306.";
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "WRONG_PORT");
            throw new Exception(errorMsg);
        } else if (port != 3306 && port != 3307) {
            // Log a warning but continue - could be non-standard port
            Log.w(TAG, "Warning: Using non-standard MySQL port: " + port + ". Standard port is 3306.");
        }
        
        // Driver registration using error handling approach to prevent crashes
        boolean driverLoaded = false;
        
        // Dynamic driver loading with proper error handling
        try {
            Log.d(TAG, "Attempting to load MariaDB JDBC driver");
            // We need to use the MariaDB driver directly without using DriverAction interface
            // which is not available on all Android versions
            Class<?> driverClass = Class.forName("org.mariadb.jdbc.Driver", true, 
                                                getClass().getClassLoader());
            driverLoaded = true;
            Log.i(TAG, "MariaDB JDBC driver loaded successfully");
            
            // Log driver version if available
            try {
                Object version = driverClass.getField("VERSION").get(null);
                Log.i(TAG, "MariaDB JDBC driver version: " + version);
            } catch (Exception ex) {
                Log.d(TAG, "Could not retrieve driver version: " + ex.getMessage());
            }
            
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // If we get here, the driver wasn't found or couldn't be initialized
            Log.e(TAG, "Failed to load MariaDB JDBC driver: " + e.getMessage(), e);
            String errorMsg = "Cannot load MariaDB JDBC driver for MySQL connection. " +
                "Please check your connection settings or try updating the app.";
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "DRIVER_LOAD_FAILED");
            throw new Exception(errorMsg, e);
        }
        
        if (!driverLoaded) {
            Log.e(TAG, "Failed to initialize MySQL/MariaDB driver");
            String errorMsg = "Failed to initialize MySQL/MariaDB driver";
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "DRIVER_INIT_FAILED");
            throw new Exception(errorMsg);
        }
        
        // Create connection URL with advanced options for better error handling
        String baseUrl;
        
        // Build URL with enhanced parameters for diagnostics
        baseUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&connectTimeout=20000" +
                "&socketTimeout=30000&allowPublicKeyRetrieval=true&useCompression=true" +
                "&characterEncoding=utf8&enablePacketDebug=true",
                connectionInfo.getHost(),
                connectionInfo.getPort(),
                connectionInfo.getDatabase());
        
        Log.d(TAG, "Connection URL (without credentials): " + baseUrl);
        
        // Create a properties object with enhanced options
        Properties props = new Properties();
        props.setProperty("user", connectionInfo.getUsername());
        props.setProperty("password", connectionInfo.getPassword());
        props.setProperty("connectTimeout", "20000"); // 20 seconds
        props.setProperty("socketTimeout", "30000"); // 30 seconds
        props.setProperty("loginTimeout", "20000"); // 20 seconds
        props.setProperty("tcpKeepAlive", "true");
        props.setProperty("useUnicode", "true");
        props.setProperty("characterEncoding", "UTF-8");
        props.setProperty("allowMultiQueries", "false"); // Security measure
        props.setProperty("usePipelineAuth", "false"); // More compatible
        
        Log.d(TAG, "Connection properties configured");
        
        // Connect with enhanced error handling
        try {
            Log.d(TAG, "Attempting to establish MariaDB connection");
            this.connection = DriverManager.getConnection(baseUrl, props);
            
            // Test connection with a simple query
            try (Statement stmt = connection.createStatement()) {
                Log.d(TAG, "Testing connection with 'SELECT 1'");
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    Log.i(TAG, "Connection test successful: " + rs.getInt(1));
                }
            }
            
            // Log connection success details
            Log.i(TAG, "Successfully connected to MySQL/MariaDB database");
            String dbDetails = "";
            if (connection.getMetaData() != null) {
                dbDetails = connection.getMetaData().getDatabaseProductName() + " " +
                      connection.getMetaData().getDatabaseProductVersion();
                Log.i(TAG, "Connected to: " + dbDetails);
                Log.i(TAG, "JDBC Driver: " + connection.getMetaData().getDriverName() + " " +
                      connection.getMetaData().getDriverVersion());
            }
            
            this.connectionInfo = connectionInfo;
            
            // Record successful connection
            ConnectionTracker.getInstance().trackConnectionSuccess(connectionTrackingId, dbDetails);
            
        } catch (SQLException e) {
            Log.e(TAG, "SQL exception during connection: " + e.getMessage(), e);
            
            // Get more detailed nested exceptions if available
            SQLException nextEx = e.getNextException();
            if (nextEx != null) {
                Log.e(TAG, "Nested SQL exception: " + nextEx.getMessage(), nextEx);
            }
            
            // Detect specific error types for user-friendly messages
            String specificError = detectSpecificError(e);
            if (specificError != null) {
                Log.e(TAG, "Specific error detected: " + specificError);
                ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, specificError, "SQL_ERROR_" + e.getErrorCode());
                throw new Exception(specificError, e);
            }
            
            // Generic error with more details
            String errorMsg = "Failed to connect to MySQL/MariaDB database (" + e.getErrorCode() + "): " + e.getMessage();
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "SQL_ERROR");
            throw new Exception(errorMsg, e);
        } catch (Exception e) {
            Log.e(TAG, "Non-SQL exception during connection: " + e.getMessage(), e);
            String errorMsg = "Unexpected error connecting to MySQL/MariaDB: " + e.getMessage();
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "UNEXPECTED_ERROR");
            throw new Exception(errorMsg, e);
        }
    }
    
    @Override
    public void disconnect() throws Exception {
        if (connection != null && !connection.isClosed()) {
            // Track disconnection if we had a successful connection
            if (connectionTrackingId != null) {
                ConnectionTracker.getInstance().trackDisconnection(connectionTrackingId);
                Log.i(TAG, "Disconnecting MySQL/MariaDB connection: " + connectionTrackingId);
            }
            
            Log.d(TAG, "Disconnecting from MySQL/MariaDB database");
            connection.close();
            connection = null;
            Log.i(TAG, "Successfully disconnected from MySQL/MariaDB database");
        } else {
            Log.d(TAG, "Disconnect called but no active connection exists");
        }
    }
    
    @Override
    public boolean isConnected() {
        try {
            if (connection == null) {
                Log.d(TAG, "Connection check: connection is null");
                return false;
            }
            
            if (connection.isClosed()) {
                Log.d(TAG, "Connection check: connection is closed");
                return false;
            }
            
            // Test connection with a lightweight ping
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("/* ping */ SELECT 1");
                Log.d(TAG, "Connection check: connection is valid and working");
                return true;
            }
            
        } catch (SQLException e) {
            Log.e(TAG, "Connection check failed: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in connection check: " + e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public List<String> getDatabases() throws Exception {
        List<String> databases = new ArrayList<>();
        
        Log.d(TAG, "Retrieving list of databases");
        try (ResultSet resultSet = connection.getMetaData().getCatalogs()) {
            while (resultSet.next()) {
                String dbName = resultSet.getString(1);
                databases.add(dbName);
                Log.d(TAG, "Found database: " + dbName);
            }
            Log.i(TAG, "Successfully retrieved " + databases.size() + " databases");
        } catch (SQLException e) {
            Log.e(TAG, "Error retrieving databases: " + e.getMessage(), e);
            
            // Check for permission issues
            if (e.getErrorCode() == 1044 || e.getMessage().contains("Access denied")) {
                throw new Exception("Permission denied: Your database user account doesn't have permission to view all databases. " +
                      "You may need SHOW DATABASES privilege.", e);
            }
            
            throw new Exception("Failed to retrieve databases: " + e.getMessage(), e);
        }
        
        return databases;
    }
    
    @Override
    public List<String> getTables(String database) throws Exception {
        List<String> tables = new ArrayList<>();
        
        Log.d(TAG, "Retrieving tables for database: " + database);
        try {
            // Switch to the requested database for compatibility
            try (Statement stmt = connection.createStatement()) {
                Log.d(TAG, "Switching to database: " + database);
                stmt.execute("USE " + database);
            }
            
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(database, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString("TABLE_NAME");
                    tables.add(tableName);
                    Log.d(TAG, "Found table: " + tableName);
                }
            }
            
            Log.i(TAG, "Successfully retrieved " + tables.size() + " tables from database " + database);
            
        } catch (SQLException e) {
            Log.e(TAG, "Error retrieving tables for database '" + database + "': " + e.getMessage(), e);
            
            // Check for permission or database not exists issues
            if (e.getErrorCode() == 1044 || e.getMessage().contains("Access denied")) {
                throw new Exception("Permission denied: Your database user account doesn't have permission to view tables in '" + 
                      database + "'. You need SELECT privilege.", e);
            } else if (e.getErrorCode() == 1049 || e.getMessage().contains("Unknown database")) {
                throw new Exception("Database '" + database + "' does not exist. Please check the database name.", e);
            }
            
            throw new Exception("Failed to retrieve tables from database '" + database + "': " + e.getMessage(), e);
        }
        
        return tables;
    }
    
    @Override
    public List<Map<String, Object>> executeQuery(String query) throws Exception {
        List<Map<String, Object>> resultList = new ArrayList<>();
        
        Log.d(TAG, "Executing query: " + query.trim());
        long startTime = System.currentTimeMillis();
        
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Log column information
            StringBuilder columnInfo = new StringBuilder("Query result columns: ");
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) columnInfo.append(", ");
                columnInfo.append(metaData.getColumnName(i))
                          .append(" (").append(metaData.getColumnTypeName(i)).append(")");
            }
            Log.d(TAG, columnInfo.toString());
            
            int rowCount = 0;
            while (resultSet.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    row.put(columnName, value);
                }
                resultList.add(row);
                rowCount++;
                
                // Log sample rows (first few rows only)
                if (rowCount <= 3) {
                    Log.d(TAG, "Sample row " + rowCount + ": " + row);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, "Query executed successfully. Retrieved " + rowCount + " rows in " + duration + "ms");
            
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Error executing query (" + duration + "ms): " + e.getMessage(), e);
            
            // Detect specific SQL errors
            if (e.getErrorCode() == 1064) { // Syntax error
                throw new Exception("SQL syntax error: " + e.getMessage(), e);
            } else if (e.getErrorCode() == 1146) { // Table doesn't exist
                throw new Exception("Table not found: " + e.getMessage(), e);
            } else if (e.getErrorCode() == 1142) { // Permission issue
                throw new Exception("Permission denied: You don't have sufficient privileges to execute this query.", e);
            }
            
            throw new Exception("Query execution failed: " + e.getMessage(), e);
        }
        
        return resultList;
    }
    
    @Override
    public int executeUpdate(String query) throws Exception {
        Log.d(TAG, "Executing update query: " + query.trim());
        long startTime = System.currentTimeMillis();
        
        try (Statement statement = connection.createStatement()) {
            int rowsAffected = statement.executeUpdate(query);
            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, "Update executed successfully. Affected " + rowsAffected + " rows in " + duration + "ms");
            return rowsAffected;
            
        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Error executing update (" + duration + "ms): " + e.getMessage(), e);
            
            // Detect specific SQL errors for better error messages
            if (e.getErrorCode() == 1064) { // Syntax error
                throw new Exception("SQL syntax error: " + e.getMessage(), e);
            } else if (e.getErrorCode() == 1146) { // Table doesn't exist
                throw new Exception("Table not found: " + e.getMessage(), e);
            } else if (e.getErrorCode() == 1142) { // Permission issue
                throw new Exception("Permission denied: You don't have sufficient privileges to execute this update.", e);
            } else if (e.getErrorCode() == 1451 || e.getErrorCode() == 1452) { // Foreign key constraint
                throw new Exception("Foreign key constraint violation: " + e.getMessage(), e);
            } else if (e.getErrorCode() == 1062) { // Duplicate entry
                throw new Exception("Duplicate entry: " + e.getMessage(), e);
            }
            
            throw new Exception("Update execution failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public Map<String, String> getTableStructure(String table) throws Exception {
        Map<String, String> structure = new HashMap<>();
        
        Log.d(TAG, "Retrieving structure for table: " + table);
        long startTime = System.currentTimeMillis();
        
        String query = "DESCRIBE " + table;
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            
            while (resultSet.next()) {
                String fieldName = resultSet.getString("Field");
                String fieldType = resultSet.getString("Type");
                String isNull = resultSet.getString("Null");
                String key = resultSet.getString("Key");
                
                structure.put(fieldName, fieldType);
                
                // Log detailed column info
                Log.d(TAG, "Column: " + fieldName + ", Type: " + fieldType + 
                      ", Nullable: " + isNull + ", Key: " + key);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            Log.i(TAG, "Retrieved structure for table '" + table + "' with " + 
                  structure.size() + " columns in " + duration + "ms");
            
        } catch (SQLException e) {
            Log.e(TAG, "Error retrieving table structure: " + e.getMessage(), e);
            
            // Detect specific errors
            if (e.getErrorCode() == 1146) { // Table doesn't exist
                throw new Exception("Table '" + table + "' does not exist. Please check the table name.", e);
            } else if (e.getErrorCode() == 1142) { // Permission issue
                throw new Exception("Permission denied: You don't have sufficient privileges to view the structure of table '" + 
                      table + "'.", e);
            }
            
            throw new Exception("Failed to retrieve structure for table '" + table + "': " + e.getMessage(), e);
        }
        
        return structure;
    }
}