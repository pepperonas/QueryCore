package io.celox.querycore.database;

import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import io.celox.querycore.models.ConnectionInfo;

/**
 * Utility class for tracking and logging database connection attempts and activities.
 * This helps with diagnosing connection issues across different database types.
 */
public class ConnectionTracker {
    private static final String TAG = "ConnectionTracker";
    private static final int MAX_HISTORY_SIZE = 20;
    
    // Singleton instance
    private static ConnectionTracker instance;
    
    // Track connection attempts and history
    private final AtomicInteger totalAttempts = new AtomicInteger(0);
    private final AtomicInteger successfulConnections = new AtomicInteger(0);
    private final AtomicInteger failedConnections = new AtomicInteger(0);
    private final List<ConnectionEvent> connectionHistory = new ArrayList<>();
    
    /**
     * Get the singleton instance of the ConnectionTracker
     */
    public static synchronized ConnectionTracker getInstance() {
        if (instance == null) {
            instance = new ConnectionTracker();
        }
        return instance;
    }
    
    private ConnectionTracker() {
        Log.i(TAG, "ConnectionTracker initialized");
    }
    
    /**
     * Log the start of a connection attempt
     * @param connectionInfo The connection info being used (sensitive data is not logged)
     * @param databaseType The type of database being connected to
     * @return A unique identifier for this connection attempt
     */
    public synchronized String trackConnectionStart(ConnectionInfo connectionInfo, String databaseType) {
        int attemptId = totalAttempts.incrementAndGet();
        String trackingId = databaseType + "-" + attemptId;
        
        // Record connection event
        ConnectionEvent event = new ConnectionEvent();
        event.trackingId = trackingId;
        event.startTime = System.currentTimeMillis();
        event.databaseType = databaseType;
        event.host = connectionInfo.getHost();
        event.port = connectionInfo.getPort();
        event.database = connectionInfo.getDatabase();
        event.hasCredentials = connectionInfo.getUsername() != null && !connectionInfo.getUsername().isEmpty();
        event.status = "CONNECTING";
        
        // Add device info
        event.deviceInfo = Build.MANUFACTURER + " " + Build.MODEL;
        event.androidVersion = Build.VERSION.SDK_INT;
        
        // Add to history (limiting size)
        synchronized (connectionHistory) {
            connectionHistory.add(event);
            if (connectionHistory.size() > MAX_HISTORY_SIZE) {
                connectionHistory.remove(0);
            }
        }
        
        // Log the connection attempt
        Log.i(TAG, "Connection attempt [" + trackingId + "] started: " + 
              databaseType + " to " + connectionInfo.getHost() + ":" + connectionInfo.getPort());
        
        return trackingId;
    }
    
    /**
     * Record a successful connection
     * @param trackingId The tracking ID returned from trackConnectionStart
     * @param databaseDetails Additional database details (version, etc.)
     */
    public synchronized void trackConnectionSuccess(String trackingId, String databaseDetails) {
        successfulConnections.incrementAndGet();
        ConnectionEvent event = findEvent(trackingId);
        
        if (event != null) {
            event.endTime = System.currentTimeMillis();
            event.duration = event.endTime - event.startTime;
            event.status = "SUCCESS";
            event.details = databaseDetails;
            
            Log.i(TAG, "Connection [" + trackingId + "] successful. Duration: " + 
                  event.duration + "ms. Details: " + databaseDetails);
        }
    }
    
    /**
     * Record a failed connection
     * @param trackingId The tracking ID returned from trackConnectionStart
     * @param errorMessage The error message
     * @param errorType The type of error (auth, network, etc.)
     */
    public synchronized void trackConnectionFailure(String trackingId, String errorMessage, String errorType) {
        failedConnections.incrementAndGet();
        ConnectionEvent event = findEvent(trackingId);
        
        if (event != null) {
            event.endTime = System.currentTimeMillis();
            event.duration = event.endTime - event.startTime;
            event.status = "FAILED";
            event.errorType = errorType;
            event.details = errorMessage;
            
            Log.e(TAG, "Connection [" + trackingId + "] failed. Duration: " + 
                  event.duration + "ms. Error type: " + errorType + ", Message: " + errorMessage);
        }
    }
    
    /**
     * Record a disconnection event
     * @param trackingId The tracking ID returned from trackConnectionStart
     */
    public synchronized void trackDisconnection(String trackingId) {
        ConnectionEvent event = findEvent(trackingId);
        
        if (event != null && "SUCCESS".equals(event.status)) {
            long disconnectTime = System.currentTimeMillis();
            long connectionDuration = disconnectTime - event.startTime;
            
            Log.i(TAG, "Connection [" + trackingId + "] disconnected. Total connection time: " + 
                  connectionDuration + "ms");
            
            event.status = "DISCONNECTED";
        }
    }
    
    /**
     * Get a connection summary suitable for diagnostics
     * @return A string containing connection statistics and recent history
     */
    public synchronized String getConnectionSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Connection Statistics:\n");
        summary.append("- Total connection attempts: ").append(totalAttempts.get()).append("\n");
        summary.append("- Successful connections: ").append(successfulConnections.get()).append("\n");
        summary.append("- Failed connections: ").append(failedConnections.get()).append("\n");
        
        if (successfulConnections.get() > 0) {
            double successRate = (double) successfulConnections.get() / totalAttempts.get() * 100;
            summary.append("- Success rate: ").append(String.format("%.1f%%", successRate)).append("\n");
        }
        
        // Add recent connection history
        summary.append("\nRecent Connection History (most recent first):\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        
        synchronized (connectionHistory) {
            for (int i = connectionHistory.size() - 1; i >= 0; i--) {
                ConnectionEvent event = connectionHistory.get(i);
                summary.append("[").append(sdf.format(new Date(event.startTime))).append("] ");
                summary.append(event.databaseType).append(" - ");
                summary.append(event.host).append(":").append(event.port).append(" - ");
                summary.append(event.status);
                
                if (event.duration > 0) {
                    summary.append(" (").append(event.duration).append("ms)");
                }
                
                if (event.errorType != null) {
                    summary.append(" - Error: ").append(event.errorType);
                }
                
                summary.append("\n");
            }
        }
        
        return summary.toString();
    }
    
    /**
     * Get detailed information about recent connections
     * @return A list of maps containing connection details
     */
    public synchronized List<Map<String, Object>> getDetailedConnectionHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        
        synchronized (connectionHistory) {
            for (ConnectionEvent event : connectionHistory) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", event.trackingId);
                entry.put("type", event.databaseType);
                entry.put("host", event.host);
                entry.put("port", event.port);
                entry.put("database", event.database);
                entry.put("status", event.status);
                entry.put("startTime", new Date(event.startTime));
                
                if (event.endTime > 0) {
                    entry.put("endTime", new Date(event.endTime));
                    entry.put("duration", event.duration);
                }
                
                if (event.errorType != null) {
                    entry.put("errorType", event.errorType);
                    entry.put("errorDetails", event.details);
                }
                
                history.add(entry);
            }
        }
        
        return history;
    }
    
    /**
     * Find a connection event by its tracking ID
     * @param trackingId The tracking ID to search for
     * @return The connection event, or null if not found
     */
    private ConnectionEvent findEvent(String trackingId) {
        synchronized (connectionHistory) {
            for (ConnectionEvent event : connectionHistory) {
                if (event.trackingId.equals(trackingId)) {
                    return event;
                }
            }
        }
        return null;
    }
    
    /**
     * Inner class representing a connection event
     */
    private static class ConnectionEvent {
        String trackingId;
        long startTime;
        long endTime;
        long duration;
        String databaseType;
        String host;
        int port;
        String database;
        boolean hasCredentials;
        String status;
        String errorType;
        String details;
        String deviceInfo;
        int androidVersion;
    }
}