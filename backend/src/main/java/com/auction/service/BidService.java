package com.auction.service;

import com.auction.dto.Dtos.BidDto;
import com.auction.dto.Dtos.PageDto;
import com.auction.dto.Mappers;
import com.auction.exception.ApiException;
import com.auction.repository.AuctionRepository;
import com.auction.repository.BidRepository;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BidService {

    private static final Logger log = LoggerFactory.getLogger(BidService.class);
    static final int MAX_ATTEMPTS = 12;

    private final BidProcessor processor;
    private final BidRepository bids;
    private final AuctionRepository auctions;
    private final AuctionEventPublisher events;

    public BidService(
            BidProcessor processor, BidRepository bids, AuctionRepository auctions, AuctionEventPublisher events) {
        this.processor = processor;
        this.bids = bids;
        this.auctions = auctions;
        this.events = events;
    }

    /**
     * Deliberately NOT transactional: every retry must start a brand-new transaction that re-reads the
     * latest auction row. A loser of a race therefore re-validates against the new price and is either
     * accepted (if still high enough) or rejected with a clear "bid too low" message.
     */
    public BidProcessor.Result placeBid(Long auctionId, Long bidderId, BigDecimal amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                BidProcessor.Result result = processor.process(auctionId, bidderId, amount);
                events.bidPlaced(result.auction(), result.bid(), result.previousBidderId());
                return result;
            } catch (ConcurrencyFailureException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Bid on auction {} gave up after {} conflicting attempts", auctionId, attempt);
                    throw ApiException.conflict("The auction is very busy right now - please try again");
                }
                backoff(attempt);
            }
        }
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(2, 8L + attempt * 4L));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("Bid interrupted");
        }
    }

    @Transactional(readOnly = true)
    public PageDto<BidDto> historyForAuction(Long auctionId, int page, int size) {
        if (!auctions.existsById(auctionId)) {
            throw ApiException.notFound("Auction not found");
        }
        return toPage(bids.findByAuctionIdOrderByAmountDesc(auctionId, pageRequest(page, size, 100)));
    }

    @Transactional(readOnly = true)
    public PageDto<BidDto> historyForUser(Long userId, int page, int size) {
        return toPage(bids.findByBidderIdOrderByCreatedAtDesc(userId, pageRequest(page, size, 50)));
    }

    private static PageRequest pageRequest(int page, int size, int max) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), max));
    }

    private static PageDto<BidDto> toPage(Page<com.auction.domain.Bid> p) {
        return new PageDto<>(
                p.getContent().stream().map(Mappers::toDto).toList(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages());
    }
}
