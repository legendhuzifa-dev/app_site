package com.example.p2pmessenger;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private String userCustomUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvMyUid = findViewById(R.id.tvMyUid);
        Button btnCopyUid = findViewById(R.id.btnCopyUid);
        Button btnShareUid = findViewById(R.id.btnShareUid);
        Button btnLogout = findViewById(R.id.btnLogout);

        String uid = Session.uid();
        userCustomUid = uid.isEmpty() ? "N/A" : uid;
        tvMyUid.setText("আমার UID: " + userCustomUid);

        btnCopyUid.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText("PertoMesh UID", userCustomUid));
            Toast.makeText(this, "UID কপি হয়ে গেছে!", Toast.LENGTH_SHORT).show();
        });

        btnShareUid.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "আমার Perto Mesh UID হলো: " + userCustomUid + "। এই আইডি দিয়ে আমাকে অ্যাড করে নাও!");
            try {
                startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"));
            } catch (Exception e) {
                Toast.makeText(this, "শেয়ার করা যায়নি।", Toast.LENGTH_SHORT).show();
            }
        });

        btnLogout.setOnClickListener(v -> logout(btnLogout));
    }

    private void logout(Button btn) {
        btn.setEnabled(false);
        final String uid = Session.uid();
        new Thread(() -> {
            // Tell the server first (needs the token), then wipe the local session.
            try {
                if (!uid.isEmpty()) {
                    JSONObject body = new JSONObject();
                    body.put("group_id", ServerConfig.MESH_GROUP_ID);
                    body.put("uid", uid);
                    ApiClient.post("/mesh/leave", body);
                }
            } catch (Exception ignored) {
                // offline logout is fine
            }
            runOnUiThread(() -> {
                try { stopService(new Intent(this, MeshService.class)); } catch (Exception ignored) {}
                Session.clear();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }).start();
    }
}
