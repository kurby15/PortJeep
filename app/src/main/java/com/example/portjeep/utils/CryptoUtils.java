package com.example.portjeep.utils;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    /**
     * Decrypts a CryptoJS AES-encrypted Base64 string starting with "U2FsdGVkX1..."
     *
     * @param cipherText The encrypted string from Firestore
     * @param secretKey  The secret key/passphrase used during encryption
     * @return Decrypted plain text string, or original text if decryption fails
     */
    public static String decrypt(String cipherText, String secretKey) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }

        try {
            byte[] cipherData = Base64.decode(cipherText, Base64.DEFAULT);

            // "Salted__" header check
            byte[] saltHeader = "Salted__".getBytes(StandardCharsets.US_ASCII);
            if (cipherData.length < 16) return cipherText;

            for (int i = 0; i < 8; i++) {
                if (cipherData[i] != saltHeader[i]) {
                    // Return raw string if it's not CryptoJS encrypted
                    return cipherText;
                }
            }

            // Extract 8-byte salt
            byte[] salt = new byte[8];
            System.arraycopy(cipherData, 8, salt, 0, 8);

            // Extract ciphertext
            byte[] encrypted = new byte[cipherData.length - 16];
            System.arraycopy(cipherData, 16, encrypted, 0, cipherData.length - 16);

            // OpenSSL / CryptoJS key derivation (EVP_BytesToKey)
            byte[][] keyAndIv = deriveKeyAndIV(secretKey.getBytes(StandardCharsets.UTF_8), salt);
            SecretKeySpec keySpec = new SecretKeySpec(keyAndIv[0], "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(keyAndIv[1]);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            e.printStackTrace();
            return cipherText; // Fallback to raw text if decryption fails
        }
    }

    private static byte[][] deriveKeyAndIV(byte[] password, byte[] salt) throws Exception {
        byte[] key = new byte[32]; // 256-bit key
        byte[] iv = new byte[16];  // 128-bit IV

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] currentHash = new byte[0];
        byte[] keyAndIv = new byte[48];
        int currentLength = 0;

        while (currentLength < 48) {
            md.reset();
            md.update(currentHash);
            md.update(password);
            md.update(salt);
            currentHash = md.digest();

            int bytesToCopy = Math.min(currentHash.length, 48 - currentLength);
            System.arraycopy(currentHash, 0, keyAndIv, currentLength, bytesToCopy);
            currentLength += bytesToCopy;
        }

        System.arraycopy(keyAndIv, 0, key, 0, 32);
        System.arraycopy(keyAndIv, 32, iv, 0, 16);

        return new byte[][]{key, iv};
    }
}