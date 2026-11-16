"""Prepare one combined read-only SQL snapshot per remaining owner; no connections or mutations."""
from pathlib import Path
import json
import re

ROOT=Path(__file__).resolve().parents[2]


def query(name,alias):
    text=(ROOT/'tools/supabase-runner'/name).read_text(encoding='utf-8')
    start=text.index('SELECT jsonb_build_object(')
    end=text.rindex(' AS '+alias+';')
    return text[start:end]


def main():
    private=ROOT/'.local/batch6b'
    props=dict(line.split('=',1) for line in (private/'targets.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.lstrip().startswith('#'))
    a=query('admin-preflight-readonly.sql','checkinn_readonly_preflight')
    b=query('event-trigger-readonly.sql','checkinn_event_trigger_details')
    out=private/'remaining-projects-readonly'
    out.mkdir(exist_ok=False)
    for owner in ['hotel','booking','revenue','regulations']:
        ref=props[owner+'.project']
        if not re.fullmatch('[a-z]{20}',ref):raise ValueError('Invalid expected project reference')
        sql=f"""-- READ ONLY: {owner}, expected Dashboard project reference {ref}.
-- Check the actual Dashboard URL before running. Expected fields below are operator context,
-- NOT independent SQL proof of Supabase project identity. Keep function definitions private.
-- No role/schema/settings changes; one JSON cell includes catalog AND full event-function metadata.
BEGIN TRANSACTION READ ONLY;
SELECT ({a}) || jsonb_build_object(
  'operatorExpectedOwner','{owner}',
  'operatorExpectedProjectRef','{ref}',
  'eventTriggerDetails',({b})
) AS checkinn_batch_readonly_preflight;
ROLLBACK;
"""
        (out/(owner+'.readonly.sql')).write_text(sql,encoding='utf-8',newline='\n')
    (out/'snapshot-format.json').write_text(json.dumps({'ownerFiles':['hotel','booking','revenue','regulations'],'queryMode':'BEGIN TRANSACTION READ ONLY; SELECT catalogs/definitions; ROLLBACK','outputShape':'one JSON cell per project','saveAs':'.local/batch6b/<owner>.admin-preflight.json','hostedChangesApplied':False},indent=2)+'\n',encoding='utf-8')
    print('Prepared four combined read-only SQL files; no passwords, database access or schema/settings changes.')


if __name__=='__main__':main()
