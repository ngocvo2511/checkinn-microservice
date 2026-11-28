# Gemini initial smoke — operator actions

24/11/2026. **UNSIGNED / NOT APPROVED / NOT EXECUTABLE**. Không có quyền gọi live trong batch setup này. Bảng fixture và hashes chính xác: [report](BATCH10B4C_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK_REPORT.md).

## NHỮNG VIỆC TÔI CẦN BẠN THỰC HIỆN

1. **Review độc lập ba fixture** trong report: xác nhận nguyên văn, CRITERIA/SEARCH, mọi field REPLACE, missingSlots=[], inferredFields=[], reference=""; VEX1-01 automatic browse, VEX1-02 giá tham khảo exclusive trên trang trả về, VEX1-03 ngày/occupancy explicit. Gửi reviewer reference và đồng ý hoặc sửa kỳ vọng cụ thể. Đây là review sản phẩm, không phải chữ ký hay quyền dispatch. Chỉ ba fixture; review 60 VNE1 dành cho campaign sau.
2. **Xác minh tài khoản và duyệt ngân sách**: tự mở [AI Studio API keys/projects](https://aistudio.google.com/api-keys), chọn đúng project; [active limits](https://aistudio.google.com/rate-limit?timeRange=last-28-days); Google Cloud Console → cùng project → APIs & Services → Credentials/API restrictions; Billing → linked billing account/tier. Không bấm chạy prompt/test key. Cung cấp safe project alias + evidence references có thể đối chiếu, thời điểm/expiry, model gemini-3.5-flash-lite entitlement, execution region, tier, key ownership/project binding/restrictions, input/output/thinking/other billing classes, RPM/TPM/RPD còn lại, shared consumers và coordinated window. Xác nhận ba call tuần tự khả thi và retention/training terms áp dụng, synthetic-only. Public prices không xác minh account. Gửi financial allocation USD rõ ràng; không gửi payment details/console export/token/key. Chưa thể chốt exposure khi chưa có upper rates account-specific và proof mọi billed class nằm trong cap.
3. **Provision trust/storage hoặc cho phép tôi hỗ trợ phần public/directory**: chọn issuer/keyId độc lập, public DER fingerprint, revocation source và đường dẫn absolute mới ngoài Git; không dùng sealed journal cũ. Private signing key do bạn tạo/giữ ngoài Agent runtime/repository trên signing host. Không gửi private key. Các lệnh dưới là hướng dẫn để operator chạy, chưa chạy với tài sản thật. Gửi public DER path, SHA256 fingerprint xác minh qua nguồn độc lập, issuer/keyId, ACL attestation và journal path. Tôi sẽ kiểm tra format/binding/path trước canonical payload; không tự coi path/key do artifact cung cấp là trust root.
4. **Credential attestation**: cấu hình GEMINI_API_KEY chỉ trong environment của process chạy sau này bằng cơ chế secret hiện có; không lưu trong repo, tham số Java, chat hoặc log. Xác nhận tên biến, ownership và project binding, không giá trị. Setup/preflight không đọc biến; chưa chứng minh credential hoạt động.
5. **Ký sau khi đủ dữ liệu**: tôi sẽ chuẩn bị exact payload/canonical bytes với identities mới, real evidence, financial envelope và UTC window mới ≤5 phút, trình bạn review. Bạn ký ngoài Agent, trả detached signature/public metadata; tôi envelope + read-only verify. Nếu hết hạn phải cấp/ký mới. Sau preflight PASS vẫn cần bạn phê duyệt riêng batch BATCH10B4C-LIVE-INITIAL-SMOKE; không chạy live trong setup.

Các mục 1–5 chặn initial smoke. Review VNE1 30–60 mẫu chỉ chặn campaign sau. Không cần gửi lại evidence còn hiệu lực đã cung cấp; hiện chưa có evidence thật trong tài liệu workspace đã kiểm tra.

## PowerShell: operator provisioning (chỉ khi bạn quyết định thực hiện)

Signing host, directory riêng đã được operator bảo vệ; cần OpenSSL hỗ trợ Ed25519/rawin. Không chạy trong repository hoặc Agent runtime:

```powershell
openssl version
openssl genpkey -algorithm ED25519 -out operator-private.pem
openssl pkey -in operator-private.pem -pubout -outform DER -out operator-public.der
Get-FileHash -Algorithm SHA256 -LiteralPath .\operator-public.der
```

Private key cần ACL/backup phù hợp signing host; chỉ public DER X.509 SubjectPublicKeyInfo được chuyển sang operator execution host. PEM public không phải input của loader. Issuer/keyId không lấy từ approval JSON. Không để untrusted writers sửa key/revocations/control/artifact.

Ví dụ path đề xuất, **chưa provision/chưa signed binding**. Tạo một lần nếu bạn chọn path này, fail nếu đã tồn tại; không xóa hoặc reset để tái dùng allocation:

```powershell
$operatorRoot = 'C:\Users\ACER\CheckInnGeminiOperator'
New-Item -ItemType Directory -Path $operatorRoot -ErrorAction Stop
$operatorAccount = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls $operatorRoot /inheritance:r /grant:r "${operatorAccount}:(OI)(CI)F"
New-Item -ItemType Directory -Path "$operatorRoot\journals" -ErrorAction Stop
icacls $operatorRoot
icacls "$operatorRoot\journals"
```

Operator kiểm ACL chỉ các principals cần thiết; không symlink/junction, scheduled cleanup hay rollback. Không đặt private key ở root runtime này. Public DER/revocations/reviewed-payload/canonical/signature/approval/control ở root, journal files chỉ trong journals. Revocation snapshot JSON array (vd [] chỉ khi thật sự chưa revoke); update từ authority, không lấy từ signed artifact.

## Verified offline CLI syntax

Từ backend root; output dùng CREATE_NEW, tên phải mới. Không mở Spring app:

```powershell
$evalJar = '.\ai-agent-service\target\ai-agent-service-0.0.1-SNAPSHOT.jar'
$evalTool = '-Dloader.main=com.example.aiagent.model.GeminiEvaluationPreflight'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher template initial-smoke 'C:\operator-review\smoke-UNSIGNED.json'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher canonicalize 'C:\operator-review\reviewed-payload.json' 'C:\operator-review\canonical.bin'
```

Template là envelope; canonicalize phải nhận **payload object** đã hoàn thiện, không envelope. Chưa canonicalize final thật khi evidence còn thiếu. Canonical profile v1 sorted object keys/array order retained/normalized decimals/Jackson compact UTF-8, không RFC8785 claim.

Operator chạy tại signing host, với exact canonical.bin đã review:

```powershell
openssl pkeyutl -sign -rawin -inkey operator-private.pem -in canonical.bin -out approval.sig
```

Sau khi operator ký:

```powershell
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher envelope 'C:\operator-review\reviewed-payload.json' 'C:\operator-review\approval.sig' 'C:\operator-review\approval.json'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher verify 'C:\operator-review\operator-public.der' REVIEWED_ISSUER REVIEWED_KEY_ID 'C:\operator-review\revocations.json' 'C:\operator-review\approval.json' - 'C:\Users\ACER\CheckInnGeminiOperator\journals' OFFLINE_ONLY
```

bind-scope dành campaign sau: `bind-scope nlu scope.json reviewed-oracle.json REVIEWED_CAMPAIGN_ID output-payload.json`; oracle draft hiện tại không được chấp nhận. Review/status flags không tự cấp authority.

## Future execution — KHÔNG CHẠY trong batch này

Entry point mới chỉ initial smoke exact order VEX1-01/02/03. Operator-owned control file phải đúng ba ASCII bytes RUN (không newline); bất kỳ thay đổi/mất/read failure hoặc revocation đều stop trước/lúc boundary tiếp theo. Kill process khi cần dừng khẩn; unfinished allocation không resume. Không có active external cancellation channel bảo đảm dừng tức thì trong một HTTP call; deadline ≤10s vẫn áp dụng.

Sau signed preflight mới + **explicit live authorization riêng**, dùng paths/issuer/keyId đã provision độc lập:

```powershell
# FUTURE ONLY: prepare RUN control through operator-owned procedure after authorization.
java '-Dloader.main=com.example.aiagent.model.GeminiInitialSmokeOperator' -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --execute-initial-smoke --enable-synthetic-live 'C:\operator-review\operator-public.der' REVIEWED_ISSUER REVIEWED_KEY_ID 'C:\operator-review\revocations.json' 'C:\operator-review\approval.json' 'C:\Users\ACER\CheckInnGeminiOperator\journals' 'C:\operator-review\execution-control'
```

Không có default execute/dry-run bên CLI live. Dùng GeminiEvaluationPreflight cho offline. Hai cờ thể hiện execution intent riêng, không bật ordinary Agent. Limits lấy từ verified signed review, không từ env/CLI budget overrides. Credential supplier chỉ System.getenv('GEMINI_API_KEY') sau durable allocation/session/process reservation; transport lazy fixed GeminiHttpTransport. Revocations/trust files reread fail-closed tại boundaries. Results immutable; scalar console outcomes, usage/latency/request identity nằm trong existing metadata journal. Không raw response storage; failure exit nonzero, no retry/fallback. Public CLI/trust filesystem là operator trust boundary, không authentication cho untrusted local user.
