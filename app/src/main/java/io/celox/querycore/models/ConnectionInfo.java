package io.celox.querycore.models;

import java.io.Serializable;

public class ConnectionInfo implements Serializable {
    public enum DatabaseType {
        MYSQL,
        MARIADB,
        MONGODB
    }
    
    private String name;
    private DatabaseType type;
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    
    public ConnectionInfo() {
    }
    
    public ConnectionInfo(String name, DatabaseType type, String host, int port, String database, String username, String password) {
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public DatabaseType getType() {
        return type;
    }
    
    public void setType(DatabaseType type) {
        this.type = type;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
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
    
    public String getDefaultPort() {
        switch (type) {
            case MYSQL:
            case MARIADB:
                return "3306";
            case MONGODB:
                return "27017";
            default:
                return "3306";
        }
    }
    
    public String getConnectionString() {
        switch (type) {
            case MYSQL:
            case MARIADB:
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", 
                                    host, port, database);
            case MONGODB:
                return String.format("mongodb://%s:%d/%s", host, port, database);
            default:
                return "";
        }
    }
}