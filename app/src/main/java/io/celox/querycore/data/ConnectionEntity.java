package io.celox.querycore.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import io.celox.querycore.models.ConnectionInfo;

@Entity(tableName = "connections")
public class ConnectionEntity {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    @NonNull
    private String name;
    
    @NonNull
    private String type;
    
    @NonNull
    private String host;
    
    private int port;
    
    private String database;
    
    private String username;
    
    private String password;
    
    public ConnectionEntity() {
    }
    
    public ConnectionEntity(String name, String type, String host, int port, String database, String username, String password) {
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }
    
    public static ConnectionEntity fromConnectionInfo(ConnectionInfo info) {
        return new ConnectionEntity(
                info.getName(),
                info.getType().name(),
                info.getHost(),
                info.getPort(),
                info.getDatabase(),
                info.getUsername(),
                info.getPassword()
        );
    }
    
    public ConnectionInfo toConnectionInfo() {
        return new ConnectionInfo(
                name,
                ConnectionInfo.DatabaseType.valueOf(type),
                host,
                port,
                database,
                username,
                password
        );
    }
    
    // Getters and setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    @NonNull
    public String getName() {
        return name;
    }
    
    public void setName(@NonNull String name) {
        this.name = name;
    }
    
    @NonNull
    public String getType() {
        return type;
    }
    
    public void setType(@NonNull String type) {
        this.type = type;
    }
    
    @NonNull
    public String getHost() {
        return host;
    }
    
    public void setHost(@NonNull String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}