package com.fintech.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SystemMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong kafkaConsumerLag = new AtomicLong(0);
    private final AtomicLong dbConnectionPoolActive = new AtomicLong(0);
    private final AtomicLong kafkaProducerRecordSendTotal = new AtomicLong(0);

    public SystemMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("jvm_memory_used_bytes", this, SystemMetrics::getJvmMemoryUsed)
                .tag("area", "heap")
                .description("JVM memory used")
                .register(meterRegistry);

        Gauge.builder("jvm_memory_max_bytes", this, SystemMetrics::getJvmMemoryMax)
                .tag("area", "heap")
                .description("JVM memory max")
                .register(meterRegistry);

        Gauge.builder("kafka_consumer_lag", kafkaConsumerLag, AtomicLong::get)
                .description("Kafka consumer lag")
                .register(meterRegistry);

        Gauge.builder("database_connection_pool_active", dbConnectionPoolActive, AtomicLong::get)
                .tag("source", "postgres")
                .description("Active database connections")
                .register(meterRegistry);

        Counter.builder("kafka_producer_record_send_total")
                .description("Total Kafka records sent")
                .register(meterRegistry);

        Gauge.builder("http_server_requests_seconds_count", this, m -> 0.0)
                .description("HTTP server requests")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 15000)
    public void collectSystemMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        Gauge.builder("jvm_heap_used_bytes", heapUsage, MemoryUsage::getUsed)
                .register(meterRegistry);
        Gauge.builder("jvm_heap_max_bytes", heapUsage, MemoryUsage::getMax)
                .register(meterRegistry);

        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        Gauge.builder("jvm_nonheap_used_bytes", nonHeapUsage, MemoryUsage::getUsed)
                .register(meterRegistry);

        Runtime runtime = Runtime.getRuntime();
        Gauge.builder("jvm_threads_live", runtime, Runtime::availableProcessors)
                .register(meterRegistry);
    }

    private double getJvmMemoryUsed() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        return memory.getHeapMemoryUsage().getUsed();
    }

    private double getJvmMemoryMax() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        return memory.getHeapMemoryUsage().getMax();
    }

    public void updateKafkaConsumerLag(long lag, String topic, int partition) {
        kafkaConsumerLag.set(lag);
    }

    public void updateDbConnectionPool(int active) {
        dbConnectionPoolActive.set(active);
    }

    public void incrementKafkaProducerSends() {
        kafkaProducerRecordSendTotal.incrementAndGet();
    }
}