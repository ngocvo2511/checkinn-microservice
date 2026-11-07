package com.example.hotelservice.Amenity.service;

import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import com.example.hotelservice.validation.InputValidation;
import java.util.*;

/** Explicit, reviewed classification. A raw label is never an alias for a code. */
@Service
@RequiredArgsConstructor
@Transactional
public class AmenityCatalogService {
    private final JdbcTemplate jdbc;
    private final HotelRepository hotels;
    public record Actor(UUID id, String role) {}
    public record CatalogInput(@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
            @NotBlank @Size(max=255) String label, @Size(max=1000) String description,
            @NotNull Boolean active, @NotNull @Min(0) Long revision) {}
    public record Proposal(@NotNull Set<@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String> codes,
            @NotNull @Min(0) Long revision, @Size(max=1000) String note) {}
    public record Review(@NotNull Boolean approve, @NotNull @Min(0) Long revision,
            @NotBlank @Size(max=1000) String reason) {}
    public record CatalogEntry(String code,String label,String description,boolean active,long revision) {}
    public record Code(String code,String label) {}

    private static void admin(Actor actor) {
        if (actor == null || actor.id()==null || !"ADMIN".equals(actor.role())) throw new SecurityException("Chỉ ADMIN được quản lý catalog và duyệt mapping.");
    }
    private Hotel authorizedHotel(UUID id, Actor actor, boolean lock) {
        if (actor==null || actor.id()==null || !("ADMIN".equals(actor.role()) || "OWNER".equals(actor.role()))) throw new SecurityException("Không có quyền quản lý tiện nghi.");
        Hotel h=(lock ? hotels.findForUpdate(id) : hotels.findById(id)).orElseThrow(()->new EntityNotFoundException("Không tìm thấy cơ sở lưu trú."));
        if (!"ADMIN".equals(actor.role()) && !actor.id().equals(h.getOwnerId())) throw new SecurityException("Không có quyền quản lý tiện nghi của cơ sở này.");
        return h;
    }
    @Transactional(readOnly=true)
    public List<CatalogEntry> catalog(boolean includeInactive, Actor actor) {
        if (includeInactive) admin(actor);
        return jdbc.query("SELECT code,label,description,active,revision FROM amenity_catalog " + (includeInactive ? "" : "WHERE active ") + "ORDER BY code",
                (r,n)->new CatalogEntry(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getLong(5)));
    }
    public CatalogEntry save(CatalogInput input, boolean create, Actor actor) {
        admin(actor); InputValidation.require(input);
        int changed;
        if (create) {
            if(input.revision()!=0) throw new IllegalArgumentException("Revision mới phải bằng 0.");
            changed=jdbc.update("INSERT INTO amenity_catalog(code,label,description,active,created_by,updated_by) VALUES (?,?,?,?,?,?) ON CONFLICT(code) DO NOTHING",
                    input.code(),input.label().trim(),input.description(),input.active(),actor.id(),actor.id());
        } else {
            changed=jdbc.update("UPDATE amenity_catalog SET label=?,description=?,active=?,revision=revision+1,updated_by=?,updated_at=now() WHERE code=? AND revision=?",
                    input.label().trim(),input.description(),input.active(),actor.id(),input.code(),input.revision());
        }
        if(changed!=1) throw conflict();
        return new CatalogEntry(input.code(),input.label().trim(),input.description(),input.active(),create?0:input.revision()+1);
    }
    public void validateCodes(Collection<String> codes) {
        if(codes==null || codes.isEmpty()) return;
        for(String code:codes) {
            if(code==null || !code.matches("[A-Z][A-Z0-9_]{0,63}") || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM amenity_catalog WHERE code=? AND active)",Boolean.class,code)))
                throw new IllegalArgumentException("Mã tiện nghi không tồn tại hoặc đã ngừng sử dụng.");
        }
    }
    private Map<String,Object> raw(UUID hotelId, UUID amenityId) {
        var rows=jdbc.queryForList("SELECT a.id,a.title FROM hotel_amenities a JOIN hotel_amenity_categories c ON c.id=a.category_id WHERE a.id=? AND c.hotel_id=?",amenityId,hotelId);
        if(rows.isEmpty()) throw new EntityNotFoundException("Không tìm thấy nhãn tiện nghi của cơ sở này.");
        return rows.getFirst();
    }
    private Map<String,Object> state(UUID amenityId) {
        return jdbc.queryForMap("SELECT * FROM amenity_mapping_states WHERE amenity_id=?",amenityId);
    }
    private static void revision(Map<String,Object> state,long expected) {
        if(((Number)state.get("revision")).longValue()!=expected) throw conflict();
    }
    private static ResponseStatusException conflict() { return new ResponseStatusException(CONFLICT,"Thông tin đã thay đổi hoặc mã đã tồn tại. Hãy tải lại."); }

    public Map<String,Object> propose(UUID hotelId, UUID amenityId, Proposal input, Actor actor) {
        authorizedHotel(hotelId,actor,true); InputValidation.require(input);
        String title=(String)raw(hotelId,amenityId).get("title");
        validateCodes(input.codes());
        jdbc.update("INSERT INTO amenity_mapping_states(amenity_id,hotel_id) VALUES (?,?) ON CONFLICT DO NOTHING",amenityId,hotelId);
        var state=state(amenityId); revision(state,input.revision());
        if(state.get("latest_request_id")!=null) jdbc.update("UPDATE amenity_mapping_requests SET status='SUPERSEDED' WHERE id=? AND status='PENDING'",state.get("latest_request_id"));
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO amenity_mapping_requests(id,hotel_id,amenity_id,title_snapshot,requested_by,note,status) VALUES (?,?,?,?,?,?,'PENDING')",id,hotelId,amenityId,title,actor.id(),input.note());
        for(String code:input.codes()) jdbc.update("INSERT INTO amenity_mapping_request_codes(request_id,code) VALUES (?,?)",id,code);
        jdbc.update("UPDATE amenity_mapping_states SET latest_request_id=?,revision=revision+1 WHERE amenity_id=?",id,amenityId);
        return request(id);
    }
    private Map<String,Object> request(UUID id) {
        var rows=jdbc.queryForList("SELECT r.*,s.revision FROM amenity_mapping_requests r JOIN amenity_mapping_states s ON s.amenity_id=r.amenity_id WHERE r.id=?",id);
        if(rows.isEmpty()) throw new EntityNotFoundException("Không tìm thấy yêu cầu mapping.");
        var row=rows.getFirst(); row.put("codes",jdbc.queryForList("SELECT code FROM amenity_mapping_request_codes WHERE request_id=? ORDER BY code",String.class,id));
        return row;
    }
    public Map<String,Object> review(UUID id, Review input, Actor actor) {
        admin(actor); InputValidation.require(input);
        var hotelIds=jdbc.queryForList("SELECT hotel_id FROM amenity_mapping_requests WHERE id=?",UUID.class,id);
        if(hotelIds.isEmpty()) throw new EntityNotFoundException("Không tìm thấy yêu cầu mapping.");
        authorizedHotel(hotelIds.getFirst(),actor,true);
        var request=request(id); UUID amenityId=(UUID)request.get("amenity_id");
        var state=state(amenityId); revision(state,input.revision());
        if(!id.equals(state.get("latest_request_id")) || !"PENDING".equals(request.get("status"))) throw conflict();
        if(input.approve()) {
            if(!Objects.equals(raw(hotelIds.getFirst(),amenityId).get("title"),request.get("title_snapshot"))) throw conflict();
            validateCodes(jdbc.queryForList("SELECT code FROM amenity_mapping_request_codes WHERE request_id=?",String.class,id));
            jdbc.update("UPDATE amenity_mapping_states SET approved_request_id=? WHERE amenity_id=?",id,amenityId);
        }
        jdbc.update("UPDATE amenity_mapping_requests SET status=?,reviewed_by=?,reviewed_at=now(),review_reason=? WHERE id=?",input.approve()?"APPROVED":"REJECTED",actor.id(),input.reason().trim(),id);
        jdbc.update("UPDATE amenity_mapping_states SET revision=revision+1 WHERE amenity_id=?",amenityId);
        return request(id);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> pending(Actor actor,int page) {
        admin(actor); if(page<0 || page>1000000) throw new IllegalArgumentException("Trang không hợp lệ.");
        var ids=jdbc.queryForList("SELECT id FROM amenity_mapping_requests WHERE status='PENDING' ORDER BY requested_at,id LIMIT 50 OFFSET ?",UUID.class,page*50);
        return ids.stream().map(this::request).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(Actor actor,int page) {
        admin(actor); if(page<0 || page>1000000) throw new IllegalArgumentException("Trang không hợp lệ.");
        return jdbc.queryForList("SELECT id FROM amenity_mapping_requests ORDER BY requested_at DESC,id LIMIT 50 OFFSET ?",UUID.class,page*50)
                .stream().map(this::request).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mappings(UUID hotelId,Actor actor) {
        authorizedHotel(hotelId,actor,false);
        var rows=jdbc.queryForList("SELECT a.id,a.title,COALESCE(s.revision,0) revision,s.latest_request_id FROM hotel_amenities a JOIN hotel_amenity_categories c ON c.id=a.category_id LEFT JOIN amenity_mapping_states s ON s.amenity_id=a.id WHERE c.hotel_id=? ORDER BY c.title,a.title,a.id",hotelId);
        var effective=effectiveCodes(hotelId);
        for(var row:rows) {
            row.put("approvedCodes",effective.getOrDefault((UUID)row.get("id"),List.of()));
            row.put("latestRequest",row.get("latest_request_id")==null ? null : request((UUID)row.get("latest_request_id")));
        }
        return rows;
    }
    @Transactional(readOnly=true)
    public Map<UUID,List<Code>> effectiveCodes(UUID hotelId) {
        Map<UUID,List<Code>> result=new HashMap<>();
        jdbc.query("""
            SELECT a.id,k.code,k.label FROM amenity_mapping_states s
            JOIN amenity_mapping_requests r ON r.id=s.approved_request_id AND r.status='APPROVED' AND r.hotel_id=s.hotel_id AND r.amenity_id=s.amenity_id
            JOIN hotel_amenities a ON a.id=s.amenity_id AND a.title=r.title_snapshot AND a.is_active
            JOIN hotel_amenity_categories c ON c.id=a.category_id AND c.hotel_id=s.hotel_id AND c.is_active
            JOIN amenity_mapping_request_codes rc ON rc.request_id=r.id
            JOIN amenity_catalog k ON k.code=rc.code AND k.active
            WHERE s.hotel_id=? ORDER BY a.id,k.code
            """,rs->{ result.computeIfAbsent(rs.getObject(1,UUID.class),id->new ArrayList<>()).add(new Code(rs.getString(2),rs.getString(3))); },hotelId);
        return result;
    }
    public boolean matches(UUID hotelId, Collection<String> codes) {
        if(codes==null || codes.isEmpty()) return true;
        var available=new HashSet<String>(); effectiveCodes(hotelId).values().forEach(items->items.forEach(c->available.add(c.code())));
        return available.containsAll(codes);
    }
    public Set<UUID> matchingHotels(Collection<UUID> hotelIds, Collection<String> codes) {
        var ids=new ArrayList<>(new LinkedHashSet<>(hotelIds));
        if(codes==null || codes.isEmpty()) return new HashSet<>(ids);
        var requested=new LinkedHashSet<>(codes);
        Set<UUID> result=new HashSet<>();
        // Bound bind-parameter growth without one round trip per property.
        for(int offset=0;offset<ids.size();offset+=500) {
            var chunk=ids.subList(offset,Math.min(offset+500,ids.size()));
            String hotelsSql=String.join(",",Collections.nCopies(chunk.size(),"?"));
            String codesSql=String.join(",",Collections.nCopies(requested.size(),"?"));
            var args=new ArrayList<Object>(chunk);args.addAll(requested);args.add(requested.size());
            jdbc.query("""
                SELECT s.hotel_id FROM amenity_mapping_states s
                JOIN amenity_mapping_requests r ON r.id=s.approved_request_id AND r.status='APPROVED' AND r.hotel_id=s.hotel_id AND r.amenity_id=s.amenity_id
                JOIN hotel_amenities a ON a.id=s.amenity_id AND a.title=r.title_snapshot AND a.is_active
                JOIN hotel_amenity_categories c ON c.id=a.category_id AND c.hotel_id=s.hotel_id AND c.is_active
                JOIN amenity_mapping_request_codes rc ON rc.request_id=r.id
                JOIN amenity_catalog k ON k.code=rc.code AND k.active
                """+" WHERE s.hotel_id IN ("+hotelsSql+") AND k.code IN ("+codesSql+") GROUP BY s.hotel_id HAVING count(DISTINCT k.code)=?",
                rs->{result.add(rs.getObject(1,UUID.class));},args.toArray());
        }
        return result;
    }
}
