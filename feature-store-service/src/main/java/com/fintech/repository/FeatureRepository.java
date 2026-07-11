package com.fintech.repository;

import com.fintech.entity.FeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeatureRepository extends JpaRepository<FeatureEntity, Long> {

    Optional<FeatureEntity> findTopBySymbolOrderByTimestampDesc(String symbol);

    List<FeatureEntity> findBySymbolAndTimestampBetweenOrderByTimestampDesc(
            String symbol, Instant start, Instant end);

    @Query("SELECT f FROM FeatureEntity f WHERE f.symbol = :symbol ORDER BY f.timestamp DESC LIMIT :limit")
    List<FeatureEntity> findLatestBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

    @Query("SELECT f FROM FeatureEntity f WHERE f.symbol = :symbol " +
            "AND f.timestamp >= :since ORDER BY f.timestamp ASC")
    List<FeatureEntity> findBySymbolSince(@Param("symbol") String symbol, @Param("since") Instant since);

    void deleteByTimestampBefore(Instant cutoff);
}