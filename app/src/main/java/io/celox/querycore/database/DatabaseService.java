package io.celox.querycore.database;

import java.util.List;
import java.util.Map;

import io.celox.querycore.models.ConnectionInfo;

public interface DatabaseService {
    
    void connect(ConnectionInfo connectionInfo) throws Exception;
    
    void disconnect() throws Exception;
    
    boolean isConnected();
    
    List<String> getDatabases() throws Exception;
    
    List<String> getTables(String database) throws Exception;
    
    List<Map<String, Object>> executeQuery(String query) throws Exception;
    
    int executeUpdate(String query) throws Exception;
    
    Map<String, String> getTableStructure(String table) throws Exception;
}