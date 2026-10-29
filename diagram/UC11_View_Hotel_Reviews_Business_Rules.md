# UC11 - View Hotel Reviews Business Rules

## Use Case Check

The use case is mostly correct, but should be adjusted to match the current code:

- The reviewed object in the implemented flow is the hotel review. The current owner review page does not load or filter reviews by room.
- The trigger in code is the host sidebar/menu item `Quản lý đánh giá`, which opens `/host/reviews`; it is not started from inside the hotel management page.
- The precondition should require the owner to be logged in and to have hotel data. Review data may be empty; the page has an empty-state message for that case.
- The postcondition should say the owner can view the review list, review contents, detailed criteria ratings, response status, and analytics for hotels they own.

Suggested corrected use case:

| Field | Corrected Content |
|---|---|
| Mã Use Case | UC11 |
| Tên Use Case | Xem đánh giá khách sạn |
| Mô tả | Chủ khách sạn xem danh sách, nội dung, điểm đánh giá và tiêu chí chi tiết của các đánh giá do khách hàng gửi cho những khách sạn thuộc quyền sở hữu của mình. |
| Tác nhân | Chủ khách sạn |
| Sự kiện kích hoạt | Chủ khách sạn chọn chức năng `Quản lý đánh giá` trong menu quản trị host. |
| Yêu cầu trước khi thực hiện | Chủ khách sạn đã đăng nhập với vai trò `OWNER`; hệ thống có dữ liệu khách sạn thuộc chủ khách sạn. |
| Yêu cầu sau khi thực hiện | Chủ khách sạn xem được danh sách đánh giá, nội dung đánh giá, điểm chi tiết, trạng thái phản hồi và thống kê đánh giá của các khách sạn mình quản lý. |

| Activity | BR Code | Description |
|---|---|---|
| (1) | BR1 | Review Management Entry Rules:<br>The owner starts the use case from the host menu item for review management. This opens the owner review page at `/host/reviews` (View: `components/host/menu/menuItems.ts`, `HostMenu`). |
| (2) | BR2 | Owner Access Rules:<br>The host layout checks that the current user is authenticated and has [role] `OWNER` before allowing the review management page to remain visible (Hook: `useProtectedRoute("OWNER")`, View: `app/host/layout.tsx`). |
| (3) | BR3 | Access Redirect Rules:<br>If the user is not authenticated, the system redirects to the host login page; if the user is authenticated but does not have the owner role, the system redirects to the home page (View: `app/host/layout.tsx`). |
| (4) | BR4 | Review Page Display Rules:<br>After owner access is confirmed, the system displays the host reviews page with review statistics, hotel filter, response filter, list tab, and analytics tab (View: `app/host/reviews/page.tsx`). |
| (5) | BR5 | Owner Hotel Loading Rules:<br>The page loads the owner's hotel list before loading reviews because review display and filtering depend on [hotels] and [hotel.id] (Function: `loadHostHotels()`, Frontend API: `hotelApi.getHotelsByOwner()`). |
| (6) | BR6 | Owner Identity Rules:<br>The hotel list request identifies the owner from the authenticated JWT subject, not from a user-entered id (Controller: `HotelController.getHotelsByOwner()`). |
| (7) | BR7 | Owned Hotel Rules:<br>Only hotels whose [ownerId] matches the authenticated owner are returned for the filter and review scope (Service: `HotelService.getByOwner()`, Repository: `hotelRepository.findByOwnerId()`). |
| (8) | BR8 | Hotel Loading Result Rules:<br>The hotel list must be loaded successfully before review loading begins. If loading succeeds, the page stores [hotels]; if it fails, the review page cannot determine owner scope. |
| (9) | BR9 | Hotel Loading Message Rules:<br>If owner hotels cannot be loaded, the page displays MSG79 and stops the review list flow (View: `app/host/reviews/page.tsx`). |
| (10) | BR10 | Filter Display Rules:<br>After [hotels] are available, the page displays the hotel filter. The owner can choose all hotels or one hotel from [hotels] (View: `app/host/reviews/page.tsx`). |
| (11) | BR11 | Hotel Filter Selection Rules:<br>The selected filter is stored as [selectedHotelId]. The value `all` means reviews across all owned hotels; a hotel id means reviews for that specific owned hotel (State: `selectedHotelId`). |
| (12) | BR12 | Review Token Rules:<br>Before loading reviews, the page checks [token] in local storage again. If [token] is missing, the review request is not sent (Function: `loadReviews()`). |
| (13) | BR13 | Review Login Redirect Rules:<br>If [token] is missing while loading reviews, the owner is redirected to `/host/login` (Function: `loadReviews()`). |
| (14) | BR14 | Review Owner Header Rules:<br>The review API decodes [token] to get [ownerId] and sends it in the `X-User-Id` header. This value scopes the owner review endpoints (Frontend API: `reviewApi.getOwnerReviews()`, `reviewApi.getOwnerHotelReviews()`). |
| (15) | BR15 | Review List Request Rules:<br>If [selectedHotelId] is `all`, the page calls `GET /api/v1/reviews/owner/all`; otherwise it calls `GET /api/v1/reviews/owner/hotel/{hotelId}` with pagination (Frontend API: `reviewApi`). |
| (16) | BR16 | Owner Review Scope Rules:<br>For all-hotel review loading, the backend first finds all hotel ids owned by [ownerId]. For one-hotel review loading, the backend receives both [ownerId] and [hotelId] (Service: `ReviewService.getReviewsByOwner()`, `ReviewService.getReviewsByOwnerAndHotel()`). |
| (17) | BR17 | Hotel Ownership Check Rules:<br>When a specific [hotelId] is selected, the backend verifies that the hotel belongs to [ownerId] before returning reviews (Repository: `hotelRepository.existsByIdAndOwnerId()`). |
| (18) | BR18 | Unauthorized Hotel Message Rules:<br>If the selected hotel does not belong to the owner, no reviews are returned and the system reports MSG83 (Service: `ReviewService.getReviewsByOwnerAndHotel()`). |
| (19) | BR19 | Published Review Retrieval Rules:<br>The owner view returns only reviews with [status] `PUBLISHED`, ordered by newest first. All-hotel mode uses [hotelIds]; one-hotel mode uses [hotelId] (Repository: `HotelReviewRepository.findByHotelIdInAndStatusOrderByCreatedAtDesc()`, `findByHotelIdAndStatusOrderByCreatedAtDesc()`). |
| (20) | BR20 | Review Enrichment Rules:<br>Each review is mapped and enriched with guest display data, owner response data, and hotel name when available. This makes the card show [guestName], [ownerResponse], and [hotelName] in addition to review content (Service: `ReviewService`, Mapper: `ReviewMapper.entityToResponse()`). |
| (21) | BR21 | Paged Review Response Rules:<br>The backend returns a paged result containing [content], [totalPages], and [totalElements]. The frontend normalizes detailed rating fields into [ratings] for display. If review loading fails, the page displays the backend message when available or MSG80 as the fallback (Frontend API: `normalizeReviewRatings()`, View: `app/host/reviews/page.tsx`). |
| (22) | BR22 | Hotel Name Matching Rules:<br>The frontend also matches each review's [hotelId] against the loaded [hotels] list to display the hotel name. If no matching hotel is found, it displays `Khách sạn không xác định` (View: `app/host/reviews/page.tsx`). |
| (23) | BR23 | Review Availability Rules:<br>Reviews are considered available when [reviews.length] is greater than zero. Empty review data is a valid outcome, not an error. |
| (24) | BR24 | Empty Review Message Rules:<br>If no reviews are available for the selected scope, the page displays MSG81 and MSG82 (View: `app/host/reviews/page.tsx`). |
| (25) | BR25 | Review List Display Rules:<br>When reviews are available, the page displays total review count, average rating, response counts, filters, and the review list (View: `app/host/reviews/page.tsx`). |
| (26) | BR26 | Review Detail Display Rules:<br>Each review card displays customer name, hotel name, creation time, overall rating, title, content, detailed criteria ratings, and existing owner response when present (Component: `HostReviewCard`). |
| (27) | BR27 | Local Filter And Analytics Rules:<br>The owner can filter loaded reviews by [responseFilter], switch between [activeTab] `list` and `analytics`, or change [currentPage]. Response filtering and analytics are computed from the loaded [reviews] list (View: `app/host/reviews/page.tsx`). |
| (28) | BR28 | Updated View Rules:<br>After a filter, tab, or page change, the system updates the displayed list or analytics. Page changes reload reviews from the backend; response-status filtering and analytics are computed on the current loaded page (View: `app/host/reviews/page.tsx`). |
