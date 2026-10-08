package com.example.p2pmessenger;

import android.util.Log;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MessageRouter {
    private static final String TAG = "MessageRouter";
    private static final int MAX_REMEMBERED_IDS = 5000;

    private final String currentUserUid;
    private final Map<String, String> connectedPeers;
    private final Map<String, String> endpointUserIds;
    private final MeshManager meshManager;
    private final Set<String> processedMessages =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    public MessageRouter(String currentUserUid,
                          Map<String, String> connectedPeers,
                          Map<String, String> endpointUserIds,
                          MeshManager meshManager) {
        this.currentUserUid = currentUserUid == null ? "" : currentUserUid;
        this.connectedPeers = connectedPeers;
        this.endpointUserIds = endpointUserIds;
        this.meshManager = meshManager;
    }

    /** incomingEndpoint is null when the message did not come from a nearby peer (e.g. from the server). */
    public void handleMessage(String incomingEndpoint, String json) {
        try {
            MeshMessage message = MeshMessage.fromJson(json);
            if (message.messageId == null || message.messageId.isEmpty()) return;

            // Bounded memory: forget old ids instead of growing forever.
            if (processedMessages.size() > MAX_REMEMBERED_IDS) processedMessages.clear();
            if (!processedMessages.add(message.messageId)) return;

            if (message.recipientId.equals(currentUserUid)) {
                deliverMessage(message);
                return;
            }

            if (message.ttl <= 0) return;
            message.ttl--;
            String updatedJson = message.toJson().toString();

            // Prefer the exact peer whose advertised UID matches the recipient.
            if (meshManager.sendToUser(message.recipientId, updatedJson)) return;

            // Otherwise forward once through other mesh peers.
            for (String endpointId : connectedPeers.keySet()) {
                if (!endpointId.equals(incomingEndpoint)) {
                    meshManager.sendToPeer(endpointId, updatedJson);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid mesh message", e);
        }
    }

    private void deliverMessage(MeshMessage message) {
        Log.d(TAG, "Message arrived for " + currentUserUid + " from " + message.senderId);
        // UI/database notification can be attached here.
    }
}
