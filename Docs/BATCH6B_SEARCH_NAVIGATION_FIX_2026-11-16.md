# Sửa tìm kiếm bị kẹt Rendering —16/11/2026

Đã tái hiện local Turbopack compile stall: request /search không hoàn tất sau14.23s trong khi trang chủ vẫn HTTP200. Browser log có Failed to fetch RSC payload; giao diện giữ trang chủ khi router transition đang chờ. Repo đã có browser runners dùng Webpack cho vấn đề compile/readiness này.

Sửa tools/supabase-runner/run-local-stack.py để frontend chạy explicit `next dev --webpack`, rồi restart đúng frontend process được ghi nhận. Không sửa application search, pricing/inventory, Place hoặc database. [Next CLI](https://nextjs.org/docs/app/api-reference/cli/next) hỗ trợ chế độ này.

Kiểm thực tế sau sửa: cold /search HTTP200 trong4.55s; trên browser chọn synthetic city, nhận20/11/2026, trả21/11/2026,1 phòng/2 người lớn/0 trẻ em rồi tìm kiếm. Trang chuyển sang /search, query giữ đúng lựa chọn và hiển thị2 khách sạn test. Navigation request sau warmup HTTP200 trong176ms.

Đây là dev-mode mitigation cho workspace này; không claim production build hoặc default Turbopack đã hết lỗi. Nếu tự chạy frontend ngoài launcher, dùng `npm run dev -- --webpack`. Refresh tab cũ để nạp client bundle mới.

[Evidence](evidence/batch-6b-supabase-local-integration/search-navigation-fix-2026-11-16.json). Các provider blockers VNPay/Storage vẫn được theo dõi riêng.
