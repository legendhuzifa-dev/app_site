package com.example.p2pmessenger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ApiClient {
    private ApiClient() {}

    private static final int MAX_RESPONSE_CHARS = 2_000_000;

    /**
     * POSTs JSON and returns the JSON answer. Never throws anything except ApiException,
     * and ApiException.getMessage() is always a non-null, user-readable text.
     */
    public static JSONObject post(String path, JSONObject body) throws ApiException {
        HttpURLConnection conn = null;
        String sentToken = Session.token();
        try {
            URL url = new URL(ServerConfig.BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            if (!sentToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + sentToken);
            }

            byte[] bytes = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String response = readAll(stream);

            JSONObject json;
            try {
                json = response.trim().isEmpty() ? new JSONObject() : new JSONObject(response);
            } catch (JSONException e) {
                throw new ApiException(code >= 400 ? code : 502, "সার্ভার থেকে ভুল উত্তর এসেছে (HTTP " + code + ")");
            }

            if (code < 200 || code >= 300) {
                String message = json.optString("message", "");
                if (message.isEmpty()) message = "সার্ভার সমস্যা (HTTP " + code + ")";
                // Session no longer valid on the server -> force a fresh login.
                if (code == 401 && !sentToken.isEmpty()
                        && !path.startsWith("/otp") && !path.startsWith("/user")) {
                    Session.clear();
                }
                throw new ApiException(code, message);
            }
            return json;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(0, "সার্ভারের সাথে সংযোগ করা যায়নি। ইন্টারনেট ও সার্ভার চেক করুন।");
        } catch (Exception e) {
            throw new ApiException(0, "অপ্রত্যাশিত সমস্যা হয়েছে। আবার চেষ্টা করুন।");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                if (sb.length() > MAX_RESPONSE_CHARS) throw new IOException("Response too large");
            }
        }
        return sb.toString();
    }

    /** Safe text for a Toast from any exception. */
    public static String friendly(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return (m == null || m.trim().isEmpty()) ? "কিছু সমস্যা হয়েছে। আবার চেষ্টা করুন।" : m;
    }

    public static class ApiException extends Exception {
        public final int statusCode;
        public ApiException(int statusCode, String message) {
            super(message == null ? "" : message);
            this.statusCode = statusCode;
        }
    }
}
