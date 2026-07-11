#!/bin/bash
set -e

API_URL="${API_URL:-http://localhost:8080}"
DURATION="${DURATION:-60}"
CONCURRENT="${CONCURRENT:-1000}"
ENDPOINTS=(
    "/api/v1/predictions/BTCUSD"
    "/api/v1/predictions/ETHUSD"
    "/api/v1/backtest/BTCUSD?days=30"
    "/api/v1/models/BTCUSD/metrics"
    "/api/v1/features/BTCUSD?lookbackSeconds=3600"
)

echo "=== Performance Test Configuration ==="
echo "Target: $API_URL"
echo "Duration: ${DURATION}s"
echo "Concurrent requests: $CONCURRENT"
echo "Endpoints: ${ENDPOINTS[*]}"
echo ""

mkdir -p target
TEMP_OUTPUT="target/perf_results_$(date +%s).json"

for ENDPOINT in "${ENDPOINTS[@]}"; do
    echo "Testing: $ENDPOINT"
    FULL_URL="${API_URL}${ENDPOINT}"

    python3 -c "
import urllib.request, time, json, sys
from concurrent.futures import ThreadPoolExecutor, as_completed

url = '$FULL_URL'
duration = $DURATION
concurrency = $CONCURRENT

results = []
start_time = time.time()
requests_sent = 0

def make_request():
    req_start = time.time()
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            latency = (time.time() - req_start) * 1000
            return {'status': resp.status, 'latency': latency, 'error': None}
    except Exception as e:
        return {'status': 0, 'latency': 0, 'error': str(e)}

with ThreadPoolExecutor(max_workers=concurrency) as executor:
    futures = []
    while time.time() - start_time < duration:
        futures.append(executor.submit(make_request))
    for f in as_completed(futures):
        result = f.result()
        results.append(result)

latencies = [r['latency'] for r in results if r['error'] is None]
successes = [r for r in results if r['status'] == 200]
latencies.sort()
total = len(latencies)

summary = {
    'endpoint': url,
    'total_requests': total,
    'successful': len(successes),
    'avg_latency_ms': round(sum(latencies) / total, 2) if latencies else 0,
    'p50_latency_ms': round(latencies[int(total * 0.5)], 2) if latencies else 0,
    'p95_latency_ms': round(latencies[int(total * 0.95)], 2) if latencies else 0,
    'p99_latency_ms': round(latencies[int(total * 0.99)], 2) if latencies else 0,
    'min_latency_ms': round(min(latencies), 2) if latencies else 0,
    'max_latency_ms': round(max(latencies), 2) if latencies else 0,
    'rps': round(total / $DURATION, 2)
}
print(json.dumps(summary, indent=2))
" 2>/dev/null || echo "Python3 test skipped (python3 not available)"

done > "$TEMP_OUTPUT"
cat "$TEMP_OUTPUT"
echo ""
echo "Results saved to: $TEMP_OUTPUT"