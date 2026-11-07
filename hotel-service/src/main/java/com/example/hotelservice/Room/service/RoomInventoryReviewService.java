package com.example.hotelservice.Room.service;

import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.validation.InputValidation;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Reviewed physical stock, separate from booking/payment history and mutable descriptions. */
@Service
@RequiredArgsConstructor
@Transactional
public class RoomInventoryReviewService {
    private final JdbcTemplate jdbc;
    private final RoomTypeRepository rooms;
    private final HotelRepository hotels;

    public record Actor(UUID id, String role) {}
    public record Proposal(@NotNull @Min(1) Integer quantity, @NotNull @Min(0) Long revision,
                           @NotBlank @Size(max=1000) String source) {}
    public record Review(@NotNull Boolean approve, @NotNull @Min(0) Long revision,
                         @NotBlank @Size(max=1000) String reason) {}
    public record State(long revision, boolean managed, boolean verified, Integer currentQuantity,
                        Map<String,Object> latestRequest, Map<String,Object> approvedRequest) {}

    private static void admin(Actor actor) {
        if(actor==null || actor.id()==null || !"ADMIN".equals(actor.role()))
            throw new SecurityException("Chỉ ADMIN được duyệt số phòng.");
    }
    private RoomType authorized(UUID id, Actor actor, boolean lock) {
        if(actor==null || actor.id()==null || !("OWNER".equals(actor.role()) || "ADMIN".equals(actor.role())))
            throw new SecurityException("Không có quyền xem nguồn xác minh số phòng.");
        RoomType room=(lock?rooms.findByIdForUpdate(id):rooms.findById(id))
                .orElseThrow(()->new EntityNotFoundException("Không tìm thấy loại phòng."));
        var hotel=hotels.findById(room.getHotelId()).orElseThrow(()->new EntityNotFoundException("Không tìm thấy cơ sở lưu trú."));
        if(!"ADMIN".equals(actor.role()) && !actor.id().equals(hotel.getOwnerId()))
            throw new SecurityException("Không có quyền quản lý loại phòng này.");
        return room;
    }
    private Map<String,Object> storedState(UUID room) {
        var values=jdbc.queryForList("SELECT * FROM room_inventory_review_states WHERE room_id=?",room);
        return values.isEmpty()?null:values.getFirst();
    }
    private Map<String,Object> request(UUID id) {
        var rows=jdbc.queryForList("SELECT r.*,s.revision FROM room_inventory_review_requests r JOIN room_inventory_review_states s ON s.room_id=r.room_id WHERE r.id=?",id);
        if(rows.isEmpty())throw new EntityNotFoundException("Không tìm thấy yêu cầu xác minh số phòng.");
        return rows.getFirst();
    }
    private static ResponseStatusException conflict() {
        return new ResponseStatusException(CONFLICT,"Thông tin số phòng đã thay đổi hoặc chưa an toàn. Hãy tải lại để kiểm tra.");
    }
    private static void revision(Map<String,Object> state,long expected) {
        if(state==null || ((Number)state.get("revision")).longValue()!=expected)throw conflict();
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public State state(UUID id,Actor actor) {
        RoomType room=authorized(id,actor,false);var state=storedState(id);
        if(state==null)return new State(0,false,false,room.getTotalRooms(),null,null);
        var latest=state.get("latest_request_id")==null?null:request((UUID)state.get("latest_request_id"));
        var approved=state.get("approved_request_id")==null?null:request((UUID)state.get("approved_request_id"));
        boolean verified=approved!=null && Objects.equals(approved.get("proposed_quantity"),room.getTotalRooms());
        return new State(((Number)state.get("revision")).longValue(),true,verified,room.getTotalRooms(),latest,approved);
    }
    public Map<String,Object> propose(UUID id,Proposal input,Actor actor) {
        if(actor==null || !"OWNER".equals(actor.role()))throw new SecurityException("Chỉ OWNER được đề xuất số phòng kèm nguồn.");
        InputValidation.require(input);RoomType room=authorized(id,actor,true);
        jdbc.update("INSERT INTO room_inventory_review_states(room_id) VALUES (?) ON CONFLICT DO NOTHING",id);
        var state=storedState(id);revision(state,input.revision());
        if(state.get("latest_request_id")!=null)
            jdbc.update("UPDATE room_inventory_review_requests SET status='SUPERSEDED' WHERE id=? AND status='PENDING'",state.get("latest_request_id"));
        UUID requestId=UUID.randomUUID();
        jdbc.update("INSERT INTO room_inventory_review_requests(id,room_id,hotel_id,previous_quantity,proposed_quantity,source,requested_by,status) VALUES (?,?,?,?,?,?,?,'PENDING')",
                requestId,id,room.getHotelId(),room.getTotalRooms(),input.quantity(),input.source().trim(),actor.id());
        jdbc.update("UPDATE room_inventory_review_states SET latest_request_id=?,revision=revision+1 WHERE room_id=?",requestId,id);
        return request(requestId);
    }
    public Map<String,Object> review(UUID id,Review input,Actor actor) {
        admin(actor);InputValidation.require(input);
        // Resolve target first, then serialize every writer on the SAME inventory room lock.
        var targets=jdbc.queryForList("SELECT room_id FROM room_inventory_review_requests WHERE id=?",UUID.class,id);
        if(targets.isEmpty())throw new EntityNotFoundException("Không tìm thấy yêu cầu xác minh số phòng.");
        RoomType room=authorized(targets.getFirst(),actor,true);
        var state=storedState(room.getId());revision(state,input.revision());
        var request=request(id);
        if(!id.equals(state.get("latest_request_id")) || !"PENDING".equals(request.get("status")))throw conflict();
        if(input.approve()) {
            if(!Objects.equals(request.get("hotel_id"),room.getHotelId())
                    || !Objects.equals(request.get("previous_quantity"),room.getTotalRooms()))throw conflict();
            var load=jdbc.queryForMap("SELECT COALESCE(MAX(held::bigint+booked::bigint),0) peak, "
                    +"COALESCE(SUM(CASE WHEN held<0 OR booked<0 THEN 1 ELSE 0 END),0) invalid FROM room_availability WHERE room_type_id=?",room.getId());
            int quantity=((Number)request.get("proposed_quantity")).intValue();
            if(((Number)load.get("invalid")).longValue()!=0 || ((Number)load.get("peak")).longValue()>quantity)throw conflict();
            room.setTotalRooms(quantity);rooms.saveAndFlush(room);
            jdbc.update("UPDATE room_inventory_review_states SET approved_request_id=? WHERE room_id=?",id,room.getId());
        }
        jdbc.update("UPDATE room_inventory_review_requests SET status=?,reviewed_by=?,reviewed_at=now(),review_reason=? WHERE id=?",
                input.approve()?"APPROVED":"REJECTED",actor.id(),input.reason().trim(),id);
        jdbc.update("UPDATE room_inventory_review_states SET revision=revision+1 WHERE room_id=?",room.getId());
        return request(id);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> queue(Actor actor,int page,boolean history) {
        admin(actor);if(page<0 || page>1000000)throw new IllegalArgumentException("Trang không hợp lệ.");
        return jdbc.queryForList("SELECT id FROM room_inventory_review_requests "+(history?"":"WHERE status='PENDING' ")
                +"ORDER BY requested_at "+(history?"DESC,":"ASC,")+"id LIMIT 50 OFFSET ?",UUID.class,page*50)
                .stream().map(this::request).toList();
    }
    /** Caller already holds the room lock. Unmanaged legacy edits retain their existing contract. */
    public void requireLegacyUpdateAllowed(RoomType room,Integer quantity) {
        if(!Objects.equals(room.getTotalRooms(),quantity) && storedState(room.getId())!=null)
            throw new ResponseStatusException(CONFLICT,"Số phòng đang được quản lý bằng quy trình OWNER đề xuất, ADMIN duyệt.");
    }
}
