package com.example.hotelservice.Hotel.service;
import com.example.hotelservice.Hotel.entity.*;
import com.example.hotelservice.Hotel.enums.*;
import com.example.hotelservice.Hotel.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccommodationTypeService {
    private final HotelRepository hotels;
    private final AccommodationTypeChangeRepository changes;
    public Hotel lockHotel(UUID id) {
        return hotels.findForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Không tìm thấy cơ sở"));
    }
    private void owner(Hotel hotel, UUID actor) {
        if (actor == null || !actor.equals(hotel.getOwnerId())) throw new AccessDeniedException("Bạn không có quyền sửa cơ sở này");
    }
    private void admin(boolean isAdmin) { if (!isAdmin) throw new AccessDeniedException("Cần quyền ADMIN"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"Yêu cầu đã thay đổi hoặc hết hiệu lực. Hãy tải lại để xem xét."); }

    /** Caller holds the hotel lock, including generic hotel updates. Never writes publication state. */
    public AccommodationTypeChange proposeLocked(Hotel hotel, AccommodationType type, UUID actor) {
        owner(hotel,actor);
        if (type == null) throw new IllegalArgumentException("Vui lòng chọn loại hình lưu trú");
        var pending = changes.findByHotelIdAndStatus(hotel.getId(),AccommodationChangeStatus.PENDING).orElse(null);
        if (pending != null && pending.getRequestedType() == type && pending.getPreviousType() == hotel.getAccommodationType()) return pending;
        if (pending != null) { pending.setStatus(AccommodationChangeStatus.SUPERSEDED); changes.saveAndFlush(pending); }
        if (type == hotel.getAccommodationType()) return null; // Explicit selection of current type cancels prior proposal.
        if (hotel.getApprovedStatus() != HotelApprovalStatus.APPROVED) { hotel.setAccommodationType(type); return null; }
        var change = new AccommodationTypeChange();
        change.setId(UUID.randomUUID()); change.setHotelId(hotel.getId()); change.setRequesterId(actor);
        change.setPreviousType(hotel.getAccommodationType()); change.setRequestedType(type);
        change.setStatus(AccommodationChangeStatus.PENDING); change.setCreatedAt(Instant.now());
        return changes.save(change);
    }
    @Transactional
    public AccommodationTypeChange propose(UUID hotelId, AccommodationType type, UUID actor) {
        return proposeLocked(lockHotel(hotelId),type,actor);
    }
    @Transactional(readOnly=true)
    public List<AccommodationTypeChange> history(UUID hotelId, UUID actor, boolean isAdmin) {
        var hotel=hotels.findById(hotelId).orElseThrow(EntityNotFoundException::new);
        if (!isAdmin) owner(hotel,actor);
        return changes.findByHotelIdOrderByCreatedAtDesc(hotelId);
    }
    @Transactional(readOnly=true)
    public List<AccommodationTypeChange> pending(boolean isAdmin) { admin(isAdmin); return changes.findByStatusOrderByCreatedAtAsc(AccommodationChangeStatus.PENDING); }
    @Transactional
    public AccommodationTypeChange review(UUID id, boolean approve, String reason, UUID actor, boolean isAdmin) {
        admin(isAdmin);
        var hotelId=changes.hotelIdFor(id).orElseThrow(EntityNotFoundException::new);
        var hotel=lockHotel(hotelId); // Global order: hotel, then request. No managed request read before lock.
        var change=changes.findForUpdate(id).orElseThrow(EntityNotFoundException::new);
        var target=approve ? AccommodationChangeStatus.APPROVED : AccommodationChangeStatus.REJECTED;
        if (change.getStatus()==target && actor.equals(change.getReviewerId())) return change; // same reviewer retry, no replayed write
        if (change.getStatus()!=AccommodationChangeStatus.PENDING || hotel.getApprovedStatus()!=HotelApprovalStatus.APPROVED
            || hotel.getAccommodationType()!=change.getPreviousType()) throw conflict();
        if (!approve && (reason==null || reason.isBlank() || reason.length()>1000)) throw new IllegalArgumentException("Cần lý do từ chối (1–1000 ký tự)");
        if (approve) hotel.setAccommodationType(change.getRequestedType());
        change.setStatus(target); change.setReviewedAt(Instant.now()); change.setReviewerId(actor);
        change.setRejectionReason(approve ? null : reason.trim());
        return change;
    }
}
