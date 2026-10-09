package com.example.p2pmessenger;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    // এটি একটি কমন সিক্রেট কি (এটি ৩০ ক্যারেক্টারের বা নির্দিষ্ট দৈর্ঘ্যের হতে হবে)
    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "PertoMeshSecureKey2026!@#$".getBytes();

    public static String encrypt(String value) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(KEY, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] encryptedValue = cipher.doFinal(value.getBytes("UTF-8"));
            return Base64.encodeToString(encryptedValue, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return value; // ফেইল করলে প্লেন টেক্সট রিটার্ন করবে
        }
    }

    public static String decrypt(String value) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(KEY, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            byte[] decodedValue = Base64.decode(value, Base64.DEFAULT);
            byte[] decryptedValue = cipher.doFinal(decodedValue);
            return new String(decryptedValue, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return value; // ডিক্রিপ্ট না হলে যেমন আছে তেমনই দেখাবে
        }
    }
}
