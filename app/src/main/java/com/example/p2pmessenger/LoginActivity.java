package com.example.p2pmessenger;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 100, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#121212"));

        TextView title = new TextView(this);
        title.setText("P2P Messenger Login");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        layout.addView(title);

        etEmail = new EditText(this);
        etEmail.setHint("Enter Registered Email");
        etEmail.setHintTextColor(Color.parseColor("#757575"));
        etEmail.setTextColor(Color.WHITE);
        etEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setTextSize(16);
        etEmail.setPadding(35, 35, 35, 35);
        etEmail.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.addView(etEmail);

        btnLogin = new Button(this);
        btnLogin.setText("LOGIN WITH OTP");
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setBackgroundColor(Color.parseColor("#00E676"));
        btnLogin.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, 30, 0, 0);
        btnLogin.setLayoutParams(bp);
        layout.addView(btnLogin);

        tvGoToSignUp = new TextView(this);
        tvGoToSignUp.setText("Don't have an account? Sign Up here");
        tvGoToSignUp.setTextColor(Color.parseColor("#00E676"));
        tvGoToSignUp.setTextSize(14);
        tvGoToSignUp.setGravity(Gravity.CENTER);
        tvGoToSignUp.setPadding(0, 40, 0, 0);
        layout.addView(tvGoToSignUp);

        setContentView(layout);

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
