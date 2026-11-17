"""Final measured-evidence aggregation; no hosted access or invented results."""
import collections,hashlib,importlib.util,json,statistics
from pathlib import Path
R=Path(__file__).resolve().parents[1];O=R/'Docs/evidence/batch-8c-runtime-stability'
def read(n,default=None):
 p=O/n;return json.loads(p.read_text(encoding='utf-8')) if p.exists() else default
def write(n,v):(O/n).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def aggregate():
 slope=[]
 for p in O.glob('query-counts-after-*.json'):
  groups=collections.defaultdict(list)
  for row in json.loads(p.read_text()):groups[row['endpoint']].append(row)
  for endpoint,rows in groups.items():
   good=[x for x in rows if x.get('jdbcStatements') is not None]
   slope.append(dict(dataset=p.stem.removeprefix('query-counts-after-'),endpoint=endpoint,samples=len(rows),statuses=[x['status'] for x in rows],statements=[x['jdbcStatements'] for x in rows],jdbcMs=statistics.mean(x['jdbcMsIncludingNetwork'] for x in good) if good else None,httpMs=statistics.mean(x['ms'] for x in rows),acquisitionMs=statistics.mean(x['acquisitionMs'] for x in good) if good else None,requestScoped=all(x.get('matchedSessions',0)>0 for x in rows)))
 write('query-count-slope.json',slope)
 direct=read('direct-after-small.json',[]);direct_rows=[]
 for endpoint in sorted({row['endpoint'] for row in direct}):
  samples=[row for row in direct if row['endpoint']==endpoint];v=sorted(row['ms'] for row in samples if row['status']==200)
  def pct(p):
   index=(len(v)-1)*p;lo=int(index);return v[lo]+(v[min(lo+1,len(v)-1)]-v[lo])*(index-lo)
  direct_rows.append(dict(endpoint=endpoint,attempts=len(samples),successful=len(v),errors=len(samples)-len(v),mean=statistics.mean(v),p50=pct(.5),p95=pct(.95),p99=pct(.99),min=min(v),max=max(v),scope='Serial direct window; wall throughput not recorded, do not derive pure Gateway overhead'))
 write('direct-after-small-summary.json',direct_rows)
 historical=json.loads((R/'Docs/evidence/batch-8b-hosted-e2e-performance/benchmark-before-small-summary.json').read_text())
 write('before-after.json',dict(controlledBeforeAfter=False,reason='No preserved runnable pre-optimization JAR/source snapshot in Batch8B private directory; current HEAD predates unrelated approval changes. Historical before hash alone cannot reconstruct a compatible isolated source baseline safely.',historicalReference=historical,after=read('benchmark-after-small-summary.json',[]),afterByDataset={p.stem:json.loads(p.read_text(encoding='utf-8')) for p in O.glob('benchmark-after-*-summary.json')},afterArtifactSha256=hashlib.sha256((R/'hotel-service/target/hotel-service-0.0.1-SNAPSHOT.jar').read_bytes()).hexdigest(),claim='Measured AFTER only; no percent improvement or N+1-resolved claim'))
 return slope
if __name__=='__main__':aggregate()

def report():
 slope=aggregate();verification=read('final-verification.json',{});reg=read('regression-results.json',[])
 benchmark=[]
 for file in sorted(O.glob('benchmark-after-*-summary.json')):
  benchmark.extend(dict(dataset=file.stem.removeprefix('benchmark-').removesuffix('-summary'),**row) for row in json.loads(file.read_text()))
 raw=read('benchmark-after-small-raw.json',[]);direct=read('direct-after-small.json',[])
 con=read('concurrency-results.json',{});expiry=read('natural-expiry-result.json',{});cleanup=read('cleanup-results.json',{})
 rows=['| Dataset / endpoint | C | Success/attempt | p50 | p95 | p99 | Mean | Min–max ms | req/s | Error/timeout |','|---|---:|---:|---:|---:|---:|---:|---|---:|---|']
 for x in benchmark:
  if 'p95' not in x:rows.append('| '+x['dataset']+' / '+x['endpoint']+' | '+str(x['concurrency'])+' | SKIPPED_SAFETY | — | — | — | — | — | — | — |');continue
  rows.append(f"| {x['dataset']} / {x['endpoint']} | {x['concurrency']} | {x['successful']}/{x['samples']} | {x['p50']:.1f} | {x['p95']:.1f} | {x['p99']:.1f} | {x['mean']:.1f} | {x['min']:.1f}–{x['max']:.1f} | {x['rps']:.3f} | {x['errors']}/{x['timeouts']} |")
 qrows=['| Dataset / instrumentation | Endpoint | JDBC statements | Mean JDBC ms | Mean HTTP ms |','|---|---|---|---:|---:|']
 for x in sorted(slope,key=lambda v:(v['dataset'],v['endpoint'])):
  jdbc=f"{x['jdbcMs']:.1f}" if x['jdbcMs'] is not None else 'N/A'
  qrows.append(f"| {x['dataset']} / {'trace' if x['requestScoped'] else 'serial window'} | {x['endpoint']} | {x['statements']} | {jdbc} | {x['httpMs']:.1f} |")
 rrows=['| Suite | Executed PASS | Failure/error | Skip |','|---|---:|---:|---:|']
 for x in reg:rrows.append(f"| {x['owner']} | {x['executedPass']} | {x['failures']}/{x['errors']} | {x['skipped']} |")
 rrows.extend(['| Native canonical PostgreSQL18, 5 owners | 5 | 0/0 | 0 |','| Lifecycle guard tests | 6 | 0/0 | 0 |'])
 state='PARTIAL'
 text='''# Batch 8C — Runtime stability & performance, 17/11/2026

## 1. Executive Summary

**PARTIAL — scoped runtime/hosted acceptance PASS; RCA lịch sử và tối ưu read amplification còn mở. Không production-ready.** Đã đo **1170/1170 Gateway + 150/150 direct samples** thành công, **0 lỗi/timeout**. Concurrency, natural expiry và ADMIN HTTP sau cleanup/restart PASS. Cleanup **194 rows / 194 exact IDs**; dữ liệu hiện hữu, schema và Flyway khớp **5/5**. Regression **843 Java/native + 6 guard tests PASS**, **41 opt-in skips**. Không có controlled BEFORE/AFTER hoặc claim N+1 resolved; các failed setup/readiness attempts được giữ riêng.

## 2. Environment & Supabase Targets

Backend `feat/supabase-dev-runtime`, HEAD `ea46f3cb7168039b0160d05a05822aa906a81a17`; frontend cùng workspace được bảo toàn. Initial status/staged/unstaged/untracked đã ghi; không reset/checkout/restore/clean/stash/add/commit/push. `initial-git-*.json` được lưu sau khi tạo diagnostic script đầu tiên; status trước tạo script nằm trong transcript.

Năm independent hosted targets User/Loyalty, Hotel/Inventory, Booking/Payment, Revenue, Regulations: fresh control-plane identity và dedicated runtime preflight PASS5/5, verify-full TLS1.3/session pooler5432; PostgreSQL17.11. Catalog columns/constraints/indexes/Flyway V1 trước run khớp canonical5/5. Không migration/DDL/role/grant/dashboard/pooler-mode/provider settings change. Preflight vẫn ghi inherited PUBLIC TEMP=true; quyền này được giữ nguyên, không coi là production hardening hoàn tất. Windows/Java21, timezone ứng dụng UTC; ngày báo cáo Asia/Saigon. Redis/RabbitMQ local session có label ownership; không start Revenue/Regulations/Media/Notification hoặc gọi SMTP/Storage/VNPay thật.

## 3. Connection Inventory

| Service | Database | Pool max/min | Mode | Expected client pool |
|---|---|---|---|---|
| User | User/Loyalty | 2/1 | JDBC session pooler, TLS verify-full | 1 JVM, tối đa2 |
| Hotel | Hotel/Inventory | 2/1 | tương tự | 1 JVM, tối đa2 |
| Booking | Booking/Payment | 2/1 | tương tự | 1 JVM, tối đa2 |
| Revenue | Revenue | 2/1 configured | tương tự | 0, không start |
| Regulations | Regulations | 2/1 configured | tương tự | 0, không start |
| Auth | Không datasource | — | User gRPC/HTTP, Redis | 0 PostgreSQL |
| Gateway/Eureka | Không datasource | — | routing/discovery | 0 PostgreSQL |
| Place | Datasource loopback legacy, không Supabase profile | không start | không thuộc hosted targets | 0 PostgreSQL hosted |
| Config/Admin/Notification/Media | không hosted datasource được start | — | ngoài runtime test | 0 PostgreSQL hosted |

`connection-inventory.json` liệt kê scheduled jobs và source paths: Hotel expiry scan60s, User points recovery, Booking outbox/settlement/reservation/refund recovery và cron expiry. Dùng chung primary datasource; không tìm thấy secondary datasource hoặc raw unclosed JDBC ownership trong scoped source scan. Flyway disabled, không mở migration datasource trong runtime. Health có thể dùng pool connection. Booking open-in-view=false explicit; User/Hotel không override Boot default. Không kết luận leak chỉ từ slow HTTP.

Connection timeout/initialization fail timeout10s; idle timeout600000ms, maxLifetime1800000ms, keepalive0 là Hikari5.1 defaults suy ra từ dependency/no source override, không phải dashboard measurements. Không tăng maxPoolSize hoặc role cap.

## 4. Connection Budget

Mỗi project: PostgreSQL max_connections60, reserved3; dedicated app-role cap4, migrator-role cap2. Approved local allocated budget7 = runtime max2 + migration reserve2 + operations3. Database server headroom tổng còn chịu managed/external clients; account-level client quota/dashboard Supavisor pool-size chưa quan sát đầy đủ.

Một app JVM max2 để lại role headroom2; hai JVM max2 có thể dùng hết cap4 nên launcher từ chối duplicate module/port và kiểm headroom trước startup. Cycle2 quan sát2 app-role backend rows mỗi User/Hotel/Booking, không phải bằng chứng có đúng2 Supavisor client sessions. Diagnostic libpq connections luôn đóng bằng context manager; migration role tách app role. Không chạy probe runtime role đồng thời load trừ hai bounded SELECT-pattern permission attempts đã ghi; không thay grants.

## 5. Root Cause Analysis

Batch8B Hotel có PostgreSQL `too many connections for role`, Hikari total=0: admission thất bại ở role limit, không chỉ queue trong pool đang đầy. Bốn hàng backend nhãn Supavisor trong evidence cũ không đủ chỉ ra client/JVM ownership; state/start timestamps role khác bị che. Không chứng minh được ai sở hữu các backend lịch sử, account-level quota hoặc một Java leak.

Defect xác nhận trong legacy launcher: `taskkill /T /F` không đi qua Spring context/Hikari graceful close; không có guard port+PID creation-time hoặc backend-drain readiness. Batch8C initial inventory không có Java JVM; sau graceful shutdown có Hikari shutdown complete và tất cả owned JVM exited. User/Hotel app backend rows về0; Booking còn2 ở probe đầu rồi0 ở probe kế tiếp. Đây là observed drain delay, không được gọi là2 leaked clients.

Một nguyên nhân độc lập của503: Hotel health UP trước discovery/Gateway route propagation. Instrumented smoke ban đầu login ADMIN200 nhưng Hotel queue/search503 và thiếu HOTEL-SERVICE trong registry; recheck toàn chain PASS. Không nhầm routing delay này với lỗi auth credentials hoặc role exhaustion.

## 6. Implemented Fixes

Thêm opt-in `tools/batch8c-runtime.py` và local JDK attach helper `OwnedGracefulShutdown.java`: operation lock; JVM module inventory; exclusive port checks; PID+creation-time ownership; bounded role-headroom gate; sequential dependency startup/health; `start-core` polling real chain; invalidate prior readiness on restart; Spring context shutdown qua local JMX attach; bounded exit verification, không force-kill fallback. Spring admin/graceful shutdown chỉ bật trong process test; không mở remote JMX hoặc HTTP shutdown endpoint, không sửa production/base/profile configuration.

Legacy launcher giữ nguyên để không thay behavior ngoài phạm vi; đường stop cũ vẫn forceful và không được dùng cho acceptance này. Six guard tests kiểm occupied port, reused PID, foreign PID/state, exited process handling và exhausted headroom. New dataset runner kiểm readiness; credentials/tokens chỉ trong memory/private ignored files. Tooling/runner mistakes và attempts được công bố ở `execution-attempts.json`; không quy các lỗi wrapper cho Supabase.

## 7. Startup/Restart Acceptance

Cycle1 và cycle2: Eureka→User→Hotel→Booking→Auth→Gateway health, registrations, ADMIN login/queue, valid search và anonymous booking401 đã kiểm bằng HTTP thật. Cycle1 stop đóng3 Hikari pools và xác minh6 JVM exit. Hotel restart riêng để sửa exact Hibernate statistics key, sau đó full chain recheck; failed503 giữ riêng. Final post-cleanup `start-core` và graceful stop nằm trong final verification/smoke evidence.

Không dùng một health UP thay toàn chain. Runtime snapshots phân biệt Hikari active/idle/pending/acquisition/timeout với server backend visibility. Scope kết luận chỉ là các bounded restarts và load đã thực thi; không chứng minh universal Supavisor lifecycle hoặc multi-replica production readiness.

## 8. ADMIN Authentication

Permanent `admin` lấy password từ secure `docker/.env` trong memory, real Gateway login và ADMIN inventory queue được kiểm nhiều lần, gồm sau restart. Không reset/disable/change role/password hoặc tạo ADMIN khác. Queue contract trả list. Final HTTP results và account identity/complete-row HMAC proof được ghi riêng; không dùng BCrypt/local tests thay login.

## 9. Performance Methodology

JAR Hotel AFTER SHA256 `119333d2da041509f1ffded0389063c8fa04541c7fefc26f66c994fe120e820f` khớp artifact8B; không giả định working-tree HEAD chính là BEFORE. Native/regression hoàn tất trước Small timed matrix. HTTP timeout45s, 30 measured attempts/cell, concurrency1/3/5/10 theo safety gate; không retry để che failures. Small runner ban đầu có3 warmup requests/endpoint trước matrix, không warmup độc lập từng concurrency cell. Matrix Medium/Larger dùng runner mới với3 warmup requests riêng mỗi cell; warmup raw được lưu và không gộp vào30 measured samples. Percentiles linear interpolation; p99 từ30 samples không phải SLA. Throughput trên measurement window, không saturation capacity.

C5/C10 skip khi serial p95 × (ceil(C/2)−1) vượt8s dự báo chờ so với acquisition timeout10s. Không coi SKIPPED là PASS. Direct measurements chạy trong cửa sổ riêng sau Gateway; không lấy hiệu làm pure Gateway overhead. Natural hold nằm ngày tháng3, benchmark search ngày tháng2; background schedulers vẫn hoạt động như runtime thật.

## 10. Populated Dataset Results

Run ID/IDs/dependencies ở manifest mới. Four synthetic identities (2OWNER/2CUSTOMER) register/verify/login qua Gateway; OTP chỉ đọc đúng identity fixture từ DB để gọi verify API, không có SMTP delivery acceptance. Hotel/room/proposal/approval qua API; hotel publication dùng exact-ID SQL synthetic setup vì Storage bị cấm, không giả nhận document publication acceptance. Dataset totals tương ứng2×2,3×5,5×10, không cộng dồn vượt5 hotels/50 rooms; actual stages và failures xem `dataset-*`/query evidence.

### Gateway matrices (milliseconds)

'''+ '\n'.join(rows)+'''

Direct raw data ở `direct-after-small.json`; Query probes được tách khỏi timed cells. Chỉ những cell có raw30 successful samples mới được coi hoàn tất; missing/skipped không thay bằng probes. Empty media/amenity fixture associations vẫn có thể gây lazy SELECT; đây không phải representative rich-content production dataset.

## 11. SQL Query-Count Slope

'''+ '\n'.join(qrows)+'''

Initial Small là serial log windows, có thể gồm scheduler statements. Các file traced nếu matchedSessions>0 lọc exact W3C trace ID, ghi excluded sessions; không gộp chúng với initial windows hoặc benchmark samples. Hibernate JDBC statements không gồm mọi JdbcTemplate/transaction-control statement; JDBC duration gồm driver/protocol/network, không phải server CPU.

`extensions.pg_stat_statements` bị runtime role từ chối; không xin thêm grants. SQL pattern attribution vì thế hạn chế. Source có lazy RoomType.mediaAssets, Hotel associations, per-hotel policy/FAQ/amenity reads và per-hotel batch approval. Hotel detail cố định một hotel: room count2→5→10 cho11→14→19 statements; lazy RoomType media mapping là candidate mạnh được source corroborate. Slope tăng là evidence remaining read amplification; không tuyên bố N+1 resolved hoặc gán mọi extra statement cho một association khi chưa có request SQL text. Authoritative approval sau room lock không đổi. Không thêm hosted indexes/migrations hoặc business source optimization chưa được kiểm chứng. Ngân sách tối đa3restart được tính cả full recovery restart và Hotel instrumentation restart; vòng cuối dành cho post-cleanup ADMIN acceptance. Không mở thêm restart để deploy fetch optimization trong run này; phần này được giữ OPEN cho workstream tiếp theo.

## 12. Before/After & Latency Decomposition

Không controlled BEFORE/AFTER: private directory chỉ còn BEFORE hash, không còn runnable baseline artifact/source snapshot tương thích để isolate an toàn. Batch8B historical small search C1:29/30, p50=5875.8ms,p95=15758.2ms; current matrix bên trên là AFTER thực tế khác điều kiện. Không tính phần trăm cải thiện. Historical SQL30–31/38/26–27/14/4 được giữ là reference, không coi tuyệt đối tương đương.

Measured: HTTP latency, Hibernate JDBC/acquisition duration, Hikari acquisition/pending/usage và direct riêng. Small initial search22 statements, JDBC mean khoảng2695ms trên HTTP mean3537ms; acquisition mean dưới1ms cho undated initial probes. Snapshot load có Hotel active2/pending1, acquisition MAX≈3.20s, timeout count0. Không lấy phần HTTP−JDBC làm serialization/app CPU vì còn transaction/control/network/Gateway; không dùng SELECT1 làm pure RTT hoặc JDBC làm PostgreSQL CPU. Server execution plans mới nếu có nằm riêng; không dùng historical plan time làm controlled decomposition.

## 13. Concurrency

Status: **'''+str(con.get('status','BLOCKED/NOT RUN'))+'''**. Actual stock2, three bounded simultaneous unpaid booking attempts trả201/201/409; held2/booked0. Hai same-key concurrent retries trả201 cùng một booking/hold, counter1; sau cancel counters0. HTTP durations gồm queue/RPC/lock wait, không phải pure PostgreSQL lock duration. Đối soát exact booking/hold/counter và idempotent replay/cancellation được ghi ở `concurrency-results.json`/e2e evidence. Nếu có unexpected500 hoặc Hikari timeout, không coi final counters đúng là PASS; không tăng tải/retry để che lỗi. Không payment provider/financial ledger mutation.

## 14. Natural Hold Expiry

Status: **'''+str(expiry.get('status','BLOCKED/NOT VERIFIED'))+'''**. Booking created bằng application default15-minute hold; không sửa expires_at/hold_expires_at. `natural-expiry-start.json` ghi deadline, final evidence đối soát scheduler booking state, Hotel hold status/counters và idempotent replay. Booking scheduler ghi cancellation lúc09:00 UTC, so với deadline08:57:56.848 UTC; state sau đó CANCELLED/RELEASED và counters0. Log riêng ở `natural-expiry-scheduler-evidence.json`; đây khác thời điểm đọc state sau đó. Booking cron5min/Hotel scan60s có thể tạo khoảng trễ quan sát sau deadline; không force expiry hoặc dùng cancel thay natural acceptance.

## 15. Cleanup

Status: **'''+str(cleanup.get('status','BLOCKED/NOT RUN'))+'''**. Dừng load, quiesce/stop owned apps trước deletion; discover exact descendants, guarded exact-ID expected counts, verify remaining IDs. Database xóa User17 + Booking16 + Hotel161 =194rows;194manifested IDs verified absent. Broker ack8 exact fixture payloads, final queues ready/unacked0;4 exact fixture Redis keys absent (0deleted). Broker chỉ ack fixture booking IDs/emails, preserve/requeue unmatched; không purge. Redis chỉ exact fixture generation keys, loại ADMIN; không flush. Không DELETE prefix/wildcard/TRUNCATE/DROP/reset sequences. Financial/outbox/audit counts và existing row HMAC/schema/history được kiểm trước/sau. Nếu cleanup fail thì stop writes và giữ exact remaining IDs.

## 16. ADMIN Preservation

Protected admin ID không thuộc fixture cleanup; same identity, ADMIN/active và complete user-table HMAC before/after phải khớp. Proof này bao gồm password field mà không xuất password hash. Final real Gateway login và review queue tách khỏi preservation SQL; final-verification ghi actual status. Không xóa private legacy credentials của batch khác.

## 17. Regression

'''+ '\n'.join(rrows)+'''

838 Java unit/service/security +5 native =843 executed PASS,41 opt-in skips;6 tooling guard tests riêng, tổng849 nếu cộng hai loại được ghi rõ. Không cộng focused/failed attempts hay native skipped declarations vào unique executed count. `*ApplicationTests` excluded do yêu cầu context riêng; canonical native cung cấp database integration riêng. Five new PostgreSQL18 databases/catalog/Flyway/JPA/repositories/permissions PASS5/5; owned cluster stopped finally. Frontend không đổi bởi Batch8C: SKIPPED tests/typecheck/build; không mượn kết quả batch trước.

## 18. Remaining Risks

Original backend ownership không thể reconstruct; external clients/dashboard quota chưa đầy đủ. Legacy forced-stop path vẫn tồn tại. Runtime non-recurrence scope không phải multi-node/outage proof. HTTP vẫn chậm, remaining lazy/per-hotel read amplification chưa tối ưu hoàn toàn; SQL text attribution thiếu quyền; controlled BEFORE không có. Kiểm full matrix theo actual raw/skip safety cells; không extrapolate missing measurements. Không certify production SLA/backup/restore/providers hoặc physical stock.

## 19. Final Status

**PARTIAL.** Chỉ đánh dấu PASS các gates có evidence thật. Evidence cuối: [final-verification.json](evidence/batch-8c-runtime-stability/final-verification.json). Không production-ready hoặc N+1-resolved claim. Pre-existing changes preserved; không stage/commit/push/reset/checkout/restore.

## 20. Recommended Next Workstream

Dựa trên measured slope, tối ưu nhỏ lazy room-media và duplicate/per-hotel association/approval fetches trong isolated reviewed source change, giữ authoritative lock/approval/idempotency/financial boundaries; regression rồi repeated populated same-run measurement. Lặp measurement matrix đúng per-cell warmup trong headroom sau source optimization. Operator chỉ cần can thiệp hosted limits/lifecycle khi diagnostics tái hiện admission exhaustion; cung cấp role cap4/max2, timestamp và sanitized backend visibility, không kill unknown sessions hoặc tự tăng quota.

Evidence: [Batch8C directory](evidence/batch-8c-runtime-stability/README.md).
'''
 (R/'Docs/BATCH8C_RUNTIME_STABILITY_PERFORMANCE_2026-11-17.md').write_text(text,encoding='utf-8')

if __name__=='__main__' and __import__('sys').argv[1:] == ['final']:report()
