package com.example.p2pmessenger;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class MeshMessage {

    public String messageId;
    public String senderId;
    public String recipientId;
    public String payload; // এটি এখন অটোমেটিক এনক্রিপ্টেড থাকবে
    public int ttl;
    public long timestamp;
    public double senderLat;
    public double senderLng;

    public MeshMessage(String senderId, String recipientId, String rawPayload) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.recipientId = recipientId;
        // পাঠানোর সময় পেলোড অটোমেটিক AES এনক্রিপ্ট হয়ে যাবে
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
        JSONObject object = new json.JSONObject(json); // or new JSONObject(json)
        JSONObject jsonObj = new JSONObject(json);
        MeshMessage message = new MeshMessage(
                jsonObj.getString("senderId"),
                jsonObj.getString("recipientId"),
                "" // খালি পাস করছি কারণ নিচে পেলোড সরাসরি সেট করা হবে
        );
        message.messageId = jsonObj.getString("messageId");
        // রিসিভ করার পর পেলোড যেমন আছে (এনক্রিপ্টেড) সেটাই থাকবে, পড়ার সময় ডিক্রিপ্ট করতে হবে
        message.payload = jsonObj.getString("payload");
        message.ttl = jsonObj.getInt("ttl");
        message.timestamp = jsonObj.getLong("timestamp");
        if (jsonObj.has("senderLat")) message.senderLat = jsonObj.getDouble("senderLat");
        if (jsonObj.has("senderLng")) message.senderLng = jsonObj.getDouble("senderLng");
        return message;
    }

    // মেসেজ পড়ার জন্য এই মেথড ব্যবহার করবেন (ডিক্রিপ্ট করার জন্য)
    public String getDecryptedPayload() {
        return CryptoUtils.decrypt(payload);
    }
}
