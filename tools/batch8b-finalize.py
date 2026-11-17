"""Summarize observed Batch8B evidence without manufacturing missing acceptance."""
import collections,hashlib,json,statistics,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'Docs/evidence/batch-8b-hosted-e2e-performance'
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8'))
def write(name,data):(OUT/name).write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')

def main():
    cleanup=read('cleanup-results.json');schema=read('final-schema-history-count-verification.json');admin=read('final-admin-preservation.json')
    assert cleanup['status']=='DONE' and not cleanup['remaining']
    assert all(r.get('schemaFingerprintUnchanged') and r.get('countsUnchanged') and r.get('catalogMatchesCanonical') for r in schema)
    assert admin['sameAccountFieldsAsBaseline'] and admin['credentialMatchesSecureEnvironment'] and admin['completeUserRowDigestUnchanged']
    m=read('fixture-manifest.json')
    assert all(r['cleanupVerification']=='ABSENT' and r['id']!=m['protectedAdminId'] for r in m['records'])
    for r in m['records']:r['cleanupOrderIndex']=m['cleanupOrder'].index(r['table'])
    write('fixture-manifest.json',m)
    git={}
    for label,repo in [('backend',ROOT),('frontend',ROOT.parent/'checkinn-frontend')]:
        initial=read('initial-git-'+label+'.json')
        def command(*args):return subprocess.check_output(['git','-C',str(repo),*args],text=True,encoding='utf-8')
        staged=command('diff','--cached','--binary');unstaged=command('diff','--binary')
        git[label]={'branch':command('branch','--show-current').strip(),'head':command('rev-parse','HEAD').strip(),'headUnchanged':initial['head'].strip()==command('rev-parse','HEAD').strip(),'stagedUnchanged':initial['staged']==staged,'initialStagedSha256':hashlib.sha256(initial['staged'].encode()).hexdigest(),'finalStagedSha256':hashlib.sha256(staged.encode()).hexdigest(),'unstagedUnchanged':initial['unstaged']==unstaged,'status':command('status','--porcelain=v1')}
        assert git[label]['headUnchanged'] and git[label]['stagedUnchanged']
    assert git['frontend']['unstagedUnchanged']
    write('final-git-state.json',git)
    qs=read('query-counts-before-small.json');patterns={}
    for endpoint in dict.fromkeys(r['endpoint'] for r in qs):
        rows=[r for r in qs if r['endpoint']==endpoint]
        patterns[endpoint]={'jdbcStatements':[r['jdbcStatements'] for r in rows],'jdbcExecuteMeanMsIncludingNetwork':statistics.mean(r['jdbcMsIncludingNetwork'] for r in rows),'httpMeanMs':statistics.mean(r['ms'] for r in rows)}
    write('query-count-slope.json',{'small2x2':patterns,'medium3x5':'NOT RUN: pressure stop','larger5x10':'NOT RUN: pressure stop','after':'BLOCKED: restarted runtime not ready, role connection exhaustion','conclusion':'Per-room and duplicate approval lookups confirmed in source; one populated size measured. Runtime slope across room counts NOT VERIFIED; N+1 NOT declared resolved. Room media lazy loading and per-hotel association/amenity reads remain follow-up.'})
    before=read('benchmark-before-small-summary.json')[0]
    write('before-after.json',{'before':before,'after':{'status':'BLOCKED','reason':'Hotel runtime role connection limit; User/Hotel readiness failed after restart and bounded recovery'},'sameFixtureIdsIntended':True,'latencyImprovementClaim':False,'sqlReductionClaimFromHostedAfter':False,'limitations':['Before has 29 successes/30 attempts, below 30 successful samples','Only one benchmark cell completed before timeout pressure stop','No Medium/Larger dataset or Gateway-vs-direct completed comparison','Natural expiry write used separate March dates during before window; baseline is development diagnostic, not isolated production benchmark']})
    executed=read('regression-results.json');native=read('local-disposable/schema-verification.json')
    total=sum(executed[s]['executedPass'] for s in ['user','auth','hotel','booking'])
    assert total==838 and all(s['passCanonical'] and s['passCatalog'] for s in native['services'])
    executed['native']={'executedPass':5,'catalogMatches':5,'clusterStopped':native['cleanup']['clusterStopped'],'hosted':False};executed['uniqueExecutedPassTotal']=total+5
    write('regression-results.json',executed)
    e2e=read('e2e-results.json');attempts=collections.Counter(r['status'] for r in e2e)
    write('acceptance-summary.json',{'finalStatus':'PARTIAL / PERFORMANCE AFTER & FULL CONCURRENCY BLOCKED','loggedChecks':dict(attempts),'notUniqueScenarioCount':True,'initialAdminLoginAndGatewayAuthorization':'PASS','finalAdminDatabaseAndCredentialPreservation':'PASS','finalAdminHttpLogin':'BLOCKED 503','ownerAdminCustomerInventoryBooking':'PASS scoped, SQL synthetic hotel publication setup','unapprovedBooking':'PASS 409; no hold/counter writes','idempotency':'PASS original ID / changed payload 409','authorizationOwnership':'PASS','concurrency':'PARTIAL: stock2 occupied2, but recovery race 409/500/409; pool acquisition timeout','naturalHoldExpiration':'NOT VERIFIED end-to-end: runtime unavailable after natural deadline','populatedBenchmark':'PARTIAL 29/30; stop on timeout','querySlope':'NOT VERIFIED across sizes','sourceOptimization':'IMPLEMENTED / local regression+native PASS / hosted after NOT VERIFIED','hostedDeletedRows':sum(r['deleted'] for r in cleanup['results']),'manifestIdsAbsent':len(m['records']),'hostedRemainingFixtureIds':0,'schemaAndHistory':'UNCHANGED 5/5','productionReady':False})
    net=read('network-transaction-probe.json')['samples'];netmean=statistics.mean(r['select1MsIncludingNetwork'] for r in net);txmean=statistics.mean(r['select1PlusRollbackMs'] for r in net)
    table='\n'.join(f"| {name} | {', '.join(map(str,r['jdbcStatements']))} | {r['jdbcExecuteMeanMsIncludingNetwork']:.1f} | {r['httpMeanMs']:.1f} |" for name,r in patterns.items())
    report=f'''# Batch 8B — Hosted E2E & performance, 17/11/2026

## 1. Executive Summary

**PARTIAL — scoped authenticated approval/booking E2E đã chạy thật; performance AFTER, query-count slope nhiều mức và full concurrency chưa đạt. Không DONE, không production-ready.**

Đã xác minh năm targets; ADMIN `admin` login và ADMIN queue qua Gateway HTTP200; tạo 2 OWNER, 2 CUSTOMER, 2 hotel, 4 room types; chạy proposal/approve/reject/authorization/booking/idempotency/cancel bằng API. Hotel publication được setup bằng exact-ID SQL synthetic, không phải acceptance upload giấy tờ/duyệt publication API.

Read optimization đã triển khai và qua **838 unit/service/security regression + 5 native canonical PostgreSQL tests**, không failure/error ở các lượt cuối. Hosted AFTER bị chặn vì runtime không sẵn sàng sau restart, có `too many connections` ở Hotel runtime role. Không có claim cải thiện latency hoặc N+1 resolved.

Cleanup xóa **47 rows**, verify **48 manifested IDs absent** (một OTP đã bị lifecycle resend xóa). Dữ liệu hiện hữu theo fingerprint khớp, schema/Flyway không đổi 5/5. ADMIN nguyên vẹn. Lượt HTTP login cuối503 do User runtime unavailable; credential BCrypt kiểm trực tiếp vẫn khớp secure environment.

## 2. Supabase Environment

User/Loyalty, Hotel/Inventory, Booking/Payment, Revenue, Regulations: control-plane identity, runtime mapping, TLS1.3 verify-full PASS ở preflight mới. Session pooler5432, guarded supabase-dev: Hibernate validate, SQL init never, Flyway false; không hosted DDL/migration/role change. Chi tiết tại `*-runtime-preflight.json`, `*-before.json`, `*-after.json`.

Năm columns/constraints/indexes/Flyway history khớp canonical và fingerprint trước/sau. Baseline:3 users,1 hotel,0 room/booking/hold; Regulations có4 snapshots/regulations/outbox hiện hữu, không khởi động worker này. Không khởi động Revenue/Media. Hikari max2/minIdle1, acquisition timeout10000ms. Windows/Java21, local Gateway/services đi Supabase; region mô tả là pooler hostname codes, không bằng chứng độc lập vị trí PostgreSQL.

## 3. ADMIN Authentication

Username `admin`, role ADMIN, active, email verified. Credentials đọc từ `docker/.env` ADMIN_PASSWORD trong memory, không hardcode hoặc xuất password/JWT/hash. Login200, role từ auth response ADMIN, JWT thật truy cập `/api/hotel/admin/inventory-reviews`200. `/admin/...` trực tiếp trên Gateway404 là sai route của runner và đã sửa; không phải authorization failure.

Protected ADMIN ID nằm riêng trong manifest; không là fixture, không dùng destructive scenario, không sửa role/password/status. Credential/token files của runner nằm private/ignored và được dọn sau phiên; evidence không chứa chúng.

## 4. Fixture Scope

Run `{m['testRunId']}`. Small2×2, 2OWNER/2CUSTOMER,5 review requests,3 booking/hold,2 daily counters,6 outbox events và dependent profiles/OTP/zero-point loyalty side effects. Không ADMIN fixture. Medium3×5/Larger5×10 NOT RUN vì pressure/readiness stop; không tạo hàng nghìn records.

Tạo accounts/OTP/login/hotel/room/proposal/review/booking qua Gateway. SMTP chỉ Mailpit riêng local; reverse DNS gây hai OTP attempts thất bại, sửa local Mailpit bằng `--smtp-disable-rdns`, resend/verify/login sau đó PASS. Hotel API publication yêu cầu Storage documents+thumbnail; chỉ cập nhật publication của exact synthetic hotel IDs để kiểm inventory, không giả nhận provider/publication acceptance. Không sở hữu/sửa hotel thật.

Manifest được tạo trước writes, liệt kê owner/table/PK/dependencies/scenario/side effects/cleanup order/verification. Có scoped cleanup gate trước writes. Cancel unpaid booking còn tạo2 zero-point command receipts và1 zero-point loyalty account; đã kiểm user/booking đúng fixtures, điểm0, không có points transaction/payment/refund/Revenue ledger. Không xóa audit/ledger thật.

## 5. OWNER → ADMIN → CUSTOMER E2E

OWNER tạo room và PENDING proposal; CUSTOMER booking trước approval trả409, không hold/counter write. ADMIN queue/approve200, available quantity2, CUSTOMER booking201. Duplicate booking key trả cùng ID201; đổi payload cùng key409.

ADMIN stale review409; quantity proposal giảm2→1 khi occupied2 bị409; reject200, approval cũ vẫn verified/currentQuantity2. Cancel2 unpaid bookings204 và occupied trở về0. Các checks là hosted HTTP thật, không lấy mocks làm acceptance. Có54 PASS/4 FAIL logged checks theo từng attempt, gồm setup/duplicate labels; **không phải54 unique business scenarios**. OTP failures/expiry request400/concurrency failure được giữ tại `e2e-results.json`.

## 6. Authorization & Booking Lifecycle

OWNER/CUSTOMER approve403; OWNER B sửa hotel OWNER A403; forged ADMIN header trên CUSTOMER JWT403; stale revision409. Direct hold endpoints theo contract chỉ nhận booking-service authority; CUSTOMER đi qua Booking API, không ký machine/ADMIN token giả.

Concurrency: tạo được đúng2 booking/hold, occupied2 với stock2, không thấy oversell trong trạng thái đối soát. Tuy nhiên lượt recovery race trả409/500/409; log có Hikari acquisition timeout, max2/10s. **PARTIAL, không concurrency PASS**; dừng tăng tải. Một probe SQL08006 đã được giữ và đối soát lại exact IDs, không replay writes mù.

Booking thứ3 trên tháng3 được tạo để chờ natural15-minute hold expiry, khác ngày search benchmark. Lượt đầu400 do unitPrice fixture không khớp catalog, sửa request rồi201. Runtime Hotel/User không ready sau đó nên **expiry/release end-to-end NOT VERIFIED**, không sửa expires_at để tạo PASS. Không payment/onsite confirm/refund/provider call; paid-confirm lifecycle ngoài phạm vi đã kiểm.

## 7. Performance Baseline

Small2 hotels×2 room types, Gateway8080→Hotel8083→Supabase. Warmup3,30 attempted samples, concurrency1. **29 successful/1 timeout**, timeout45s, error/timeout3.33%. Chưa đạt30 successful samples; không retry để che lỗi. Sau cell lỗi dừng, không tăng concurrency/dataset. Direct comparison và các cells khác NOT RUN. Percentile linear interpolation trên successful samples, không tính timeout thành success.

| Endpoint | n success/attempt | p50 ms | p95 ms | p99 ms | Mean ms | Min–max ms | attempted req/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| search undated C1 | 29/30 | {before['p50']:.1f} | {before['p95']:.1f} | {before['p99']:.1f} | {before['mean']:.1f} | {before['min']:.1f}–{before['max']:.1f} | {before['rps']:.3f} |

Raw có timeout45s riêng. Đây là development diagnostic; một functional expiry setup khác ngày có overlap cửa sổ trước, không benchmark isolated/production hoặc saturation capacity. Không gán giá trị thiếu cho C3/C5/C10/direct/detail/availability benchmark.

## 8. N+1 Analysis

Instrumentation riêng, warmup1/endpoint +3 serial samples, không gộp với benchmark:

| Endpoint | JDBC statements/request | Mean JDBC ms, gồm network | Mean HTTP ms |
|---|---|---:|---:|
{table}

Sai lệch1statement ở một số sample có thể gồm scheduled session; Hibernate session metrics không trace-isolated và không bao gồm transaction-control/JdbcTemplate ngoài Hibernate. Source xác nhận hai per-room approval loops ở public room mapping/lowest price, thêm checks ở search và batch availability. **Chỉ có một populated size: chưa đo runtime slope2×2→3×5→5×10; không tuyên bố N+1 resolved.** Lazy room-media và association/amenity reads theo hotel còn mở.

SELECT-only EXPLAIN ANALYZE trên Small: single approval0.825ms, batch candidate1.087ms server execution; không phải HTTP/JDBC time. Persistent TLS libpq SELECT1 mean{netmean:.1f}ms, SELECT1+rollback mean{txmean:.1f}ms,10 samples; driver/protocol/network cộng chung, không phải pure RTT hay Hikari. Network/transaction overhead rất khác server execution; Hikari wait và app processing/Gateway chưa tách đầy đủ.

## 9. Optimizations

`RoomTypeRepository.findEffectivelyApprovedRoomIds`: set-based native predicate giữ đủ room/hotel/quantity/status/provenance/reviewer/time/effective pointer/latest-approval constraints. `HotelMapper` lấy approved IDs theo batch500 và dùng chung cho public room list+lowestPrice, không cache xuyên request. `AvailabilityService` batch approval trong batch availability; `HotelOfferSearchService` batch eligible IDs/dated availability, không gọi single availability mỗi room.

Giữ nguyên authoritative `hasEffectiveInventoryApproval` sau room FOR UPDATE ở new hold, transaction boundaries, authorization, idempotency, confirm/release và provenance. Không schema/index/V1/financial/frontend change. Native test thêm đối chiếu batch predicate khi approval valid và khi corruption metadata. Duplicate reads theo hotel và lazy associations chưa tối ưu toàn bộ.

## 10. Before/After

BEFORE artifact/hash + raw/query counts có evidence. AFTER JAR build PASS sau khi dừng process Windows đang giữ JAR;838+5 regressions PASS. Tuy nhiên hosted restart không đạt readiness, Hotel báo runtime role quá nhiều connections; User/Hotel vẫn không sẵn sàng sau bounded quiesce/restart. Migration-role read-only probe thấy4 Supavisor sessions/role ở một số targets, không đủ quyền xem owner/state để terminate an toàn. Không tăng pool/role cap hoặc kill backend session không xác định.

**AFTER benchmark BLOCKED, không có % improvement, không có measured SQL reduction claim.** Không dùng native/mock/empty-dataset thay comparison. Implementation chưa đạt hosted performance acceptance.

## 11. Cleanup Verification

Dừng clients và toàn bộ owned JVM; không Revenue/Regulations consumer. Ack đúng6 fixture booking events và2 fixture OTP DLQ payloads, không purge; final queues ready/unacked0. Redis chỉ kiểm/xóa4 exact fixture session-generation keys (0 tồn tại), loại ADMIN. Xóa2 container Mailpit riêng của batch,4 captured messages synthetic; original infra dừng/retained, không xóa dữ liệu hạ tầng có trước.

Database cleanup qua TLS verify-full migration-role transaction, exact PK lists + expected row counts; không prefix DELETE/TRUNCATE/DROP/sequence reset. Một OTP đã lifecycle-delete nên lần guard mismatch rollback; đối soát actual exact-ID counts trước khi cleanup lại. Xóa User15 +Booking12 +Hotel20 =47 rows;48 manifested IDs verified absent. Outbox/loyalty/account/dependent rows đều nằm manifest; không residue.

Schema/history/counts unchanged5/5, non-fixture complete-row salted fingerprints match5/5. Fingerprint baseline được lấy sau registration, exclude exact manifested IDs; bảng baseline0 được chuẩn hóa rỗng theo snapshot pre-write. Vì vậy không tuyên bố có full-content snapshot trước mọi write. Counters sequence có thể tiến do synthetic events, không reset; schema/history không đổi.

## 12. ADMIN Preservation

Final `admin` tồn tại, ADMIN, active/email verified, same ID hash/baseline fields, complete user row fingerprint unchanged, BCrypt match với secure environment PASS. Không lưu password hash/JWT. Initial Gateway login/authorization200; **final actual HTTP login503/BLOCKED do User unavailable**, không đánh đồng BCrypt check với login E2E. ADMIN không ở cleanup fixture IDs và được giữ cho batch sau.

## 13. Regression

| Suite cuối | Executed PASS | FAIL/error | Opt-in skipped |
|---|---:|---:|---:|
| User |110|0|4|
| Auth |47|0|0|
| Hotel |396|0|33|
| Booking |285|0|4|
| Native PostgreSQL18 canonical (5 owners) |5|0|0|

**843 unique executed PASS,41 opt-in skips.** Chỉ cộng final full suite/module và5 native executions riêng; không cộng focused/failed attempts. Default ApplicationTests không chạy lại vì thiếu environment context, explicit excluded. Initial default User context error và intermediate Mockito setup failure được giữ riêng; final suites không che chúng. Frontend không đổi bởi Batch8B nên SKIPPED; không dùng kết quả Batch7B làm test mới.

## 14. Remaining Risks

Connection budget/role cap/Supavisor session lifecycle sau restart chưa được giải quyết. Không terminate backend không xác định ownership hoặc chỉnh hosted role/DDL để vượt gate. Cần operator xác minh pools/active clients/idle session behavior, restart procedure và connection headroom trước load tiếp.

Medium/Larger,30 successful samples/cell,Gateway/direct overhead,C3/5/10 read benchmark,AFTER comparison,runtime room-count slope,steady expiry/release/paid-confirm và final HTTP ADMIN re-login còn thiếu. Hotel publication từ documents chưa acceptance vì Storage thật bị cấm. N+1 room-media/per-hotel association còn mở. Không production-ready.

## 15. Final Status

**PARTIAL / HOSTED PERFORMANCE AFTER & FULL LIFECYCLE ACCEPTANCE BLOCKED.** Scoped initial auth/approval/booking/idempotency/authorization/cancel PASS; concurrency/pool/readiness và performance gates chưa đủ. Cleanup DONE, permanent ADMIN preserved, schema/Flyway unchanged. Backend/frontend HEAD và staged index giữ nguyên; frontend pre-existing diff byte-for-byte preserved, không stage/commit/push/reset/checkout/restore.

## 16. Recommended Next Workstream

Giải quyết runtime-role/Supavisor connection lifecycle với operator, trong budget được duyệt và không tự apply DDL. Sau readiness ổn định, tạo run mới/exact-ID manifest mới; hoàn tất hosted AFTER & same-dataset BEFORE có đủ successful samples, direct/Gateway metrics,3 dataset sizes/query slope,concurrency/expiry/final HTTP admin login. Kiểm room-media/association fetching bằng SQL patterns; chỉ tối ưu tiếp khi có populated evidence. Giữ ADMIN hiện hữu, không tạo ADMIN khác. Batch8 lịch sử được giữ nguyên.

Evidence: [Batch8B directory](evidence/batch-8b-hosted-e2e-performance/README.md).
'''
    (ROOT/'Docs/BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md').write_text(report,encoding='utf-8')
    (OUT/'README.md').write_text('''# Batch8B evidence — 17/11/2026

**PARTIAL.** See [full report](../../BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md). No performance improvement or production-readiness claim.

- `initial-git-*.json`, `final-git-state.json`: branch/HEAD/index/diff provenance; prior changes retained.
- `*-runtime-preflight.json`, `*-before.json`, `*-after.json`: fresh target/TLS/catalog/history/count checks.
- `admin-baseline.json`, `admin-authentication.json`, `final-admin-preservation.json`: protected permanent account; initial real login/authorization, final DB+credential preservation; final HTTP503 explicitly blocked.
- `fixture-manifest.json`, `cleanup-exact-id-plan.json`, `cleanup-results.json`:48 exact IDs absent,47 rows explicitly deleted; old OTP lifecycle-deleted.
- `e2e-results.json`, `concurrency-*.json`: raw attempt-level outcomes including failures; not unique scenario totals.
- `financial-side-effect-check.json`, `zero-point-side-effects.json`: scoped zero-point cancellation receipts/account, no provider/payment/refund/real ledger effects.
- `query-counts-before-small.json`, `query-count-slope.json`, `sql-plans-small.json`: populated counts and limits; multi-size slope/AFTER missing.
- `benchmark-before-small-raw.json`, `benchmark-before-small-summary.json`, `before-after.json`:29/30, one timeout, stop; AFTER blocked, no fabricated metrics.
- `network-transaction-probe.json`, Hikari snapshot, readiness/pressure files: separate diagnostics, not pure RTT/CPU attribution.
- `regression-results.json`, `local-disposable/`:843 executed final PASS/41 opt-in skips, local native tests do not replace hosted acceptance.
- `existing-row-digests-baseline.json`, `existing-row-digests-after.json`: salted opaque complete-row digests,5/5 equality after cleanup. Baseline captured after registration with exact fixture exclusion; baseline-empty tables normalized using pre-write counts. `existing-row-digests-before.json` is an earlier superseded capture and is not the comparison baseline.
- `broker-cleanup.json`, `local-side-effect-cleanup.json`: exact fixture payload cleanup, no queue purge/cache flush; owned runtime stopped.

No credentials, password hashes or JWTs belong in this directory. Private test logs/input files remain ignored. Existing permanent ADMIN must never be included in future fixture cleanup. Re-run tooling requires a new manifest/run; completed manifest writesAllowed=false.
''',encoding='utf-8')
    checkpoint='''\n\n## Batch 8B — hosted authenticated E2E & performance follow-up, 17/11/2026

**PARTIAL / PERFORMANCE AFTER & FULL CONCURRENCY BLOCKED.** Permanent `admin` login200/ADMIN queue200; real Gateway OWNER→proposal→ADMIN approve/reject→CUSTOMER booking/idempotency/cancel and ownership/header/stale guards exercised. Synthetic hotel publication SQL setup is separate from document/provider acceptance. Small2×2 populated SQL counts/search measured; benchmark29/30 C1 with timeout, stopped. Read batching implemented; final843 executed local regression/native PASS,41 opt-in skips. Hosted AFTER/multi-size slope/expiry/final HTTP ADMIN login not verified because role-connection exhaustion and failed User/Hotel readiness after restart; no latency improvement/N+1-resolved/production-ready claim.

Exact-ID cleanup47 rows,48 manifested IDs absent, fixture broker/DLQ payloads removed without purge; existing row digests and schema/Flyway/counts5/5 match. ADMIN complete row unchanged,active/ADMIN and BCrypt credentials match; final runtime login503 is explicitly BLOCKED. No role/password change,extra ADMIN,hosted DDL/V1/provider/frontend/index/HEAD change or Git stage/commit/push. Prior Batch8 conclusions remain historical. Next: operator-reviewed Supavisor/runtime-role connection lifecycle and readiness, then new controlled populated BEFORE/AFTER+concurrency/expiry/query slope run preserving permanent ADMIN.

[Batch8B report](../BATCH8B_HOSTED_E2E_PERFORMANCE_2026-11-17.md), [evidence](../evidence/batch-8b-hosted-e2e-performance/README.md). Parent standardization/production gates remain open.
'''
    for name in ['PROJECT_STANDARDIZATION_PLAN.md','PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md']:
        p=ROOT/'Docs/standardization'/name;t=p.read_text(encoding='utf-8')
        marker='\n\n## Batch 8B — hosted authenticated E2E & performance follow-up, 17/11/2026'
        if marker not in t:p.write_text(t+checkpoint,encoding='utf-8')
    for label,repo in [('backend',ROOT),('frontend',ROOT.parent/'checkinn-frontend')]:
        git[label]['status']=subprocess.check_output(['git','-C',str(repo),'status','--porcelain=v1'],text=True,encoding='utf-8')
    write('final-git-state.json',git)
    print('Batch8B PARTIAL report/evidence written; cleanup/schema/ADMIN assertions passed')
if __name__=='__main__':main()
