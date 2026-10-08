package com.example.p2pmessenger;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail;
    private Button btnLogin;
    private TextView tvGoToSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Session.isLoggedIn()) {
            openMain(Session.uid(), Session.name());
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToSignUp = findViewById(R.id.tvGoToSignUp);

        btnLogin.setOnClickListener(v -> requestLoginOtp());
        tvGoToSignUp.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void requestLoginOtp() {
        final String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "একটি সঠিক ইমেইল এড্রেস দিন!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("purpose", "login");
                ApiClient.post("/otp/request", body);

                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    if (!alive()) return;
                    Intent intent = new Intent(this, OtpActivity.class);
                    intent.putExtra("USER_EMAIL", email);
                    intent.putExtra("OTP_PURPOSE", "login");
                    startActivity(intent);
                });
            } catch (Exception e) {
                final String msg = ApiClient.friendly(e);
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    if (alive()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openMain(String uid, String name) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("USER_UID", uid);
        intent.putExtra("USER_NAME", name);
        startActivity(intent);
        finish();
    }
}
