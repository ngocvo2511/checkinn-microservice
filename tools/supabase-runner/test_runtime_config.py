"""Configuration regression tests; no processes, Docker or database connections."""
import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('local_stack', Path(__file__).with_name('run-local-stack.py'))
local = importlib.util.module_from_spec(spec)
spec.loader.exec_module(local)


class RuntimeConfigTest(unittest.TestCase):
    def test_compose_preparation_only_attaches_mailpit_during_startup(self):
        docker_spec = importlib.util.spec_from_file_location('docker_stack', Path(__file__).with_name('run-docker-stack.py'))
        stack = importlib.util.module_from_spec(docker_spec)
        docker_spec.loader.exec_module(stack)
        for starting in (False, True):
            with self.subTest(starting=starting), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                runtime = dict(SECRET_KEY='s' * 32, NEXTAUTH_SECRET='synthetic', CHECKINN_ENVIRONMENT='supabase')
                values = dict(runtime, SUPABASE_DEV_DATABASES_READY='true', SUPABASE_DEV_INFRA_ISOLATED='true')
                state = dict(prepared=True, session='synthetic', composeProject='synthetic',
                             rabbitContainer='rabbit', redisContainer='redis')

                def docker(*args):
                    if args[:2] == ('network', 'connect'):
                        return ''
                    if 'NetworkSettings' in args[2]:
                        return '{}'
                    return 'healthy' if 'Health' in args[2] else 'synthetic'

                with patch.object(stack, 'ROOT', root), patch.object(stack, 'COMPOSE', root / 'compose.json'), \
                     patch.object(stack.local, 'OWNERS', ()), patch.object(stack.local, 'MODULES', {}), \
                     patch.object(stack.local, 'values', return_value=values), \
                     patch.object(stack.local, 'runtime_values', return_value=runtime), \
                     patch.object(stack.local, 'state', return_value=state), \
                     patch.object(stack.local, 'docker', side_effect=docker) as operation:
                    stack.prepare(require_healthy=starting)
                    connections = [call for call in operation.call_args_list if call.args[:2] == ('network', 'connect')]
                    self.assertEqual(len(connections), int(starting))

    def test_host_runtime_never_enables_refunds_without_explicit_configuration(self):
        for configured, expected in [(None, 'false'), ('false', 'false'), ('true', 'true')]:
            with self.subTest(configured=configured), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                target = root / 'booking-service/target'
                target.mkdir(parents=True)
                (target / 'booking.jar').touch()
                values = dict(SECRET_KEY='synthetic', SUPABASE_DEV_DATABASES_READY='true',
                              SUPABASE_DEV_INFRA_ISOLATED='true', SUPABASE_DEV_REDIS_PORT='6388',
                              SUPABASE_DEV_RABBIT_PORT='5678', SUPABASE_DEV_RABBIT_USERNAME='synthetic',
                              SUPABASE_DEV_RABBIT_PASSWORD='synthetic', SUPABASE_DEV_RABBIT_VHOST='test')
                if configured is not None:
                    values['REFUND_INTERNAL_ENABLED'] = configured
                state = dict(prepared=True, processes={}, composeProject='synthetic',
                             session='synthetic', rabbitContainer='rabbit', redisContainer='redis',
                             ports={'booking': 8084})

                def docker(*args):
                    if args[0] == 'ps':
                        return ''
                    return 'healthy' if 'Health' in args[2] else 'synthetic'

                with patch.object(local, 'ROOT', root), patch.object(local, 'PRIVATE', root), \
                     patch.object(local, 'state', return_value=state), \
                     patch.object(local, 'values', return_value=values), \
                     patch.object(local, 'docker', side_effect=docker), patch.object(local, 'save'), \
                     patch.object(local, 'sandbox_environment', return_value={}), \
                     patch.object(local, 'clean_parent_env', return_value={}), \
                     patch.object(local.subprocess, 'Popen', return_value=SimpleNamespace(pid=42)) as process:
                    local.start(['booking'])
                    self.assertEqual(process.call_args.kwargs['env']['REFUND_INTERNAL_ENABLED'], expected)


if __name__ == '__main__':
    unittest.main()
