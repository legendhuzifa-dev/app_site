package com.example.p2pmessenger;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST = 7001;

    private TextView myCustomUidText;
    private RecyclerView peerRecyclerView;
    private FloatingActionButton fabAddFriend;
    private String userUid = "";
    private boolean redirected = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Session.isLoggedIn()) {
            goToLogin();
            return;
        }
        setContentView(R.layout.activity_main);
        userUid = Session.uid();

        myCustomUidText = findViewById(R.id.myCustomUidText);
        peerRecyclerView = findViewById(R.id.peerRecyclerView);
        fabAddFriend = findViewById(R.id.fabAddFriend);

        peerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        myCustomUidText.setText("UID: " + userUid.substring(0, Math.min(userUid.length(), 8))
                + (userUid.length() > 8 ? "..." : ""));
        myCustomUidText.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        fabAddFriend.setOnClickListener(v -> showAddFriendDialog());
        requestMeshPermissions();
    }

    @Override protected void onResume() {
        super.onResume();
        if (redirected) return;
        if (!Session.isLoggedIn()) {
            goToLogin(); // session was cleared (logout / expired on the server)
            return;
        }
        // If the user granted permissions from system settings, start the mesh now.
        if (requiredPermissionsGranted()) startMeshService();
    }

    private void goToLogin() {
        redirected = true;
        try { stopService(new Intent(this, MeshService.class)); } catch (Exception ignored) {}
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Everything the mesh needs. Notifications are optional (service still runs without them). */
    private boolean requiredPermissionsGranted() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)
                && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)
                    || !granted(Manifest.permission.BLUETOOTH_CONNECT)
                    || !granted(Manifest.permission.BLUETOOTH_ADVERTISE)) return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!granted(Manifest.permission.NEARBY_WIFI_DEVICES)) return false;
        }
        return true;
    }

    private void requestMeshPermissions() {
        List<String> wanted = new ArrayList<>();
        wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wanted.add(Manifest.permission.BLUETOOTH_SCAN);
            wanted.add(Manifest.permission.BLUETOOTH_CONNECT);
            wanted.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> missing = new ArrayList<>();
        for (String p : wanted) if (!granted(p)) missing.add(p);

        if (missing.isEmpty()) {
            startMeshService();
        } else {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != PERMISSION_REQUEST) return;
        if (requiredPermissionsGranted()) {
            startMeshService();
        } else {
            Toast.makeText(this, "Mesh চালাতে Bluetooth/Location/Nearby permission প্রয়োজন। Settings থেকে অনুমতি দিন।",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startMeshService() {
        try {
            ContextCompat.startForegroundService(this, new Intent(this, MeshService.class));
        } catch (RuntimeException e) {
            // e.g. background start restrictions or a missing permission: never crash the UI.
            Toast.makeText(this, "Mesh সার্ভিস চালু করা যায়নি।", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddFriendDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_friend, null);
        EditText etTargetUid = dialogView.findViewById(R.id.etTargetUid);
        EditText etNickName = dialogView.findViewById(R.id.etNickName);
        Button btnAdd = dialogView.findViewById(R.id.btnAdd);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        btnAdd.setOnClickListener(v -> {
            String uid = etTargetUid.getText().toString().trim();
            String nick = etNickName.getText().toString().trim();
            if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(nick)) {
                Toast.makeText(this, "UID ও Nickname দুটোই দিন।", Toast.LENGTH_SHORT).show();
                return;
            }
            // Contact persistence/UI can be attached to the local database next.
            Toast.makeText(this, "Contact added: " + nick, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }
}
