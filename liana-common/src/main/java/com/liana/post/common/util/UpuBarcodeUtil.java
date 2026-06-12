package com.liana.post.common.util;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * UPU S10 国际邮件号工具。
 *
 * 格式：AA123456789AA
 * - 前 2 位：服务标识
 * - 中间 8 位：序列号
 * - 第 11 位：校验码
 * - 后 2 位：国家/地区代码
 */
public final class UpuBarcodeUtil {

    private static final Pattern S10_PATTERN = Pattern.compile("^[A-Z]{2}\\d{9}[A-Z]{2}$");
    private static final int[] WEIGHT_FACTORS = {8, 6, 4, 2, 3, 5, 9, 7};
    private static final int MODULO = 11;
    private static final String DEFAULT_COUNTRY_CODE = "LN";

    private UpuBarcodeUtil() {
    }

    public static boolean validate(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        String normalized = normalize(barcode);
        if (!S10_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        String serial = normalized.substring(2, 10);
        char checksum = normalized.charAt(10);
        return checksum == calculateChecksum(serial);
    }

    public static String generate(String serviceIndicator, String serialNumber, String countryCode) {
        String normalizedServiceIndicator = normalizeServiceIndicator(serviceIndicator);
        String normalizedSerial = normalizeSerial(serialNumber);
        String normalizedCountry = normalizeCountryCode(countryCode);
        return normalizedServiceIndicator + normalizedSerial + calculateChecksum(normalizedSerial) + normalizedCountry;
    }

    public static String generateRandom(String serviceIndicator) {
        return generateRandom(serviceIndicator, DEFAULT_COUNTRY_CODE);
    }

    public static String generateRandom(String serviceIndicator, String countryCode) {
        Random random = new Random();
        String serialNumber = String.format("%08d", random.nextInt(100_000_000));
        return generate(serviceIndicator, serialNumber, countryCode);
    }

    public static String normalize(String barcode) {
        return barcode == null ? null : barcode.trim().toUpperCase();
    }

    private static String normalizeServiceIndicator(String serviceIndicator) {
        String normalized = normalize(serviceIndicator);
        if (normalized == null || !normalized.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("serviceIndicator must be 2 letters");
        }
        return normalized;
    }

    private static String normalizeSerial(String serialNumber) {
        String normalized = serialNumber == null ? null : serialNumber.trim();
        if (normalized == null || !normalized.matches("^\\d{8}$")) {
            throw new IllegalArgumentException("serialNumber must be 8 digits");
        }
        return normalized;
    }

    private static String normalizeCountryCode(String countryCode) {
        String normalized = normalize(countryCode);
        if (normalized == null || !normalized.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("countryCode must be 2 letters");
        }
        return normalized;
    }

    private static char calculateChecksum(String serialNumber) {
        if (serialNumber == null || !serialNumber.matches("^\\d{8}$")) {
            throw new IllegalArgumentException("serialNumber must be 8 digits");
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            int digit = Character.getNumericValue(serialNumber.charAt(i));
            sum += digit * WEIGHT_FACTORS[i];
        }
        int remainder = sum % MODULO;
        int checksum = MODULO - remainder;
        if (checksum == MODULO) {
            checksum = 5;
        } else if (checksum == 10) {
            checksum = 0;
        }
        return Character.forDigit(checksum, 10);
    }
}
