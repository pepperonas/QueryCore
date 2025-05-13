package io.celox.querycore.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import io.celox.querycore.database.ConnectionTracker;

/**
 * Utility class for connection diagnostics and network troubleshooting.
 * Provides methods to get detailed information about connection attempts
 * and current network status.
 */
public class ConnectionDiagnostics {
    private static final String TAG = "ConnectionDiagnostics";

    /**
     * Get a detailed diagnostics report for troubleshooting database connection issues
     * @param context The application context
     * @return A diagnostic report as a string
     */
    public static String getDiagnosticsReport(Context context) {
        StringBuilder report = new StringBuilder();
        
        // Add report header
        report.append("=== CONNECTION DIAGNOSTICS REPORT ===\n\n");
        
        // Add date and time
        report.append("Generated: ").append(new java.util.Date()).append("\n\n");
        
        // Add device information
        report.append("Device Information:\n");
        report.append("- Model: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("- Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("- Device: ").append(Build.DEVICE).append("\n");
        report.append("- Product: ").append(Build.PRODUCT).append("\n\n");
        
        // Add network information
        report.append("Network Information:\n");
        try {
            report.append(getNetworkInfo(context));
        } catch (Exception e) {
            report.append("- Error retrieving network info: ").append(e.getMessage()).append("\n");
        }
        report.append("\n");
        
        // Add connection history from the tracker
        report.append(ConnectionTracker.getInstance().getConnectionSummary());
        
        // Add network test results if possible
        report.append("\nNetwork Tests:\n");
        try {
            // Check popular hosts
            report.append(runNetworkTest("github.com"));
            report.append(runNetworkTest("google.com"));
            report.append(runNetworkTest("1.1.1.1")); // Cloudflare DNS
        } catch (Exception e) {
            report.append("- Error running network tests: ").append(e.getMessage()).append("\n");
        }
        
        return report.toString();
    }
    
    /**
     * Get detailed network information from the device
     * @param context The application context
     * @return Network information as a string
     */
    private static String getNetworkInfo(Context context) {
        StringBuilder networkInfo = new StringBuilder();
        
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return "- ConnectivityManager not available\n";
        }
        
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null) {
            return "- No active network connection\n";
        }
        
        networkInfo.append("- Connected: ").append(activeNetwork.isConnected()).append("\n");
        networkInfo.append("- Type: ").append(activeNetwork.getTypeName()).append("\n");
        networkInfo.append("- Subtype: ").append(activeNetwork.getSubtypeName()).append("\n");
        networkInfo.append("- Roaming: ").append(activeNetwork.isRoaming()).append("\n");
        
        // Try to get IP address
        try {
            String ipAddress = "Unknown";
            InetAddress inetAddress = InetAddress.getLocalHost();
            ipAddress = inetAddress.getHostAddress();
            networkInfo.append("- Local IP: ").append(ipAddress).append("\n");
        } catch (Exception e) {
            networkInfo.append("- Could not determine local IP: ").append(e.getMessage()).append("\n");
        }
        
        // Try to get DNS servers
        try {
            Process process = Runtime.getRuntime().exec("getprop net.dns1");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String dns1 = reader.readLine();
            reader.close();
            
            process = Runtime.getRuntime().exec("getprop net.dns2");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String dns2 = reader.readLine();
            reader.close();
            
            if (dns1 != null && !dns1.isEmpty()) {
                networkInfo.append("- DNS Server 1: ").append(dns1).append("\n");
            }
            if (dns2 != null && !dns2.isEmpty()) {
                networkInfo.append("- DNS Server 2: ").append(dns2).append("\n");
            }
        } catch (Exception e) {
            networkInfo.append("- Could not determine DNS servers: ").append(e.getMessage()).append("\n");
        }
        
        return networkInfo.toString();
    }
    
    /**
     * Run a network test to a given host
     * @param host The host to test
     * @return Test results as a string
     */
    private static String runNetworkTest(String host) {
        StringBuilder result = new StringBuilder();
        result.append("- Testing connectivity to ").append(host).append(": ");
        
        try {
            // Try to resolve the host
            InetAddress address = InetAddress.getByName(host);
            result.append("Resolved to ").append(address.getHostAddress()).append(", ");
            
            // Try to ping the host
            boolean reachable = address.isReachable(5000);
            result.append(reachable ? "reachable" : "not reachable");
            result.append("\n");
            
        } catch (Exception e) {
            result.append("Failed - ").append(e.getMessage()).append("\n");
        }
        
        return result.toString();
    }
    
    /**
     * Get a JSON-compatible map of connection history for display in the UI
     * @return A list of connection history entries
     */
    public static List<Map<String, Object>> getConnectionHistory() {
        return ConnectionTracker.getInstance().getDetailedConnectionHistory();
    }
}