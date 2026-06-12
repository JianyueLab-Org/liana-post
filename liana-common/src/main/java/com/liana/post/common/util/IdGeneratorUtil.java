package com.liana.post.common.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务编号生成工具。
 */
public final class IdGeneratorUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter ID_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private IdGeneratorUtil() {
    }

    public static String generateBizNo(String prefix) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim();
        String timestamp = LocalDateTime.now().format(ID_TIME_FORMATTER);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return normalizedPrefix + timestamp + randomPart;
    }

    public static String generateDispatchNo() {
        return generateBizNo("D");
    }

    public static String generateBagNo() {
        return generateBizNo("B");
    }

    public static String generateTrackingEventNo() {
        return generateBizNo("T");
    }

    public static String generateSalt(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }
        final char[] alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(alphabet[SECURE_RANDOM.nextInt(alphabet.length)]);
        }
        return builder.toString();
    }
}