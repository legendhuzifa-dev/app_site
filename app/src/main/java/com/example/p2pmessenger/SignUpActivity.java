package com.example.p2pmessenger;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class SignUpActivity extends AppCompatActivity {
    private LinearLayout layoutStep1, layoutStep2;
    private EditText etFirstName, etLastName, etDob, etEmail, etCustomUid;
    private Button btnSendOtp, btnFinalRegister;

    private String verifiedEmail = "";
    private String verificationToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        layoutStep1 = findViewById(R.id.layoutStep1);
        layoutStep2 = findViewById(R.id.layoutStep2);
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etDob = findViewById(R.id.etDob);
        etEmail = findViewById(R.id.etEmail);
        etCustomUid = findViewById(R.id.etCustomUid);
        btnSendOtp = findViewById(R.id.btnSendOtp);
        btnFinalRegister = findViewById(R.id.btnFinalRegister);

        String email = getIntent().getStringExtra("VERIFIED_EMAIL");
        String token = getIntent().getStringExtra("VERIFICATION_TOKEN");
        verifiedEmail = email == null ? "" : email;
        verificationToken = token == null ? "" : token;

        String incomingFirst = getIntent().getStringExtra("SIGNUP_FIRST_NAME");
        String incomingLast = getIntent().getStringExtra("SIGNUP_LAST_NAME");
        String incomingDob = getIntent().getStringExtra("SIGNUP_DOB");
        if (incomingFirst != null) etFirstName.setText(incomingFirst);
        if (incomingLast != null) etLastName.setText(incomingLast);
        if (incomingDob != null) etDob.setText(incomingDob);

        if (!verifiedEmail.isEmpty() && !verificationToken.isEmpty()) {
            etEmail.setText(verifiedEmail);
            etEmail.setEnabled(false);
            showStep2();
        }

        btnSendOtp.setOnClickListener(v -> requestSignupOtp());
        btnFinalRegister.setOnClickListener(v -> registerUser());
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void requestSignupOtp() {
        final String first = etFirstName.getText().toString().trim();
        final String last = etLastName.getText().toString().trim();
        final String dob = etDob.getText().toString().trim();
        final String email = etEmail.getText().toString().trim();

        if (first.isEmpty() || last.isEmpty() || dob.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "নাম, জন্মতারিখ ও সঠিক ইমেইল দিন।", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendOtp.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("purpose", "signup");
                body.put("first_name", first);
                body.put("last_name", last);
                body.put("dob", dob);
                ApiClient.post("/otp/request", body);

                runOnUiThread(() -> {
                    btnSendOtp.setEnabled(true);
                    if (!alive()) return;
                    Intent intent = new Intent(this, OtpActivity.class);
                    intent.putExtra("USER_EMAIL", email);
                    intent.putExtra("OTP_PURPOSE", "signup");
                    intent.putExtra("SIGNUP_FIRST_NAME", first);
                    intent.putExtra("SIGNUP_LAST_NAME", last);
                    intent.putExtra("SIGNUP_DOB", dob);
                    startActivity(intent);
                });
            } catch (Exception e) {
                final String msg = ApiClient.friendly(e);
                runOnUiThread(() -> {
                    btnSendOtp.setEnabled(true);
                    if (alive()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showStep2() {
        layoutStep1.setVisibility(View.GONE);
        layoutStep2.setVisibility(View.VISIBLE);
    }

    private void registerUser() {
        final String uid = etCustomUid.getText().toString().trim();
        if (uid.isEmpty()) {
            etCustomUid.setError("দয়া করে UID দিন");
            return;
        }
        if (uid.length() > 80) {
            etCustomUid.setError("UID সর্বোচ্চ ৮০ অক্ষরের হতে পারে");
            return;
        }
        if (TextUtils.isEmpty(verificationToken) || TextUtils.isEmpty(verifiedEmail)) {
            Toast.makeText(this, "আগে ইমেইল OTP ভেরিফাই করুন।", Toast.LENGTH_SHORT).show();
            return;
        }

        // Read all UI values on the UI thread, then use them in the worker thread.
        final String first = etFirstName.getText().toString().trim();
        final String last = etLastName.getText().toString().trim();
        final String dob = etDob.getText().toString().trim();
        final String email = verifiedEmail;
        final String token = verificationToken;

        btnFinalRegister.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("verification_token", token);
                body.put("uid", uid);
                body.put("first_name", first);
                body.put("last_name", last);
                body.put("dob", dob);
                body.put("device_name", android.os.Build.MODEL == null ? "" : android.os.Build.MODEL);

                final JSONObject result = ApiClient.post("/user/register", body);

                runOnUiThread(() -> {
                    Session.save(uid, (first + " " + last).trim(), email, result.optString("session_token", ""));
                    if (!alive()) return;
                    Toast.makeText(this, "অ্যাকাউন্ট সফলভাবে তৈরি হয়েছে!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("USER_UID", uid);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                final String msg = ApiClient.friendly(e);
                runOnUiThread(() -> {
                    btnFinalRegister.setEnabled(true);
                    if (alive()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
