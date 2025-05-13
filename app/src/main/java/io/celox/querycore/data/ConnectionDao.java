package io.celox.querycore.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ConnectionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertConnection(ConnectionEntity connection);
    
    @Update
    void updateConnection(ConnectionEntity connection);
    
    @Delete
    void deleteConnection(ConnectionEntity connection);
    
    @Query("SELECT * FROM connections ORDER BY name")
    LiveData<List<ConnectionEntity>> getAllConnections();
    
    @Query("SELECT * FROM connections WHERE id = :id")
    LiveData<ConnectionEntity> getConnectionById(int id);
    
    @Query("SELECT * FROM connections WHERE name LIKE :searchQuery || '%' ORDER BY name")
    LiveData<List<ConnectionEntity>> searchConnections(String searchQuery);
}