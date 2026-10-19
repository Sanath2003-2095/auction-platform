package com.auction.service;

import com.auction.domain.AuctionStatus;
import com.auction.dto.Dtos.StatsDto;
import com.auction.repository.AuctionRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private record Counts(long active, long bids, long users) {}

    // Every page view hits this endpoint, so the three COUNT queries are cached in memory for 5 seconds.
    private final LoadingCache<String, Counts> cache;

    public StatsService(AuctionRepository auctions, BidRepository bids, UserRepository users) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .maximumSize(1)
                .build(k -> new Counts(auctions.countByStatus(AuctionStatus.ACTIVE), bids.count(), users.count()));
    }

    public StatsDto stats() {
        Counts c = cache.get("counts");
        // serverTime is never cached: the client uses it to correct its clock for countdowns.
        return new StatsDto(c.active(), c.bids(), c.users(), Instant.now());
    }
}
