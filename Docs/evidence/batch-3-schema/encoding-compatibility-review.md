# Historical encoding — STD-024

Fresh executed: HotelMapperTextTest + RoomDescriptionTest, cùng RegulationConsistencyTest (26 focused tests tổng cộng khi cộng inventory). PostgreSQL byte-preserving UTF-8 pipe probe kiểm raw Unicode/newline/quoted/legacy structured text, JSONB string/object/array/SQL NULL/nested JSON null và timestamptz +07↔UTC equality. All final probes PASS; không rewrite values.

Hotel/Room descriptions là text; capacity/address/amenities JSONB dưới String mapping, mapper chỉ serialize structured fields. HotelMapperTextTest xác nhận PLAIN_V1 cho scalar fields kể cả chuỗi literal null/undefined/quoted; RoomDescriptionTest giữ Unicode, markup, dài/null. RegulationConsistencyTest round-trip quotes/backslashes/newlines/Unicode của snapshot qua actual serializer. Source inventory chứa media metadata entities; media storage/provider serialization không chạy fresh trong Batch3: NOT VERIFIED.

Failure retained: first Windows subprocess text pipe converted LF to CRLF before psql. Sửa harness dùng byte UTF-8, fresh encoding_probe_v2 chạy lại PASS; không application defect. SQL/json scalar compatibility không đồng nghĩa mọi historical malformed structure đều được chấp nhận; adapters hiện có giữ nguyên. Không xóa adapters hoặc convert historical JSON/string.

Giới hạn: không có real historical sample classification/corpus, không native JPA read/write từng legacy JSON shape trong batch này; selected mapper tests + SQL roundtrip được báo riêng. Historical production encoding/provenance NOT VERIFIED; parent acceptance không được đóng bởi synthetic cases.
