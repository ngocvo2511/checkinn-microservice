# Service/schema ownership

| Logical owner | Physical database default | Runtime/migration owner | Evidence and boundaries |
|---|---|---|---|
| Auth | Không có PostgreSQL datasource riêng | User APIs/gRPC; Redis session/revocation | auth application.yml; không suy Auth có users table riêng |
| User + Loyalty | checkinn_user | Flyway V2–V5, baseline-on-migrate=true, JPA validate | users/user_profiles/otps/loyalty_points/points_transactions/loyalty_commands/points_reservations; no V1 |
| Hotel + Room + availability + media metadata | checkinn_hotel | JPA update; SQL thủ công; Hotel V1–V5 không có Flyway dependency | room_types.hotel_id và polymorphic media không ép FK theo entity; audit IDs có chủ ý sống qua target deletion |
| Booking + Payment + Refund | checkinn_booking | JPA update + manual SQL, schema.sql không tự đảm bảo runtime execution | Payment/Refund lưu scalar booking/payment IDs; không tự coi scalar reference là FK |
| Revenue | checkinn_revenue | JPA update cho projections + SQL-owned cash/receipts/gaps | Cross-service booking/payment IDs không có cross-database FK |
| Regulations | checkinn_regulations | JPA update + SQL outbox | version guard; snapshots JSON serialization |
| Place | checkinn_place (config metadata) | Protected; NOT VERIFIED | Không sửa source/migrations hoặc DB |
| Media service | Không có local datasource | Supabase object storage + Hotel metadata RPC | Không suy metadata thuộc DB Media |
| Gateway/Eureka/Config/Admin/Notification | Không thấy application PostgreSQL datasource riêng | Routing/discovery/config/notification infrastructure | Không dựng DB giả cho services này |

docker-compose.yml có USER_DB_URL/HOTEL_DB_URL/BOOKING_DB_URL/REVENUE_DB_URL/REGULATIONS_DB_URL override; physical targets thực tế có thể khác defaults. Batch3 chỉ URL hard-coded tới PostgreSQL mới trong network internal riêng. Shared database access ngoài source/config: UNKNOWN; không suy deployment isolation từ tên DB.

Normalized snapshots liệt kê tables, columns (precision/scale/null/default/time semantics), PK/FK/unique/CHECK/validated flag, indexes, sequences, extensions. pg_get_constraintdef bao gồm ON DELETE/cascade. Schema public trong các disposable databases; không claim database role/table owner production. Không thu thập credentials/PII.

Enum lưu varchar + CHECK theo từng source/JPA, không phải PostgreSQL enum chung. Booking LocalDateTime/hibernate.jdbc.time_zone=Asia/Ho_Chi_Minh khác Instant/timestamptz; tuyệt đối không tự đổi timestamp lịch sử thành UTC. Numeric SQL cash unrestricted numeric và entity money mặc định numeric(38,2) có thể khác: giữ nguyên, cần service-specific decision/validation. JSONB String là representation có adapter, không mặc định bug.
