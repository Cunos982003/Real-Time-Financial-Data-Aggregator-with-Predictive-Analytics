#!/bin/bash
set -e

SYMBOLS="${SYMBOLS:-BTCUSD,ETHUSD,XRPUSD}"
DURATION="${DURATION:-3600}"
FREQUENCY="${FREQUENCY:-1}"

IFS=',' read -ra SYM_ARRAY <<< "$SYMBOLS"

echo "Generating test market data for symbols: ${SYM_ARRAY[*]}"
echo "Duration: ${DURATION}s, Frequency: ${FREQUENCY}s"

BASE_PRICES=(
    ["BTCUSD"]=42150.0
    ["ETHUSD"]=2280.0
    ["XRPUSD"]=0.62
    ["SOLUSD"]=98.5
    ["ADAUSD"]=0.58
)

for SYM in "${SYM_ARRAY[@]}"; do
    BASE="${BASE_PRICES[$SYM]:-100.0}"
    echo "Generating data for $SYM (base: $BASE)..."
    python3 -c "
import random, time, json, sys
base = $BASE
sym = '$SYM'
duration = $DURATION
freq = $FREQUENCY
start = time.time()
while time.time() - start < duration:
    drift = random.gauss(0, 0.001)
    base = base * (1 + drift)
    tick = {
        'symbol': sym,
        'price': round(base, 4),
        'volume': round(random.uniform(0.1, 10), 4),
        'bid': round(base * 0.9998, 4),
        'ask': round(base * 1.0002, 4),
        'timestamp': time.time()
    }
    print(json.dumps(tick), flush=sys.stdout.flush())
    time.sleep(freq)
" 2>/dev/null &
done

echo "Test data generation started. PID: $!"
echo "Press Ctrl+C or wait for ${DURATION}s to stop."