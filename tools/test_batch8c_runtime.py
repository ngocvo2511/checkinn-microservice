"""Guards must reject occupied ports, reused PIDs, and foreign processes."""
import importlib.util,socket,unittest
from pathlib import Path
from unittest.mock import patch
s=importlib.util.spec_from_file_location('runtime',Path(__file__).with_name('batch8c-runtime.py'));r=importlib.util.module_from_spec(s);s.loader.exec_module(r)
class OwnershipTest(unittest.TestCase):
 def test_headroom_exhaustion_never_starts(self):
  diagnostic=dict(status='PASS',runtimeRoleCap=4,backendActivity=[dict(role='checkinn_user_app',backendRows=4)])
  with patch.object(r.stack,'values',return_value={'USER_DB_POOL_MAX':'2'}),patch.object(r.d,'diagnostic',return_value=diagnostic),patch.object(r.d,'write'),patch.object(r.time,'sleep'):
   with self.assertRaisesRegex(RuntimeError,'INSUFFICIENT_ROLE_HEADROOM'):r.connection_headroom('user')
 def test_occupied_port_rejected(self):
  with socket.socket() as sock:
   sock.bind(('127.0.0.1',0));sock.listen()
   with self.assertRaises(OSError):r.assert_port_free(sock.getsockname()[1])
 def test_reused_pid_rejected(self):
  p=dict(pid=1,port=8082,creationDate='original')
  command=str(r.stack.PRIVATE)+' --server.port=8082 --batch8c-owned=true'
  with patch.object(r,'process',return_value=dict(CommandLine=command,CreationDate='new')):
   with self.assertRaisesRegex(AssertionError,'PID reused'):r.assert_owned('user',p)
 def test_foreign_pid_rejected(self):
  with patch.object(r,'process',return_value=dict(CommandLine='unrelated',CreationDate='original')):
   with self.assertRaisesRegex(AssertionError,'Unverified PID'):r.assert_owned('user',dict(pid=1,port=8082,creationDate='original'))
 def test_foreign_state_never_shutdown(self):
  with patch.object(r.stack,'state',return_value={'processes':{'user':dict(pid=1,port=8082)}}),patch.object(r.subprocess,'run') as call:
   with self.assertRaises(AssertionError):r.stop('user')
   call.assert_not_called()
 def test_exited_owned_process_removed_without_kill(self):
  state={'processes':{'user':dict(pid=1,port=8082,creationDate='original',batch8cOwned=True)}}
  with patch.object(r.stack,'state',return_value=state),patch.object(r,'process',return_value=None),patch.object(r.stack,'save') as save,patch.object(r.subprocess,'run') as call:
   r.stop('user');call.assert_not_called();self.assertEqual(state['processes'],{});save.assert_called_once()
if __name__=='__main__':unittest.main()
