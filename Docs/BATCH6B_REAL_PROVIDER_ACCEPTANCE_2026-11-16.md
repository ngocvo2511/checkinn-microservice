# Batch6B — VNPay Sandbox và Storage thực tế

> **Cập nhật 17/11/2026: DONE trong phạm vi provider Sandbox/Storage của Batch6B.**
> VNPay NCB test-card/OTP đã trả 00; HMAC hợp lệ, payment COMPLETED,
> booking CONFIRMED/V1_CONFIRMED, Revenue một CREDIT. Replay 3 lần không cộng
> tiền trùng; sai hash/số tiền bị từ chối. Dữ liệu test đã dọn có chọn lọc.
> [Kết quả VNPay thực tế](BATCH6B_VNPAY_SANDBOX_ACCEPTANCE_2026-11-17.md).
> Storage upload/private signing đã PASS. Các blocker bên dưới là lịch sử;
> không suy production stability, IPN webhook hay real refund từ kết quả này.

> Latest Storage recheck after the owner confirmed Active: DNS now resolves on
> Google and Cloudflare, authenticated bucket API returns 200, hotel_storage is
> public and hotel-documents is private. Enabled Storage in docker/.env and
> passed real Media gRPC public upload/download/delete, private upload/signed
> download, batch signing and anonymous denial. Both probe files were removed;
> no Hotel/database fixtures were created. The permanent Media container was
> recreated with real Storage configuration and left stopped with the existing
> stack. [Machine evidence](evidence/batch-6b-supabase-local-integration/storage-live-acceptance-2026-11-16.json).
> The DNS/disabled findings below describe the earlier failed state; the VNPay
> merchant approval limitation remains separate.

16/11/2026. **BLOCKED ở provider; core database/local acceptance trước đó vẫn PASS.** Người dùng yêu cầu kiểm thực tế VNPay Sandbox và chỉ định dùng provider credentials trong `docker/.env`. Đã đọc đúng các field được chỉ định, không in keys/passwords, không nhập PostgreSQL credentials cũ vào runtime.

## VNPay Sandbox

- `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, pay URL và return URL đã có trong file được người dùng chỉ định.
- Đã copy riêng provider fields sang ignored `.local/batch6b/.env`, chọn explicit `SUPABASE_DEV_VNPAY_MODE=sandbox`; Booking restart healthy. Khóa callback giả lập không ghi đè real merchant credentials nữa.
- Tạo một synthetic Booking/payment mới qua API thật, ký checkout bằng merchant được cung cấp và mở đúng trang `sandbox.vnpayment.vn` trong browser.
- Provider trả **mã71, “Website này chưa được phê duyệt”**. Mã tra cứu trên trang: `2XXLB0oXLL`. Chưa vào form ngân hàng, chưa submit test card/OTP, chưa nhận callback thanh toán thật từ VNPay. Không được coi callback giả lập trước đó là provider PASS.
- Booking unpaid của probe đã cancel qua API nghiệp vụ bình thường; giữ Booking/payment rows, không delete/clean, không refund/provider transfer.

Việc cần owner xử lý: kiểm kích hoạt/phê duyệt website merchant trên VNPay Sandbox, hoặc cung cấp merchant Sandbox đã duyệt bằng cách cập nhật chính file private đã chỉ định. Không chuyển sang production hoặc dùng merchant/key công khai của bên khác để vượt chặn.

Tài liệu provider: [VNPay integration](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), [official test instruments](https://sandbox.vnpayment.vn/apis/vnpay-demo/).

## Supabase Storage

Recheck after runtime configuration consolidation (16/11/2026): the daily source
is now `docker/.env`. `SUPABASE_DEV_STORAGE_MODE=disabled` still selects
`http://127.0.0.1:9`; inspection of the retained main Media container confirms
that effective placeholder. The configured real Storage hostname still fails
system DNS and independent Google DNS-over-HTTPS returns rcode3 / no A records.
The server key format, service_role claim, project ref and expiry remain valid;
the control domains resolve. `MediaGrpcServiceImpl` maps Storage IOExceptions to
gRPC UNAVAILABLE, explaining the generic warning. The Eureka resolver INFO line
is unrelated to this failed Storage transport. Project status/current URL must
be confirmed by its owner before bucket access/upload can be verified.

- `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, public/private bucket names đều đã có trong `docker/.env`.
- Key là legacy server `service_role`, ref trong key khớp URL, chưa hết hạn. Project này là Storage project cũ riêng, không thuộc năm PostgreSQL projects mới.
- **Hostname Storage hiện không resolve:** Windows system DNS lookup failed; independent DNS-over-HTTPS trả NXDOMAIN/rcode3, zero A records. Các control `supabase.com` và API hostname của Hotel project mới đều resolve bình thường.
- Ba Management PAT hiện tại đều trả403 khi GET metadata của project Storage cũ. Không suy ra project đã xóa chỉ từ DNS/403; chưa xác định được trạng thái Active/Paused trong Dashboard.
- Không có request upload/bucket settings write nào được gửi. Chưa kiểm được hai bucket tồn tại/visibility. Media vẫn disabled/fail-closed cho provider, chờ endpoint hợp lệ; không thay DNS hệ thống hoặc tắt TLS để vượt lỗi.

Owner cần kiểm project chứa Storage có Active/Paused và URL hiện tại có đúng file không. Nếu đổi project, URL và backend key phải thuộc cùng project. Chỉ sau khi GET bucket metadata xác nhận public media bucket và private documents bucket đúng visibility mới upload synthetic files dưới prefix riêng; giữ files, không overwrite/delete assets cũ.

Tài liệu: [Storage bucket access models](https://supabase.com/docs/guides/storage/buckets/fundamentals), [backend API keys](https://supabase.com/docs/guides/getting-started/api-keys).

## Tooling và kiểm tra đã hoàn tất

Explicit provider-mode validation chặn production VNPay endpoint, thiếu real credentials không fallback sang fake, kiểm URL/project/key role và hai bucket riêng.8 provider-config boundary tests PASS. Storage HTTP client hỗ trợ opaque `sb_secret_` qua apikey header; legacy service_role JWT giữ Authorization+apikey. Media regression thực tế được ghi riêng; không thay payment/refund business rules hoặc schema.

11 core/local processes vẫn healthy. `.local/batch6b/provider-acceptance.private.json`, DNS diagnostic và Sandbox order evidence lưu private; không public actual project refs/keys/signed checkout URL.

**Trạng thái hiện tại: PARTIAL cho phạm vi người dùng yêu cầu mở rộng.** Không thể đánh provider payment/upload PASS trước khi merchant được duyệt và Storage endpoint hoạt động. Các kết quả core117 checks/848 backend/214 frontend trước đây giữ nguyên đúng scope lịch sử.

## Recheck merchant mới —16/11/2026 14:09:47 Asia/Saigon

Đã đọc merchant/secret mới từ docker/.env, copy đúng provider fields, restart Booking healthy, tạo checkout mới qua actual API. Kiểm độc lập vnp_TmnCode khớp cấu hình mới và HMAC khớp secret đang cấu hình; không fake override. Browser VNPay vẫn trả code71 “Website này chưa được phê duyệt”, mã tra cứu ODXO7ycYqa. Chưa tới bank form hoặc nhập thẻ/OTP. Unpaid synthetic Booking đã cancel bình thường, giữ rows, không giao dịch/refund provider. Cần merchant/website được provider duyệt trước khi thử thanh toán thành công.
