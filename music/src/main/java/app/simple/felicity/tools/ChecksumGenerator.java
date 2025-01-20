package app.simple.felicity.tools;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class ChecksumGenerator {
    
    private static final String TAG = "ChecksumGenerator";
    
    public static String calculateSHA256Checksum(String filepath) {
        try (InputStream inputStream = Files.newInputStream(Paths.get(filepath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            byte[] block = new byte[4096];
            int length;
            while ((length = inputStream.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            
            return bytesToHex(digest.digest());
        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error creating SHA-256 digest", e);
            return null;
        }
    }
    
    public static String calculateSHA256Checksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error creating SHA-256 digest", e);
            return null;
        }
    }
    
    public static String calculateSHA256Checksum(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            byte[] block = new byte[4096];
            int length;
            while ((length = inputStream.read(block)) > 0) {
                digest.update(block, 0, length);
            }
            
            return bytesToHex(digest.digest());
        } catch (IOException e) {
            Log.e(TAG, "Error reading input stream", e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error creating SHA-256 digest", e);
            return null;
        }
    }
    
    public static String calculateSHA256Checksum(File file) {
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return calculateSHA256Checksum(inputStream);
        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
            return null;
        }
    }
    
    private static String bytesToHex(byte[] bytes) {
        try (Formatter formatter = new Formatter()) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }
}
