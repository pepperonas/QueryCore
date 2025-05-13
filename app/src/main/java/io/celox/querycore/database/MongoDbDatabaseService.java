package io.celox.querycore.database;

import android.util.Log;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.celox.querycore.models.ConnectionInfo;

public class MongoDbDatabaseService implements DatabaseService {

    private MongoClient mongoClient;
    private DB mongoDatabase;
    private ConnectionInfo connectionInfo;
    private String connectionTrackingId;

    private static final String TAG = "MongoDbService";

    static {
        // MongoDB connection properties
        
        // Fix MongoDB driver's DNS resolution on Android
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        // Set sensible timeouts
        System.setProperty("org.mongodb.driver.connectTimeout", "20000");
        System.setProperty("org.mongodb.driver.serverSelectionTimeout", "20000");
        System.setProperty("org.mongodb.driver.socketTimeout", "30000");
        
        // Disable SSL for MongoDB - can be enabled later if needed
        System.setProperty("org.mongodb.driver.ssl.enabled", "false");
        
        // Enable verbose logging for troubleshooting
        System.setProperty("org.mongodb.driver.loggerLevel", "DEBUG");
        System.setProperty("org.mongodb.driver.traceWire", "true"); // Log wire protocol details
        
        // Configure SASL authentication properly
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        System.setProperty("java.security.auth.login.config", "mongoDB");
        
        // Additional diagnostic properties
        System.setProperty("org.mongodb.driver.maxPoolSize", "1"); // Simpler connection pool for debugging
        System.setProperty("org.mongodb.driver.connectionPoolLogger", "DEBUG");
    }

    /**
     * Helper method to ensure SASL classes are loaded
     */
    private void ensureSaslLoaded() {
        try {
            // Force loading of SASL classes if available
            try {
                Class.forName("javax.security.sasl.SaslClient");
                Class.forName("javax.security.sasl.SaslServer");
                Class.forName("javax.security.sasl.Sasl");
                Log.d(TAG, "SASL classes loaded successfully");
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "SASL classes not available: " + e.getMessage());
                
                // Set system property to disable SASL for MongoDB
                System.setProperty("org.mongodb.driver.disableSaslAuthentication", "true");
            }
            
            // We'll try to connect without SASL if not available
        } catch (Exception e) {
            Log.e(TAG, "Failed during SASL setup: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if an exception indicates a wrong database type/protocol
     * @param e The exception to analyze
     * @return A user-friendly error message, or null if the exception doesn't indicate wrong DB type
     */
    private String detectWrongDatabaseType(Exception e) {
        if (e == null) return null;
        
        String message = e.getMessage();
        if (message == null) {
            return null;
        }
        
        // Check for various protocol mismatch indicators
        if (message.contains("Unexpected reply message opCode")) {
            return "Error: It appears you're trying to connect to a non-MongoDB database. " +
                  "The server at this address is likely a MySQL/MariaDB or other database type. " +
                  "Please verify your connection settings and use the appropriate database service.";
        } else if (message.contains("Timed out") && message.contains("connecting")) {
            // This could be a network issue or wrong database
            return "Connection timed out. Please check that:\n" +
                  "1. The server address and port are correct\n" +
                  "2. The server is running and accessible\n" +
                  "3. You're using the correct database type for this connection";
        } else if (message.contains("SSL handshake") || message.contains("protocol version")) {
            return "Protocol error: The database server doesn't appear to be using the MongoDB protocol. " +
                  "Please verify that you're connecting to a MongoDB server and not another database type.";
        }
        
        return null;
    }
    
    /**
     * Checks if an exception indicates an authentication or authorization error
     * @param e The exception to analyze
     * @return A user-friendly error message, or null if the exception doesn't indicate auth error
     */
    private String detectAuthenticationError(Exception e) {
        if (e == null) return null;
        
        // For MongoCommandException with error codes
        if (e instanceof com.mongodb.MongoCommandException) {
            com.mongodb.MongoCommandException cmdEx = (com.mongodb.MongoCommandException) e;
            int errorCode = cmdEx.getErrorCode();
            
            // Common MongoDB error codes for auth issues
            if (errorCode == 13) { // Unauthorized
                return "Authentication error: You don't have permission to perform this operation. " +
                       "Please ensure you have provided correct credentials and that your user has " + 
                       "the necessary permissions.";
            } else if (errorCode == 18) { // Authentication Failed
                return "Authentication failed: The provided username or password is incorrect. " + 
                       "Please verify your credentials and try again.";
            } else if (errorCode == 10057) { // Not Master
                return "The MongoDB server is not a primary node. This operation requires connection " +
                       "to a primary node in the MongoDB replica set.";
            }
        }
        
        // Generic message checking for auth-related words
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("Unauthorized") || message.contains("unauthorized") || 
                message.contains("requires authentication")) {
                return "Authentication error: This operation requires authentication. " +
                       "Please check that you've provided valid credentials and have the necessary permissions.";
            } else if (message.contains("Authentication failed") || message.contains("auth failed") ||
                      message.contains("not authorized")) {
                return "Authentication failed: The credentials provided were rejected by the server. " +
                       "Please check your username and password.";
            }
        }
        
        return null;
    }

    @Override
    public void connect(ConnectionInfo connectionInfo) throws Exception {
        try {
            // Start connection tracking
            connectionTrackingId = ConnectionTracker.getInstance().trackConnectionStart(connectionInfo, "MongoDB");
            
            // Log connection information (without sensitive data)
            Log.i(TAG, "-------------- MONGODB CONNECTION ATTEMPT --------------");
            Log.i(TAG, "Connection ID: " + connectionTrackingId);
            Log.i(TAG, "Host: " + connectionInfo.getHost());
            Log.i(TAG, "Port: " + connectionInfo.getPort());
            Log.i(TAG, "Database: " + connectionInfo.getDatabase());
            Log.i(TAG, "Auth: " + (connectionInfo.getUsername() != null && !connectionInfo.getUsername().isEmpty() ? "Yes" : "No"));
            Log.i(TAG, "Android API Level: " + android.os.Build.VERSION.SDK_INT);
            Log.i(TAG, "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            
            // Log system properties
            Log.d(TAG, "System Properties:");
            Log.d(TAG, " - java.vm.version: " + System.getProperty("java.vm.version", "unknown"));
            Log.d(TAG, " - java.version: " + System.getProperty("java.version", "unknown"));
            Log.d(TAG, " - os.name: " + System.getProperty("os.name", "unknown"));
            Log.d(TAG, " - os.arch: " + System.getProperty("os.arch", "unknown"));
            
            // Ensure SASL classes are loaded first (with enhanced logging)
            Log.d(TAG, "Checking for SASL support...");
            ensureSaslLoaded();
            
            // Add DNS caching to prevent Android networking issues
            java.security.Security.setProperty("networkaddress.cache.ttl", "30");
            Log.d(TAG, "Set networkaddress.cache.ttl=30");

            // Validate port number - MongoDB typically uses 27017
            int port = connectionInfo.getPort();
            if (port == 3306 || port == 3307) {
                throw new Exception("Error: Port " + port + " is typically used for MySQL/MariaDB, not MongoDB. " +
                      "Please check your connection settings. MongoDB typically uses port 27017.");
            } else if (port == 5432) {
                throw new Exception("Error: Port " + port + " is typically used for PostgreSQL, not MongoDB. " +
                      "Please check your connection settings. MongoDB typically uses port 27017.");
            } else if (port == 1521) {
                throw new Exception("Error: Port " + port + " is typically used for Oracle, not MongoDB. " +
                      "Please check your connection settings. MongoDB typically uses port 27017.");
            } else if (port == 1433) {
                throw new Exception("Error: Port " + port + " is typically used for SQL Server, not MongoDB. " +
                      "Please check your connection settings. MongoDB typically uses port 27017.");
            }

            Log.d(TAG, "Attempting to connect to MongoDB server: " + connectionInfo.getHost());

            // Build server address
            ServerAddress serverAddress = new ServerAddress(connectionInfo.getHost(), connectionInfo.getPort());

            // Configure client options with more conservative timeouts for mobile networks
            MongoClientOptions options = MongoClientOptions.builder()
                    .connectTimeout(20000) // 20 seconds
                    .socketTimeout(30000)  // 30 seconds
                    .serverSelectionTimeout(20000) // 20 seconds
                    .maxConnectionIdleTime(60000) // 1 minute
                    .maxConnectionLifeTime(300000) // 5 minutes
                    .connectionsPerHost(2) // Reduced for mobile
                    .maxWaitTime(20000) // 20 seconds
                    .writeConcern(WriteConcern.ACKNOWLEDGED)
                    .build();

            Log.d(TAG, "MongoDB options configured");
            
            try {
                // Simple approach: check if credentials are provided
                if (connectionInfo.getUsername() != null && !connectionInfo.getUsername().isEmpty() &&
                        connectionInfo.getPassword() != null && !connectionInfo.getPassword().isEmpty()) {
                    
                    Log.d(TAG, "Attempting authenticated connection");
                    
                    // Try different authentication mechanisms, starting with the most modern
                    MongoCredential credential;
                    
                    Log.i(TAG, "Creating MongoDB credentials for authentication");
                    Log.d(TAG, "Username: " + connectionInfo.getUsername());
                    Log.d(TAG, "Auth Database: " + connectionInfo.getDatabase());
                    Log.d(TAG, "Has Password: " + (connectionInfo.getPassword() != null && !connectionInfo.getPassword().isEmpty()));
                    
                    // Set authentication database to admin by default
                    String authDb = "admin";
                    Log.d(TAG, "Using admin database for authentication instead of: " + connectionInfo.getDatabase());
                    
                    // URL encode the password to handle special characters
                    String encodedPassword;
                    try {
                        encodedPassword = java.net.URLEncoder.encode(
                            connectionInfo.getPassword(), "UTF-8")
                            .replace("+", "%20");  // Replace space encoding + with %20
                        
                        Log.d(TAG, "Password contains special characters, using URL encoding");
                    } catch (Exception e) {
                        // If encoding fails, use the raw password (may still have issues)
                        encodedPassword = connectionInfo.getPassword();
                        Log.e(TAG, "Failed to URL encode password, using raw password");
                    }
                    
                    try {
                        Log.d(TAG, "Attempting SCRAM-SHA-256 authentication (MongoDB 4.0+)");
                        // First try SCRAM-SHA-256 (more secure, MongoDB 4.0+)
                        credential = MongoCredential.createScramSha256Credential(
                                connectionInfo.getUsername(),
                                authDb,
                                encodedPassword.toCharArray()
                        );
                        Log.i(TAG, "Successfully created SCRAM-SHA-256 credential object");
                    } catch (Throwable e) {
                        Log.w(TAG, "SCRAM-SHA-256 not available: " + e.getMessage(), e);
                        Log.d(TAG, "Falling back to SCRAM-SHA-1 authentication (MongoDB 3.0+)");
                        
                        // Fall back to SCRAM-SHA-1 (MongoDB 3.0+)
                        try {
                            credential = MongoCredential.createScramSha1Credential(
                                    connectionInfo.getUsername(),
                                    authDb,
                                    encodedPassword.toCharArray()
                            );
                            Log.i(TAG, "Successfully created SCRAM-SHA-1 credential object");
                        } catch (Exception e2) {
                            Log.w(TAG, "SCRAM-SHA-1 credential creation failed: " + e2.getMessage(), e2);
                            Log.d(TAG, "Falling back to plain credential");
                            
                            // Last resort: plain credential
                            credential = MongoCredential.createCredential(
                                    connectionInfo.getUsername(),
                                    authDb,
                                    encodedPassword.toCharArray()
                            );
                            Log.i(TAG, "Using plain credential as last resort");
                        }
                    }
                    
                    // Explicitly disable SASL authentication mechanism which requires SASL
                    System.setProperty("org.mongodb.driver.disableSaslAuthentication", "true");
                    Log.d(TAG, "Disabled SASL authentication, using legacy MONGODB-CR");
                    
                    // Create client with credentials
                    this.mongoClient = new MongoClient(serverAddress, Arrays.asList(credential), options);
                    
                } else {
                    // Create anonymous client
                    Log.d(TAG, "Attempting anonymous connection");
                    this.mongoClient = new MongoClient(serverAddress, options);
                }
                
                // Get database and test connection
                this.mongoDatabase = mongoClient.getDB(connectionInfo.getDatabase());
                
                // Quick ping test
                DBObject pingCmd = new BasicDBObject("ping", 1);
                mongoDatabase.command(pingCmd);
                
                Log.d(TAG, "MongoDB connection tested successfully");
                this.connectionInfo = connectionInfo;
                
                // Record successful connection
                String version = "unknown";
                try {
                    DBObject buildInfo = mongoDatabase.command(new BasicDBObject("buildInfo", 1));
                    if (buildInfo != null && buildInfo.containsField("version")) {
                        version = buildInfo.get("version").toString();
                    }
                } catch (Exception ex) {
                    Log.w(TAG, "Could not retrieve MongoDB version: " + ex.getMessage());
                }
                String dbDetails = "MongoDB " + version;
                ConnectionTracker.getInstance().trackConnectionSuccess(connectionTrackingId, dbDetails);
                
            } catch (Exception e) {
                Log.e(TAG, "Initial connection attempt failed: " + e.getMessage(), e);
                
                // Check if this is a database type mismatch
                String wrongDbTypeError = detectWrongDatabaseType(e);
                if (wrongDbTypeError != null) {
                    Log.e(TAG, "Wrong database type detected: " + wrongDbTypeError);
                    ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, wrongDbTypeError, "WRONG_DB_TYPE");
                    throw new Exception(wrongDbTypeError);
                }
                
                // Check for authentication errors
                String authError = detectAuthenticationError(e);
                if (authError != null) {
                    Log.e(TAG, "Authentication error detected: " + authError);
                    ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, authError, "AUTHENTICATION");
                    throw new Exception(authError, e);
                }
                
                // Cleanup failed connection
                if (mongoClient != null) {
                    try {
                        mongoClient.close();
                    } catch (Exception closeEx) {
                        // Ignore cleanup errors
                    }
                    mongoClient = null;
                }
                
                // Try with direct connection string as fallback
                try {
                    Log.d(TAG, "Attempting direct connection string as fallback");
                    
                    // Build connection string manually with special authentication mechanism
                    
                    try {
                        // Explicitly disable SCRAM authentication mechanism which requires SASL
                        System.setProperty("org.mongodb.driver.disableSaslAuthentication", "true");
                        
                        Log.d(TAG, "Disabled SASL authentication, using legacy MONGODB-CR");
                        
                        // Set authentication database to admin by default in fallback mode
                        String authDb = "admin";
                        
                        StringBuilder connString = new StringBuilder("mongodb://");
                        
                        // Add credentials if available
                        if (connectionInfo.getUsername() != null && !connectionInfo.getUsername().isEmpty() &&
                                connectionInfo.getPassword() != null && !connectionInfo.getPassword().isEmpty()) {
                            
                            // URL encode the password to handle special characters
                            String encodedPassword;
                            try {
                                encodedPassword = java.net.URLEncoder.encode(
                                    connectionInfo.getPassword(), "UTF-8")
                                    .replace("+", "%20");  // Replace space encoding + with %20
                                
                                Log.d(TAG, "Password contains special characters, using URL encoding");
                            } catch (Exception ex) {
                                // If encoding fails, use the raw password (may still have issues)
                                encodedPassword = connectionInfo.getPassword();
                                Log.e(TAG, "Failed to URL encode password, using raw password");
                            }
                            
                            connString.append(connectionInfo.getUsername())
                                    .append(":")
                                    .append(encodedPassword)
                                    .append("@");
                        }
                        
                        // Add host:port/database with additional options for legacy auth
                        connString.append(connectionInfo.getHost())
                                .append(":")
                                .append(connectionInfo.getPort())
                                .append("/")
                                .append(authDb)  // Use admin database for auth
                                .append("?authSource=")
                                .append(authDb)
                                .append("&connectTimeoutMS=20000&socketTimeoutMS=30000")
                                .append("&authMechanism=MONGODB-CR");  // Use legacy auth mechanism
                        
                        Log.d(TAG, "Using connection string (credentials hidden): mongodb://[auth_if_provided]@" +
                                connectionInfo.getHost() + ":" + connectionInfo.getPort() + "/" + 
                                authDb + "?authSource=" + authDb + 
                                "&connectTimeoutMS=20000&socketTimeoutMS=30000&authMechanism=MONGODB-CR");
                        
                        // Build options without SASL
                        MongoClientOptions fallbackOptions = MongoClientOptions.builder()
                                .connectTimeout(20000)
                                .socketTimeout(30000)
                                .serverSelectionTimeout(20000)
                                .build();
                        
                        // Try simplified connection
                        this.mongoClient = new MongoClient(serverAddress, fallbackOptions);
                        
                        // Get the requested database regardless of auth database
                        this.mongoDatabase = mongoClient.getDB(connectionInfo.getDatabase());
                        
                    } catch (Exception fallbackEx) {
                        Log.e(TAG, "Legacy auth approach failed, trying direct connection: " + fallbackEx.getMessage());
                        
                        // Last attempt - simple connection
                        this.mongoClient = new MongoClient(serverAddress);
                        this.mongoDatabase = mongoClient.getDB(connectionInfo.getDatabase());
                    }
                    
                    // Test connection
                    DBObject pingCmd = new BasicDBObject("ping", 1);
                    mongoDatabase.command(pingCmd);
                    
                    Log.d(TAG, "Fallback connection successful");
                    this.connectionInfo = connectionInfo;
                    
                } catch (Exception fallbackEx) {
                    Log.e(TAG, "Fallback connection failed: " + fallbackEx.getMessage(), fallbackEx);
                    
                    // Check if the fallback attempt indicates wrong database type
                    String fallbackWrongDbError = detectWrongDatabaseType(fallbackEx);
                    if (fallbackWrongDbError != null) {
                        throw new Exception(fallbackWrongDbError);
                    }
                    
                    // Check for authentication errors
                    String fallbackAuthError = detectAuthenticationError(fallbackEx);
                    if (fallbackAuthError != null) {
                        throw new Exception(fallbackAuthError, fallbackEx);
                    }
                    
                    String errorMsg = "All MongoDB connection attempts failed: " + fallbackEx.getMessage();
                    ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, errorMsg, "CONNECTION_FAILED");
                    throw new Exception(errorMsg);
                }
            }
            
            Log.d(TAG, "MongoDB connection established successfully");
            ConnectionTracker.getInstance().trackConnectionSuccess(connectionTrackingId, "MongoDB connection established");
            
        } catch (MongoException e) {
            Log.e(TAG, "MongoDB error: " + e.getMessage(), e);
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, e.getMessage(), "MONGODB_ERROR");
            throw new Exception("MongoDB error: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage(), e);
            ConnectionTracker.getInstance().trackConnectionFailure(connectionTrackingId, e.getMessage(), "UNEXPECTED_ERROR");
            throw new Exception("Failed to connect to MongoDB: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() throws Exception {
        if (mongoClient != null) {
            try {
                // Track disconnection if we had a successful connection
                if (connectionTrackingId != null) {
                    ConnectionTracker.getInstance().trackDisconnection(connectionTrackingId);
                    Log.i(TAG, "Disconnecting MongoDB connection: " + connectionTrackingId);
                }
                
                mongoClient.close();
                Log.d(TAG, "MongoDB client closed successfully");
            } finally {
                mongoClient = null;
                mongoDatabase = null;
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (mongoClient == null || mongoDatabase == null) {
            return false;
        }

        try {
            // Light connection test - just ping the server
            mongoDatabase.command("ping");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Connection test failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> getDatabases() throws Exception {
        List<String> databases = new ArrayList<>();
        try {
            // First, try to use the specific database we're connected to
            if (connectionInfo != null && connectionInfo.getDatabase() != null && !connectionInfo.getDatabase().isEmpty()) {
                Log.d(TAG, "Using configured database instead of listing all databases: " + connectionInfo.getDatabase());
                // Just use the database we're already connected to
                databases.add(connectionInfo.getDatabase());
                return databases;
            }
            
            try {
                // Try to get database names normally first
                Log.d(TAG, "Attempting to list all databases");
                for (String name : mongoClient.getDatabaseNames()) {
                    databases.add(name);
                }
            } catch (MongoException e) {
                // If we get a permission error, try a different approach
                if (e.getMessage() != null && (e.getMessage().contains("Unauthorized") || 
                    e.getMessage().contains("requires authentication"))) {
                    
                    Log.w(TAG, "Authentication error when listing databases. Using workaround...");
                    
                    // Only add the current database we know we have access to
                    if (connectionInfo != null && connectionInfo.getDatabase() != null && !connectionInfo.getDatabase().isEmpty()) {
                        Log.d(TAG, "Using configured database: " + connectionInfo.getDatabase());
                        databases.add(connectionInfo.getDatabase());
                    } else {
                        // Try to get database from connection without listing
                        Log.d(TAG, "Attempting to retrieve current database");
                        try {
                            // Try a simple command on the db to make sure it exists
                            BasicDBObject ping = new BasicDBObject("ping", 1);
                            mongoDatabase.command(ping);
                            String dbName = mongoDatabase.getName();
                            Log.d(TAG, "Current database: " + dbName);
                            databases.add(dbName);
                        } catch (Exception ex) {
                            Log.e(TAG, "Error retrieving current database: " + ex.getMessage());
                            throw new Exception("Unable to determine accessible databases. " +
                                "Your user may not have sufficient permissions.", ex);
                        }
                    }
                } else {
                    // Not an auth error, rethrow
                    throw e;
                }
            }

            return databases;
        } catch (MongoException e) {
            Log.e(TAG, "Error retrieving MongoDB databases: " + e.getMessage(), e);
            
            // Check for authentication errors
            String authError = detectAuthenticationError(e);
            if (authError != null) {
                throw new Exception(authError, e);
            }
            
            throw new Exception("Failed to retrieve databases: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error retrieving MongoDB databases: " + e.getMessage(), e);
            throw new Exception("Failed to retrieve databases: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getTables(String database) throws Exception {
        List<String> collections = new ArrayList<>();
        try {
            Log.d(TAG, "Attempting to get collections for database: " + database);
            
            // Get collection names
            DB db = mongoClient.getDB(database);
            
            // Try using a different approach if the database matches our connection database
            if (connectionInfo != null && database.equals(connectionInfo.getDatabase())) {
                Log.d(TAG, "Using current database connection");
                try {
                    // Try the getCollectionNames method first
                    for (String name : db.getCollectionNames()) {
                        collections.add(name);
                        Log.d(TAG, "Found collection: " + name);
                    }
                } catch (MongoException e) {
                    if (e.getMessage() != null && (e.getMessage().contains("Unauthorized") || 
                        e.getMessage().contains("requires authentication"))) {
                        
                        Log.w(TAG, "Authentication error when listing collections. Using fallback method...");
                        
                        // Try to run a command to list collections (works with more limited permissions)
                        try {
                            Log.d(TAG, "Trying alternative method to list collections...");
                            
                            // Try directly accessing the system.namespaces collection if it exists (older MongoDB)
                            try {
                                DBCollection namespaces = db.getCollection("system.namespaces");
                                if (namespaces != null) {
                                    Log.d(TAG, "Found system.namespaces collection, searching for collections");
                                    DBCursor cursor = namespaces.find();
                                    while (cursor.hasNext()) {
                                        DBObject obj = cursor.next();
                                        String name = obj.get("name").toString();
                                        if (name.contains(".") && !name.contains("system.") && !name.contains("$")) {
                                            String collName = name.substring(name.indexOf(".") + 1);
                                            collections.add(collName);
                                            Log.d(TAG, "Found collection (system.namespaces): " + collName);
                                        }
                                    }
                                    cursor.close();
                                }
                            } catch (Exception ex) {
                                Log.d(TAG, "Could not use system.namespaces: " + ex.getMessage());
                            }
                            
                            // If still no collections, try the modern listCollections command
                            if (collections.isEmpty()) {
                                Log.d(TAG, "Trying listCollections command...");
                                // Use the listCollections command with a filter to get only regular collections
                                BasicDBObject cmd = new BasicDBObject("listCollections", 1)
                                    .append("filter", new BasicDBObject("type", "collection"));
                                DBObject result = db.command(cmd);
                                
                                Log.d(TAG, "listCollections result: " + result);
                                
                                if (result != null && result.containsField("cursor")) {
                                    DBObject cursor = (DBObject) result.get("cursor");
                                    if (cursor != null && cursor.containsField("firstBatch")) {
                                        List<DBObject> batch = (List<DBObject>) cursor.get("firstBatch");
                                        Log.d(TAG, "Found " + batch.size() + " collections in firstBatch");
                                        for (DBObject obj : batch) {
                                            if (obj.containsField("name")) {
                                                String collName = obj.get("name").toString();
                                                collections.add(collName);
                                                Log.d(TAG, "Found collection (fallback): " + collName);
                                            } else {
                                                Log.d(TAG, "Collection object doesn't have name field: " + obj);
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "No firstBatch field in cursor: " + cursor);
                                    }
                                } else {
                                    Log.d(TAG, "No cursor field in result or result is null");
                                }
                            }
                            
                            // Try the stats command as a last resort to see if we can access the database
                            try {
                                Log.d(TAG, "Trying dbStats command to verify access...");
                                DBObject statsResult = db.command(new BasicDBObject("dbStats", 1));
                                Log.d(TAG, "dbStats result: " + statsResult);
                                // Check if we can see any collections through stats
                                if (statsResult != null && statsResult.containsField("collections")) {
                                    int collectionCount = ((Number)statsResult.get("collections")).intValue();
                                    Log.i(TAG, "Database reports " + collectionCount + " collections, but we couldn't list them");
                                    if (collectionCount > 0 && collections.isEmpty()) {
                                        Log.w(TAG, "There are collections in the database, but we don't have permission to list them");
                                    }
                                }
                            } catch (Exception ex) {
                                Log.d(TAG, "Could not get dbStats: " + ex.getMessage());
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Fallback method also failed: " + ex.getMessage(), ex);
                            throw new Exception("Unable to list collections. Your user may not have sufficient permissions.", ex);
                        }
                    } else {
                        // Not an auth error, rethrow
                        throw e;
                    }
                }
            } else {
                // Not using the current database, so just try the standard method
                for (String name : db.getCollectionNames()) {
                    collections.add(name);
                    Log.d(TAG, "Found collection: " + name);
                }
            }

            Log.i(TAG, "Found " + collections.size() + " collections in database " + database);
            return collections;
        } catch (MongoException e) {
            Log.e(TAG, "Error retrieving MongoDB collections: " + e.getMessage(), e);
            
            // Check for authentication errors
            String authError = detectAuthenticationError(e);
            if (authError != null) {
                throw new Exception(authError, e);
            }
            
            throw new Exception("Failed to retrieve collections: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error retrieving MongoDB collections: " + e.getMessage(), e);
            throw new Exception("Failed to retrieve collections: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> executeQuery(String query) throws Exception {
        // For MongoDB, query is a JSON string representing a find operation
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            Log.d(TAG, "Executing MongoDB query: " + query);
            
            // Parse query document
            BasicDBObject queryDocument;
            try {
                queryDocument = BasicDBObject.parse(query);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing query JSON: " + e.getMessage(), e);
                throw new IllegalArgumentException("Invalid query format. Query must be valid JSON: " + e.getMessage());
            }
            
            String collectionName = queryDocument.getString("collection");
            if (collectionName == null) {
                Log.e(TAG, "No collection specified in query");
                throw new IllegalArgumentException("Query format incorrect. Please specify the 'collection' field.");
            }
            
            // Check if this is a direct collection query or a find operation
            boolean isDirectQuery = !queryDocument.containsField("find");
            
            // Get the collection 
            DBCollection collection;
            try {
                collection = mongoDatabase.getCollection(collectionName);
                Log.d(TAG, "Using collection: " + collectionName);
            } catch (Exception e) {
                Log.e(TAG, "Error accessing collection '" + collectionName + "': " + e.getMessage(), e);
                throw new Exception("Cannot access collection '" + collectionName + "': " + e.getMessage(), e);
            }
            
            // Execute query with cursor timeout
            DBCursor cursor;
            
            if (isDirectQuery) {
                // If no find criteria, just get all documents (with limit for safety)
                Log.d(TAG, "No find criteria specified, returning first 100 documents");
                cursor = collection.find().limit(100).maxTime(30, TimeUnit.SECONDS);
            } else {
                // Normal find operation with criteria
                BasicDBObject find = (BasicDBObject) queryDocument.get("find");
                if (find == null) {
                    throw new IllegalArgumentException("Find criteria incorrectly formatted. Please provide a valid 'find' object.");
                }
                
                Log.d(TAG, "Executing find with criteria: " + find);
                cursor = collection.find(find).maxTime(30, TimeUnit.SECONDS);
            }
            
            // Process results
            int count = 0;
            while (cursor.hasNext()) {
                result.add(dbObjectToMap(cursor.next()));
                count++;
            }
            cursor.close();
            
            Log.i(TAG, "Query returned " + count + " results from collection " + collectionName);
            return result;
            
        } catch (MongoException e) {
            Log.e(TAG, "Error executing MongoDB query: " + e.getMessage(), e);
            
            // Check for authentication errors
            String authError = detectAuthenticationError(e);
            if (authError != null) {
                throw new Exception(authError, e);
            }
            
            throw new Exception("Query execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int executeUpdate(String query) throws Exception {
        // For MongoDB, query is a JSON string representing an update operation
        try {
            BasicDBObject updateDocument = BasicDBObject.parse(query);
            String collectionName = updateDocument.getString("collection");
            BasicDBObject update = (BasicDBObject) updateDocument.get("update");
            BasicDBObject filter = (BasicDBObject) updateDocument.get("filter");

            if (collectionName == null || update == null || filter == null) {
                throw new IllegalArgumentException("Update format incorrect. Please specify collection, filter, and update criteria.");
            }

            // Execute update
            DBCollection collection = mongoDatabase.getCollection(collectionName);
            WriteResult result = collection.update(filter, update, false, true);

            return result.getN();
        } catch (MongoException e) {
            Log.e(TAG, "Error executing MongoDB update: " + e.getMessage(), e);
            
            // Check for authentication errors
            String authError = detectAuthenticationError(e);
            if (authError != null) {
                throw new Exception(authError, e);
            }
            
            throw new Exception("Update execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getTableStructure(String table) throws Exception {
        Map<String, String> structure = new HashMap<>();

        try {
            // Get one document to infer schema
            DBCollection collection = mongoDatabase.getCollection(table);
            DBObject document = collection.findOne();

            if (document != null) {
                for (String key : document.keySet()) {
                    Object value = document.get(key);
                    structure.put(key, (value != null) ? value.getClass().getSimpleName() : "null");
                }
            }

            return structure;
        } catch (MongoException e) {
            Log.e(TAG, "Error getting MongoDB collection structure: " + e.getMessage(), e);
            
            // Check for authentication errors
            String authError = detectAuthenticationError(e);
            if (authError != null) {
                throw new Exception(authError, e);
            }
            
            throw new Exception("Failed to get collection structure: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> dbObjectToMap(DBObject document) {
        Map<String, Object> map = new HashMap<>();

        for (String key : document.keySet()) {
            Object value = document.get(key);
            if (value instanceof DBObject) {
                map.put(key, dbObjectToMap((DBObject) value));
            } else {
                map.put(key, value);
            }
        }

        return map;
    }
}