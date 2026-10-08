package com.example.p2pmessenger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MeshService extends Service {
    private static final String TAG = "PertoMesh";
    private static final String CHANNEL_ID = "perto_mesh_channel";
    private static final int NOTIFICATION_ID = 1001;

    private MeshManager meshManager;
    private LocationManager locationManager;
    private ScheduledExecutorService scheduler;
    private ExecutorService netExecutor;
    private LocationListener locationListener;
    private String userUid = "";
    private final String meshGroupId = ServerConfig.MESH_GROUP_ID;
    private volatile Location lastLocation;
    private volatile int refreshGeneration = 0;
    private volatile boolean destroyed = false;

    @Override public void onCreate() {
        super.onCreate();

        // Once startForegroundService() was called we MUST call startForeground() quickly,
        // otherwise Android kills the app. Do it first, before anything that can fail.
        createNotificationChannel();
        boolean foreground = startForegroundSafely();

        userUid = Session.uid();
        if (!foreground || userUid.isEmpty() || !Session.isLoggedIn()) {
            stopSelf();
            return;
        }

        netExecutor = Executors.newSingleThreadExecutor();
        try {
            meshManager = new MeshManager(this, userUid);
            meshManager.start();
        } catch (Throwable t) {
            Log.e(TAG, "Mesh start failed", t);
        }

        refreshGeneration = getSharedPreferences(Session.PREFS, MODE_PRIVATE)
                .getInt("mesh_refresh_generation", 0);
        joinMeshGroup();
        startLocationReporting();
        startBackgroundWork();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return (meshManager != null) ? START_STICKY : START_NOT_STICKY;
    }

    // ---------- foreground notification ----------

    private boolean startForegroundSafely() {
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Exception e) {
            // SecurityException (missing location permission on Android 14) or
            // ForegroundServiceStartNotAllowedException (background start on Android 12+).
            Log.e(TAG, "startForeground failed", e);
            return false;
        }
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Perto Mesh")
                .setContentText("Mesh network is active")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return; // channels do not exist before Android 8
        try {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Mesh Network", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        } catch (Exception e) {
            Log.w(TAG, "Channel creation failed", e);
        }
    }

    // ---------- location ----------

    private boolean has(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationReporting() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;

        boolean fine = has(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!fine && !coarse) return;

        // All four callbacks must exist: on Android 6-10 the system calls the old ones
        // and a missing implementation throws AbstractMethodError (hard crash).
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) { handleLocation(location); }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        requestUpdates(LocationManager.NETWORK_PROVIDER);
        if (fine) requestUpdates(LocationManager.GPS_PROVIDER);

        Location best = null;
        best = better(best, lastKnown(LocationManager.NETWORK_PROVIDER));
        if (fine) best = better(best, lastKnown(LocationManager.GPS_PROVIDER));
        if (best != null) handleLocation(best);
    }

    private void requestUpdates(String provider) {
        try {
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 60_000L, 50f, locationListener);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission unavailable for " + provider);
        } catch (Exception e) {
            Log.w(TAG, "Location provider unavailable: " + provider, e);
        }
    }

    @SuppressWarnings("MissingPermission")
    private Location lastKnown(String provider) {
        try {
            if (locationManager.isProviderEnabled(provider)) return locationManager.getLastKnownLocation(provider);
        } catch (Exception ignored) {}
        return null;
    }

    private static Location better(Location a, Location b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.getTime() > a.getTime() ? b : a;
    }

    private void handleLocation(Location location) {
        if (location == null || destroyed || userUid.isEmpty()) return;
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        if (Double.isNaN(lat) || Double.isNaN(lng)) return;
        lastLocation = location;
        final double accuracy = location.hasAccuracy() ? location.getAccuracy() : 0;
        if (meshManager != null) meshManager.broadcastLocation(lat, lng, accuracy);
        if (netExecutor != null && !netExecutor.isShutdown()) {
            try { netExecutor.execute(() -> postLocation(lat, lng, accuracy)); } catch (Exception ignored) {}
        }
    }

    private void postLocation(double lat, double lng, double accuracy) {
        if (destroyed) return;
        try {
            JSONObject body = new JSONObject();
            body.put("uid", userUid);
            body.put("group_id", meshGroupId);
            body.put("lat", lat);
            body.put("lng", lng);
            body.put("accuracy", accuracy);
            body.put("is_gateway", hasInternet());
            ApiClient.post("/location/update", body);
        } catch (Throwable t) {
            Log.d(TAG, "Location update failed: " + t.getMessage());
        }
    }

    private boolean hasInternet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();   // API 23+
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            @SuppressWarnings("deprecation")
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- background work ----------

    private void startBackgroundWork() {
        scheduler = Executors.newScheduledThreadPool(2);
        // Exceptions escaping a scheduled task silently cancel all future runs, so every task is wrapped.
        scheduler.scheduleWithFixedDelay(guarded(this::pullGatewayMessages), 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(guarded(this::syncMeshState), 8, 15, TimeUnit.SECONDS);
        // The server treats a gateway as alive only if it reported in the last 3 minutes.
        // A phone that stands still gets no location callbacks, so send a heartbeat.
        scheduler.scheduleWithFixedDelay(guarded(this::heartbeat), 45, 60, TimeUnit.SECONDS);
    }

    private Runnable guarded(final Runnable task) {
        return () -> {
            try {
                if (destroyed) return;
                if (!Session.isLoggedIn()) { stopSelf(); return; } // logged out / session expired
                task.run();
            } catch (Throwable t) {
                Log.w(TAG, "Background task failed", t);
            }
        };
    }

    private void heartbeat() {
        Location l = lastLocation;
        if (l == null || !hasInternet()) return;
        postLocation(l.getLatitude(), l.getLongitude(), l.hasAccuracy() ? l.getAccuracy() : 0);
    }

    private void pullGatewayMessages() {
        if (meshManager == null || !hasInternet()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("gateway_uid", userUid);
            body.put("limit", 20);
            JSONObject result = ApiClient.post("/message/pull", body);
            JSONArray messages = result.optJSONArray("messages");
            if (messages == null) return;

            for (int i = 0; i < messages.length(); i++) {
                JSONObject row = messages.optJSONObject(i);
                if (row == null) continue;
                String recipient = row.optString("recipient_uid", "");
                String messageId = row.optString("message_id", "");
                if (recipient.isEmpty() || messageId.isEmpty()) continue;

                JSONObject packet = new JSONObject();
                packet.put("messageId", messageId);
                packet.put("senderId", row.optString("sender_uid", ""));
                packet.put("recipientId", recipient);
                packet.put("payload", row.optString("payload", ""));
                packet.put("ttl", 5);
                packet.put("timestamp", row.optLong("created_at", System.currentTimeMillis() / 1000L) * 1000L);

                boolean handled;
                if (recipient.equals(userUid)) {
                    // The message is for this very phone: no peer to forward to, deliver locally.
                    meshManager.deliverLocally(packet.toString());
                    handled = true;
                } else {
                    handled = meshManager.sendToUser(recipient, packet.toString());
                }

                if (handled) {
                    JSONObject ack = new JSONObject();
                    ack.put("gateway_uid", userUid);
                    ack.put("message_id", messageId);
                    ApiClient.post("/message/ack", ack);
                }
            }
        } catch (ApiClient.ApiException e) {
            Log.d(TAG, "Gateway polling failed: " + e.getMessage());
        } catch (Exception e) {
            Log.d(TAG, "Gateway polling error: " + e.getMessage());
        }
    }

    private void joinMeshGroup() {
        if (netExecutor == null) return;
        netExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("group_id", meshGroupId);
                body.put("uid", userUid);
                body.put("refresh_generation", refreshGeneration);
                handleRefreshGeneration(ApiClient.post("/mesh/join", body));
            } catch (Throwable t) {
                Log.d(TAG, "Mesh join failed: " + t.getMessage());
            }
        });
    }

    private void syncMeshState() {
        if (!hasInternet()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("group_id", meshGroupId);
            body.put("uid", userUid);
            handleRefreshGeneration(ApiClient.post("/mesh/state", body));
        } catch (Exception e) {
            Log.d(TAG, "Mesh state sync failed: " + e.getMessage());
        }
    }

    private void handleRefreshGeneration(JSONObject result) {
        int generation = result.optInt("refresh_generation", refreshGeneration);
        if (generation <= refreshGeneration) return;
        refreshGeneration = generation;
        SharedPreferences prefs = getSharedPreferences(Session.PREFS, MODE_PRIVATE);
        prefs.edit().putInt("mesh_refresh_generation", generation).apply();
        if (hasInternet()) uploadFullLocationRefresh(generation);
    }

    /**
     * Runs only when the server's 5-new-member threshold creates a new generation.
     * A gateway uploads the locations it currently knows; it does not ask every
     * existing member to transmit again.
     */
    private void uploadFullLocationRefresh(final int generation) {
        if (netExecutor == null || netExecutor.isShutdown()) return;
        netExecutor.execute(() -> {
            try {
                JSONArray locations = new JSONArray();
                Location own = lastLocation;
                if (own != null) {
                    JSONObject self = new JSONObject();
                    self.put("uid", userUid);
                    self.put("lat", own.getLatitude());
                    self.put("lng", own.getLongitude());
                    self.put("accuracy", own.hasAccuracy() ? own.getAccuracy() : 0);
                    locations.put(self);
                }
                if (meshManager != null) {
                    for (Map.Entry<String, MeshManager.PeerLocation> entry : meshManager.snapshotPeerLocations().entrySet()) {
                        MeshManager.PeerLocation p = entry.getValue();
                        if (p == null || p.uid == null || p.uid.isEmpty()) continue;
                        if (Double.isNaN(p.lat) || Double.isNaN(p.lng)) continue; // NaN makes JSON throw
                        JSONObject row = new JSONObject();
                        row.put("uid", p.uid);
                        row.put("lat", p.lat);
                        row.put("lng", p.lng);
                        row.put("accuracy", p.accuracy);
                        locations.put(row);
                    }
                }
                JSONObject body = new JSONObject();
                body.put("group_id", meshGroupId);
                body.put("gateway_uid", userUid);
                body.put("refresh_generation", generation);
                body.put("locations", locations);
                ApiClient.post("/mesh/location/batch", body);
            } catch (Throwable t) {
                Log.d(TAG, "Full location refresh failed: " + t.getMessage());
            }
        });
    }

    @Override public void onDestroy() {
        destroyed = true;
        if (scheduler != null) scheduler.shutdownNow();
        if (netExecutor != null) netExecutor.shutdown();
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
        if (meshManager != null) {
            try { meshManager.stop(); } catch (Throwable ignored) {}
            if (!userUid.isEmpty() && Session.isLoggedIn() && hasInternet()) {
                final String uid = userUid;
                new Thread(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("group_id", meshGroupId);
                        body.put("uid", uid);
                        ApiClient.post("/mesh/leave", body);
                    } catch (Throwable ignored) {}
                }).start();
            }
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
