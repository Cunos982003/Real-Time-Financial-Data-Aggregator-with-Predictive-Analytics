package com.fintech.repository;

import com.fintech.entity.PredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionRepository extends JpaRepository<PredictionEntity, Long> {

    Optional<PredictionEntity> findTopBySymbolOrderByTimestampDesc(String symbol);

    List<PredictionEntity> findBySymbolAndTimestampBetweenOrderByTimestampDesc(
            String symbol, Instant start, Instant end);

    @Query("SELECT p FROM PredictionEntity p WHERE p.symbol = :symbol " +
            "AND p.timestamp >= :since ORDER BY p.timestamp ASC")
    List<PredictionEntity> findBySymbolSince(@Param("symbol") String symbol, @Param("since") Instant since);

    void deleteByTimestampBefore(Instant cutoff);
}