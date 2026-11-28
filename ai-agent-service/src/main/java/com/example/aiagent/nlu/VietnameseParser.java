package com.example.aiagent.nlu;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import static com.example.aiagent.nlu.ExtractionProposal.*;

/** Conservative deterministic grammar. No model, network, inferred IDs or implicit occupancy. */
public final class VietnameseParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private final DestinationLabels destinations;
    public VietnameseParser() { this(DestinationLabels.reviewedDefaults()); }
    public VietnameseParser(DestinationLabels destinations) { this.destinations = Objects.requireNonNull(destinations); }
    public static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").strip();
    }
    private static Matcher match(String regex, String text) { return Pattern.compile(regex).matcher(text); }
    private static void put(Map<String, Change> fields, String key, Object value) {
        var next = new Change(Operation.REPLACE, JSON.valueToTree(value), Provenance.EXPLICIT_USER);
        if (fields.putIfAbsent(key, next) != null) throw new IllegalArgumentException("Conflicting mentions");
    }
    private static ExtractionProposal ask(Intent intent, String question) { return new ExtractionProposal(intent, Map.of(), question); }
    public ExtractionProposal extract(String raw, Clock clock) {
        if (raw == null || raw.isBlank() || raw.getBytes(StandardCharsets.UTF_8).length > 4096
                || raw.codePoints().anyMatch(c -> Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE || Character.isISOControl(c) && !Character.isWhitespace(c)))
            throw new IllegalArgumentException("Invalid Vietnamese input");
        destinations.validateReviewedMentions(raw);
        String text = normalize(raw);
        // Bounded discovery clauses and edge politeness; unknown suffixes/constraints remain consumed below.
        text = text.replaceFirst("^(?:minh|toi|tui) muon (?:kiem|tim) (?:mot )?", "tim ")
                .replaceFirst("^(?:kiem|tim) giup (?:minh|toi|tui) ", "tim ")
                .replaceFirst("^chi xem danh sach ", "tim ")
                .replaceFirst("^cho (?:toi|minh|tui) xem (?:noi luu tru|cho nghi)", "tim khach san")
                .replaceFirst("^co cho nao (?:ngu|nghi) ", "tim khach san ")
                .replaceAll("\\b(?:cho nghi|noi luu tru)\\b", "khach san")
                .replaceFirst(" (?:hong ban|khong ban|nha|nhe ban)[.!?]?$", "");
        // Complete explicit removal clauses only; never strip a suffix containing another constraint.
        text = text.replaceFirst("^(bo|xoa|khong can) gioi han (gia|so sao)(?: di| nua)?[.!?]?$", "$1 $2");
        if (match("dat (?:phong|luon|khach san|cho)|huy (?:booking|dat|phong)|booking|giu (?:cho|phong)|thanh toan|hoan tien|api|system prompt|developer|system:|huong dan|tu xac nhan|gia su|https?://|[{}<>`]", text).find())
            return ask(Intent.UNSUPPORTED, "Mình chỉ hỗ trợ tìm kiếm và xem thông tin khách sạn đã được xác minh. Bạn thực hiện đặt, hủy hoặc cập nhật thông tin qua luồng phù hợp trên website.");
        if (match("thoi tiet|khuyen mai|cach bien|cu bia|du api khong co", text).find())
            return ask(Intent.UNVERIFIABLE, "Không có dữ liệu được xác minh cho khẳng định này; mình không thể bịa giá, đánh giá, khoảng cách, thời tiết hoặc chính sách.");
        text = text.replaceFirst("^chua biet ngay, gia tham khao cua (.+) the nao[?]?$", "kham pha $1");
        boolean destinationReplacement = match("^(?:doi|chuyen) diem den sang ", text).find();
        if (text.matches("^tim o .+ thay vi .+$")) {
            String previous = text.substring(text.indexOf(" thay vi ") + " thay vi ".length());
            destinationReplacement = previous.equals("diem den truoc")
                    || destinations.matches(previous).values().stream().anyMatch(label -> normalize(label).equals(previous));
        }
        boolean keepFilter = match("^(?:chi )?(?:giu|loc)(?: lai)? (?:khach san )?[0-9]+ sao(?: nhe)?[.!?]?$", text).find();
        Intent intent = destinationReplacement || keepFilter || match("doi sang|doi tu|doi thanh|doi ngay|khong phai|them |bo |xoa |khong can|gia duoi", text).find() ? Intent.REFINE
                : match("kham pha|cho minh xem", text).find() ? Intent.BROWSE
                : match("tim |khach san|di da lat", text).find() ? Intent.SEARCH : Intent.CLARIFY;
        var fields = new HashMap<String, Change>();
        if (match("cuoi tuan|thang sau|ngay kia", text).find()) return ask(intent,
                match("[0-9]+ nguoi(?! lon)", text).find()
                        ? "Câu này chưa được áp dụng. Bạn gửi lại các thay đổi, ghi rõ ngày nhận/trả phòng có năm, số người lớn, trẻ em và phòng."
                        : "Câu này chưa được áp dụng. Bạn gửi lại các thay đổi kèm ngày nhận và trả phòng cụ thể, gồm cả năm.");
        if (match("gan bien|ho boi|bai do xe|tien ich|sap xep|re nhat|gan nhat|gia thap nhat", text).find())
            return ask(intent, "Chưa có ánh xạ được xác minh cho tiêu chí này; chưa áp dụng thay đổi. Bạn muốn bỏ tiêu chí đó hay cung cấp thông tin rõ hơn?");
        if (text.contains("khong phai") && !text.contains("doi sang") || match("khong (?:can|phai) [0-9]+", text).find())
            return ask(intent, "Bạn muốn thay tiêu chí này bằng giá trị nào?");
        if (match("khong (?:can|muon)", text).find() && !match("khong can (?:bo loc )?(?:dia diem|ten khach san|gia|so sao)", text).find())
            return ask(intent, "Bạn muốn xóa hoặc thay đổi tiêu chí nào?");
        text = VietnameseNumbers.normalizeGrammar(text);
        text = text.replace("moi phong moi dem", "/phong/dem")
                .replaceAll("([0-9]+(?:[.,][0-9]+)? (?:trieu|tr|nghin|ngan|k)) vnd\\b", "$1");
        if (destinationReplacement) {
            text = text.replaceFirst("^(?:doi|chuyen) diem den sang ", "doi sang ");
            if (text.matches("^tim o .+ thay vi .+$")) text = "doi sang " + text.substring(6, text.indexOf(" thay vi "));
        }
        if (keepFilter) text = text.replaceFirst("^(?:chi )?(?:giu|loc)(?: lai)? ", "");
        String locationText = text;
        int correction = text.lastIndexOf("doi sang ");
        if (correction >= 0) locationText = text.substring(correction + 9);
        if (text.contains("doi tu ") && text.contains(" sang ")) locationText = text.substring(text.lastIndexOf(" sang ") + 6);
        String namesOnly = locationText.replaceAll("(?:khach san ten|khach san) [\"“][^\"”]{1,200}[\"”]", " ");
        for (var city : destinations.matches(namesOnly).entrySet()) put(fields, "cityName", city.getValue());
        var query = match("(?iu)(?:ở|o|tại|tai|đổi sang|doi sang)\\s+[\"“]([^\"”]{1,120})[\"”]", raw);
        if (query.find()) {
            String label = query.group(1).strip().replaceAll("\\s+", " "); DestinationLabels.validateQuery(label);
            if (fields.containsKey("cityName") && !normalize(fields.get("cityName").value().asText()).equals(normalize(label)))
                throw new IllegalArgumentException("Conflicting destination");
            fields.put("cityName", new Change(Operation.REPLACE, JSON.valueToTree(label), Provenance.EXPLICIT_USER));
            if (query.find()) throw new IllegalArgumentException("Multiple destination queries");
        }
        var hotel = match("(?iu)(?:khách sạn tên|khach san ten|khách sạn|khach san)\\s+[\"“]([^\"”]{1,200})[\"”]", raw);
        while (hotel.find()) put(fields, "hotelName", hotel.group(1));
        for (var entry : Map.of("dia diem", "cityName", "ten khach san", "hotelName", "gia", "maxPrice", "so sao", "starRatings").entrySet()) {
            if (match("(?:bo|xoa|khong can) (?:bo loc )?" + entry.getKey(), text).find()) {
                fields.put(entry.getValue(), new Change(Operation.CLEAR, null, Provenance.EXPLICIT_USER));
                if (entry.getKey().equals("gia")) fields.put("minPrice", new Change(Operation.CLEAR, null, Provenance.EXPLICIT_USER));
            }
        }
        var dates = new ArrayList<LocalDate>();
        var date = match("(?<![0-9])([0-9]{1,2}/[0-9]{1,2}/[0-9]{4})(?![0-9])", text);
        while (date.find()) dates.add(LocalDate.parse(date.group(1), DATE));
        var written = match("ngay ([0-9]{1,2}) thang ([0-9]{1,2}) nam ([0-9]{4})", text);
        while (written.find()) dates.add(LocalDate.of(Integer.parseInt(written.group(3)), Integer.parseInt(written.group(2)), Integer.parseInt(written.group(1))));
        if (dates.size() > 2) throw new IllegalArgumentException("Ambiguous dates");
        if (!dates.isEmpty() && text.contains("ngay mai")) throw new IllegalArgumentException("Conflicting dates");
        if (dates.isEmpty() && text.contains("ngay mai")) {
            dates.add(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).plusDays(1));
        }
        if (dates.isEmpty() && match("ngay [0-9]|thang [0-9]|[0-9]{1,2}/[0-9]{1,2}", text).find())
            return ask(intent, "Bạn vui lòng ghi đầy đủ ngày/tháng/năm nhận và trả phòng.");
        if (!dates.isEmpty()) {
            put(fields, text.contains("tra phong") && !text.contains("nhan phong") && dates.size() == 1 ? "checkOut" : "checkIn", dates.getFirst().toString());
            if (dates.size() == 2) put(fields, "checkOut", dates.getLast().toString());
        }
        String numbers = text.replaceAll("\\bmot\\b", "1").replaceAll("\\bhai\\b", "2").replaceAll("\\bba\\b", "3");
        if (match("them [0-9]+ (?:tre em|nguoi lon|phong)", numbers).find())
            return ask(intent, "Chưa áp dụng phép tăng. Hiện tại bạn muốn đặt cho tổng cộng bao nhiêu người lớn, trẻ em và phòng?");
        for (var entry : Map.of("nguoi lon", "adults", "tre em", "children", "phong", "rooms").entrySet()) {
            var count = match("(?<![0-9.,/-])([0-9]+) " + entry.getKey() + "\\b", numbers);
            while (count.find()) put(fields, entry.getValue(), Integer.parseInt(count.group(1)));
        }
        if (match("khong (?:co )?tre em", text).find()) put(fields, "children", 0);
        if (match("[0-9]+ nguoi(?! lon)", numbers).find()) return ask(intent, "Có bao nhiêu người lớn và trẻ em, và bạn cần mấy phòng?");
        if (match("-[0-9]|[0-9]+[.,][0-9]+ (?:nguoi|tre em|phong)", numbers).find()) throw new IllegalArgumentException("Invalid quantity");
        // Domain maxPrice is inclusive. A strict 'below' bound cannot silently become <=.
        boolean referenceBelow = text.contains("gia tham khao duoi") && dates.isEmpty();
        if (text.contains("duoi") && !referenceBelow && match("trieu|nghin|ngan|vnd|dong|\\bgia\\b", text).find())
            return ask(intent, "Bạn vui lòng xác nhận mức giá tối đa cho một phòng mỗi đêm (bao gồm mức giá đó).");
        if (referenceBelow) {
            text = text.replace("gia tham khao duoi", "gia toi da");
            numbers = numbers.replace("gia tham khao duoi", "gia toi da");
            put(fields, "referencePriceExclusive", true);
        }
        var budget = match("(?:gia )?(toi da|khong qua|tu|den) ([0-9]+(?:[.,][0-9]+)?) (trieu|tr|nghin|ngan|k|vnd|dong)\\b", numbers);
        while (budget.find()) {
            if (!referenceBelow && !text.contains("mot dem") && !text.contains("1 dem") && !text.contains("/dem") && !text.contains("moi dem"))
                return ask(intent, "Ngân sách này tính cho một phòng mỗi đêm hay tổng chuyến đi?");
            if (budget.group(2).length() > 18) throw new IllegalArgumentException("Invalid budget size");
            var amount = new BigDecimal(budget.group(2).replace(',', '.'));
            amount = amount.multiply(switch (budget.group(3)) { case "trieu", "tr" -> BigDecimal.valueOf(1000000); case "nghin", "ngan", "k" -> BigDecimal.valueOf(1000); default -> BigDecimal.ONE; });
            put(fields, budget.group(1).equals("tu") ? "minPrice" : "maxPrice", amount);
        }
        if (text.contains("gia") && !text.contains("gia tham khao") && !fields.containsKey("maxPrice") && !fields.containsKey("minPrice"))
            return ask(intent, "Bạn vui lòng ghi rõ ngân sách VND cho một phòng mỗi đêm.");
        var stars = match("([0-9]+) sao", text); while (stars.find()) put(fields, "starRatings", List.of(Integer.parseInt(stars.group(1))));
        // Fail closed on unconsumed constraints instead of silently dropping an unknown filter.
        String remainder = numbers.replaceAll("(?:khach san ten|khach san) [\"“][^\"”]{1,200}[\"”]", " ");
        remainder = destinations.remove(remainder).replaceAll("(?:o|tai|doi sang) [\"“][^\"”]{1,120}[\"”]", " ");
        remainder = remainder.replaceAll("(?:ngay )?[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}|ngay mai", " ")
                .replaceAll("[0-9]+ (?:nguoi lon|tre em|phong)|khong (?:co )?tre em|[0-9]+ sao", " ")
                .replaceAll("(?:gia )?(?:toi da|khong qua|tu|den) [0-9]+(?:[.,][0-9]+)? (?:trieu|tr|nghin|ngan|k|vnd|dong)\\b", " ")
                .replaceAll("(?:1|moi) dem|/phong/dem|/dem|(?:bo|xoa|khong can) (?:bo loc )?(?:dia diem|ten khach san|gia|so sao)|khong phai|doi sang|doi thanh|doi ngay|doi tu|giu lich va so khach|theo lich da xac nhan|theo cityid da xac nhan|gia co so|gia tham khao|(?:ngay )?(?:nhan|tra) phong|\\bsang\\b", " ");
        var fillers = Set.of("tim", "khach", "san", "phong", "o", "tai", "tu", "den", "cho", "minh", "toi", "xem", "kham", "pha", "co", "nao", "khong", "voi", "va", "can", "muon", "nhe", "a", "di", "dung");
        for (String token : remainder.split("[\\s.,!?;:]+")) if (!token.isEmpty() && !fillers.contains(token))
            return ask(intent, "Câu này chưa được áp dụng vì còn phần chưa hiểu. Bạn vui lòng tách riêng từng thay đổi và ghi rõ giá trị cần sửa.");
        if (fields.isEmpty() && !text.contains("theo lich da xac nhan")) return ask(intent, "Bạn vui lòng nêu địa điểm hoặc tiêu chí lưu trú cụ thể.");
        return new ExtractionProposal(intent, fields, null);
    }
}
