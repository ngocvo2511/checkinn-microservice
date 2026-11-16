# Schema snapshot inventory — 13/11/2026

Đọc toàn bộ 5 file plain SQL từ pg_dump 18.0, nằm tại `C:/Users/ACER/IdeaProjects/DA1/database-schema-snapshots/`. DDL-only theo kiểm tra statement đầu dòng; không COPY/INSERT. Không xuất dữ liệu thực. Không tìm thấy credential theo heuristic; không phải chứng nhận secrets toàn workspace. OWNER TO postgres có mặt; không replay ownership/grants trên Supabase.

Database identity không được nhúng bằng CREATE DATABASE/\connect. Ownership bên dưới là **service/domain khớp cấu trúc + datasource default**, không phải chứng nhận tên database nguồn hoặc endpoint đang chạy. Mọi bảng trong dump ở `public`; không suy deployed shared/separate DB chỉ từ file name.

| File | SHA-256 | Service đối chiếu | Tables | Columns | Explicit indexes | PK / FK / UNIQUE / CHECK | Special objects |
|---|---|---|---:|---:|---:|---|---|
| `checkinn_booking_schema.sql` | `c1e041f04b9e1d392dda31e3817050775e48b8fe3b3b3b63b067742f760a40a5` | booking; tables + datasource contract | 3 | 50 | 0 | 3 / 1 / 1 / 3 | none |
| `checkinn_hotel_schema.sql` | `59a9bc1b8f96338a2f424080e9d1a783e28c13ba6be945b2f23c8e998c0abbf8` | hotel; tables + datasource contract | 16 | 129 | 6 | 16 / 7 / 7 / 6 | none |
| `checkinn_regulations_schema.sql` | `cef144ee4d7065445294149a7ab8d826e35ec4ab8a4f52efeaf22c0f8a35d921` | regulations; tables + datasource contract | 2 | 16 | 0 | 2 / 0 / 1 / 0 | SEQUENCE |
| `checkinn_revenue_schema.sql` | `3cbb2e8f4879952c43251cbeed471a3e537bbde5847ddeb79bf1f6ceb22955be` | revenue; tables + datasource contract | 2 | 26 | 0 | 2 / 0 / 0 / 0 | none |
| `checkinn_user_schema.sql` | `d7109b3a616c315c8973fdd45bcdc69f5eafe05e8bad38e2b74dab1cc4365e9f` | user; users/profiles/role/loyalty | 6 | 48 | 1 | 6 / 2 / 4 / 1 | none |

Explicit index count excludes implicit PK/UNIQUE backing indexes. Including backing indexes inferred from PostgreSQL PK/UNIQUE definitions: Booking4, Hotel29, Regulations3, Revenue2, User11 =49 total (7 explicit +42 backing). These are inferred schema index totals, not a live pg_indexes query. Constraints count includes inline CREATE TABLE checks and ALTER TABLE constraints. User contains Flyway history **structure only**; rows/applied versions/checksums NOT VERIFIED. Regulations has 2 identity sequences. No PostgreSQL enum types, extensions, functions, triggers, views or materialized views found in the provided scope. No grants/revokes found; schema ownership statements preserved only as metadata. No Place snapshot supplied; protected scope read-only, not compared against pgAdmin.

[Full per-column catalog, constraints, indexes and object metadata](snapshot-catalog.json). Parsing: plain SQL table/ALTER constraint/CREATE INDEX + TOC entries, balanced comma splitting; not a general PostgreSQL SQL parser. Database identity, excluded schemas/objects and actual pgAdmin export options cannot be recovered. Header says pg_dump; pgAdmin origin is user-provided provenance. Generated identity sequence options remain in original dump and TOC, not inferred from file names.

[All 68 visible workspace SQL classifications and SHA-256](sql-source-classification.json): pgAdmin dumps distinguished from Flyway resources (not every service enabled), Docker init, fixture, test-only bootstrap, legacy/manual and unknown SQL. Ignored/private SQL not automatically elevated to snapshot authority. Root manual NULL-to-10 migration remains legacy and is never executed.
