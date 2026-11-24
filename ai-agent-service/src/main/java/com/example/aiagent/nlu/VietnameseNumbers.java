package com.example.aiagent.nlu;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Bounded literal number grammar; normalization never supplies a missing year or money unit. */
final class VietnameseNumbers {
    private static final Map<String, Integer> DIGITS = Map.of("khong", 0, "mot", 1, "hai", 2, "ba", 3, "bon", 4,
            "nam", 5, "sau", 6, "bay", 7, "tam", 8, "chin", 9);
    private static final String WORD = "(?:khong|mot|hai|ba|bon|nam|sau|bay|tam|chin|muoi|tram|nghin|ngan|le|linh|lam|tu)";
    private static final String NUMBER = "(?:[0-9]{1,4}|" + WORD + "(?: " + WORD + "){0,14})";
    private VietnameseNumbers() {}
    static int integer(String value) {
        if (value.matches("[0-9]{1,4}")) return Integer.parseInt(value);
        var parts = value.split(" (?:nghin|ngan) ", -1);
        if (parts.length == 2) {
            var thousands = DIGITS.get(parts[0]);
            if (thousands == null || thousands == 0) throw new IllegalArgumentException("Invalid number words");
            return Math.addExact(Math.multiplyExact(thousands, 1000), hundreds(parts[1]));
        }
        if (parts.length != 1) throw new IllegalArgumentException("Invalid number words");
        return hundreds(value);
    }
    private static int hundreds(String value) {
        var parts = value.split(" tram(?: |$)", -1);
        if (parts.length == 2) {
            var digit = DIGITS.get(parts[0]);
            if (digit == null) throw new IllegalArgumentException("Invalid hundreds");
            String tail = parts[1].replaceFirst("^(?:le|linh) ", "");
            return digit * 100 + (tail.isEmpty() ? 0 : tens(tail));
        }
        if (parts.length != 1) throw new IllegalArgumentException("Invalid hundreds");
        return tens(value);
    }
    private static int tens(String value) {
        if (DIGITS.containsKey(value)) return DIGITS.get(value);
        var words = value.split(" ");
        int base, offset;
        if (words[0].equals("muoi")) { base = 10; offset = 1; }
        else if (words.length >= 2 && DIGITS.getOrDefault(words[0], 0) >= 2 && words[1].equals("muoi")) {
            base = DIGITS.get(words[0]) * 10; offset = 2;
        } else throw new IllegalArgumentException("Invalid tens");
        if (words.length == offset) return base;
        if (words.length != offset + 1) throw new IllegalArgumentException("Invalid tens");
        String unit = words[offset];
        int digit = unit.equals("lam") ? 5 : unit.equals("tu") ? 4 : DIGITS.getOrDefault(unit, -1);
        if (digit < 1) throw new IllegalArgumentException("Invalid unit");
        return base + digit;
    }
    private static String replace(String text, String pattern, java.util.function.Function<Matcher, String> replacement) {
        var matcher = Pattern.compile(pattern).matcher(text); var result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.apply(matcher)));
        matcher.appendTail(result); return result.toString();
    }
    static String normalizeGrammar(String text) {
        text = replace(text, "(?<![a-z0-9])(?:ngay )?(" + NUMBER + ") thang (" + NUMBER + ") nam (" + NUMBER + ")(?![a-z0-9])",
                m -> integer(m.group(1)) + "/" + integer(m.group(2)) + "/" + integer(m.group(3)));
        text = replace(text, "(?<![0-9])([0-9]{1,2})[–-]([0-9]{1,2})/([0-9]{1,2})/([0-9]{4})(?![0-9])",
                m -> m.group(1) + "/" + m.group(3) + "/" + m.group(4) + " den " + m.group(2) + "/" + m.group(3) + "/" + m.group(4));
        text = replace(text, "(?<![0-9/])([0-9]{1,2}/[0-9]{1,2}) den ([0-9]{1,2}/[0-9]{1,2}/([0-9]{4}))(?![0-9])",
                m -> m.group(1) + "/" + m.group(3) + " den " + m.group(2));
        text = replace(text, "(?<![a-z0-9])(" + NUMBER + ") trieu (" + NUMBER + ") nghin(?![a-z])", m ->
                BigDecimal.valueOf(integer(m.group(1))).add(BigDecimal.valueOf(integer(m.group(2)), 3)).toPlainString() + " trieu");
        text = replace(text, "(?<![a-z0-9])(" + NUMBER + ") trieu ruoi(?![a-z])", m ->
                BigDecimal.valueOf(integer(m.group(1))).add(new BigDecimal("0.5")).toPlainString() + " trieu");
        return replace(text, "(?<![a-z0-9])(" + NUMBER + ") (?=nguoi lon\\b|tre em\\b|phong\\b|trieu\\b|nghin\\b|ngan\\b|sao\\b)",
                m -> integer(m.group(1)) + " ");
    }
}
