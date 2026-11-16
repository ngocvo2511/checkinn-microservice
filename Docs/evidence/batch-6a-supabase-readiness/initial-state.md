# Initial state — 14/11/2026, Asia/Saigon

Static/read-only audit. No hosted query, application startup, migration, backup or provider operation.

| Repository | Branch / HEAD | Pre-existing changes |
|---|---|---|
| checkinn | feat/canonical-uuid-schema / 909e73d6021d076479bb6533badb9a029c5a9c2a | .idea/compiler.xml; .idea/encodings.xml |
| checkinn-frontend | fix/place-card-distance-visibility / a10f82b7d84d941e77a2c5ae44aa2a899ab0a547 | clean |

No pre-existing staged/nonignored untracked files. Initial git queries preceded file creation. Machine [snapshot](initial-state.json) was captured after adding audit-static.py: its untracked evidence directory is a Batch6A addition. [Tracked baseline](protected-before.json).

13 required documents were loaded in full by the inspector; [input manifest](required-inputs.json) records hashes/line counts/headings. Long generated standardization documents were examined by structure and relevant sections in addition to full-file programmatic scanning. Source/POM/YAML/SQL/scripts and current PR-review evidence were compared with historical Batch5A/5B; previous tests were not rerun or represented as current executions.

Exact V1: `<owner>-service/src/main/resources/db/canonical/V1__canonical_schema.sql`, owner=user/hotel/booking/revenue/regulations. All five hashes match5B. [Inventory](source-inventory.json).

Canonical profiles require CANONICAL_DB_URL/APP_USER/APP_PASSWORD, validate Hibernate, disable Flyway/SQL init, initialize UTC. Default YAML remains development configuration. Compose activates docker only, supplies SPRING_DATASOURCE_* with local fallbacks and disables Config Server; no hosted CI/manifests found, .github directories absent. Dockerfile skips tests during packaging, requiring separate release acceptance.

Credential sources: project application placeholders, Compose inputs, ignored .env files, examples and scripts. No secrets sought outside project trees. Supabase CLI not found on PATH; no Supabase connector exposed among available tools. No credentials used. Media Supabase configuration does not identify the five database projects.
