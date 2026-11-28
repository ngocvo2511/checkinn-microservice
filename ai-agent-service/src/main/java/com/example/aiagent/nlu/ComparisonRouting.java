package com.example.aiagent.nlu;

import java.util.*;

/** Complete bounded comparison grammar. No IDs or arbitrary suffixes are accepted. */
public final class ComparisonRouting {
    public record Reference(List<Integer> ordinals, List<String> names) {
        public Reference { ordinals = List.copyOf(ordinals); names = List.copyOf(names); }
    }
    private ComparisonRouting() {}
    public static boolean candidate(String raw) {
        String s = VietnameseParser.normalize(raw);
        return s.contains("so sanh") || s.contains("re hon") || s.contains("phu hop hon");
    }
    public static Optional<Reference> parse(String raw) {
        String s = VietnameseParser.normalize(raw).replaceAll("[.?!]+$", "");
        if (s.matches("so sanh (?:gia va danh gia cua )?hai khach san dau tien")
                || s.equals("so sanh khach san thu nhat va thu hai")) return Optional.of(new Reference(List.of(0, 1), List.of()));
        if (s.matches("so sanh (?:(?:gia va danh gia|thong tin chi tiet) (?:cua )?)?hai khach san (?:vua tim|do)")
                || s.equals("khach san nao phu hop hon")) return Optional.of(new Reference(List.of(), List.of()));
        var m = java.util.regex.Pattern.compile("khach san [\"“]([^\"”]{1,200})[\"”] hay [\"“]([^\"”]{1,200})[\"”] re hon").matcher(s);
        if (m.matches()) return Optional.of(new Reference(List.of(), List.of(m.group(1), m.group(2))));
        m = java.util.regex.Pattern.compile("khach san ([a-z0-9 ]{1,200}) hay ([a-z0-9 ]{1,200}) re hon").matcher(s);
        if (m.matches()) return Optional.of(new Reference(List.of(), List.of(m.group(1), m.group(2))));
        return Optional.empty();
    }
}
