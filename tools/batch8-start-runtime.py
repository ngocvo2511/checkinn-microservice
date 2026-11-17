"""Start only necessary guarded local JVMs; local-only metrics and simulated providers."""
import sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
original=h.stack.values
def values():
    v=original();v['SUPABASE_DEV_VNPAY_MODE']='simulated';v['SUPABASE_DEV_STORAGE_MODE']='disabled';return v
h.stack.values=values
parent=h.stack.clean_parent_env
def environment():
    env=parent()
    env.update(JAVA_TOOL_OPTIONS='-Xmx384m -Duser.timezone=UTC',
      SPRING_JPA_PROPERTIES_HIBERNATE_GENERATE_STATISTICS='true',
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE='health,info,metrics',
      LOGGING_LEVEL_ORG_HIBERNATE_STAT='OFF',
      LOGGING_LEVEL_ORG_HIBERNATE_ENGINE_INTERNAL_STATISTICALLOGGINGSESSIONEVENTLISTENER='INFO' if '--sql-metrics' in sys.argv else 'OFF')
    if '--sql-metrics' in sys.argv:
        env['SPRING_APPLICATION_JSON']=json.dumps({'logging':{'level':{'org.hibernate.engine.internal.StatisticalLoggingSessionEventListener':'INFO'}},'spring':{'jpa':{'properties':{'hibernate.generate_statistics':True}}}})
    return env
h.stack.clean_parent_env=environment
h.stack.start([x for x in sys.argv[1:] if x!='--sql-metrics'])
