package com.oussama_chatri.productivityx.core.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cryptographic utilities. SHA-256 is guaranteed present in every JVM,
 * so NoSuchAlgorithmException is treated as an unrecoverable startup fault.
 */
@Component
public class CryptoUtils {

    private static final MessageDigest SHA_256;

    static {
        try {
            SHA_256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError("SHA-256 not available — JVM is broken");
        }
    }

    /**
     * Thread-safe SHA-256 hex digest. Each call clones the cached digest
     * rather than creating a new MessageDigest instance.
     */
    public String sha256Hex(String input) {
        try {
            MessageDigest digest = (MessageDigest) SHA_256.clone();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (CloneNotSupportedException e) {
            // MessageDigest.clone() is supported by every standard provider
            throw new IllegalStateException("SHA-256 clone failed", e);
        }
    }
}