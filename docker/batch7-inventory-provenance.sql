-- Metadata-only review list. Never treats quantity 10 as evidence for or against stock.
-- Run only against an owner-authorized target; Batch7 runs this on disposable fixtures.
-- No source/reason text, actor IDs, customer data, mutation, or automatic approval.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
WITH counters AS (
    SELECT room_type_id,
           max(held::bigint + booked::bigint) AS peak_committed,
           bool_or(held < 0 OR booked < 0) AS invalid_counters
    FROM public.room_availability
    GROUP BY room_type_id
), provenance AS (
    SELECT r.id AS room_id, r.hotel_id, r.total_rooms,
           s.revision AS review_revision,
           coalesce(c.peak_committed, 0) AS peak_committed,
           coalesce(c.invalid_counters, false) AS invalid_counters,
           s.room_id IS NOT NULL AS managed,
           a.id IS NOT NULL
             AND a.room_id = r.id AND a.hotel_id = r.hotel_id
             AND a.status = 'APPROVED'
             AND a.proposed_quantity = r.total_rooms AND r.total_rooms > 0
             AND length(trim(a.source)) > 0 AND a.requested_by IS NOT NULL
             AND a.requested_at IS NOT NULL AND a.reviewed_by IS NOT NULL
             AND a.reviewed_at IS NOT NULL
             AND length(trim(a.review_reason)) > 0 AS approval_matches,
           (s.latest_request_id IS NOT NULL AND
             (l.id IS NULL OR l.room_id <> r.id OR l.hotel_id <> r.hotel_id))
           OR (s.approved_request_id IS NOT NULL AND
             (a.id IS NULL OR a.room_id <> r.id OR a.hotel_id <> r.hotel_id))
             AS broken_review_link
    FROM public.room_types r
    LEFT JOIN public.room_inventory_review_states s ON s.room_id = r.id
    LEFT JOIN public.room_inventory_review_requests a ON a.id = s.approved_request_id
    LEFT JOIN public.room_inventory_review_requests l ON l.id = s.latest_request_id
    LEFT JOIN counters c ON c.room_type_id = r.id
)
SELECT room_id, hotel_id, total_rooms, review_revision, peak_committed, managed,
       CASE
         WHEN total_rooms <= 0 OR invalid_counters OR peak_committed > total_rooms
           THEN 'INVALID_CONFIRMED'
         WHEN broken_review_link THEN 'UNVERIFIED'
         WHEN approval_matches THEN 'VERIFIED'
         WHEN managed THEN 'UNVERIFIED'
         ELSE 'UNKNOWN_SOURCE'
       END AS provenance_class,
       CASE
         WHEN total_rooms <= 0 THEN 'NONPOSITIVE_STOCK'
         WHEN invalid_counters THEN 'NEGATIVE_COUNTER'
         WHEN peak_committed > total_rooms THEN 'STOCK_BELOW_COMMITTED'
         WHEN broken_review_link THEN 'REVIEW_LINK_MISMATCH'
         WHEN total_rooms IS NULL THEN 'MISSING_STOCK'
         WHEN approval_matches THEN 'MATCHING_RECORDED_APPROVAL'
         WHEN managed THEN 'OWNER_ADMIN_REVIEW_REQUIRED'
         ELSE 'NO_RECORDED_SOURCE'
       END AS review_reason_code
FROM provenance
ORDER BY provenance_class, room_id;
COMMIT;
