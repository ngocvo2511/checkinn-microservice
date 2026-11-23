# AI Agent READ tools — Batch 10A

> **Checkpoint 10B.2 — 22/11/2026:** D04 được duyệt chỉ cho **10 internal READ tools A01–A10 và minimal public projection**. Registry, validation, fixed anonymous GET adapters, bounded projection và deterministic mock HTTP tests đã triển khai. Không public API/chat, không tự expose toàn registry cho LLM, không automatic tool execution/WRITE. Feature OFF, fake provider giữ nguyên. Các caps adapter 256KiB/depth32/data12KiB là biện pháp an toàn local theo task, không tuyên bố D03 được duyệt toàn bộ. [Báo cáo 10B.2](BATCH10B2_READ_TOOLS_IMPLEMENTATION_REPORT.md) ghi evidence và giới hạn hiện hành; checkpoint 10B.1 bên dưới là lịch sử. **Không triển khai 10B.3 trong batch này.**


> **Checkpoint 10B.1 — 22/11/2026:** Java 21 Agent Service độc lập và LangChain4j đã được duyệt; implementation giới hạn ở skeleton offline. Chọn LangChain4j core **1.17.0**, Spring Boot **3.3.4**, không starter/AI Services. Gemini Free Tier trước, Gemini Paid/OpenAI về sau; model, budget và provider data terms chưa duyệt, **không live calls**. Chỉ các hard limits 10s/tool, 30s/turn, 6 HTTP attempts, 6 proposals, 3 model requests, 2 rounds, retry0 được duyệt; các giới hạn khác trong 10A vẫn là đề xuất. Local pilot được duyệt với feature OFF; không phải approval bật ingress/UI hoặc start stack trong batch này. [Báo cáo 10B.1](BATCH10B1_LANGCHAIN4J_SKELETON_REPORT.md) là checkpoint hiện hành; nội dung 10A bên dưới là thiết kế và bằng chứng lịch sử khi có khác biệt.


22/11/2026. **PROPOSED**, không thêm endpoint/application code. Đối chiếu [READ_TOOL_CONTRACT](READ_TOOL_CONTRACT.md), [source index E05–E12](BATCH10A_ARCHITECTURE_REVIEW.md#source-evidence-index). HTTP paths dưới đây là Gateway routes đã tồn tại; tên tool và projection là thiết kế mới.

## API inventory

Mọi route trong bảng là **GET, public anonymous**. Hotel sở hữu tất cả trừ cancellation-policy thuộc Booking. Không Authorization/cookie/service identity. Deadline Agent đề xuất ≤10.000ms gồm connect, headers, body, parse; Gateway hiện connect 3s/response 20s mặc định; canonical DB pool wait 10s. Timeout client có thể xảy ra trước body 503 từ pool: không hứa sẽ luôn nhận 503. Với mọi route: 400 malformed/type/validation, 401/403 unexpected access failure, 404 missing/route, 409 business conflict nếu phát sinh, 500/503/504 dependency failure theo bảng mapping; không khẳng định mọi endpoint chủ động ném mọi mã. 429 là defensive handling, không có chứng cứ Gateway limiter đang áp dụng.

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

Các API contract cũ sau có thật nhưng **không đưa vào MVP allow-list** (không thiếu endpoint): `/api/hotel/hotels/offers` list không paged trùng A01; `/api/hotel/cities` → CityResponse list (id/name/coordinates/hotelCount/createdAt/parentName/parentId); `/api/hotel/hotels/amenities/categories` và `/api/hotel/hotels/policies/categories` → `[{type}]` chỉ taxonomy, không chứng minh hotel sở hữu; `/api/hotel/api/v1/reviews/stats/hotel/{hotelId}` → HotelReviewStatsResponse (averageRating,totalReviews,ratingDistribution1..10, averageStaff/Amenities/Cleanliness/Comfort/ValueForMoney/LocationRating,recentReviews). Stats có review text/user-related data không cần thiết khi summaries đã đủ. Tất cả public GET theo AgentReadContractTest; cùng deadline/error policy nếu sau này thêm, phải review schema/PII và explicit allow-list. Không dùng generic `catalog(kind)` nhận route tùy ý.

### Backend nested DTO fields (source, not new projections)

`HotelAddressDto={street,city,country,latitude:double|null,longitude:double|null,coordinateSource}`; forRead clears coordinates unless OWNER_PROVIDED/GEOCODED, so no distance claim from raw or legacy coordinates. `CityResponse={id,name,latitude,longitude,hotelCount,createdAt,parentName,parentId}`. `MediaAssetResponse={id,targetId,targetType,url,isThumbnail,sortOrder,createdAt}`. Hotel policies are `[{category,content}]`; FAQs `[{question,answer}]`; amenityCategories `[{id,title,items:[{id,title,verifiedCodes:[{code,label}]}]}]`. Room CapacityDto includes adults/children, bedType, roomSize, breakfastIncluded and breakfastQuantity; nullable descriptive values are unknown, not false/zero. Their absence from the MVP projection is data minimization, not evidence the backend lacks those fields. Public room price is basePrice, not room-size-dependent pricing invented by Agent. Nullability/collections must be checked before claims, never filled from neighboring hotel/room. Links: [address DTO](../../hotel-service/src/main/java/com/example/hotelservice/Hotel/dto/request/HotelAddressDto.java), [capacity DTO](../../hotel-service/src/main/java/com/example/hotelservice/Room/dto/request/CapacityDto.java), [media DTO](../../hotel-service/src/main/java/com/example/hotelservice/MediaAsset/dto/response/MediaAssetResponse.java), [city DTO](../../hotel-service/src/main/java/com/example/hotelservice/City/dto/response/CityResponse.java).

## Search semantics và slot handling

Source `HotelOfferCriteria`, `HotelOfferPageCriteria`, `HotelOfferPaging`, `HotelOfferSearchService` là evidence cho các quy tắc sau:

- UUID string native identifiers, không số/v4-only. cityId precedence trước provinceId rồi cityName; Agent chọn đúng một location selector để tránh silent precedence. hotelName có thể bổ sung location (AND). Ít nhất một selector hoặc hotelName.
- Ngày civil `YYYY-MM-DD`, cả hai hoặc không; checkIn ≥ today Asia/Ho_Chi_Minh, checkOut>checkIn, tối đa 30 đêm lịch. Backend mặc định rooms=1/adults=1/children=0; **không được biến defaults thành thông tin người dùng đã cung cấp**.
- Agent browse mode: không dates/occupancy/price nếu người dùng chưa cho; request bỏ occupancy nên backend dùng defaults, trả metadata `occupancyBasis=BACKEND_DEFAULT_BROWSE`, nói đây là gợi ý catalog sơ bộ, chưa kiểm ngày/số khách. Không cần hỏi ngày trước khi đưa gợi ý sơ bộ cho “Tìm khách sạn ở Đà Nẵng”.
- Agent stay mode: yêu cầu đủ hai ngày, rooms, adults, children (có thể người dùng xác nhận “không trẻ em”); nếu thiếu hỏi đúng slot trước tuyên bố phù hợp đoàn/availability. Không tự chọn ngân sách, ngày, hoặc children=0 cho confirmed fit. Nếu “2 khách” mơ hồ adults/children thì hỏi. Nếu yêu cầu ngày cụ thể mà thiếu occupancy, có thể trả catalog với caveat, không gọi dated fit mặc định.
- Price min/max ≥0, min≤max: **base unitPrice mỗi room/night VND**, không tổng ngân sách nhiều phòng/toàn kỳ. Hỏi rõ đơn vị ngân sách mơ hồ; không âm thầm chuyển total budget sang filter. Giá số thập phân, xử lý BigDecimal, không thay rounding.
- RoomCount×capacity.adults/children ≥ tổng adults/children riêng; không gộp sức chứa nhiều room types, không suy trẻ em dùng adult capacity. One type offer; search không tìm split-room combinations.
- amenityCodes AND, canonical active code; accommodationTypes any-of enum; starRatings any-of 1–5 (≤5 phần tử); guestRatings any threshold average≥n trên 0–10 (≤20 phần tử, không khẳng định OR là AND); no reviews bị loại khi guestRatings filter. sort PRICE_* theo unitPrice, RATING_DESC null last, ties hotel ID; DEFAULT ID. Page snapshot không pinned giữa calls; không hứa cross-page stable inventory.
- Raw label `amenities` tồn tại trên page API (AND exact label, ≤50 labels/≤255 chars) nhưng không expose cho model MVP, ưu tiên codes đã xác minh. `availableAmenities` là facets candidate trước page filters, không phải amenities của mọi kết quả.

## JSON input schemas (Draft 2020-12, thiết kế mới)

Registry này là artifact có thể trích xuất để validate trong 10B. Tất cả object `additionalProperties:false`; không nhận auth/header/url/method. Descriptions có thể rút từ bảng A01–A10; trình cung cấp schema phải dereference `$ref` nếu provider không hỗ trợ. Giới hạn thêm (text length, array count, size≤5) là **Agent budget đề xuất**, không thay contract server size≤50. Format UUID/date phải bật assertion trong validator; cross-field rules ở phần tiếp theo bắt buộc thực thi trong code, không chỉ prompt.

```json
{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "$defs":{
    "id":{"type":"string","format":"uuid"},
    "date":{"type":"string","format":"date","pattern":"^[0-9]{4}-[0-9]{2}-[0-9]{2}$"},
    "empty":{"type":"object","additionalProperties":false,"properties":{}},
    "hotel":{"type":"object","additionalProperties":false,"required":["hotelId"],"properties":{"hotelId":{"$ref":"#/$defs/id"}}},
    "room":{"type":"object","additionalProperties":false,"required":["roomTypeId"],"properties":{"roomTypeId":{"$ref":"#/$defs/id"}}}
  },
  "tools":{
    "search_hotels":{
      "type":"object","additionalProperties":false,
      "properties":{
        "cityId":{"$ref":"#/$defs/id"},"provinceId":{"$ref":"#/$defs/id"},
        "cityName":{"type":"string","minLength":1,"maxLength":120},
        "hotelName":{"type":"string","minLength":1,"maxLength":200},
        "checkIn":{"$ref":"#/$defs/date"},"checkOut":{"$ref":"#/$defs/date"},
        "rooms":{"type":"integer","minimum":1,"maximum":20},
        "adults":{"type":"integer","minimum":1,"maximum":40},
        "children":{"type":"integer","minimum":0,"maximum":40},
        "minPrice":{"type":"number","minimum":0},"maxPrice":{"type":"number","minimum":0},
        "accommodationTypes":{"type":"array","maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}},
        "amenityCodes":{"type":"array","maxItems":10,"uniqueItems":true,"items":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{0,63}$"}},
        "starRatings":{"type":"array","maxItems":5,"uniqueItems":true,"items":{"type":"integer","minimum":1,"maximum":5}},
        "guestRatings":{"type":"array","maxItems":20,"uniqueItems":true,"items":{"type":"number","minimum":0,"maximum":10}},
        "page":{"type":"integer","minimum":0,"maximum":2147483647},
        "size":{"type":"integer","minimum":1,"maximum":5},
        "sort":{"enum":["DEFAULT","PRICE_ASC","PRICE_DESC","RATING_DESC"]}
      },
      "anyOf":[{"required":["cityId"]},{"required":["provinceId"]},{"required":["cityName"]},{"required":["hotelName"]}],
      "dependentRequired":{"checkIn":["checkOut"],"checkOut":["checkIn"]}
    },
    "get_hotel":{"$ref":"#/$defs/hotel"},
    "list_public_rooms":{"$ref":"#/$defs/hotel"},
    "get_public_room":{"$ref":"#/$defs/room"},
    "check_availability":{
      "type":"object","additionalProperties":false,"required":["roomTypeId","checkIn","checkOut","quantity"],
      "properties":{"roomTypeId":{"$ref":"#/$defs/id"},"checkIn":{"$ref":"#/$defs/date"},"checkOut":{"$ref":"#/$defs/date"},"quantity":{"type":"integer","minimum":1,"maximum":20}}
    },
    "resolve_locations":{"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":120}}},
    "list_accommodation_types":{"$ref":"#/$defs/empty"},
    "list_amenity_catalog":{"$ref":"#/$defs/empty"},
    "get_review_summaries":{"type":"object","additionalProperties":false,"required":["hotelIds"],"properties":{"hotelIds":{"type":"array","minItems":1,"maxItems":3,"uniqueItems":true,"items":{"$ref":"#/$defs/id"}}}},
    "get_cancellation_policy":{"$ref":"#/$defs/empty"}
  }
}
```

Cross-field validation: trim then nonblank; reject incompatible selectors; stay limits with injected clock; min≤max; catalog membership (fetch A07/A08 only when needed); occupancy provenance; `check_availability.quantity` equals explicit requested rooms and room belongs known selected public hotel (A03/A04 or search evidence). If user supplies ID not yet grounded, A02/A04 can resolve public identity; never accept path text. Dated search requires all occupancy slots at Agent layer. Page default0/size5/sortDEFAULT proposed; no silent filter drop. Validation failure produces clarification/typed error before HTTP. No automatic business-limit expansion or decrement 21→20.

## Output schema và projection

Backend DTO schemas ở bảng inventory giữ nguyên; Agent sử dụng **schema projection version `agent-read-v1`**, không đưa raw response vào LLM. Common envelope (thiết kế, không đổi backend API):

```json
{
  "tool":"check_availability","schemaVersion":"agent-read-v1","criteriaRevision":2,
  "outcome":"OK","data":{"roomTypeId":"33333333-3333-4333-8333-333333333333","checkIn":"2026-12-01","checkOut":"2026-12-03","quantity":1,"available":true,"availableRooms":2},
  "evidence":{"id":"turn1-call2","sourceApi":"A05","requestId":"safe-id","retrievedAt":"2026-11-22T03:00:00Z","checkedAt":"2026-11-22T03:00:00Z","checkedAtSource":"SERVER","truncated":false},
  "error":null
}
```

Normative shape: tool enum A01–A10 names; revision nonnegative integer; outcome `OK|EMPTY|ERROR`; data typed below or null for ERROR; evidence includes retrievedAt UTC (Agent clock), optional checkedAt UTC/requestId, checkedAtSource `SERVER|CLIENT_RETRIEVED|NOT_AVAILABLE`; ERROR contains `{category,status:int|null,code,safeMessage,retryable:boolean}`. EMPTY only valid 200 empty list/page, not dependency failure. Missing checkedAt on current dated API is schema error; compatibility with older server only if separately enabled and labelled CLIENT_RETRIEVED, never pretend server verified instant.

| Tool | Projected `data` type, fields mandatory unless `?`; limits proposed | LLM minimization / validation |
|---|---|---|
| search_hotels | `{items:[{hotelId,name,cityName?:string|null,starRating?:int|null,accommodationType?:string|null,offer:{roomTypeId,quantity,unitPrice:decimal,totalPerNight:decimal,availableRooms:int|null,checkIn:date|null,checkOut:date|null,checkedAt:instant|null},reviews:{hotelId,totalReviews:int,averageRating:decimal|null}}],page,size,totalElements,totalPages,sort,occupancyBasis:string}` ≤5 items | No room/media/contact/owner nested graphs; validate echoed dates, quantity, totals equality, unique IDs, count/page math, nonnegative stock; active+APPROVED. No zero fallback for missing reviews |
| get_hotel | `{hotelId,name,description?:string|null,address?:object|null,starRating?:int|null,accommodationTypeLabel?:string|null,lowestBasePrice:decimal|null,policies:[{category,content}],faqs:[{question,answer}],amenities:[{label,verifiedCodes:[{code,label}]}]}` | Only requested sections; max10 entries per section, each text≤1.000 chars, desc≤2.000; truncation explicit, never truncate clause then summarize it as complete policy; return complete retained items. Detail link constructed from UUID, not arbitrary tool text |
| list_public_rooms | `{hotelId,rooms:[PublicRoom],hasMore:boolean}` ≤10 retained | `PublicRoom={roomTypeId,hotelId,name,basePrice:decimal|null,capacity:{adults:int|null,children:int|null},amenities:[string],description?:string|null}`; don't send totalRooms as availability, drop media/timestamps; explicit truncation, no global cheapest claim on subset |
| get_public_room | `PublicRoom` | Validate selected hotel relationship; 409 not data; no capacity claim if null |
| check_availability | `{roomTypeId,checkIn,checkOut,quantity,available:boolean,availableRooms:int}` | Require count≥0, boolean=count≥quantity; dates are request provenance, not new fields claimed returned by server; checkedAt in evidence |
| resolve_locations | `{locations:[{id,name,parentName:string|null,type:CITY/PROVINCE,parentId:string|null}],hasMore:boolean}` ≤10 | Drop coordinates/count/timestamps; ambiguous → ask, never fabricate ID |
| list_accommodation_types | `{types:[{code,label}]}` ≤50 | Exact server codes/labels, no invented synonyms as new code |
| list_amenity_catalog | `{amenities:[{code,label}]}` ≤200 | Only active, no revision/description unless specifically needed; oversize reject rather than implicit complete catalog |
| get_review_summaries | `{summaries:[{hotelId,totalReviews,averageRating:decimal|null}],missingHotelIds:[uuid]}` ≤3 | Missing IDs = unknown/private, not zero; zero count requires null average; positive count requires valid 0–10 score |
| get_cancellation_policy | `{version:string,checkInTime:string,timeZone:string,freeCancellationHours:int}` | Validate source types, display general policy only, no personalized cancellation eligibility |

Model-facing decimal values retain backend precision; formatted currency rendered deterministically. Arithmetic totalPerNight×calendar nights may be labelled **estimated base subtotal**, never returned as inclusive final payable price; MVP prefer per-night amount. Agent must not invent taxes/fees/discount/room bed/breakfast/refund. Contact excluded even if public; user contact question can follow hotel page link in MVP, adding projection requires explicit policy review.

## Error mapping

| Transport / source code | Agent category | Answer/action | Automatic retry |
|---|---|---|---|
| 400 INVALID_REQUEST/VALIDATION_ERROR | INVALID_INPUT | Explain safe field issue, clarify; no error→empty | None unchanged |
| 401 UNAUTHORIZED | ACCESS_UNEXPECTED | Public route unexpectedly denied; cannot verify, support requestId; don't demand login or attach token to bypass | No |
| 403 FORBIDDEN | ACCESS_DENIED | Cannot access this data; no preview/admin alternative | No |
| 404 NOT_FOUND | NOT_PUBLIC_OR_MISSING | Not found/no longer publicly available; no existence inference | No |
| 409 INVENTORY_APPROVAL_REQUIRED | APPROVAL_REQUIRED | Chưa thể nhận đặt mới do tồn phòng cần duyệt; not sold out | No |
| 409 INVENTORY_NOT_CONFIGURED / INVENTORY_INCONSISTENT | INVENTORY_UNKNOWN | Chưa xác minh được tồn phòng, không thay bằng zero | No |
| 409 CONFLICT/unknown business code | STATE_CONFLICT | State changed/not ready; explicit reread next turn allowed | No automatic loop |
| 429 RATE_LIMITED (if received) | RATE_LIMITED | Too many requests, safe bounded Retry-After UX; no wait beyond turn | No |
| 500 INTERNAL_ERROR | INTERNAL_FAILURE | Temporary data unavailable, sanitized message, requestId | No |
| 502/503 DEPENDENCY_UNAVAILABLE | DEPENDENCY_UNAVAILABLE | Không truy xuất được dữ liệu phụ thuộc; partial results may remain | No (MVP proposal) |
| 504 DEPENDENCY_TIMEOUT | DEPENDENCY_TIMEOUT | Backend timed out, data unknown | No (MVP proposal) |
| deadline/network abort | TOOL_TIMEOUT / NETWORK_FAILURE | Unknown, never no inventory | No (MVP proposal) |
| 200 invalid JSON/schema, non200 success, excessive body | INVALID_RESPONSE / RESPONSE_TOO_LARGE | Fail closed for facts from this call, no defaults | No |
| Unknown code/status | UPSTREAM_FAILURE | Generic safe unavailable, retain numeric status for diagnostics | No |

READ_TOOL_CONTRACT allows one retry for client timeout; stricter **zero automatic retry** is proposed for MVP to prevent cost/loop ambiguity. User explicit retry creates new turn/budget/revision evidence. Even safe backend messages are data, not instructions; LLM receives known category + fixed Vietnamese safe message, not raw error text. 409 extras come from Hotel GlobalExceptionHandler; not covered by short old contract table, preserved here.

## Execution budgets (proposed)

All new budgets need D03 approval. Hard per-tool ≤10s is task requirement. Time measured monotonic; cancel HTTP/body/model work on turn expiry, superseded criteria or user cancel; SDK retries disabled. Proposed turn total **30s**, each model request ≤8s, reserve last2s for deterministic response; tool deadline=min(10s,remaining−2s), do not start new work without positive remaining budget. This is a cap, not sum of per-operation limits and not a latency guarantee.

| Limit | Proposal | Enforcement |
|---|---|---|
| Tool calls/turn | max6 attempted HTTP calls, including failed calls; max6 model tool proposals | Dispatcher validates before network, whole batch admitted only within cap; rejected proposals consume proposal budget |
| Model/tool cycles | max3 model requests, max2 execution rounds (initial plan, tools, refinement/tools, final synthesis) | If third model asks tools, stop and deterministic grounded response; no fourth reasoning request |
| Parallelism | max2 HTTP in flight; max1 model request per turn | Deadline-aware executor, no unbounded fan-out |
| Per tool | search≤2; availability≤3; detail≤3; room list≤2; room detail≤3; resolve≤1; each catalog≤1; summaries≤1; policy≤1 | Counts include errors; global6 wins; same normalized args/revision only once per turn |
| Comparison | ≤3 hotel IDs, same dates/rooms/guests/currency/unit | No extra compare endpoint/tool; local deterministic comparison over evidence |
| Network response | ≤256KiB decoded body/call, stream cap even chunked/compressed; JSON depth≤32 | Abort read before unbounded allocation; content-length alone insufficient |
| Model context | tool data≤12KiB/call, total≤32KiB/turn; model input≤12.000 tokens incl schemas/history | Project before context, truncate whole noncritical fields/items with flag; if essential facts exceed, fail call. UTF-8 bytes separate from token count |
| Model output / user response | ≤1.500 output tokens; answer≤12KiB and ≤5 result cards | Safe structured response schema + bounded renderer |
| History | last8 messages, 30min idle, ≤1.000 in-memory sessions, message≤4KiB | Discard expired/session overflow safely, ask again; no durable memory |
| Admission / cost | 10 turns/min anonymous session, 30/min client IP at trusted ingress; 20 concurrent turns/instance; model≤3 calls/turn | Local one-replica limiter for pilot only; daily currency cap and token-accounting limit must be picked with provider price (D02/D03); no launch without a finite daily cap |

No page auto-walk, retry, alternate route or repeated identical call. Catalog cache may be request-local only in MVP; no cross-turn availability cache. Dedup key tool+normalized args+criteriaRevision; repeated proposal returns cached same-turn observation or POLICY_LOOP_STOP without network and counts toward proposal cap.

## Orchestration state machine

1. Sanitize/size-check message, scope classify READ vs WRITE, load session/revision. Unsupported outside-system facts → state unavailable; no general web browsing.
2. Extract slots with provenance `USER_CONFIRMED|BACKEND_DEFAULT_BROWSE|UNSPECIFIED`, resolve ambiguous entity only if needed. Destination/name absent: ask. City text enough for backend search, no mandatory resolution call; resolve if ambiguity/type filters require ID.
3. Browse mode permits city-only call; stay request must satisfy confirmed slots. Unknown budget is optional, ask only if necessary to interpret explicit budget instruction. Never invent missing filter.
4. Model proposes **names + JSON args** only. Dispatcher applies schema/cross-field validation, allow-list, budget, duplicate checks and fixed URL builder. Never turn LLM reasoning text into an HTTP request.
5. Validate successful payload, reduce facts and record evidence. `checkedAt` only exists for inventory/date observations; price from undated base can use retrievedAt labelled correctly. All statements carry source/evidence ID internally.
6. On criteria change increment revision, invalidate earlier price/availability/fit, cancel outstanding work; response from prior revision must not enter current evidence or UI. Unchanged static detail may be reused only labelled with origin/time; MVP simpler refetch as needed.
7. Partial failure: compare verified fields only, annotate unknown fields separately, no zero substitution; do not rank unavailable row as cheaper/fully booked. Inconsistent facts/timestamps: explicitly unknown, one user-triggered refresh rather than merge synthetic offer.
8. Generate structured claims `{kind,value,unit,evidenceId,hotelId,roomTypeId,criteriaRevision}` plus bounded narrative. Deterministically validate price/inventory/rating/amenity claims and local hotel URLs. Missing evidence drops claim and renders “chưa xác minh”; semantic test/manual review cover policy paraphrases. Respond with per-result timestamp and “kiểm tra không giữ phòng”.

If user asks booking/hold/pay/cancel/refund/profile change, refuse execution and link appropriate website navigation only. No WRITE intent triggers hidden prefetch of private booking/account data. Frontend cancellation is separate from hotel cancellation policy, not a domain action.

## Proposed chat response contract (not an existing endpoint)

For evaluation/orchestration consistency, proposed response fields are `{schemaVersion:"agent-chat-read-v1",requestId,turnId,criteriaRevision,status,answer,clarifications:[],warnings:[],claims:[],evidence:[],errors:[],cards:[]}`. `status` enum `ANSWER|CLARIFICATION|EMPTY|PARTIAL|UNAVAILABLE|REFUSED|POLICY_STOP`. No domain write acknowledgement. `claims` entries `{kind,value,unit,evidenceId,hotelId?,roomTypeId?,criteriaRevision}`: kind enum `NAME|BASE_UNIT_PRICE|TOTAL_PER_NIGHT|AVAILABLE|AVAILABLE_ROOMS|REVIEW_COUNT|AVERAGE_RATING|AMENITY|POLICY`; value typed per kind, currency/quantity must use deterministic renderer. `evidence` contains tool envelope evidence plus normalized request criteria and projection data; model-visible projection may be smaller, UI must not receive secrets/raw backend DTO. `errors` entries `{tool,category,code,status,requestId?}`. `warnings` enum values include `BROWSE_DEFAULTS_NOT_CONFIRMED`, `AVAILABILITY_NOT_VERIFIED`, `NOT_A_HOLD`, `PARTIAL_DATA`, `TRUNCATED`, `POLICY_LOOP_STOP`, `BUDGET_EXCEEDED`, `OUTSIDE_SYSTEM_DATA`. Cards permit null price/rating/availability with explicit unknown state; do not cast directly to existing HotelCard required numeric price interface. Chat status reflects orchestration outcome, HTTP transport status remains independently validated. Acceptance tests use these fields; final API schema must be approved in10B before UI implementation.

## Implementation mapping 10B.2 (không đổi approved input contracts)

Executable schema resource: `ai-agent-service/src/main/resources/tools/read-input-schemas.json` trích nguyên JSON block10A ở trên. `ToolArguments` thực thi tất cả keywords mà block sử dụng (object/type/required/additionalProperties/anyOf/dependentRequired/format/pattern/bounds/array uniqueness) và rules ngày/occupancy/selectors/catalog/context. Đây không phải general-purpose Draft2020-12 engine; thêm keyword mới phải bổ sung validator/tests. Strict duplicate JSON keys/trailing tokens, UTF-8 arguments≤16KiB, depth32. Clock injectable, Asia/Ho_Chi_Minh rõ ràng.

`read-output-schemas.json` mô tả public projection `agent-read-v1`; `ToolProjection` dựng whitelist, kiểm kiểu/identity/ranges/page math/offer quantity và price multiplication, summary consistency, available/count, mandatory server checkedAt. `available=false` vẫn OK observation, khác EMPTY và ERROR; không hold. Long noncritical description/policy/FAQ entries bị bỏ nguyên item với truncated=true, không cắt câu; public room/location lists có hasMore. Catalog overflow fail closed. Projected data >12KiB fail closed. A01 checkedAt giữ tại từng offer, không tổng hợp một timestamp giả cho nhiều items. A05 request dates/quantity là provenance, không giả backend echo. Request ID do Agent tạo và upstream request ID được tách biệt trong evidence.

LangChain4j1.17.0 ToolSpecification root chỉ nhận JsonObjectSchema, không biểu diễn root anyOf/dependentRequired. `specifications()` giữ properties (dereferenced raw child schemas), required và additionalProperties=false; full authoritative Draft2020-12 schema ở metadata `draft202012InputSchema`. Validator luôn thực thi root/cross-field constraints độc lập SDK/model. Không claim provider đã enforce full schema; future provider phải kiểm dialect trước enable. Specification không executable và không tự expose cho LLM.

`ToolResult` dùng status OK/EMPTY/ERROR (Java internal envelope, không public chat contract); metadata toolId/name/schemaVersion/revision, sourceRoute template, system requestId, validated upstreamRequestId, retrievedAt UTC, optional checkedAt/source/truncated, typed error. Không log raw payload/URL/query/exception. No auto retry; public origin fixed từ trusted config, exact fixed routes only. Chi tiết evidence và remaining gates ở [report](BATCH10B2_READ_TOOLS_IMPLEMENTATION_REPORT.md).
