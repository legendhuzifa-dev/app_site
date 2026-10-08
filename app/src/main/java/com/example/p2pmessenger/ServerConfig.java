package com.example.p2pmessenger;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ServerConfig {
    private ServerConfig() {}

    // ব্যাকআপ ডিফল্ট URL (যদি ইন্টারনেট বা গিটহাব কাজ না করে)
    public static volatile String BASE_URL = "http://192.168.0.121:8080";
    public static final String MESH_GROUP_ID = "default-mesh-group";

    // server-url রিপোজিটরির Raw লিঙ্ক
    private static final String CONFIG_RAW_URL = "https://raw.githubusercontent.com/legendhuzifa-dev/server-url/main/server_config.txt";

    public static void fetchLatestUrlAsync() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(CONFIG_RAW_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                
                // গিটহাব থেকে টেক্সট রেসপন্স রিড করা
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        String fetchedUrl = line.trim();
                        
                        // ইউআরএল এর শেষে ভুলে / থাকলে তা তুলে দেওয়া
                        if (fetchedUrl.endsWith("/")) {
                            fetchedUrl = fetchedUrl.substring(0, fetchedUrl.length() - 1);
                        }
                        
                        BASE_URL = fetchedUrl;
                        Log.d("ServerConfig", "Successfully updated BASE_URL to: " + BASE_URL);
                    }
                    reader.close();
                }
            } catch (Exception e) {
                Log.w("ServerConfig", "Failed to fetch remote server URL, using fallback BASE_URL", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
