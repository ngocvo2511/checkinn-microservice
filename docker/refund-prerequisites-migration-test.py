"""Test only the additive VNPay request-date migration in a disposable PostgreSQL.

No host ports, credentials, existing databases, Compose stacks or provider calls.
Usage: python docker/refund-prerequisites-migration-test.py
"""
from pathlib import Path
import subprocess
import time
import uuid


def run(*args, sql=None):
    result = subprocess.run(args, input=sql, text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def main():
    name = "checkinn-refund-migration-test-" + uuid.uuid4().hex
    migration = (Path(__file__).parent / "sql/booking-vnpay-create-date-v1-20261112.sql").read_text(encoding="utf-8")
    run("docker", "run", "--detach", "--name", name, "--label", "checkinn.refund-migration-test=true",
        "--env", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:18-alpine")
    try:
        for _ in range(60):
            ready = subprocess.run(["docker", "exec", name, "pg_isready", "-U", "postgres"], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError("Disposable PostgreSQL did not become ready")

        def sql(statement):
            return run("docker", "exec", "-i", name, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-U", "postgres", sql=statement)

        sql("CREATE TABLE payments(id varchar(255) PRIMARY KEY, amount numeric(38,2), status varchar(32), paid_at timestamp);"
            "INSERT INTO payments VALUES ('legacy',1000000,'COMPLETED','2026-11-01 10:00:00');")
        before = sql("SELECT row_to_json(p)::text FROM payments p;")
        sql(migration)
        assert sql("SELECT count(*) FROM payments WHERE id='legacy' AND vnpay_create_date IS NULL;") == "1"
        assert sql("SELECT row_to_json(x)::text FROM (SELECT id,amount,status,paid_at FROM payments) x;") == before
        sql(migration)
        sql("INSERT INTO payments(id,amount,status,vnpay_create_date) VALUES ('new',1000000,'PENDING','20261112141516');")
        assert sql("SELECT vnpay_create_date FROM payments WHERE id='new';") == "20261112141516"
        assert sql("SELECT data_type||':'||character_maximum_length FROM information_schema.columns "
                   "WHERE table_name='payments' AND column_name='vnpay_create_date';") == "character varying:14"
        sql("BEGIN; INSERT INTO payments(id,vnpay_create_date) VALUES ('rolled-back','20261112141516'); ROLLBACK;")
        assert sql("SELECT count(*) FROM payments WHERE id='rolled-back';") == "0"
        sql("DROP TABLE payments; CREATE TABLE payments(id varchar(255) PRIMARY KEY);")
        sql(migration)
        assert sql("SELECT count(*) FROM payments;") == "0"
        print("PASS: legacy preserved/NULL, replay, new date round-trip, varchar(14), rollback, empty-table upgrade")
    finally:
        run("docker", "rm", "--force", name)


if __name__ == "__main__":
    main()
