"""Opt-in actual JPA query-count/dated-inventory test against verified clones only."""
import importlib.util
import json
import os
import re
import socket
import subprocess
import sys
import time
from pathlib import Path

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('h', root / 'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    label = sys.argv[1] if len(sys.argv) > 1 else 'availability-range-integration'
    assert re.fullmatch('[a-z0-9-]+', label)
    suite = sys.argv[2] if len(sys.argv) > 2 else 'range'
    test = {'range': 'AvailabilityRangeIntegrationTest', 'location': 'LocationSearchIntegrationTest',
            'candidates': 'HotelOfferCandidatesIntegrationTest', 'amenities': 'AmenityCatalogQueryIntegrationTest',
            'revenue': 'RevenueEventOrderingIntegrationTest', 'outbox': 'BookingEventOutboxIntegrationTest',
            'regulations': 'RegulationTransactionIntegrationTest', 'reviews':'ReviewResponseIntegrationTest','content':'HotelContentIntegrationTest','catalog':'CatalogMediaIntegrationTest','amenity-writes':'AmenityWriteIntegrationTest','media-metadata':'MediaMetadataIntegrationTest'}[suite]
    service = {'revenue':'revenue-service','outbox':'booking-service','regulations':'regulations-service'}.get(suite,'hotel-service')
    module = service
    database = {'revenue':'checkinn_revenue','outbox':'checkinn_booking','regulations':'checkinn_regulations'}.get(suite,'checkinn_hotel')
    h.verify_running_clone(service)
    container = None
    try:
        result = subprocess.run(['docker', 'run', '--rm', '-d', '--label', 'checkinn.test.range-transport=true',
            '--network', config['networks']['checkinn']['name'], '-p', '127.0.0.1::15432',
            '--mount', 'type=bind,source=' + str(root / 'TestTransportForward.java') + ',target=/tmp/TestTransportForward.java,readonly',
            '--entrypoint', 'java', 'maven:3.9.9-eclipse-temurin-21', '/tmp/TestTransportForward.java'],
            capture_output=True, text=True, timeout=240)
        if result.returncode:
            raise RuntimeError('Clone test transport startup failed')
        container = result.stdout.strip()
        assert re.fullmatch('[a-f0-9]{64}', container)
        port = h.run(['docker', 'port', container, '15432/tcp']).rsplit(':', 1)[1]
        deadline = time.monotonic() + 45
        while True:
            try:
                with socket.create_connection(('127.0.0.1', int(port)), timeout=2):
                    break
            except OSError:
                if time.monotonic() > deadline:
                    raise RuntimeError('Clone test transport readiness timeout')
                time.sleep(.25)
        env = dict(os.environ)
        env.update({'CHECKINN_RANGE_TEST': 'true',
            'CHECKINN_RANGE_DB_URL': 'jdbc:postgresql://127.0.0.1:' + port + '/' + database,
            'CHECKINN_RANGE_DB_PASSWORD': config['services'][service]['environment']['SPRING_DATASOURCE_PASSWORD']})
        maven = os.environ.get('MAVEN_CMD') or str(Path.home() / 'Downloads/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd')
        with (root.parent.parent / 'logs' / (label + '.log')).open('w', encoding='utf-8') as log:
            result = subprocess.run([maven, '-o', '-B', '-ntp', '-pl', module,
                '-Dtest=' + test, 'test'], cwd=root.parent, env=env,
                stdout=log, stderr=subprocess.STDOUT, timeout=900)
        print(json.dumps({'suite': label, 'exit': result.returncode}), flush=True)
        return result.returncode
    finally:
        if container:
            assert h.run(['docker', 'inspect', '--format', '{{index .Config.Labels "checkinn.test.range-transport"}}', container]) == 'true'
            h.run(['docker', 'rm', '-f', container])


if __name__ == '__main__':
    raise SystemExit(main())
