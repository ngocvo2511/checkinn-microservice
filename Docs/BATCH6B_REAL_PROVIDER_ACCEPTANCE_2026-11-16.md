# Batch6B — VNPay Sandbox và Storage thực tế

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
