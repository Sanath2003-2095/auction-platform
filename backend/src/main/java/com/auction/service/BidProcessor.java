package com.auction.service;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import com.auction.domain.Bid;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.BidDto;
import com.auction.dto.Mappers;
import com.auction.exception.ApiException;
import com.auction.repository.AuctionRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs ONE bid attempt inside ONE transaction. Concurrency safety comes from the {@code @Version}
 * column on {@link Auction}: if two transactions read the same auction row and both try to update it,
 * the second flush fails with an optimistic-lock exception instead of overwriting the first bid.
 * {@link BidService} retries such failures against the fresh state.
 */
@Component
public class BidProcessor {

    /** A bid landing inside this window pushes the end time out, so nobody can snipe at the last second. */
    static final Duration ANTI_SNIPE_WINDOW = Duration.ofSeconds(30);

    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final UserRepository users;

    public BidProcessor(AuctionRepository auctions, BidRepository bids, UserRepository users) {
        this.auctions = auctions;
        this.bids = bids;
        this.users = users;
    }

    public record Result(AuctionDto auction, BidDto bid, Long previousBidderId) {}

    @Transactional
    public Result process(Long auctionId, Long bidderId, BigDecimal rawAmount) {
        BigDecimal amount = rawAmount.setScale(2, RoundingMode.HALF_UP);
        Auction auction = auctions.findById(auctionId).orElseThrow(() -> ApiException.notFound("Auction not found"));
        User bidder = users.findById(bidderId).orElseThrow(() -> ApiException.notFound("User not found"));
        Instant now = Instant.now();

        if (auction.getStatus() != AuctionStatus.ACTIVE || !now.isBefore(auction.getEndTime())) {
            throw ApiException.badRequest("This auction has ended");
        }
        if (auction.getSeller().getId().equals(bidderId)) {
            throw ApiException.forbidden("You cannot bid on your own auction");
        }
        User previous = auction.getHighestBidder();
        if (previous != null && previous.getId().equals(bidderId)) {
            throw ApiException.badRequest("You are already the highest bidder");
        }
        BigDecimal min = auction.minNextBid();
        if (amount.compareTo(min) < 0) {
            throw ApiException.badRequest("Bid must be at least " + min.toPlainString());
        }

        auction.setCurrentPrice(amount);
        auction.setHighestBidder(bidder);
        auction.setBidCount(auction.getBidCount() + 1);
        if (Duration.between(now, auction.getEndTime()).compareTo(ANTI_SNIPE_WINDOW) < 0) {
            auction.setEndTime(auction.getEndTime().plus(ANTI_SNIPE_WINDOW));
        }
        Bid bid = new Bid(auction, bidder, amount);
        bids.save(bid);
        // Flush now so a version conflict surfaces here (and is retried), not at an unpredictable commit.
        auctions.saveAndFlush(auction);

        return new Result(Mappers.toDto(auction), Mappers.toDto(bid), previous == null ? null : previous.getId());
    }
}
