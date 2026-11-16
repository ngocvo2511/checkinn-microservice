# Backup/restore plan

All five actual plan/tier/retention/PITR/oldest recovery timestamp/backup success/quota/encryption controls UNKNOWN / NOT VERIFIED. No backup/download/restore6A. [Official backups](https://supabase.com/docs/guides/platform/backups), checked14/11/2026: eligibility/configuration varies; project existence does not prove PITR. Database backup excludes Storage object bytes; custom-role passwords need separate recovery management.

Per-project administrator records actual capabilities, last backup/time/retention, restore permissions/destinations/outage estimate. Assign platform recovery operator, incident lead and User/Loyalty, Hotel, Booking/Payment, Revenue, Regulations owners. Numeric RPO/RTO per domain and end-to-end payment journey need owner agreement; none invented. Financial owner signs reconciliation.

Proposed logical backups: verified direct/session endpoint, pg_dump client compatible with actual server major, separate backup login with adequate SELECT/sequence/history rights and RLS/coverage review. App role cannot read all history/data, so not backup role. Encrypted custom-format business dump plus schema-only export, role/owner/ACL/extension/sequence/history manifest; no unreviewed dump/restore of managed global roles. Record source/checksums/config-secret references, broker checkpoints, pending commands/outbox/event UUID/source versions, provider window. Secure service/env injection; no password URI CLI. No backup command executed.

Encrypted transit/at-rest storage and controlled keys, off-project access policy/audit/MFA, approved retention/deletion; least-privilege restore operator. Platform controls/region compliance NOT VERIFIED. Media objects have separate storage backup/recovery policy. Backups contain sensitive data; never publish dumps.

Future restore drill6B or separate authorized batch:

1. Approve isolated destination and access. Never create extra Supabase project automatically or overwrite originals. Owned disposable local PostgreSQL matching major can prove logical restore, not platform PITR.
2. Coordinate writer/consumer freeze; record cross-project checkpoint/event IDs/versions/ledger commands/receipts/holds/quarantine. Five snapshots are not atomic distributed snapshot.
3. Collect encrypted checksummed backup/schema/security/sequence manifests; preserve original sources.
4. Restore isolated with reviewed owner/managed-object mapping and separately recovered secrets; validate Storage bytes independently.
5. Compare46 tables/458 columns/constraints/indexes/sequences/identity/history/checksum/ACL/RLS; Hibernate validate, synthetic workflow/security tests, isolated broker replay dedup; reconcile payment/refund/loyalty/inventory/revenue.
6. Measure time/data gap against approved RTO/RPO. File existence is not successful recovery. Failed drill blocks traffic; preserve forensic evidence.

Post-write restore one project can invalidate other projections/commands/receipts. Freeze affected services, quarantine callbacks durably, restore isolated and reconcile before switching routes. No destructive in-place default rollback.
