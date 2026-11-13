"""Cross-database deadline/policy verification with actual persisted API readback."""
import datetime
import decimal
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('smoke',ROOT/'booking-gateway-smoke.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
h.EVIDENCE=h.EVIDENCE.with_name('final-sql-results.json')
h.verify()
customer,token=h.actor()
owner,_=h.actor('OWNER')
hotel,room=h.fixture(owner,1)
status,created=h.http('POST','/api/bookings',h.body(hotel,room),token)
h.check(status==201,'Deadline fixture created through Gateway',http=status)
bid=created['id']
# Booking LocalDateTime uses Hibernate JDBC Asia/Ho_Chi_Minh convention;
# Hotel expiry is timestamptz. Never interpret the raw Booking wall clock as UTC.
values=h.sql('booking',f"SELECT hold_id,extract(epoch FROM hold_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh'),extract(epoch FROM check_in_at-free_cancellation_until),extract(hour FROM check_in_at AT TIME ZONE 'Asia/Ho_Chi_Minh') FROM bookings WHERE id='{bid}';").split('|')
hotel_epoch=decimal.Decimal(h.sql('hotel',f"SELECT extract(epoch FROM expires_at) FROM room_holds WHERE id='{values[0]}';"))
status,persisted=h.http('GET','/api/bookings/'+bid,None,token)
def epoch(value):return decimal.Decimal(str(datetime.datetime.fromisoformat(value.replace('Z','+00:00')).timestamp()))
h.check(status==200 and abs(decimal.Decimal(values[1])-hotel_epoch)<=decimal.Decimal('.001')
        and abs(epoch(created['holdExpiresAt'])-hotel_epoch)<=decimal.Decimal('.001')
        and abs(epoch(persisted['holdExpiresAt'])-hotel_epoch)<=decimal.Decimal('.001'),
        'Hotel expiry matches Booking SQL, create response and persisted Gateway readback',
        booking_sql_timestamp_zone='Asia/Ho_Chi_Minh (Hibernate JDBC configuration)',tolerance_ms=1)
h.check(decimal.Decimal(values[2])==86400 and decimal.Decimal(values[3])==14,
        'Policy snapshot is 14:00 Vietnam check-in with 24-hour cancellation deadline')
h.check(h.http('DELETE','/api/bookings/'+bid,None,token)[0]==204,'Deadline fixture cancelled through Gateway')
h.check(h.sql('hotel','SELECT count(*) FROM room_availability a JOIN room_types r ON r.id=a.room_type_id WHERE held<0 OR booked<0 OR held+booked>total_rooms;')=='0',
        'Final SQL global inventory invariant has zero violations')
h.check(all(h.sql(db,"SELECT count(*) FROM pg_trigger WHERE tgname='batch2_failure';")=='0' for db in ('hotel','booking')),
        'No test fault triggers remain in either database')
print(json.dumps({'passed':len(h.RESULTS),'failed':0}),flush=True)
