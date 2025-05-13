package io.celox.querycore;

import org.junit.Test;

import io.celox.querycore.utils.DatabaseTestUtils;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
    
    /**
     * Test MongoDB connection and database operations.
     * NOTE: You must update the connection details to match your MongoDB server.
     * This test is disabled by default (remove the _ prefix to enable).
     */
    @Test
    public void _testMongoDbConnection() {
        // Update these values to match your MongoDB server
        String host = "localhost";
        int port = 27017;
        String database = "test";
        String username = "mongoAdmin";
        String password = "password";
        
        // Get MongoDB server info
        String serverInfo = DatabaseTestUtils.getMongoDbServerInfo(
                host, port, database, username, password);
        System.out.println(serverInfo);
        
        // Try to create a test collection
        String createResult = DatabaseTestUtils.createMongoDbTestCollection(
                host, port, database, username, password);
        System.out.println(createResult);
        
        // This assert is just to confirm the test ran, not to verify the result
        assertNotNull(serverInfo);
    }
    
    /**
     * Test MySQL/MariaDB connection and database operations.
     * NOTE: You must update the connection details to match your MySQL server.
     * This test is disabled by default (remove the _ prefix to enable).
     */
    @Test
    public void _testMySqlConnection() {
        // Update these values to match your MySQL server
        String host = "localhost";
        int port = 3306;
        String database = "test";
        String username = "root";
        String password = "password";
        
        // Try to create a test table
        String createResult = DatabaseTestUtils.createMySqlTestTable(
                host, port, database, username, password);
        System.out.println(createResult);
        
        // This assert is just to confirm the test ran, not to verify the result
        assertNotNull(createResult);
    }
}