# Vietnam destination reference data

`vietnam-destinations-2026.json` contains 34 current provincial-level units and
40 curated travel destinations. The province membership follows the
[Government publication of Resolution 202/2025/QH15](https://baochinhphu.vn/nghi-quyet-cua-quoc-hoi-ve-sap-xep-don-vi-hanh-chinh-cap-tinh-102250612191145158.htm).
Travel destinations are informed by the
[Vietnam National Authority of Tourism destination guide](https://vietnam.travel/vi/place-to-go/)
and the existing repository's historic destination catalog. Membership was
updated to the current provinces; historic SQL was not changed or executed.
Display name `TP. Hồ Chí Minh` matches the frontend's existing popular-location
label. City means a travel destination in this application, not an exhaustive
administrative city/ward/commune list.

Examples: Phú Quốc → An Giang; Vũng Tàu/Côn Đảo → TP. Hồ Chí Minh;
Hội An → Đà Nẵng; Quy Nhơn → Gia Lai; Hà Giang → Tuyên Quang;
Phan Thiết/Mũi Né → Lâm Đồng; Kon Tum → Quảng Ngãi.

New rows receive hotel_count=0. No hotels, rooms, bookings, accounts or fabricated
inventory are added. Coordinates are NULL until a separately verified geospatial
dataset is supplied; they must not be interpreted as zero coordinates. IDs are
deterministic UUIDv5 for reference rows. Existing names/IDs/counts/coordinates
are preserved; an existing City with a different parent aborts the precheck.

From `checkinn`, using the existing approved private Supabase Hotel target:

```powershell
javac -cp 'tools/supabase-runner/target/supabase-runner-1.3.0.jar;tools/supabase-runner/target/dependency-10.22.0/*' -d .local/batch6b/approved-classes tools/supabase-runner/acceptance/java/HostedReferenceSeed.java
python tools/supabase-runner/seed-vietnam-reference.py check
# Explicit insert-only operation; not automatically run at application startup.
python tools/supabase-runner/seed-vietnam-reference.py apply
```

The tool validates target identity/TLS using the existing runner, writes with
the Hotel runtime role, keeps a private preimage, inserts in a transaction with
ON CONFLICT(name) DO NOTHING, checks parent mappings and existing rows, and
immediately reapplies to prove zero duplicate inserts. It is separate from the
sealed synthetic acceptance session and never changes schema/Flyway history.

Applied 2026-11-16: 74 rows inserted, immediate reapply 0. Gateway exposed all
34 provinces and 40 destinations. Evidence:
`Docs/evidence/batch-6b-supabase-local-integration/vietnam-reference-2026-11-16.json`.
