package com.example.p2pmessenger;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeshManager {
    private static final String TAG = "PertoMesh";
    public static final String SERVICE_ID = "com.example.pertopersms";
    private static final int MAX_PAYLOAD_BYTES = 65536;

    private final Context context;
    private final String deviceId = UUID.randomUUID().toString();
    private final String userUid;
    private final Map<String, String> connectedPeers = new ConcurrentHashMap<>();
    private final Map<String, String> endpointUserIds = new ConcurrentHashMap<>();
    private final Map<String, PeerLocation> peerLocations = new ConcurrentHashMap<>();
    private final Set<String> pendingRequests =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final MessageRouter router;
    private volatile PeerLocation ownLocation;
    private volatile boolean running = false;

    public MeshManager(Context context, String userUid) {
        this.context = context.getApplicationContext();
        this.userUid = userUid == null ? "" : userUid;
        router = new MessageRouter(this.userUid, connectedPeers, endpointUserIds, this);
    }

    private ConnectionsClient client() {
        return Nearby.getConnectionsClient(context);
    }

    private void forget(String endpointId) {
        connectedPeers.remove(endpointId);
        endpointUserIds.remove(endpointId);
        peerLocations.remove(endpointId);
        pendingRequests.remove(endpointId);
    }

    private final ConnectionLifecycleCallback connectionCallback = new ConnectionLifecycleCallback() {
        @Override public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
            try {
                client().acceptConnection(endpointId, payloadCallback)
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "acceptConnection failed: " + e.getMessage());
                            forget(endpointId);
                        });
            } catch (Exception e) {
                Log.w(TAG, "acceptConnection error", e);
                forget(endpointId);
            }
        }

        @Override public void onConnectionResult(String endpointId, ConnectionResolution result) {
            pendingRequests.remove(endpointId);
            if (result.getStatus().isSuccess()) {
                connectedPeers.put(endpointId, endpointId);
                sendHello(endpointId);
                Log.d(TAG, "Connected: " + endpointId);
            } else {
                forget(endpointId);
            }
        }

        @Override public void onDisconnected(String endpointId) {
            forget(endpointId);
        }
    };

    private final EndpointDiscoveryCallback discoveryCallback = new EndpointDiscoveryCallback() {
        @Override public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
            if (!running) return;
            if (connectedPeers.containsKey(endpointId) || !pendingRequests.add(endpointId)) return;
            try {
                client().requestConnection(deviceId, endpointId, connectionCallback)
                        .addOnFailureListener(e -> {
                            // Usually "already connected/connecting": harmless.
                            pendingRequests.remove(endpointId);
                            Log.d(TAG, "requestConnection failed: " + e.getMessage());
                        });
            } catch (Exception e) {
                pendingRequests.remove(endpointId);
                Log.w(TAG, "requestConnection error", e);
            }
        }

        @Override public void onEndpointLost(String endpointId) {
            pendingRequests.remove(endpointId);
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override public void onPayloadReceived(String endpointId, Payload payload) {
            try {
                if (payload.getType() != Payload.Type.BYTES) return;
                byte[] bytes = payload.asBytes();
                if (bytes == null || bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) return;
                String json = new String(bytes, StandardCharsets.UTF_8);

                JSONObject obj = null;
                try { obj = new JSONObject(json); } catch (Exception ignored) { /* not JSON: router will reject */ }

                if (obj != null) {
                    String type = obj.optString("type", "");
                    if ("hello".equals(type) || "location".equals(type)) {
                        String peerUid = obj.optString("uid", "");
                        if (!peerUid.isEmpty() && peerUid.length() <= 80) endpointUserIds.put(endpointId, peerUid);
                        PeerLocation loc = readLocation(peerUid, obj);
                        if (loc != null) peerLocations.put(endpointId, loc);
                        return;
                    }
                }
                router.handleMessage(endpointId, json);
            } catch (Throwable t) {
                Log.w(TAG, "Bad payload ignored", t);
            }
        }

        @Override public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {}
    };

    /** Returns null unless lat/lng are real, in-range numbers (NaN would break JSON later). */
    private static PeerLocation readLocation(String uid, JSONObject obj) {
        if (!obj.has("lat") || !obj.has("lng")) return null;
        double lat = obj.optDouble("lat", Double.NaN);
        double lng = obj.optDouble("lng", Double.NaN);
        double acc = obj.optDouble("accuracy", 0);
        if (Double.isNaN(lat) || Double.isNaN(lng) || Double.isInfinite(lat) || Double.isInfinite(lng)) return null;
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;
        if (Double.isNaN(acc) || Double.isInfinite(acc) || acc < 0) acc = 0;
        return new PeerLocation(uid, lat, lng, acc);
    }

    private void sendHello(String endpointId) {
        try {
            JSONObject hello = new JSONObject();
            hello.put("type", "hello");
            hello.put("uid", userUid);
            PeerLocation own = ownLocation;
            if (own != null) {
                hello.put("lat", own.lat);
                hello.put("lng", own.lng);
                hello.put("accuracy", own.accuracy);
            }
            sendToPeer(endpointId, hello.toString());
        } catch (Exception e) {
            Log.e(TAG, "Could not send hello", e);
        }
    }

    public void start() {
        running = true;
        startAdvertising();
        startDiscovery();
    }

    private void startAdvertising() {
        try {
            AdvertisingOptions options = new AdvertisingOptions.Builder()
                    .setStrategy(Strategy.P2P_CLUSTER).build();
            client().startAdvertising(userUid.isEmpty() ? deviceId : userUid, SERVICE_ID, connectionCallback, options)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Advertising started"))
                    .addOnFailureListener(e -> Log.e(TAG, "Advertising failed", e));
        } catch (Throwable t) {
            Log.e(TAG, "Advertising error", t);
        }
    }

    private void startDiscovery() {
        try {
            DiscoveryOptions options = new DiscoveryOptions.Builder()
                    .setStrategy(Strategy.P2P_CLUSTER).build();
            client().startDiscovery(SERVICE_ID, discoveryCallback, options)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Discovery started"))
                    .addOnFailureListener(e -> Log.e(TAG, "Discovery failed", e));
        } catch (Throwable t) {
            Log.e(TAG, "Discovery error", t);
        }
    }

    public void setOwnLocation(double lat, double lng, double accuracy) {
        ownLocation = new PeerLocation(userUid, lat, lng, accuracy);
    }

    public void broadcastLocation(double lat, double lng, double accuracy) {
        if (Double.isNaN(lat) || Double.isNaN(lng)) return;
        if (Double.isNaN(accuracy) || Double.isInfinite(accuracy)) accuracy = 0;
        setOwnLocation(lat, lng, accuracy);
        try {
            JSONObject packet = new JSONObject();
            packet.put("type", "location");
            packet.put("uid", userUid);
            packet.put("lat", lat);
            packet.put("lng", lng);
            packet.put("accuracy", accuracy);
            broadcast(packet.toString(), null);
        } catch (Exception e) {
            Log.w(TAG, "Could not broadcast location", e);
        }
    }

    public Map<String, PeerLocation> snapshotPeerLocations() {
        return new HashMap<>(peerLocations);
    }

    public static final class PeerLocation {
        public final String uid;
        public final double lat;
        public final double lng;
        public final double accuracy;
        public PeerLocation(String uid, double lat, double lng, double accuracy) {
            this.uid = uid; this.lat = lat; this.lng = lng; this.accuracy = accuracy;
        }
    }

    public void sendToPeer(String endpointId, String json) {
        if (endpointId == null || json == null) return;
        try {
            client().sendPayload(endpointId, Payload.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            Log.w(TAG, "sendPayload failed", t);
        }
    }

    public boolean sendToUser(String recipientUid, String json) {
        if (recipientUid == null || recipientUid.isEmpty() || json == null) return false;
        for (Map.Entry<String, String> entry : endpointUserIds.entrySet()) {
            if (recipientUid.equals(entry.getValue()) && connectedPeers.containsKey(entry.getKey())) {
                sendToPeer(entry.getKey(), json);
                return true;
            }
        }
        return false;
    }

    public void broadcast(String json, String exceptEndpoint) {
        for (String endpointId : connectedPeers.keySet()) {
            if (!endpointId.equals(exceptEndpoint)) sendToPeer(endpointId, json);
        }
    }

    /** Hand a message that arrived from the server (not from a nearby peer) to the router. */
    public void deliverLocally(String json) {
        router.handleMessage(null, json);
    }

    public void stop() {
        running = false;
        try { client().stopAdvertising(); } catch (Throwable ignored) {}
        try { client().stopDiscovery(); } catch (Throwable ignored) {}
        try { client().stopAllEndpoints(); } catch (Throwable ignored) {}
        connectedPeers.clear();
        endpointUserIds.clear();
        peerLocations.clear();
        pendingRequests.clear();
    }

    public String getDeviceId() { return deviceId; }
    public String getUserUid() { return userUid; }
    public Map<String, String> getConnectedPeers() { return connectedPeers; }
    public Map<String, String> getEndpointUserIds() { return endpointUserIds; }
}
