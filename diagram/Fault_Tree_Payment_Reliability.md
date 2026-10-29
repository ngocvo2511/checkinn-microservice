## Key statuses (ngữ cảnh)

- Trạng thái `Booking`:
    - `PENDING`: Đã tạo đơn đặt và tạm giữ phòng; chờ khách chọn phương thức thanh toán.
    - `PENDING_PAYMENT`: Khách đã chọn phương thức thanh toán bên ngoài (ví dụ VNPay); đã gia hạn thời gian giữ và chờ callback từ cổng thanh toán.
    - `CONFIRMED`: Đơn đã được xác nhận, inventory đã được commit với khách sạn.
    - `CANCELLED`: Đơn bị hủy (tự động khi hết thời gian giữ hoặc do khách/khách sạn hủy).
    - `CHECKED_IN`: Khách đã nhận phòng (đã check-in).
    - `CHECKED_OUT`: Khách đã trả phòng (đã check-out); vòng đời đơn hoàn tất.

- Trạng thái `Payment`:
    - `PENDING`: Đã tạo bản ghi thanh toán; chờ xử lý bởi cổng thanh toán.
    - `COMPLETED`: Thanh toán thành công và đã được ghi nhận.
    - `FAILED`: Thanh toán thất bại hoặc bị từ chối.
    - `CANCELLED`: Thanh toán bị hủy bởi người dùng hoặc hệ thống.
    - `REFUNDED`: Số tiền đã được hoàn trả cho khách.
    - `ONSITE_PENDING`: Thanh toán sẽ thu trực tiếp tại khách sạn (không qua cổng thanh toán).

TOP EVENT:
Phòng bị overbook hoặc trạng thái booking/payment không nhất quán
│
├── OR ── Booking được xác nhận sai hoặc không đồng bộ
│           │
│           ├── AND ── Payment COMPLETED nhưng booking chưa CONFIRMED
│           │            │
│           │            ├── Payment service cập nhật trạng thái thành công
│           │            └── Event publish / confirm booking thất bại
│           │
│           ├── VNPay callback về nhưng update booking thất bại
│           │
│           ├── Gọi confirm hold sang service khác bị timeout
│           │
│           └── Event publish thất bại, downstream service không nhận trạng thái mới
│
├── OR ── Thanh toán thất bại nhưng booking vẫn bị giữ sai trạng thái
│           │
│           ├── Callback thất bại nhưng booking không rollback về PENDING
│           │
│           ├── Payment FAILED nhưng hold không được release
│           │
│           ├── Người dùng quay lại thanh toán nhưng trạng thái cũ chưa cleanup
│           │
│           └── AND ── Hold hết hạn nhưng booking không bị hủy
│                        │
│                        ├── Hold đã hết hạn
│                        └── Scheduled cleanup task không chạy hoặc query bị lỗi
│
├── OR ── Overbooking xảy ra
│           │
│           └── AND
│                │
│                ├── Hai request thanh toán gần như đồng thời
│                └── Không đủ locking / transaction isolation ở DB
│
├── OR ── Duplicate payment hoặc xử lý lặp
│           │
│           ├── AND
│           │    │
│           │    ├── Callback VNPay bị gọi lại nhiều lần
│           │    └── Không có idempotency check
│           │
│           └── AND
│                │
│                ├── Retry mechanism được kích hoạt
│                └── Không có deduplication logic
│
└── OR ── Hủy booking xử lý không hoàn chỉnh
            │
            ├── Xử lý hủy booking lỗi giữa chừng
            │
            ├── Payment vẫn ở trạng thái treo
            │
            └── holdExpiresAt sai hoặc không được cập nhật