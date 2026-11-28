package com.example.aiagent.nlu;

import com.example.aiagent.orchestration.PolicyExecutor.Intent;

/** Complete literal READ clauses only. An intent never supplies target identity or authorization. */
public final class ReadIntentRouting {
    private ReadIntentRouting() {}
    /** Extraction evidence only; does not resolve an ordinal or issue a selected-target capability. */
    public static boolean selectedReferenceCandidate(String raw) {
        if (selectedIntent(raw) != null) return true;
        String text = VietnameseParser.normalize(raw).replaceAll("[.?!]+$", "");
        return text.matches("xem khach san thu (?:nhat|hai|ba|[1-9][0-9]?)")
                || text.matches("gia phong thu (?:nhat|hai|ba|[1-9][0-9]?) bao nhieu")
                || text.matches("(?:kiem tra |hoi gia cua |xem )?(?:phong|khach san) (?:nay|do|vua chon|da chon|dau tien|thu (?:nhat|hai|ba|[1-9][0-9]?))(?: (?:con cho khong|con phong khong|gia bao nhieu(?: va con phong khong)?|con khong))?");
    }
    /** Only a parsed hotel discovery request may use D1; never a target/availability/WRITE question. */
    public static boolean automaticBrowseCandidate(String raw) {
        String text = VietnameseParser.normalize(raw);
        return text.matches("(?:tim|co|cho (?:toi|minh) xem) khach san.*")
                && !text.matches(".*(?:kiem tra|con phong|ton phong|khach san nay|da chon|chi tiet|dat phong|so sanh).*?");
    }
    public static Intent selectedIntent(String raw) {
        if (raw == null || raw.length() > 4096) return null;
        String text = VietnameseParser.normalize(raw).replaceAll("[.?!]+$", "");
        if (text.matches("(?:xem|doc mo ta|xem chi tiet) khach san (?:da chon|co id da chon nhung da go cong khai)")) return Intent.DETAIL;
        if (text.equals("liet ke phong cong khai cua khach san da chon")) return Intent.ROOMS;
        if (text.matches("xem (?:loai phong|chi tiet phong|phong cong khai) da chon")) return Intent.ROOM_DETAIL;
        if (text.matches("(?:xem )?danh gia (?:cua )?khach san da chon")) return Intent.REVIEWS;
        if (text.matches("kiem tra (?:ton )?phong da chon(?: cho lich cua toi|, du lieu bi treo khi tai)?")) return Intent.AVAILABILITY;
        if (text.equals("gia co so va phong con khong cho lich cua toi")) return Intent.AVAILABILITY;
        return null;
    }
}
