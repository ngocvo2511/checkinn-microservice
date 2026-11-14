# Initial state — 14/11/2026, Asia/Saigon

Evidence finalized 2026-11-14T11:35:56.805645+07:00. Full before-edit Git snapshot: [JSON](session-initial-state.json). Existing initial-state.json/private before-source.zip/initial-hashes.json were already present and retained.

Backend test/booking-hotel-gateway-acceptance / 8ee230b250863164dd47f83af037711a0ea4a0ef; frontend main / d446b9808f8732c9982da2c156979e245742aa7f, initially clean. Backend contained Batch5A UUID source/test/docs, IDE changes, deleted Batch2 review and untracked prior evidence/harnesses. No stage/commit/reset.

Default User legacy Flyway baseline=true and other four services Hibernate update unchanged. New Flyway dependencies are TEST scoped, avoiding default runtime activation. Canonical opt-in runtime profile disables Flyway/SQL init; guarded job exclusively db/canonical, baseline=false/clean disabled.

PostgreSQL18 Windows service5432, earlier5A cluster and all existing Docker volumes protected, no business database connection/enumeration. Docker29.1.3 currently works, no initially running containers. Installed PostgreSQL17/18; only new ignored Batch5B postgres-data/loopback55486 used. Existing checkinn-local/checkinn-test/booking-smoke/refund-smoke/schema-smoke/anonymous/other-project volumes retained.

Baseline tsc --noEmit --incremental false: TS2322 NearbyPlacesSection.tsx161 showDistance absent from PlaceCardProps. Raw (local only: `logs/frontend-baseline.log`). [Protected hashes](protected-hashes.json).
