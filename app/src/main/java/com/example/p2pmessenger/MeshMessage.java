package com.example.p2pmessenger;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class MeshMessage {

    public String messageId;
    public String senderId;
    public String recipientId;
    public String payload;
    public int ttl;
    public long timestamp;
    public double senderLat;
    public double senderLng;

    public MeshMessage(String senderId, String recipientId, String rawPayload) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.payload = CryptoUtils.encrypt(rawPayload);
        this.ttl = 5;
        this.timestamp = System.currentTimeMillis();
        this.senderLat = 0.0;
        this.senderLng = 0.0;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("messageId", messageId);
        object.put("senderId", senderId);
        object.put("recipientId", recipientId);
        object.put("payload", payload);
        object.put("ttl", ttl);
        object.put("timestamp", timestamp);
        object.put("senderLat", senderLat);
        object.put("senderLng", senderLng);
        return object;
    }

    public static MeshMessage fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        MeshMessage message = new MeshMessage(
                object.getString("senderId"),
                object.getString("recipientId"),
                ""
        );
        message.messageId = object.getString("messageId");
        message.payload = object.getString("payload");
        message.ttl = object.getInt("ttl");
        message.timestamp = object.getLong("timestamp");
        if (object.has("senderLat")) message.senderLat = object.getDouble("senderLat");
        if (object.has("senderLng")) message.senderLng = object.getDouble("senderLng");
        return message;
    }

    public String getDecryptedPayload() {
        return CryptoUtils.decrypt(payload);
    }
}
