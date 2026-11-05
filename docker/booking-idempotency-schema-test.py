"""Apply/replay additive idempotency expansion to verified running clones only."""
import importlib.util
from pathlib import Path

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('security', root / 'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)

for service, database, table, filename, columns, index in [
    ('booking-service', 'checkinn_booking', 'bookings', 'booking-idempotency-20261105.sql',
     ['idempotency_key_hash', 'idempotency_request_hash'], 'bookings_idempotency_key_hash_unique'),
    ('hotel-service', 'checkinn_hotel', 'room_holds', 'hold-idempotency-20261105.sql',
     ['command_id'], 'room_holds_command_id_unique'),
]:
    h.verify_running_clone(service)
    fingerprint = "SELECT count(*) || '|' || coalesce(md5(string_agg(md5((to_jsonb(t) - 'idempotency_key_hash' - 'idempotency_request_hash' - 'command_id')::text),'' ORDER BY id)),'empty') FROM " + table + " t"
    before = h.sql(database, fingerprint)
    for _ in range(2):
        h.run(h.COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                          '-U', 'checkinn_test', '-d', database], (root / 'sql' / filename).read_text())
    after = h.sql(database, fingerprint)
    if before != after:
        raise RuntimeError('Historical row fingerprint changed during expansion')
    for column in columns:
        check = h.sql(database, "SELECT is_nullable || '|' || coalesce(column_default,'NONE') FROM information_schema.columns WHERE table_name='" + table + "' AND column_name='" + column + "'")
        if check != 'YES|NONE':
            raise RuntimeError('Expansion must stay nullable and have no default')
    if h.sql(database, "SELECT indisunique AND indisvalid FROM pg_index WHERE indexrelid=to_regclass('" + index + "')") != 't':
        raise RuntimeError('Idempotency uniqueness index is missing/invalid')
    print(service + ': migration replay, historical fingerprint, nullable/no-default and uniqueness PASS', flush=True)
