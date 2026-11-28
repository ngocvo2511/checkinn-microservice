package com.example.aiagent.nlu;

import java.util.*;
import java.util.regex.Pattern;

/** Trusted immutable label snapshot, not catalog identities, evidence or a conversation store. */
public final class DestinationLabels {
    private final Map<String, String> labels;
    public DestinationLabels(Collection<String> snapshot) {
        if (snapshot.size() > 200) throw new IllegalArgumentException("Too many destination labels");
        var result = new LinkedHashMap<String, String>();
        for (String label : snapshot) {
            validateQuery(label);
            if (result.putIfAbsent(VietnameseParser.normalize(label), label) != null) throw new IllegalArgumentException("Ambiguous destination labels");
        }
        labels = Map.copyOf(result);
    }
    public static DestinationLabels reviewedDefaults() {
        return new DestinationLabels(List.of("Đà Lạt", "Nha Trang", "Đà Nẵng", "Hà Nội", "Hồ Chí Minh", "Phú Quốc", "Huế", "Vũng Tàu"));
    }
    /** Exact finite reviewed equivalence; never substring/fuzzy matching or catalog resolution. */
    public String canonicalLabel(String label) {
        if(label == null) return null;
        String original=java.text.Normalizer.normalize(label,java.text.Normalizer.Form.NFC).strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        String key=VietnameseParser.normalize(original), canonical=labels.get(key);
        return canonical != null && (original.equals(key) || original.equals(canonical.toLowerCase(Locale.ROOT))) ? canonical : null;
    }
    /** Accentless aliases are reviewed; arbitrary alternative accented spellings are not. */
    void validateReviewedMentions(String raw) {
        String[] words=raw.split("[\\s,.!?;:]+");
        for(int start=0;start<words.length;start++) for(int end=start+1;end<=Math.min(start+4,words.length);end++) {
            String phrase=String.join(" ",Arrays.copyOfRange(words,start,end));
            if(labels.containsKey(VietnameseParser.normalize(phrase)) && canonicalLabel(phrase)==null)
                throw new IllegalArgumentException("Unreviewed destination spelling");
        }
    }
    static void validateQuery(String label) {
        if (label == null || label.isBlank() || label.length() > 120 || !label.matches("[\\p{L}][\\p{L} '\\-]{0,119}"))
            throw new IllegalArgumentException("Invalid destination query");
    }
    Map<String, String> matches(String text) {
        var result = new HashMap<String, String>();
        labels.forEach((alias, label) -> { if (pattern(alias).matcher(text).find()) result.put(alias, label); });
        return result;
    }
    String remove(String text) { for (String alias : labels.keySet()) text = pattern(alias).matcher(text).replaceAll(" "); return text; }
    private static Pattern pattern(String alias) { return Pattern.compile("(?<![a-z])" + Pattern.quote(alias) + "(?![a-z])"); }
}
