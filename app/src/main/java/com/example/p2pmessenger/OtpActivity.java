package com.example.p2pmessenger;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import java.util.Locale;

public class OtpActivity extends AppCompatActivity {
    private static final long OTP_TOTAL_MS = 300000L;
    private static final long RESEND_AFTER_SEC = 30L;

    private EditText etOtpCode;
    private Button btnVerifyOtp;
    private TextView tvTimer, tvResendOtp;
    private String userEmail = "";
    private String purpose = "login";
    private CountDownTimer countDownTimer;
    private boolean expired = false;
    private boolean resendEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String email = getIntent().getStringExtra("USER_EMAIL");
        userEmail = email == null ? "" : email;
        String p = getIntent().getStringExtra("OTP_PURPOSE");
        purpose = "signup".equals(p) ? "signup" : "login";

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "ইমেইল পাওয়া যায়নি, আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 80, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#121212"));

        TextView title = new TextView(this);
        title.setText("OTP Verification");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("6-digit code sent to: " + userEmail);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#B0BEC5"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 15, 0, 30);
        layout.addView(subtitle);

        etOtpCode = new EditText(this);
        etOtpCode.setHint("Enter 6-digit OTP");
        etOtpCode.setHintTextColor(Color.parseColor("#757575"));
        etOtpCode.setTextColor(Color.WHITE);
        etOtpCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        etOtpCode.setGravity(Gravity.CENTER);
        etOtpCode.setTextSize(18);
        etOtpCode.setPadding(30, 30, 30, 30);
        etOtpCode.setBackgroundColor(Color.parseColor("#1E1E1E"));
        layout.addView(etOtpCode);

        tvTimer = new TextView(this);
        tvTimer.setTextColor(Color.parseColor("#FF1744"));
        tvTimer.setGravity(Gravity.CENTER);
        tvTimer.setPadding(0, 20, 0, 20);
        layout.addView(tvTimer);

        btnVerifyOtp = new Button(this);
        btnVerifyOtp.setText("VERIFY & CONTINUE");
        btnVerifyOtp.setTextColor(Color.BLACK);
        btnVerifyOtp.setBackgroundColor(Color.parseColor("#00E676"));
        btnVerifyOtp.setTypeface(null, Typeface.BOLD);
        layout.addView(btnVerifyOtp);

        tvResendOtp = new TextView(this);
        tvResendOtp.setText("Didn't receive code? Resend OTP");
        tvResendOtp.setTextSize(14);
        tvResendOtp.setTextColor(Color.parseColor("#757575"));
        tvResendOtp.setGravity(Gravity.CENTER);
        tvResendOtp.setPadding(0, 40, 0, 20);
        tvResendOtp.setEnabled(false);
        layout.addView(tvResendOtp);

        setContentView(layout);
        startOtpTimer();

        btnVerifyOtp.setOnClickListener(v -> verifyOtp());
        tvResendOtp.setOnClickListener(v -> requestResend());
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void verifyOtp() {
        if (expired) {
            Toast.makeText(this, "OTP এর মেয়াদ শেষ! Resend OTP চাপুন।", Toast.LENGTH_LONG).show();
            return;
        }
        final String code = etOtpCode.getText().toString().trim();
        if (TextUtils.isEmpty(code) || code.length() != 6) {
            Toast.makeText(this, "৬ ডিজিটের OTP দিন!", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(this, "লগইন সম্পন্ন করা যায়নি (UID পাওয়া যায়নি)।", Toast.LENGTH_LONG).show();
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
                            Toast.makeText(this, "ভেরিফিকেশন ব্যর্থ হয়েছে। আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show();
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
                tvTimer.setText(String.format(Locale.US, "OTP expires in: %02d:%02d", seconds / 60, seconds % 60));
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
