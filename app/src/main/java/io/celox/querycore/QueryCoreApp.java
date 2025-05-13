package io.celox.querycore;

import android.content.Context;
import android.os.StrictMode;
import android.util.Log;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import java.lang.Thread.UncaughtExceptionHandler;

public class QueryCoreApp extends MultiDexApplication {
    
    private static final String TAG = "QueryCore";
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Setup thread exception handling
        setupUncaughtExceptionHandler();
        
        // Allow network operations on main thread during development
        // WARNING: This should be removed for production!
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitNetwork()
                .permitDiskReads()
                .permitDiskWrites()
                .build();
        StrictMode.setThreadPolicy(policy);
        
        // Allow disk/file operations on main thread during development
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .build();
        StrictMode.setVmPolicy(vmPolicy);
    }
    
    private void setupUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(TAG, "Uncaught exception in thread: " + thread.getName(), ex);
                
                // Log the exception details
                StringBuilder error = new StringBuilder();
                error.append("Thread: ").append(thread.getName()).append("\n");
                error.append("Exception: ").append(ex.getMessage()).append("\n");
                
                // Log the stack trace
                for (StackTraceElement element : ex.getStackTrace()) {
                    error.append("\tat ").append(element.toString()).append("\n");
                }
                
                // Log the complete error
                Log.e(TAG, error.toString());
                
                // Pass to the default handler
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, ex);
            }
        });
    }
}