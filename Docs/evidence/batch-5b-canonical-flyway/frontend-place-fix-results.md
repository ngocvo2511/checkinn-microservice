# Targeted Place fix — PASS

NearbyPlacesSection intended showDistance=(latitude!=null && longitude!=null), but PlaceCardProps lacked it and card always displayed time/distance. Existing other callers omit prop and depend on visible badge. Add optional boolean in PlaceCard, default=true, wrap Road Distance Badge only; false hides time/distance, directions/calculations unchanged. Caller retained.

Only production frontend change components/PlaceCard.tsx; focused tests/place-card-distance.test.mjs renders real component via TypeScript transpile + React SSR, checks default/true/false and retained title/directions. No Place API/backend/search/style/routing/architecture changes.

tsc --noEmit --incremental false PASS; node --test tests/place-card-distance.test.mjs3 PASS; npm run test:regression211 PASS; npm run lint -- components/PlaceCard.tsx tests/place-card-distance.test.mjs exit0,0 errors,2 pre-existing warnings (Clock/no-img-element). Earlier broad lint also noted unchanged NearbyPlacesSection warnings; no unrelated cleanup. Raw logs (local only: `logs/`).
