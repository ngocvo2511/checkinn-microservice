"""Exercise the actual Hotel cleanup scheduler on synthetic expired holds only."""
import importlib.util
import json
import time
from pathlib import Path

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('smoke',ROOT/'booking-gateway-smoke.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
h.EVIDENCE=h.EVIDENCE.with_name('expiry-results.json')
h.verify()
customer,token=h.actor()
owner,_=h.actor('OWNER')
hotel,room=h.fixture(owner,1)
request=h.body(hotel,room)
status,booking=h.http('POST','/api/bookings',request,token)
h.check(status==201,'Expiry fixture created through Gateway',http=status)
hold=h.sql('booking',f"SELECT hold_id FROM bookings WHERE id='{booking['id']}';")
h.sql('hotel',f"UPDATE room_holds SET expires_at=now()-interval '1 second' WHERE id='{hold}';")
deadline=time.monotonic()+90
while time.monotonic()<deadline:
    state=h.sql('hotel',f"SELECT status FROM room_holds WHERE id='{hold}';")
    if state=='RELEASED':break
    time.sleep(2)
h.check(state=='RELEASED' and all(x['held']==0 and x['booked']==0 for x in h.counters(room)),
        'Actual Hotel expiry scheduler releases original hold without negative counters',counters=h.counters(room))
before=h.holds(room)
status,_=h.http('DELETE','/api/bookings/'+booking['id'],None,token)
h.check(status==204 and h.holds(room)==before and all(x['held']==0 for x in h.counters(room)),
        'Cancel after scheduler release is safe and does not double release',http=status)
print(json.dumps({'passed':len(h.RESULTS),'failed':0}),flush=True)
