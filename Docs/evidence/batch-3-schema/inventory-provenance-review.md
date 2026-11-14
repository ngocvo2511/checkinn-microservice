# Inventory provenance — STD-006

Source reviewed: RoomType.totalRooms nullable Integer, InventoryCounterSafety/AvailabilityService, RoomInventoryReviewService, room-inventory-review-v1 SQL, root migration_add_total_rooms.sql và Batch2 fixture.

| Classification | Evidence | Decision |
|---|---|---|
| Explicitly configured | Synthetic Batch2 stock=1/2; create input roomAmount; no inferred stock in Batch3 | Chỉ fixture/rules, không physical stock proof |
| Reviewed/approved | Review source nonblank, proposed_quantity>0, reviewer/revision; approved quantity phải khớp current total | Workflow tồn tại; không fabricate actual approvals |
| Legacy default suspected | Root migration thực sự UPDATE total_rooms=10 WHERE NULL, dù comment gọi Optional | Không chạy/sửa file; 10 không tự là sai, production application UNKNOWN |
| Unknown provenance | Existing production/developer inventory không được đọc | Cần OWNER count + source và ADMIN review từng room type |
| Invalid/inconsistent | Synthetic NULL/negative stock được giữ nguyên qua review migration; counters guarded trong source/tests | Không repair, tăng stock, giảm booked hoặc xóa holds |
| Requires manual review | Không có quantity evidence hoặc approved quantity lệch current total; historical default suspected | Evidence + revision review; không bulk approve/backfill |

Fresh tests: 22 Hotel mapper/inventory tests PASS (bao gồm 7 RoomInventoryReviewServiceTest); PostgreSQL review SQL replay twice, NULL/10/-1 unchanged, no proposals created, negative proposal rejected. Không lấy các tests đó làm physical provenance. Current review approval đọc peak held+booked, chặn invalid counters/quantity dưới load; không thay reservation policy.

Manual plan: export aggregate metadata qua quy trình owner-authorized, map room/source/revision, OWNER xác nhận số vật lý, ADMIN review theo workflow hiện có; đánh dấu unknown riêng. Không dùng total_rooms=10 làm bằng chứng duy nhất của defect; không retrospective booking/hold/cash repair.
