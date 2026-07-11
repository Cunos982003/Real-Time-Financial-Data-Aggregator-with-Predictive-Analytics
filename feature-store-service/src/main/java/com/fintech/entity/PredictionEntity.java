package com.fintech.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "predictions", indexes = {
        @Index(name = "idx_predictions_symbol_timestamp", columnList = "symbol, timestamp DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private Instant timestamp;

    private Integer prediction;
    private BigDecimal confidence;
    private BigDecimal probabilityUp;
    private BigDecimal probabilityDown;
    private String modelVersion;
    private BigDecimal latestPrice;
    private Instant featureTimestamp;
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}