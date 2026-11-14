# Financial schema — STD-007/029

Source reviewed: Booking/Payment/RefundRequest mappings, manual/sandbox refund SQL, booking outbox SQL, Revenue cash/receipts/source versions/history gaps, User loyalty V3–V5.

| Contract | Schema enforcement | Limitation |
|---|---|---|
| Payment identity | unique VNPay order/transaction_no (JPA) | Scalar bookingId không tự có FK; receipt-processing paidAt khác occurred_at |
| Refund request | unique payment_id; amount numeric(38,2); version; status check | JPA-only schema có thể thiếu SQL lifecycle checks |
| Refund execution | unique request/payment; FK tới requests/payments/bookings; positive amount/VND; audit FK; evidence check | Không provider outcome proof; REFUNDED chỉ SANDBOX_INTERNAL theo SQL |
| Cash receipt | unique source_event_id và (payment_id,kind); CREDIT/DEBIT; positive amount/nights/rooms; timestamptz occurred_at | Schema không suy được missing DEBIT hoặc reconcile actual settlement |
| Outbox | unique event_version, sequence, PENDING/DELIVERED check, due/stream indexes | Không cross-DB atomic transaction hoặc live-broker recovery proof trong Batch3 |
| Revenue receipts | (kind,event_hash) unique; projection booking uniqueness; dispositions | Duplicate preexisting projection chủ động fail migration, không winner/delete tự động |
| Loyalty | command_key PK + unique(booking_id,command_type); nonnegative points/discount and positive rate; recovery indexes | Legacy ledger guard indexes không unique và không chứng minh ledger completeness |

Fresh PostgreSQL: cash clean-vs-source-derived upgrade MATCH; original fields/amount/receipt unchanged, new mode NULL, replay, negative amount/dedup rejection/rollback. Synthetic refund baseline: replay manual+internal SQL, negative amount/evidence/duplicate rejection, rollback PASS. Synthetic loyalty baseline: V3–V5 giữ original ledger, command dedup và guard indexes PASS. Catalogs xác nhận FK/check/index thực tế, không suy từ Java đơn thuần.

Historical ambiguities: missing occurred_at/create-date/source-event identities, duplicate projections/ledger entries, unknown provider/internal mode, missing original CREDIT/DEBIT. Không backfill từ latest booking/payment status hoặc paidAt; cần original receipt/provider statement + source audit + owner-approved reconciliation. Không cập nhật Payment.status/RefundRequest.status hoặc tạo correction trên DB thật. Internal Refund demo không là provider settlement; Batch1.6 semantics giữ nguyên.

Failure atomicity: existing sandbox upgrade bị synthetic LEGACY_UNKNOWN status chặn; PostgreSQL rolls back cả DDL lẫn data, original row giữ nguyên. Hotel V2 không có BEGIN/COMMIT toàn script: các columns trước syntax error đã tồn tại sau fail; cần review partial application, không blind replay. Catalog hotel_v2_partial_failure lưu evidence.
