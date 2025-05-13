package io.celox.querycore.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.celox.querycore.data.AppDatabase;
import io.celox.querycore.data.ConnectionDao;
import io.celox.querycore.data.ConnectionEntity;

public class ConnectionRepository {
    
    private ConnectionDao connectionDao;
    private LiveData<List<ConnectionEntity>> allConnections;
    private final ExecutorService executorService;
    
    public ConnectionRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        connectionDao = database.connectionDao();
        allConnections = connectionDao.getAllConnections();
        executorService = Executors.newFixedThreadPool(3);
    }
    
    public void insert(ConnectionEntity connection) {
        executorService.execute(() -> connectionDao.insertConnection(connection));
    }
    
    public void update(ConnectionEntity connection) {
        executorService.execute(() -> connectionDao.updateConnection(connection));
    }
    
    public void delete(ConnectionEntity connection) {
        executorService.execute(() -> connectionDao.deleteConnection(connection));
    }
    
    public LiveData<List<ConnectionEntity>> getAllConnections() {
        return allConnections;
    }
    
    public LiveData<ConnectionEntity> getConnectionById(int id) {
        return connectionDao.getConnectionById(id);
    }
    
    public LiveData<List<ConnectionEntity>> searchConnections(String query) {
        return connectionDao.searchConnections(query);
    }
    
    // Shutdown the executor service when appropriate
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}