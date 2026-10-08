package com.example.p2pmessenger;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Locale;

public class OtpActivity extends AppCompatActivity {
    private static final long OTP_TOTAL_MS = 300000L;
    private static final long RESEND_AFTER_SEC = 30L;

    private EditText otp1, otp2, otp3, otp4, otp5, otp6;
    private Button btnVerifyOtp;
    private TextView tvSubtitle, tvTimer, tvResendOtp;
    private String userEmail = "";
    private String purpose = "login";
    private CountDownTimer countDownTimer;
    private boolean expired = false;
    private boolean resendEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        String email = getIntent().getStringExtra("USER_EMAIL");
        userEmail = email == null ? "" : email;
        String p = getIntent().getStringExtra("OTP_PURPOSE");
        purpose = "signup".equals(p) ? "signup" : "login";

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "ইমেইল পাওয়া যায়নি, আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvSubtitle.setText("6-digit code sent to: " + userEmail);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);

        tvTimer = findViewById(R.id.tvTimer);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        tvResendOtp = findViewById(R.id.tvResendOtp);

        setupOtpInputAutoJump();
        startOtpTimer();

        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        tvResendOtp.setOnClickListener(v -> requestResend());
    }

    private void setupOtpInputAutoJump() {
        EditText[] boxes = new EditText[]{otp1, otp2, otp3, otp4, otp5, otp6};
        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && index < boxes.length - 1) {
                        boxes[index + 1].requestFocus();
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private String getEnteredOtp() {
        return otp1.getText().toString().trim() +
               otp2.getText().toString().trim() +
               otp3.getText().toString().trim() +
               otp4.getText().toString().trim() +
               otp5.getText().toString().trim() +
               otp6.getText().toString().trim();
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void verifyOtp() {
        if (expired) {
            Toast.makeText(this, "OTP এর মেয়াদ শেষ! Resend OTP চাপুন।", Toast.LENGTH_LONG).show();
            return;
        }
        final String code = getEnteredOtp();
        if (code.length() != 6) {
            Toast.makeText(this, "৬ ডিজিটের পুরো OTP লিখুন!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnVerifyOtp.setEnabled(false);
        final String email = userEmail;
        final String purposeNow = purpose;
        final String first = getIntent().getStringExtra("SIGNUP_FIRST_NAME");
        final String last = getIntent().getStringExtra("SIGNUP_LAST_NAME");
        final String dob = getIntent().getStringExtra("SIGNUP_DOB");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("purpose", purposeNow);
                body.put("code", code);
                final JSONObject result = ApiClient.post("/otp/verify", body);

                runOnUiThread(() -> {
                    if (!alive()) return;
                    if ("login".equals(purposeNow)) {
                        String uid = result.optString("uid", "");
                        String name = result.optString("name", "User");
                        if (uid.isEmpty()) {
                            btnVerifyOtp.setEnabled(true);
                            Toast.makeText(this, "লগইন সম্পন্ন করা যায়নি।", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Session.save(uid, name, email, result.optString("session_token", ""));
                        if (countDownTimer != null) countDownTimer.cancel();

                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra("USER_UID", uid);
                        intent.putExtra("USER_NAME", name);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        String token = result.optString("verification_token", "");
                        if (token.isEmpty()) {
                            btnVerifyOtp.setEnabled(true);
                            Toast.makeText(this, "ভেরিফিকেশন ব্যর্থ হয়েছে।", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (countDownTimer != null) countDownTimer.cancel();
                        Intent intent = new Intent(this, SignUpActivity.class);
                        intent.putExtra("VERIFICATION_TOKEN", token);
                        intent.putExtra("VERIFIED_EMAIL", email);
                        intent.putExtra("SIGNUP_FIRST_NAME", first);
                        intent.putExtra("SIGNUP_LAST_NAME", last);
                        intent.putExtra("SIGNUP_DOB", dob);
                        startActivity(intent);
                        finish();
                    }
                });
            } catch (Exception e) {
                final String msg = ApiClient.friendly(e);
                runOnUiThread(() -> {
                    btnVerifyOtp.setEnabled(true);
                    if (alive()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void requestResend() {
        if (!resendEnabled) return;
        tvResendOtp.setEnabled(false);
        resendEnabled = false;
        final String email = userEmail;
        final String purposeNow = purpose;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("purpose", purposeNow);
                ApiClient.post("/otp/request", body);
                runOnUiThread(() -> { if (alive()) startOtpTimer(); });
            } catch (Exception e) {
                final String msg = ApiClient.friendly(e);
                runOnUiThread(() -> {
                    if (!alive()) return;
                    setResendEnabled(true);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setResendEnabled(boolean enabled) {
        resendEnabled = enabled;
        tvResendOtp.setEnabled(enabled);
        tvResendOtp.setTextColor(Color.parseColor(enabled ? "#00E676" : "#757575"));
    }

    private void startOtpTimer() {
        expired = false;
        if (countDownTimer != null) countDownTimer.cancel();
        setResendEnabled(false);
        countDownTimer = new CountDownTimer(OTP_TOTAL_MS, 1000) {
            @Override public void onTick(long ms) {
                long seconds = ms / 1000;
                tvTimer.setText(String.format(Locale.US, "Expires in: %02d:%02d", seconds / 60, seconds % 60));
                long elapsed = OTP_TOTAL_MS / 1000 - seconds;
                if (!resendEnabled && elapsed >= RESEND_AFTER_SEC) setResendEnabled(true);
            }
            @Override public void onFinish() {
                expired = true;
                tvTimer.setText("OTP Expired!");
                setResendEnabled(true);
            }
        }.start();
    }

    @Override protected void onDestroy() {
        if (countDownTimer != null) countDownTimer.cancel();
        super.onDestroy();
    }
}
