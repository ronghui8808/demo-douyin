package com.example.douyin.friends;

import com.example.douyin.auth.PhoneValidator;

public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    /**
     * Normalize a raw contact phone to a mainland 11-digit mobile, or null if invalid.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.startsWith("0086") && cleaned.length() > 4) {
            cleaned = cleaned.substring(4);
        } else if (cleaned.startsWith("86") && cleaned.length() == 13) {
            cleaned = cleaned.substring(2);
        }
        if (PhoneValidator.isValidPhone(cleaned)) {
            return cleaned;
        }
        return null;
    }
}
