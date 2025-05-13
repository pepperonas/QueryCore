package io.celox.querycore.utils;

import android.util.Log;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;

/**
 * Utility class for testing database connections and performing basic diagnostics
 */
public class DatabaseTestUtils {
    private static final String TAG = "DatabaseTestUtils";

    /**
     * Create a test collection in MongoDB for verification
     * 
     * @param host MongoDB server host
     * @param port MongoDB server port
     * @param database Database name
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Success message or error message
     */
    public static String createMongoDbTestCollection(String host, int port, String database, 
                                                   String username, String password) {
        MongoClient mongoClient = null;
        
        try {
            Log.i(TAG, "Attempting direct MongoDB connection to create test collection");
            
            // Create a MongoDB credential
            MongoCredential credential = null;
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                Log.d(TAG, "Creating MongoDB credentials for authentication");
                try {
                    // First try SCRAM-SHA-256 (more secure, MongoDB 4.0+)
                    // Use admin database for authentication
                    String authDb = "admin";
                    // URL encode the password to handle special characters
                    char[] encodedPassword;
                    try {
                        String encoded = java.net.URLEncoder.encode(
                            password, "UTF-8")
                            .replace("+", "%20");  // Replace space encoding + with %20
                        encodedPassword = encoded.toCharArray();
                        Log.d(TAG, "Password contains special characters, using URL encoding");
                    } catch (Exception e) {
                        // If encoding fails, use the raw password (may still have issues)
                        encodedPassword = password.toCharArray();
                        Log.e(TAG, "Failed to URL encode password, using raw password");
                    }
                    credential = MongoCredential.createScramSha256Credential(
                            username, 
                            authDb,
                            encodedPassword
                    );
                } catch (Throwable e) {
                    Log.w(TAG, "SCRAM-SHA-256 not available: " + e.getMessage());
                    
                    // Fall back to plain credential using admin database
                    String authDb = "admin";
                    // URL encode the password to handle special characters
                    char[] encodedPassword;
                    try {
                        String encoded = java.net.URLEncoder.encode(
                            password, "UTF-8")
                            .replace("+", "%20");  // Replace space encoding + with %20
                        encodedPassword = encoded.toCharArray();
                        Log.d(TAG, "Password contains special characters, using URL encoding");
                    } catch (Exception ex) {
                        // If encoding fails, use the raw password (may still have issues)
                        encodedPassword = password.toCharArray();
                        Log.e(TAG, "Failed to URL encode password, using raw password");
                    }
                    credential = MongoCredential.createCredential(
                            username,
                            authDb,
                            encodedPassword
                    );
                }
            }
            
            // Explicitly disable SASL authentication mechanism which requires SASL
            System.setProperty("org.mongodb.driver.disableSaslAuthentication", "true");
            Log.d(TAG, "Disabled SASL authentication, using legacy authentication");
            
            // Configure client options with more conservative timeouts for mobile networks
            MongoClientOptions options = MongoClientOptions.builder()
                    .connectTimeout(20000) // 20 seconds
                    .socketTimeout(30000)  // 30 seconds
                    .serverSelectionTimeout(20000) // 20 seconds
                    .build();
            
            // Build server address
            ServerAddress serverAddress = new ServerAddress(host, port);
            
            // Create client with credentials or anonymously
            if (credential != null) {
                mongoClient = new MongoClient(serverAddress, Arrays.asList(credential), options);
            } else {
                mongoClient = new MongoClient(serverAddress, options);
            }
            
            // Get the database
            DB db = mongoClient.getDB(database);
            
            // Create a test collection - this will fail if user doesn't have permissions
            String collectionName = "test_collection_" + System.currentTimeMillis();
            DBCollection collection = db.createCollection(collectionName, new BasicDBObject());
            
            // Insert a test document
            DBObject testDoc = new BasicDBObject("test_field", "test_value")
                    .append("created_at", System.currentTimeMillis())
                    .append("app", "QueryCore");
            
            collection.insert(testDoc);
            
            // Quick ping test to verify it's there
            long count = collection.count();
            
            return "Successfully created test collection '" + collectionName + "' with " + count + 
                   " document(s). You can now use this collection to test queries.";
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating MongoDB test collection: " + e.getMessage(), e);
            return "Failed to create MongoDB test collection: " + e.getMessage();
        } finally {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Create a test table in MySQL/MariaDB for verification
     * 
     * @param host MySQL server host
     * @param port MySQL server port
     * @param database Database name
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Success message or error message
     */
    public static String createMySqlTestTable(String host, int port, String database, 
                                             String username, String password) {
        Connection connection = null;
        
        try {
            Log.i(TAG, "Attempting direct MySQL connection to create test table");
            
            // Ensure MySQL driver is loaded (not MariaDB)
            try {
                // First try MySQL driver
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    Log.d(TAG, "Using MySQL JDBC driver");
                } catch (ClassNotFoundException e) {
                    // Fall back to MariaDB as backup
                    Log.d(TAG, "MySQL driver not available, falling back to MariaDB driver");
                    Class.forName("org.mariadb.jdbc.Driver");
                    Log.d(TAG, "Using MariaDB JDBC driver as fallback");
                }
            } catch (ClassNotFoundException e) {
                return "Cannot load MySQL or MariaDB JDBC driver: " + e.getMessage();
            }
            
            // Let's build the URL with credentials directly embedded in it
            String url;
            if (username != null && !username.isEmpty() && 
                password != null && !password.isEmpty()) {
                
                // URL encode the password
                String encodedPassword;
                try {
                    encodedPassword = java.net.URLEncoder.encode(password, "UTF-8");
                    Log.d(TAG, "Test table: Password encoded for URL inclusion");
                } catch (Exception e) {
                    encodedPassword = password;
                    Log.e(TAG, "Test table: Failed to URL encode password", e);
                }
                
                // Build a URL that works with both MySQL and MariaDB
                try {
                    // First try MySQL format
                    url = String.format("jdbc:mysql://%s:%s@%s:%d/%s?useSSL=false&connectTimeout=10000",
                            username,
                            encodedPassword,
                            host,
                            port,
                            database);
                    Log.d(TAG, "Using MySQL connection URL format");
                } catch (Exception e) {
                    // Fall back to MariaDB format
                    url = String.format("jdbc:mariadb://%s:%s@%s:%d/%s?useSSL=false&connectTimeout=10000",
                            username,
                            encodedPassword,
                            host,
                            port,
                            database);
                    Log.d(TAG, "Using MariaDB connection URL format");
                }
                
                Log.d(TAG, "Test table: Using URL with embedded credentials (password hidden): " + 
                      url.replaceAll(encodedPassword, "********"));
            } else {
                Log.w(TAG, "Test table creation: Username or password is empty - this will likely cause authentication issues");
                return "Error: Username and password cannot be empty";
            }
            
            // Connect to database directly with URL
            Log.d(TAG, "Test table: Attempting to connect with URL containing credentials");
            connection = DriverManager.getConnection(url);
            
            // Create a test table
            String tableName = "test_table_" + System.currentTimeMillis();
            
            try (Statement stmt = connection.createStatement()) {
                // Create table with basic structure
                String createTableSql = "CREATE TABLE " + tableName + " (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "test_field VARCHAR(255) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                
                stmt.executeUpdate(createTableSql);
                
                // Insert a test row
                String insertSql = "INSERT INTO " + tableName + " (test_field) VALUES ('test_value_from_querycore')";
                int rowsAffected = stmt.executeUpdate(insertSql);
                
                return "Successfully created test table '" + tableName + "' with " + rowsAffected + 
                       " row(s). You can now use this table to test queries.";
            }
            
        } catch (SQLException e) {
            Log.e(TAG, "SQL error creating MySQL test table: " + e.getMessage(), e);
            return "Failed to create MySQL test table: " + e.getMessage();
        } catch (Exception e) {
            Log.e(TAG, "Error creating MySQL test table: " + e.getMessage(), e);
            return "Failed to create MySQL test table: " + e.getMessage();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Try to get MongoDB server info for diagnostics
     * 
     * @param host MongoDB server host
     * @param port MongoDB server port
     * @param database Database name
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Server info or error message
     */
    public static String getMongoDbServerInfo(String host, int port, String database, 
                                             String username, String password) {
        MongoClient mongoClient = null;
        
        try {
            Log.i(TAG, "Attempting direct MongoDB connection to get server info");
            
            // Create a MongoDB credential
            MongoCredential credential = null;
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                Log.d(TAG, "Creating MongoDB credentials for authentication");
                try {
                    // First try SCRAM-SHA-256 (more secure, MongoDB 4.0+)
                    // Use admin database for authentication
                    String authDb = "admin";
                    // URL encode the password to handle special characters
                    char[] encodedPassword;
                    try {
                        String encoded = java.net.URLEncoder.encode(
                            password, "UTF-8")
                            .replace("+", "%20");  // Replace space encoding + with %20
                        encodedPassword = encoded.toCharArray();
                        Log.d(TAG, "Password contains special characters, using URL encoding");
                    } catch (Exception e) {
                        // If encoding fails, use the raw password (may still have issues)
                        encodedPassword = password.toCharArray();
                        Log.e(TAG, "Failed to URL encode password, using raw password");
                    }
                    credential = MongoCredential.createScramSha256Credential(
                            username, 
                            authDb,
                            encodedPassword
                    );
                } catch (Throwable e) {
                    Log.w(TAG, "SCRAM-SHA-256 not available: " + e.getMessage());
                    
                    // Fall back to plain credential using admin database
                    String authDb = "admin";
                    // URL encode the password to handle special characters
                    char[] encodedPassword;
                    try {
                        String encoded = java.net.URLEncoder.encode(
                            password, "UTF-8")
                            .replace("+", "%20");  // Replace space encoding + with %20
                        encodedPassword = encoded.toCharArray();
                        Log.d(TAG, "Password contains special characters, using URL encoding");
                    } catch (Exception ex) {
                        // If encoding fails, use the raw password (may still have issues)
                        encodedPassword = password.toCharArray();
                        Log.e(TAG, "Failed to URL encode password, using raw password");
                    }
                    credential = MongoCredential.createCredential(
                            username,
                            authDb,
                            encodedPassword
                    );
                }
            }
            
            // Explicitly disable SASL authentication mechanism which requires SASL
            System.setProperty("org.mongodb.driver.disableSaslAuthentication", "true");
            Log.d(TAG, "Disabled SASL authentication, using legacy authentication");
            
            // Configure client options with more conservative timeouts for mobile networks
            MongoClientOptions options = MongoClientOptions.builder()
                    .connectTimeout(20000) // 20 seconds
                    .socketTimeout(30000)  // 30 seconds
                    .serverSelectionTimeout(20000) // 20 seconds
                    .build();
            
            // Build server address
            ServerAddress serverAddress = new ServerAddress(host, port);
            
            // Create client with credentials or anonymously
            if (credential != null) {
                mongoClient = new MongoClient(serverAddress, Arrays.asList(credential), options);
            } else {
                mongoClient = new MongoClient(serverAddress, options);
            }
            
            // Get the database
            DB db = mongoClient.getDB(database);
            
            // Get server status
            DBObject serverStatus = db.command("serverStatus");
            
            // Extract key information
            String version = "Unknown";
            if (serverStatus.containsField("version")) {
                version = serverStatus.get("version").toString();
            }
            
            // Get build info 
            DBObject buildInfo = db.command("buildInfo");
            String gitVersion = "Unknown";
            if (buildInfo.containsField("gitVersion")) {
                gitVersion = buildInfo.get("gitVersion").toString();
            }
            
            // Get database stats
            DBObject dbStats = db.command("dbStats");
            
            StringBuilder info = new StringBuilder();
            info.append("MongoDB Server Information:\n");
            info.append("- Version: ").append(version).append("\n");
            info.append("- Git Version: ").append(gitVersion).append("\n");
            
            // Check if we can access collection information
            try {
                info.append("- Collections: ").append(db.getCollectionNames().size()).append("\n");
            } catch (Exception e) {
                info.append("- Collections: Unable to list (").append(e.getMessage()).append(")\n");
            }
            
            if (dbStats.containsField("collections")) {
                info.append("- Collections Count: ").append(dbStats.get("collections")).append("\n");
            }
            
            if (dbStats.containsField("objects")) {
                info.append("- Objects: ").append(dbStats.get("objects")).append("\n");
            }
            
            if (dbStats.containsField("dataSize")) {
                info.append("- Data Size: ").append(dbStats.get("dataSize")).append(" bytes\n");
            }
            
            // Check user permissions (try common operations)
            info.append("\nPermission Tests:\n");
            
            try {
                // Test listing databases
                mongoClient.getDatabaseNames();
                info.append("- List Databases: ✓\n");
            } catch (Exception e) {
                info.append("- List Databases: ✘ (").append(e.getMessage()).append(")\n");
            }
            
            try {
                // Test listing collections
                db.getCollectionNames();
                info.append("- List Collections: ✓\n");
            } catch (Exception e) {
                info.append("- List Collections: ✘ (").append(e.getMessage()).append(")\n");
            }
            
            try {
                // Test creating collection
                String testCollName = "test_coll_" + System.currentTimeMillis();
                db.createCollection(testCollName, null);
                info.append("- Create Collection: ✓\n");
                
                // Clean up test collection
                db.getCollection(testCollName).drop();
            } catch (Exception e) {
                info.append("- Create Collection: ✘ (").append(e.getMessage()).append(")\n");
            }
            
            return info.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting MongoDB server info: " + e.getMessage(), e);
            return "Failed to get MongoDB server info: " + e.getMessage();
        } finally {
            if (mongoClient != null) {
                try {
                    mongoClient.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}