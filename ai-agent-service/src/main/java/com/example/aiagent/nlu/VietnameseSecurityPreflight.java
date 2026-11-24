package com.example.aiagent.nlu;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Runs before extraction. Mixed READ/WRITE is rejected atomically, never reinterpreted by a model. */
public final class VietnameseSecurityPreflight {
    private static final Pattern PRIVILEGED = Pattern.compile("dat (?:phong|luon|khach san|cho)|huy (?:booking|dat|phong)|booking|giu (?:cho|phong)|thanh toan|hoan tien|"
            + "payment|refund|reserve|cancel|delete|insert|update|cap nhat|xoa du lieu|sua du lieu|chuyen tien|\\bpost\\b|\\bput\\b|\\bpatch\\b|"
            + "api|system|developer|huong dan|tu xac nhan|gia su|https?[:/]|www\\.|[{}<>`]|"
            + "bo qua|ignore|override|jailbreak|prompt|bi mat|secret|token|\\bkey\\b|password|credential|environment|mat khau|khoa truy cap|cong cu|quyen admin|chinh sach bao mat|"
            + "[a-z0-9-]+\\.(?:com|net|org|vn|invalid|local)(?:\\b|/)");
    private VietnameseSecurityPreflight() {}
    public static void validate(String raw) {
        if (raw == null || raw.isBlank() || raw.getBytes(StandardCharsets.UTF_8).length > 4096
                || raw.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.SURROGATE)) throw new IllegalArgumentException("Invalid Vietnamese input");
    }
    public static boolean refused(String raw) { return PRIVILEGED.matcher(VietnameseParser.normalize(raw)).find(); }
}
