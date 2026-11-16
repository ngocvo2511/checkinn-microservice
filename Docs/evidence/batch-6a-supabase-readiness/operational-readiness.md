# Operational readiness

STATIC VERIFIED: health/info/prometheus configs and Prometheus registry in five modules; health details never. monitoring/prometheus/prometheus.yml uses Eureka host.docker.internal:8761,5s scrape; Loki/Promtail Compose scaffolding. No hosted metrics/alerts/routing verified. Random application tag suffix creates potential series churn. Protect management endpoints/private network/auth; no public env/configprops/loggers.

[Source schedules/consumers](source-signals.json): Booking/Regulations outbox recovery; Revenue receipt/quarantine/gaps; ManualRefundRecovery/PaidReservationRecovery/OnsiteReservationRecovery/LoyaltySettlementRecovery/User PointsReservationRecovery; Hotel hold cleanup. Notification retry/no-requeue, RabbitMQ definitions exist. Actual vhost/DLQ/publisher confirm/return/poison-message paths NOT VERIFIED.

| Required signal | Pre-traffic gate / proposed alert |
|---|---|
| Health, SQLSTATE08/28/42501 | service/dependency outage/auth failure notification, no restart-loop readiness |
| Hikari active/idle/max/pending/timeout/acquire latency | sustained pending/new timeouts; correlate pooler client/backend/server budget |
| Flyway | target alias/version/checksum/status/duration; any failure/drift blocks startup |
| PostgreSQL | long/idle transactions, locks/deadlocks, storage/connection saturation, TLS expiry |
| RabbitMQ | queue age/depth/unacked, publish failures/returns, retries/DLQ, consumer availability |
| Outbox | oldest PENDING/retry count/last success; growing backlog beyond approved retry window |
| Revenue | quarantine/gap age/hash conflicts/duplicate anomaly; unexplained cash variance freezes financial traffic |
| Payment/refund | pending/executing/unknown age/recovery/provider failure |
| Loyalty | held/unknown/settlement/reverse backlog and duplicate-command anomaly |
| Inventory | expired holds/unresolved paid-onsite/unknown stock/review backlog |
| SMTP/Media/RPC/HTTP | latency/errors/timeouts/delivery/storage authorization |

Business metrics are proposed instrumentation/runbook requirements, not claimed existing counters. Numeric SLO/threshold/window/observation period need measurement/owner agreement. Test alert receiver/escalation before traffic; one replica initially and observe relevant recovery/scheduler cycles. Multi-node acceptance separate.

Log only bounded correlation IDs/error classes/SQLSTATE with restricted access. No passwords/tokens/OTP/raw email/phone/user payload/VNPay signatures/authorization headers. Disable inherited SQL/bind TRACE and redact gateway query strings/provider callbacks; inspect failed SQL/HTTP/provider logs privately. Payment clientIP/IDs and UserController exception paths need minimization. Focused Auth/OTP/Media/Payment review found sanitized operation logs, not exhaustive privacy guarantee. Public evidence aliases/results only.
