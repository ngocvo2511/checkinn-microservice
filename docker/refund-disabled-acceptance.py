"""Batch 1.6 extra assertions around existing disabled Gateway runner."""
import importlib.util,pathlib,json
spec=importlib.util.spec_from_file_location('disabled',pathlib.Path(__file__).with_name('refund-disabled-smoke.py'));d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
row=d.s.sql('checkinn_smoke_booking',f"SELECT p.status,b.paid_amount,(SELECT count(*) FROM manual_refund_executions WHERE payment_id=p.id),(SELECT count(*) FROM booking_event_outbox WHERE booking_id=b.id AND routing_key='payment.refunded') FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.id='{d.p}'").strip()
assert row=='COMPLETED|1000000.00|0|0',row
assert d.s.sql('checkinn_smoke_revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{d.p}' AND kind='DEBIT'").strip()=='0'
print(json.dumps(dict(case='disabled_final',payment=d.p,state=row,debit=0,result='PASS')))
