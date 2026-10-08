#!/usr/bin/env python3
"""End-to-end check of a running Compose stack. Releases demo memory at the end."""
import json
import time
import urllib.request
import urllib.error
import urllib.parse


def request(port, path, method='GET'):
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', method=method)
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def data(port, path):
    status, body = request(port, path)
    assert status == 200, (path, status, body)
    return json.loads(body)


def wait_for(check, label, timeout=120):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            if check():
                print('PASS:', label, flush=True)
                return
        except (OSError, ValueError, AssertionError) as error:
            last = error
        time.sleep(2)
    raise AssertionError(f'Timeout: {label}; last error: {last}')


def query(expression):
    return data(9090, '/api/v1/query?' + urllib.parse.urlencode({'query': expression}))['data']['result']


wait_for(lambda: data(8080, '/actuator/health')['status'] == 'UP', 'application health')
for path, expected in [('/api/ok', 200), ('/api/not-found', 404), ('/api/error', 500)]:
    assert request(8080, path)[0] == expected
print('PASS: HTTP 200, 404, 500', flush=True)
before = query('demo_controller_calls_total')
count = float(before[0]['value'][1]) if before else 0
for _ in range(3):
    assert request(8080, '/api/counted')[0] == 200
wait_for(lambda: any(float(x['value'][1]) >= count + 3 for x in query('demo_controller_calls_total')), 'custom counter scraped')
wait_for(lambda: any(float(x['value'][1]) == 1 for x in query('up{job="spring"}')), 'Spring scrape')
for code in ['200', '404', '500']:
    wait_for(lambda: bool(query('http_server_requests_seconds_count{uri=~"/api/.*",status="' + code + '"}')), 'HTTP metric ' + code)
assert query('jvm_memory_used_bytes{area="heap"}')
wait_for(lambda: data(3000, '/api/health')['database'] == 'ok', 'Grafana health')
wait_for(lambda: data(3000, '/api/datasources/uid/prometheus/health')['status'] == 'OK', 'Grafana datasource')
dashboard = data(3000, '/api/dashboards/uid/memory-demo')['dashboard']
assert len(dashboard['panels']) == 6
for panel in dashboard['panels']:
    for target in panel['targets']:
        expression = target['expr']
        result = data(3000, '/api/datasources/proxy/uid/prometheus/api/v1/query?' + urllib.parse.urlencode({'query': expression}))
        assert result['status'] == 'success'
print('PASS: provisioned dashboard and all panel queries through Grafana', flush=True)
wait_for(lambda: bool(query('ALERTS{alertname="HighRetainedMemory",alertstate="firing"}')), 'memory alert FIRING')
assert data(8080, '/api/memory')['retainedBytes'] == 256 * 1024 * 1024
assert request(8080, '/api/memory', 'DELETE')[0] == 200
wait_for(lambda: not query('ALERTS{alertname="HighRetainedMemory"}'), 'memory alert resolved after release')
print('All checks passed. Restart app to repeat the memory demonstration.', flush=True)
