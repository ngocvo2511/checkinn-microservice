# Batch 10B.2 READ tools implementation report

Ngày 22/11/2026. **Status: DONE — internal adapter scope, unit + loopback mock HTTP verified.** Chỉ internal tools; không 10B.3.

## 1. Git baseline

Backend `ci/github-actions`, HEAD `5f7d22b16f8a7ff4e3d8d68e45f84dd5b64851cd`.
Pre-existing: modified `.idea/compiler.xml`, `.idea/encodings.xml`, `pom.xml`; untracked `ai-agent-service/` và sáu tài liệu AI 10A/10B.1. Frontend `fix/frontend-ci-remediation`, HEAD `1c972c009a1439c85b7673895d31f932ec833b06`, clean. Không stage/commit/push/stash/reset/clean/checkout.

## 2. Scope

Chỉ Agent Service và AI documents. No domain business/schema/proto/event/config changes, hosted/DB/provider calls, Docker, frontend hoặc Place.

## 3. Contract reconciliation — recorded BEFORE adapters

Source read ngày 22/11/2026, không coi docs là endpoint authority. Bảng inventory dưới đã đối chiếu controller, DTO, service, validation, security và Gateway rewrite:

| ID / tool đề xuất | Route thực tế | Inputs / validation server | 200 output schema / facts dùng được | Không được suy diễn / evidence |
|---|---|---|---|---|
| A01 `search_hotels` | `/api/hotel/hotels/offers/page` | destination/name ≥1; dates both/neither; rooms/adults/children; price; codes; paging/sort/rating (chi tiết dưới) | `{content:[{hotel:HotelResponse,offer:RoomOffer,reviews:ReviewSummary}],page,size,totalElements,totalPages,sort,availableAmenities:[string]}` | Giá và capacity thuộc một room offer; empty không chỉ ra nguyên nhân; không final checkout quote. E05/E06; AgentReadContractTest, HotelOfferControllerTest, HotelOfferPagingTest |
| A02 `get_hotel` | `/api/hotel/hotels/{hotelId}` | UUID, published active hotel, else 404 | `HotelResponse`: id, ownerId, cityId, name, description, starRating, address, contactEmail/Phone, policies, faqs, amenityCategories, isActive, approvedStatus, city, createdAt/updatedAt, lowestPrice, roomTypes, mediaAssets, accommodationType/Label, textEncoding | ownerId mapper public null (không khẳng định field bị xóa); lowestPrice không availability; không suy hủy booking cụ thể từ free text. E07/E08; HotelMapperPublicBatchTest, InventoryPublicCatalogTest |
| A03 `list_public_rooms` | `/api/hotel/api/v1/hotel/{hotelId}` | UUID; hotel active/published; filter active/effective approval | `[RoomTypeResponse]`: id,hotelId,name,basePrice,capacity:{adults,children,bedType,roomSize,breakfastIncluded,breakfastQuantity},amenities:[string],isActive,totalRooms,createdAt,updatedAt,mediaAssets,description,textEncoding | totalRooms là approved stock, không remaining by date. Empty là không có room public, không “hết phòng”. E08/E09; RoomTypeServiceImplTest, AgentReadContractTest |
| A04 `get_public_room` | `/api/hotel/api/v1/{roomTypeId}` | UUID; active room/published hotel; effective approval | `RoomTypeResponse` | Unapproved → **409 INVENTORY_APPROVAL_REQUIRED**, không hiển thị như bookable; missing/inactive/unpublished →404. E09; RoomTypeServiceImplTest |
| A05 `check_availability` | `/api/hotel/api/v1/availability/check` | required roomTypeId UUID, checkIn/checkOut ISO dates, quantity integer 1–20; stay rules | `{available:boolean,availableRooms:integer,checkedAt:UTC instant}` | available = count≥quantity, không bảo đảm capacity hay giá; không hold. 409 approval/config/inconsistent khác sold out. E10; AvailabilityReadValidationTest, AgentReadContractTest |
| A06 `resolve_locations` | `/api/hotel/cities/search/locations` | required `query:string`; LocationService dùng name-containing-ignore-case cho province/city | `[{id,name,latitude,longitude,hotelCount,createdAt,parentName,type:CITY/PROVINCE,parentId}]` | hotelCount không phải tồn phòng; không tính khoảng cách/Nearby; nhiều match cần phân biệt bằng parent/type. Controller comment nói tên/code nhưng service khảo sát chỉ tìm name. E11; LocationSearchIntegrationTest |
| A07 `list_accommodation_types` | `/api/hotel/hotels/accommodation-types` | none | `[{code,label}]` từ enum | Không gán type cho hotel null. E11; AccommodationTypeServiceTest |
| A08 `list_amenity_catalog` | `/api/hotel/amenity-catalog` | none; active canonical entries | `[{code,label,description,active,revision}]` | catalog existence không chứng minh hotel có amenity; không tự map raw label sang code thiếu evidence. E11; AmenityCatalogServiceTest |
| A09 `get_review_summaries` | `/api/hotel/api/v1/reviews/summaries` | `hotelIds` UUID list 1–100, dedup preserves order | `[{hotelId,totalReviews:long,averageRating:decimal|null}]`, avg scale 0–10, round2 HALF_UP | absent/private ID bị omit, không synthesize zero; zero count + null = known no reviews. Search đã có summary nên tránh gọi trùng. E12; ReviewSummaryServiceTest |
| A10 `get_cancellation_policy` | `/api/bookings/cancellation-policy` | none | `{version,checkInTime,timeZone,freeCancellationHours}` từ CancellationPolicy.Details | Chỉ policy chung; không giải thích thành refund eligibility/amount hoặc chính sách mọi rate plan. E12; Booking controller/security + AgentWriteContractTest public boundary |


| Tool | Controller / service evidence | Projection / limits / limitations |
|---|---|---|
| A01 | HotelOfferController.searchPage; HotelOfferCriteria/PageCriteria; HotelOfferSearchService; HotelOfferPaging | items + offer + summaries only; one type/hotel; active APPROVED; browse defaults explicitly labelled; dated occupancy explicit; base VND/room/night, no final quote |
| A02 | HotelController.getHotel → HotelServiceImpl.getDetail; HotelMapper public | hotelId/name/address whitelist/policy/FAQ/amenity labels+verified codes/nullable lowestBasePrice; drop owner/contact/media/room graph |
| A03 | RoomTypeController.getByHotel → RoomTypeServiceImpl.getPublicByHotel | public rooms ≤10, hasMore; service effective approval authoritative, totalRooms never remaining availability |
| A04 | RoomTypeController.getById → getPublicById | same PublicRoom; 404 absent/inactive/private distinct from 409 approval |
| A05 | AvailabilityController.check → AvailabilityService.check; AvailabilityCheckResponse | available/count/server checkedAt; request dates/quantity are request provenance, not echoed server facts; no hold |
| A06 | CityController.searchLocations → LocationService.searchLocations | id/name/parent/type ≤10, hasMore; name search only despite controller comment mentioning code |
| A07 | AccommodationTypeController.catalog | exact code/label ≤50, no invented synonyms |
| A08 | AmenityCatalogController.catalog → AmenityCatalogService.catalog(false,null) | active code/label ≤200; reject oversize catalog, no complete-catalog truncation |
| A09 | ReviewSummaryController.summarize → ReviewSummaryService.summarize | ≤3 summaries + missingHotelIds; omit private/missing, never fill zero; no reviewer identities |
| A10 | BookingController.cancellationPolicy → CancellationPolicy.details; Booking SecurityConfig explicit permitAll | four typed general fields only; no booking/refund eligibility |

All methods fixed GET. Hotel Gateway `/api/hotel/**` strips prefix, Booking `/api/bookings/**` preserves path. Hotel security permits these anonymous reads; private/preview/admin/hold excluded. All tool errors distinguish transport status and allowlisted business code; 200 empty arrays/pages only become EMPTY. Parameters/schema match Batch10A (stricter agent limits than server size50/review100). Model cannot set method/path/headers. Request-ID backend convention `[A-Za-z0-9_-]{1,64}`; only system-generated outbound ID, validated upstream ID retained separately.

Discrepancies: old READ_TOOL_CONTRACT suggests retry1/contact opt-in/older checkedAt compatibility; current task requires retry0/no contact/strict current server checkedAt. No domain API change. Room DTO has no approval flag: trust public service effective predicate, never infer approval from totalRooms or isActive alone. D04 approved ONLY internal A01–A10/minimal projection, not automatic LLM exposure or public ingress. D03 other caps remain proposals, applied as local adapter safety limits without claiming global approval.

## 4. Files changed in this batch

Only `ai-agent-service/**` và AI documents:

- `ai-agent-service/pom.xml`: add Boot-managed Apache HttpClient5; LangChain4j/Boot versions unchanged.
- `ai-agent-service/README.md`, `src/main/resources/application.yml`: internal adapter usage/trusted Gateway configuration; feature OFF and fake default unchanged.
- New `src/main/java/com/example/aiagent/tools/ToolId.java`, `ToolError.java`, `ToolFailure.java`, `ToolResult.java`, `ToolExecutionContext.java`, `ToolArguments.java`, `GatewayReadClient.java`, `UpstreamErrors.java`, `ToolProjection.java`, `ToolRegistry.java`, `ReadToolConfiguration.java`.
- New `src/main/resources/tools/read-input-schemas.json`, `read-output-schemas.json`.
- New `src/test/java/com/example/aiagent/tools/ReadToolsTest.java`, `src/test/resources/tools/synthetic-fixtures.json` (extracted synthetic10A fixture bodies, not domain data).
- Updated `AI_AGENT_READ_TOOL_DESIGN.md`, `AI_AGENT_READ_ARCHITECTURE.md`, `BATCH10B_AI_READ_IMPLEMENTATION_PLAN.md`, `BATCH10A_ARCHITECTURE_REVIEW.md` (D04 scoped approval) and this new report.

Root POM/IDE edits predate10B.2 and were not edited. Existing provider/foundation source/tests, evaluation plan and10B.1 report preserved. No sibling service/frontend/schema/proto/event/production config edits. Standard Maven lifecycle generated ignored target output; no manual generated-file edits.

## 5. Registry architecture

Exactly10 closed ToolId enum values and unique stable names. Each Definition exposes ID/name/LLM description, dereferenced input schema, output schema, GET/path template, timeout, body/output caps, named error mapper and handler. `execute(name,rawJson,context)` rejects unknown names; typed overload accepts no route/method/header. No generic HTTP, SQL, browser, code or WRITE tool. No controller/public ingress.

`specifications()` creates passive LangChain4j ToolSpecifications. No callback registration, @Tool, AI Services or automatic execution. Fake proposal remains data (test observes zero requests). Caller must explicitly choose specifications in10B.3; this batch does not expose all ten to a model.

SDK1.17.0 root JsonObjectSchema cannot encode root anyOf/dependentRequired. Properties use dereferenced raw child schemas, required/additionalProperties retained; complete authoritative Draft2020-12 schema is metadata `draft202012InputSchema`. Server enforces root/cross-field rules regardless of model capability. This is an explicit SDK representation limitation, not permission to drop validation; live provider dialect acceptance remains NOT_RUN. No claim Gemini accepts/enforces full Draft2020-12.

## 6. Validation implementation

- Input JSON extracted exactly from approved10A block; parity test compares document/resource. Validator covers every keyword used there, not arbitrary future Draft2020-12 extensions. Adding new keywords requires validator/test changes.
- Strict JSON duplicate keys/trailing tokens/type/required/unknown-property/enum/bounds/array uniqueness checks. Reject null wrong shape, oversized raw arguments16KiB UTF-8, depth>32, unsupported names. No URL/header/method accepted, no guessed/clamped values.
- UUIDv3/v4 canonical strings only, no numeric/short UUID/path text. Civil ISO dates validated with injected Clock and explicit Asia/Ho_Chi_Minh; both/neither; today or later; checkout later;≤30 calendar nights. Midnight UTC+7 and30/31-night boundaries tested.
- rooms1–20/adults1–40/children0–40; min/max nonnegative and ordered; only one location selector, hotelName may supplement. Missing destination fails before HTTP.
- Browse omits dates/occupancy/prices; transport explicitly sets page0,size5,sortDEFAULT, labels BACKEND_DEFAULT_BROWSE. Dated search requires all three occupancy arguments AND exact match to server-owned user-confirmed context; model-provided numbers alone cannot become USER_CONFIRMED. Missing confirmations→MISSING_REQUIRED_CRITERIA; mismatch→POLICY_DENIED.
- Catalog membership comes from explicit validated request-local context. No hidden A07/A08 calls; missing catalog evidence fails closed. Phase10B.3 can make explicit catalog calls and populate context. Unknown code with known catalog→INVALID_ARGUMENTS.
- A05 requires confirmed requested quantity and public room→selected-hotel relation from prior A03/A04/search evidence. Missing identity grounding→POLICY_DENIED. A04 can resolve a previously ungrounded public ID and validates known relationship if provided. Context is internal constructor-only API, not a model/user JSON contract.

## 7. HTTP client implementation

Dedicated Apache HttpClient5.3.1, Boot-managed httpcore5/httpcore5-h2.5.2.5; stable Java21, no LangChain starter. JDK HTTP transport was not used for domain tools, so retry behavior is explicitly controlled by Apache disableAutomaticRetries rather than relying on JDK GET defaults. Does not use system proxy properties, caller credentials, cookie store or auth negotiation. TLS verification defaults preserved.

Trusted origin from `checkinn.read-tools.gateway-origin`/`AGENT_GATEWAY_ORIGIN`, default loopback8080. Reject non-http(s), userinfo, path prefixes, query, fragment. No per-tool/caller base URL. Fixed ToolId paths and encoded query parameters, validated UUID path interpolation only. No private/admin/preview/internal/hold/Place/payment endpoints or alternate route fallback. Array query parameters repeated, not arbitrary query fragments.

GET request supplies only Accept application/json, Accept-Encoding identity and system-generated X-Request-ID (plus client standard protocol headers). No inbound request/header object exists in this API. Responses require exact HTTP200 for data; redirects/other2xx are errors. Content type application/json (optional parameters), encoding identity only. Compression disabled AND compressed responses rejected before parse, so compressed payloads cannot bypass decoded-size cap. Both content length and streamed byte count enforced.

## 8. Security boundary

Feature OFF before adapter execution, fake model default unchanged, no key lookup/model calls. Internal method dispatch only. No JWT/cookie/user/service headers or service credentials. Generated request ID is correlation, not authorization. No payload/exception/query/URL logging; Apache and LangChain logging OFF in default YAML. Upstream error text/body/unknown code/stack trace never enters ToolError. No domain database/config client/migration/RPC dependencies.

Structured ownerId/contact/media/reviewer/internal fields dropped by construction, including nested allowlists. Null rating/capacity/base price stay unknown. Text policies/descriptions are untrusted data; nothing in them controls transport/registry. Narrative prompt-injection/grounding protection is10B.3+ scope, not claimed from adapter tests. Public service effective approval remains authoritative; no fabricated approval field or stock-based approval inference in Agent.

## 9. Projection per tool

| Tool | Implemented projection / essential guards | Status |
|---|---|---|
| A01 | ≤5 items, hotelId/name/optional city/star/type, coherent offer and summaries, page math; approval/public hotel flags, identity, quantity, dates and unit×quantity equality; no room graph or final quote; occupancy provenance; each offer keeps checkedAt | Implemented + unit/mock HTTP verified |
| A02 | hotelId/name/optional description/address whitelist/star/type label, nullable lowestBasePrice, whole policies/FAQs, amenity label+verified codes; max10 sections, whole long items dropped and flagged | Implemented + unit/mock HTTP verified |
| A03 | hotelId/≤10 public rooms/hasMore; IDs/relationship, room active, nullable basePrice/capacity, strings only amenities; no totalRooms as availability | Implemented + unit/mock HTTP verified |
| A04 | same PublicRoom whitelist; requested identity/known hotel relation;409 never becomes data | Implemented + unit/mock HTTP verified |
| A05 | request-provenance room/date/quantity + available/count; count≥0, boolean=count≥quantity, required server checkedAt in evidence; false is OK negative observation, not EMPTY/error/hold | Implemented + unit/mock HTTP verified |
| A06 | ≤10 id/name/parent/type, hasMore, valid CITY/PROVINCE; no coordinates/count | Implemented + unit/mock HTTP verified |
| A07 | exact code/label≤50; catalog overflow rejected, not silently complete | Implemented + unit/mock HTTP verified |
| A08 | active code/label≤200; no description/revision; inactive or oversized payload rejected | Implemented + unit/mock HTTP verified |
| A09 | ≤3 requested unique summaries + omitted IDs; count0 requires null avg, count>0 requires score0–10; no identities or zero fill | Implemented + unit/mock HTTP verified |
| A10 | typed version/checkInTime/timeZone/freeCancellationHours only; validates time/zone; no booking/payment/refund fields or eligibility | Implemented + unit/mock HTTP verified |

Prices preserve backend BigDecimal precision; no currency/fees/tax/coupon fields fabricated where DTO lacks them. API/source/ToolSpecification describe base room/night semantics, not inclusive final quote. Omitted noncritical descriptions/clauses flagged truncated, never partial text represented as complete policy. Essential projection>12KiB UTF-8 fails OUTPUT_TOO_LARGE. No auto paging.

## 10. Error mapping and evidence

ToolError `{category,status,code,safeMessage,retryable:false}` retains HTTP status independently of allowlisted business code. Known Vietnamese messages are fixed, not upstream text. Unknown codes normalize UPSTREAM_FAILURE.

| Condition | Category/behavior |
|---|---|
| malformed/wrong/unknown/unsupported input | INVALID_ARGUMENTS / UNSUPPORTED_TOOL |
| missing confirmed slots/evidence; invalid dates/occupancy | MISSING_REQUIRED_CRITERIA / INVALID_DATE_OCCUPANCY |
| context denied/feature OFF/busy | POLICY_DENIED |
|404|NOT_FOUND (missing or not public; no existence inference)|
|409 approval|INVENTORY_APPROVAL_REQUIRED|
|409 unconfigured/inconsistent|INVENTORY_UNKNOWN|
|409 other|STATE_CONFLICT|
|401/403|UNAUTHORIZED_UPSTREAM / FORBIDDEN_UPSTREAM, no escalation|
|429|RATE_LIMITED, no retry/wait loop|
|500/502/503, connection failure|BACKEND_UNAVAILABLE|
|504, connect/socket/full deadline|BACKEND_TIMEOUT|
|invalid JSON/depth/duplicate keys|INVALID_UPSTREAM_JSON|
|wrong successful shape/type/content/encoding/non200 success/redirect|INVALID_UPSTREAM_RESPONSE|
|body too large; projection too large|OVERSIZED_UPSTREAM_RESPONSE / OUTPUT_TOO_LARGE|
|cancel/interrupt|CANCELLED, interrupt restored|
|unexpected adapter exception|INTERNAL_ADAPTER_FAILURE, no raw cause|

EMPTY occurs only after valid200 empty page/list; missing summary IDs remain unknown. A05 false availability is a valid OK observation carrying false/count, distinguishable from error/empty. Metadata toolId/name/schemaVersion/criteriaRevision and source route template; generated requestId identifies outbound attempt, validated upstreamRequestId kept separately, retrievedAt from injectable UTC Clock, optional server checkedAt (A05; A01 per offer), checkedAtSource and truncated. Never synthesize server checkedAt, backend requestId or missing financial/inventory facts. No assertion of full W3C trace continuity: that real integration is NOT_RUN.

## 11. Timeout / size / retry enforcement

- Connect timeout positive whole milliseconds≤3s (default3s), per-request timeout≤10s and socket timeout bounded. Registry monotonic deadline starts BEFORE validation and includes worker admission, headers, body reads, JSON parse and projection. Validation also bounded16KiB; worker checks expiry before request; caller rejects late completion.
- Caller polls cancellable Future within remaining deadline, checks explicit cancellation/interruption. Expiry/cancel cancels HttpGet, interrupts worker and closes socket. Stream oversize cancels request BEFORE stream cleanup to avoid draining unbounded body. No queue, max2 workers/pool2; busy fails closed. These are adapter safeguards, not full turn-budget executor/global D03 approval.
- Body read at most256KiB+1 detection byte; allocation bounded before parse. Content-Length checked but not trusted as sole guard; chunked oversize tested. Accept identity only, reject compression. JSON depth32 enforced by Jackson constraints, strict duplicate/trailing tokens. Data UTF-8≤12KiB after whitelist projection.
- **Retry0** explicitly disableAutomaticRetries; redirects disabled, no alternate endpoint, no page walks/catalog prefetch/provider fallback. Status429/500/502/503/504 and connection failure counted once. Mock recorder verifies no second requests/redirect/private path.
- Deadline includes body/parse from caller perspective. Java cannot forcibly stop bounded Jackson CPU parsing mid-instruction; cancellation discards late result, bounded worker can finish short CPU work after caller timeout. No claim hard realtime OS scheduling or live-network TLS cancellation acceptance. Stalled headers and partially written stalled bodies return timeout in test deadline200ms (assert<1s); production cap stays≤10s, not raised to wait Gateway20s.
- Context attempt ledger increments immediately before the single client.execute, records failed attempts too; local validation/disabled/busy rejection do not count as HTTP. It records dispatch attempts, not a claim server received a failed connection. No6-attempt turn executor/dedup/budget implemented yet.

## 12. Executed commands

All final Maven commands from `checkinn/`, Java21.0.3/Maven3.9.9, no external-resource integration flags.

| Command | Result |
|---|---|
| Git branch/rev-parse/status both repos before changes | Baselines in§1 |
| `mvn -B -ntp -pl ai-agent-service test -DskipTests` | Compile/testCompile SUCCESS; tests explicitly SKIPPED here, not acceptance evidence |
| `mvn -o -B -ntp -pl ai-agent-service test` | Intermediate127/0/0/0 SUCCESS |
| `mvn -o -B -ntp -pl ai-agent-service package` (final21:19:26) | **140 tests,0fail,0error,0skip; BUILD SUCCESS** |
| `mvn -o -B -ntp -pl ai-agent-service dependency:tree -Dverbose "-DoutputFile=target/dependency-tree-10b2.txt"` | SUCCESS; HttpClient5.3.1/core5.2.5, no new LangChain version/BOM changes |
| `mvn -o -B -ntp -pl '!place-service' -DskipTests compile` | **BUILD SUCCESS**,13service + root14entries; excludes Place; final Agent package includes later provenance guard refinement |
| `mvn -o -B -ntp -pl hotel-service "-Dtest=AgentReadContractTest,AvailabilityReadValidationTest,HotelOfferCriteriaLimitsTest" test` (rerun after reactor) | **15tests,0fail,0error,0skip; BUILD SUCCESS21:19:10**; MockMvc/unit, no DB |
| Git diff --check both repos + direct source/resource/doc whitespace checks | PASS; tracked diff alone cannot cover untracked files, so direct checks added |
| Static route/method/header/callback inspection + schema JSON parity | PASS; exactly10 distinct fixed allowed routes, no WRITE/private/Place/header escalation; schema parity also executable test |

Intermediate failures retained transparently: initial Maven command mistakenly from workspace root could not select module (no source failure); corrected cwd. Hotel test first overlapped reactor compilation in same target and failed testCompile missing symbols; rerun after reactor completed PASS15 without changing Hotel source/tests. One static text scanner naively flagged substring Cookie inside disableCookieManagement; corrected to actual credential-header setters, PASS. No tests/assertions removed or weakened. Initial Agent127 and intermediate139 are checkpoints, not totals added to final140.

## 13. Test results and evidence classification

**Final Agent140 = existing foundation32 + new adapter/registry/validation108.**0fail/0error/0skip. Surefire XML/text under `ai-agent-service/target/surefire-reports/`, package and dependency tree under target (ignored generated evidence; reproducible commands above). Hotel15 separate. Existing foundation deny-all network guard still asserts zero network calls; HTTP tests run separately with only loopback server.

108 cases cover each tool happy/invalid/empty-or404/failure/projection, fixed GET and exact path, credentials absent, generated requestId propagated/upstream ID retained/revision/time, schema parity + passive specifications/no auto execution, required/wrong/null/unknown/duplicate/malformed/oversized input, enums/catalog evidence, selectors, UUIDv3/v4, civil dates/midnight/30night bounds, guest limits, browse defaults, unconfirmed occupancy, grounded A05, public/approval distinction, no-availability negative fact, all status/error variants, no redirects/retry, malformed/duplicate/deep JSON, content type/encoding, fixed-length and chunked oversize, full-body/header timeout, in-flight cancel/interrupt, flag OFF/origin rejection, unknown/null vs zero, price arithmetic mismatch, missing checkedAt, complete-clause truncation, output cap, failed connection attempt counting. Assertions inspect parsed structured data; not keyword-only conversational grading.

| Evidence level | Status |
|---|---|
|10internal tool implementations|Implemented10/10|
|Agent unit/validation/foundation|Unit tested PASS (included140)|
|Real loopback socket adapters with synthetic backend-shaped JSON|Mock HTTP verified PASS (included108; not Gateway)|
|Hotel public boundary/validation regression|15PASS, unit/MockMvc only|
|Selected reactor compile excluding Place|PASS; tests NOT_RUN by compile|
|Real Gateway→Hotel/Booking Agent integration|**NOT_RUN**|
|Gemini live integration / OpenAI live integration|**NOT_RUN**|
|Hosted Supabase/domain DB/provider/payment/refund/Docker|**NOT_RUN**, outside scope|
|Full reactor tests/frontend/npm/browser/40-case orchestration/model evaluation|**NOT_RUN**, not implemented/required for this boundary|

No skipped assertions in final selected suites. Warnings: existing Java21 SecurityManager/dynamic Mockito agent and Hotel Lombok/MapStruct warnings; not failures or skipped tests.

## 14. Remaining gaps / decisions

No tool is BLOCKED/PARTIAL against the internal10B.2 contract. No blocking endpoint/DTO mismatch; source limitations in§3 remain explicit (A01 one type and no currency/final quote fields, A05 request provenance, no room approval flag in DTO, A06 name-only search). No business API change to accommodate adapters.

Future PolicyExecutor MUST own actual per-turn budgets/proposal admission/dedup/revision staleness, populate server-owned confirmed occupancy and grounded context from trusted user/evidence, perform explicit catalog calls only when needed, and expose only approved selected declarations. No orchestration/context deserialization endpoint exists yet. Full context untrusted-text grounding, model behavior, observability spans/end-to-end trace, OS/live TLS/real Gateway behavior are not proven by loopback tests.

Remaining approvals: D02 exact model/account/region/data terms/finite budget before live model; D03 other proposed admission/context/parallel/global limits before their phase; D05 ingress/session/privacy; D07 evaluation thresholds; D08 enable/rollout. D04 approved ONLY internal A01–A10/minimal projection. Local caps enforced safely without declaring all D03 approved. No new approval required to complete scoped10B.2; no stage/commit/push or user data operations performed.

## 15. Readiness for Batch 10B.3

**READY as technical internal foundation for separately instructed10B.3.** Registry, schemas, strict validation, safe cancellable fixed GET transport, per-tool projection/errors/evidence, attempt ledger and deterministic tests are available. Batch10B.2 **DONE**, not whole10B or deployment/model readiness. Feature remains OFF. No implementation of PolicyExecutor/AgentOrchestrator/session/chat/UI/live provider; stop after10B.2 as requested.
