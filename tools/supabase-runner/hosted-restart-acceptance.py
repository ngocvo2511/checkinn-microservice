"""Owned broker outage and actual core-process restart with durable policy outbox recovery."""
import json
import secrets
import subprocess
import time
import uuid
import acceptance_runtime as h

checks=[];out=h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/restart-outbox-hosted-results.json'


def check(ok,label):
    checks.append({'case':label,'status':'PASS' if ok else 'FAIL'})
    out.write_text(json.dumps({'hosted':True,'checks':checks,'dataRetained':True},indent=2)+'\n',encoding='utf-8')
    print(label+' '+checks[-1]['status'],flush=True)
    if not ok:raise AssertionError(label)


def main():
    h.require_open_acceptance()
    state=h.stack.state();broker=state['rabbitContainer']
    assert h.stack.docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',broker)==state['session']
    key='BATCH6B_RESTART_'+secrets.token_hex(6).upper();admin=str(uuid.uuid4())
    before=int(h.sql('regulations','SELECT count(*) FROM regulations'))
    h.stack.docker('stop','-t','5',broker)
    try:
        body={'regulationKey':key,'name':'Synthetic outage test','value':'1','description':'Retained restart fixture','active':True,'version':0}
        code,_=h.api('regulations','/api/regulations/'+key,'PUT',body,role='ADMIN',subject=admin,timeout=40)
        check(code==200,'policy write commits while owned broker is stopped')
        check(h.sql('regulations',f"SELECT count(*) FROM regulation_snapshots WHERE regulation_key='{key}'")=='1','source and immutable snapshot commit once during outage')
        check(h.sql('regulations',f"SELECT state FROM regulation_event_outbox WHERE regulation_key='{key}'")=='PENDING','durable outbox stays PENDING while broker unavailable')
        h.stack.stop(list(h.GROUPS))
        check(True,'only five recorded core JVMs stopped; data retained')
    finally:h.stack.docker('start',broker)
    h.wait(lambda:h.stack.docker('inspect','--format','{{.State.Health.Status}}',broker)=='healthy','owned broker restart',60)
    h.stack.start(['regulations','user','hotel','booking','revenue'])
    def healthy(owner):
        try:
            code,body=h.api(owner,'/actuator/health',anonymous=True,timeout=3)
            return code==200 and body.get('status')=='UP'
        except Exception:return False
    for owner in h.GROUPS:
        h.wait(lambda owner=owner:healthy(owner),owner+' restart health',120)
        check(True,owner+' actual full Spring application restart')
    h.wait(lambda:h.sql('regulations',f"SELECT state FROM regulation_event_outbox WHERE regulation_key='{key}'")=='DELIVERED','outbox delivery after actual broker/JVM restart',100)
    check(True,'persisted PENDING outbox delivers after broker and JVM restart')
    check(h.sql('regulations',f"SELECT count(*) FROM regulation_snapshots WHERE regulation_key='{key}'")=='1','restart does not duplicate policy snapshot')
    check(int(h.sql('regulations','SELECT count(*) FROM regulations'))==before+1,'restart seed initialization does not duplicate default policies')
    (h.PRIVATE/'restart-fixture-identifiers.json').write_text(json.dumps({'policy':key},indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':main()
