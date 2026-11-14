# Remaining risks / explicit limits

- NOT VERIFIED: Supabase version/network/roles/Data API/RLS/SSL/pooling/backups/restore/retention/cutover; D06 hosted/D07/D08 remain Batch6. No project connection/deployment.
- Flyway10.10 warns PG18 newer than certified16. Local bootstrap/runtime pass, vendor-supported version alignment required before hosting; no blanket dependency upgrade in5B.
- Tested JVM/JDBC/sessionUTC. LocalDateTime stays wall-clock timestamp, Instant timestamptz, check-in policy Asia/Ho_Chi_Minh. Mixed deployment JVM zones and multi-node scheduler behavior remain unverified.
- No demo import/stock inference; total_rooms NULL remains fail-closed, no default10; STD-006 OPEN. Room–Hotel logical lifecycle preserved, no cascade.
- Baseline nullable room amenities is not accepted by existing response mapper; synthetic normal HTTP fixture supplies[] rather than NULL. No mapper/business scope expansion or nullability coercion.
- Unit/mock coverage exceeds real integrated scenarios. Gateway/browser/SMTP/Media/real-provider/full VNPay HTTP/full positive Review→Booking gRPC and all skipped opt-in suites NOT VERIFIED. No multi-node/distributed failover certification.
- Multi-process first-seed race not verified. Java remains sole seed owner; single-process restart no duplicate policies PASS.
- Failed synthetic attempts/data retained isolated for audit; no financial/stock repair/deletion. Stop owned runtime/PG/dependencies, preserve data/logs/volumes; private credentials remain ignored.

Cleanup verified:0 owned listeners/0 JVMs, RabbitMQ/Redis exited, PostgreSQL test stopped, data retained; protected5432 still listening. See cleanup-results.json.
