package com.auction.service;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Mappers;
import com.auction.repository.AuctionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Closes auctions whose end time has passed. The highest bidder at that moment is the winner. */
@Component
public class AuctionCloser {

    private static final Logger log = LoggerFactory.getLogger(AuctionCloser.class);

    private final AuctionRepository auctions;
    private final AuctionEventPublisher events;
    private final AuctionCloser self;

    public AuctionCloser(AuctionRepository auctions, AuctionEventPublisher events, @org.springframework.context.annotation.Lazy AuctionCloser self) {
        this.auctions = auctions;
        this.events = events;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${app.closer.interval-ms:2000}")
    public void closeExpired() {
        List<Long> ids = auctions.findIdsByStatusAndEndTimeBefore(AuctionStatus.ACTIVE, Instant.now());
        for (Long id : ids) {
            try {
                self.closeIfExpired(id).ifPresent(events::closed);
            } catch (ConcurrencyFailureException e) {
                // A last-second bid raced with us; the next tick re-evaluates with the fresh end time.
                log.debug("Auction {} changed while closing, will retry", id);
            } catch (RuntimeException e) {
                log.error("Failed to close auction {}", id, e);
            }
        }
    }

    @Transactional
    public Optional<AuctionDto> closeIfExpired(Long id) {
        Auction a = auctions.findById(id).orElse(null);
        if (a == null || a.getStatus() != AuctionStatus.ACTIVE || a.getEndTime().isAfter(Instant.now())) {
            return Optional.empty();
        }
        a.setStatus(AuctionStatus.CLOSED);
        return Optional.of(Mappers.toDto(auctions.saveAndFlush(a)));
    }
}
