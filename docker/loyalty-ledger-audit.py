"""Read-only clone audit. Private identifiers stay in ignored docker/logs, never stdout."""
import importlib.util
import json
import subprocess
from pathlib import Path

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('user-service')
    query = """
      BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
      WITH duplicate_accounts AS (
        SELECT user_id,count(*) AS rows FROM loyalty_points GROUP BY user_id HAVING count(*)>1
      ), duplicate_commands AS (
        SELECT booking_id,transaction_type,count(*) AS rows
        FROM points_transactions WHERE transaction_type IN ('EARN','USE')
        GROUP BY booking_id,transaction_type HAVING count(*)>1
      ), missing_receipts AS (
        SELECT t.id,t.user_id,t.booking_id,t.transaction_type,t.points FROM points_transactions t
        WHERE t.transaction_type IN ('EARN','USE') AND NOT EXISTS (
          SELECT 1 FROM loyalty_commands c WHERE c.booking_id=t.booking_id
          AND c.command_type=t.transaction_type AND c.user_id=t.user_id)
      ), journal AS (
        SELECT user_id,sum(CASE WHEN transaction_type='EARN' THEN points
          WHEN transaction_type='REVERSE_EARN' THEN -points ELSE 0 END) AS earned,
          sum(CASE WHEN transaction_type IN ('USE','REVERSE_USE') THEN points ELSE 0 END) AS used
        FROM points_transactions GROUP BY user_id
      ), opening_differences AS (
        SELECT p.user_id,p.total_points,p.used_points,coalesce(j.earned,0) AS journal_earned,
          coalesce(j.used,0) AS journal_used FROM loyalty_points p LEFT JOIN journal j ON j.user_id=p.user_id
        WHERE p.total_points<>coalesce(j.earned,0) OR p.used_points<>coalesce(j.used,0)
      ) SELECT json_build_object(
        'duplicate_accounts', (SELECT coalesce(json_agg(d),'[]') FROM duplicate_accounts d),
        'duplicate_earn_use', (SELECT coalesce(json_agg(d),'[]') FROM duplicate_commands d),
        'earn_use_without_receipt', (SELECT coalesce(json_agg(d),'[]') FROM missing_receipts d),
        'unverified_opening_differences', (SELECT coalesce(json_agg(d),'[]') FROM opening_differences d)
      ); ROLLBACK;
    """
    result = subprocess.run(h.COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-U', 'checkinn_test',
                            '-d', 'checkinn_user', '-qAtc', query], capture_output=True, text=True, encoding='utf-8', timeout=45)
    if result.returncode: raise RuntimeError('Read-only clone audit failed')
    details = json.loads(result.stdout)
    report = {'scope': 'checkinn-test user clone only', 'mode': 'read-only repeatable-read',
              'decision': 'REVIEW_REQUIRED; no automatic correction or historical backfill',
              'note': 'Opening differences may include legitimate historical opening balances; provenance is required.',
              'counts': {key: len(rows) for key, rows in details.items()}, 'private_details': details}
    destination = Path(__file__).parent / 'logs' / 'loyalty-ledger-audit-private.json'
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({'audit': 'PASS', 'scope': report['scope'], 'mode': report['mode'], 'counts': report['counts']}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'audit': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
