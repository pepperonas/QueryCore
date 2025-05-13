package io.celox.querycore.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import io.celox.querycore.data.ConnectionEntity;
import io.celox.querycore.repository.ConnectionRepository;

public class ConnectionViewModel extends AndroidViewModel {
    
    private final ConnectionRepository repository;
    private final LiveData<List<ConnectionEntity>> allConnections;
    
    public ConnectionViewModel(@NonNull Application application) {
        super(application);
        repository = new ConnectionRepository(application);
        allConnections = repository.getAllConnections();
    }
    
    public void insert(ConnectionEntity connection) {
        repository.insert(connection);
    }
    
    public void update(ConnectionEntity connection) {
        repository.update(connection);
    }
    
    public void delete(ConnectionEntity connection) {
        repository.delete(connection);
    }
    
    public LiveData<List<ConnectionEntity>> getAllConnections() {
        return allConnections;
    }
    
    public LiveData<ConnectionEntity> getConnectionById(int id) {
        return repository.getConnectionById(id);
    }
    
    public LiveData<List<ConnectionEntity>> searchConnections(String query) {
        return repository.searchConnections(query);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        repository.shutdown();
    }
}