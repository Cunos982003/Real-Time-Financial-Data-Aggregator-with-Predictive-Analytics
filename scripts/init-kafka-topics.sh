#!/bin/bash
set -e

KAFKA_HOST="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
TOPICS=("raw-ticks" "market-events" "features" "candles")
PARTITIONS=32
REPLICATION_FACTOR=1

echo "Creating Kafka topics on $KAFKA_HOST..."

for topic in "${TOPICS[@]}"; do
    if kafka-topics --bootstrap-server "$KAFKA_HOST" --list | grep -q "^${topic}$"; then
        echo "Topic '$topic' already exists, skipping..."
    else
        kafka-topics --bootstrap-server "$KAFKA_HOST" \
            --create \
            --topic "$topic" \
            --partitions "$PARTITIONS" \
            --replication-factor "$REPLICATION_FACTOR" \
            --config retention.ms=604800000 \
            --config cleanup.policy=delete
        echo "Created topic: $topic"
    fi
done

echo "Listing all topics:"
kafka-topics --bootstrap-server "$KAFKA_HOST" --list

echo "Kafka topics initialized successfully."