package com.liana.post.postage.retail;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class StampCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return format(HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT));
    }

    public static String normalize(String code) {
        String value = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!value.matches("LP[A-F0-9]{32}")) throw new IllegalArgumentException("数码邮票代码格式无效");
        return format(value.substring(2));
    }

    private static String format(String hex) {
        return "LP-" + hex.substring(0, 8) + "-" + hex.substring(8, 16) + "-" + hex.substring(16, 24) + "-" + hex.substring(24);
    }
}
