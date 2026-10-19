package com.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.auction.domain.Auction;
import com.auction.domain.Bid;
import com.auction.domain.Role;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.AuctionRequest;
import com.auction.exception.ApiException;
import com.auction.repository.AuctionRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Fires many simultaneous bids at one auction and verifies that no bid is lost, no bid is accepted at
 * an invalid price, and the auction row always ends in a state consistent with the bid history.
 */
@SpringBootTest
@ActiveProfiles("test")
class BidConcurrencyTest {

    private static final int BIDDERS = 40;

    @Autowired BidService bidService;
    @Autowired AuctionService auctionService;
    @Autowired AuctionRepository auctions;
    @Autowired BidRepository bids;
    @Autowired UserRepository users;

    @Test
    void simultaneousBidsNeverCorruptTheAuction() throws Exception {
        User seller = users.save(new User("conc_seller", "conc_seller@x.com", "hash", Role.USER));
        List<User> bidders = new ArrayList<>();
        for (int i = 0; i < BIDDERS; i++) {
            bidders.add(users.save(new User("conc_b" + i, "conc_b" + i + "@x.com", "hash", Role.USER)));
        }
        AuctionDto created = auctionService.create(
                new AuctionRequest("Race", "Concurrency test", null, "Test",
                        new BigDecimal("10.00"), new BigDecimal("1.00"), 60),
                seller.getId());

        ExecutorService pool = Executors.newFixedThreadPool(BIDDERS);
        CountDownLatch ready = new CountDownLatch(BIDDERS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < BIDDERS; i++) {
            final User bidder = bidders.get(i);
            final BigDecimal amount = new BigDecimal(20 + i * 5); // every bidder uses a distinct amount
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    bidService.placeBid(created.id(), bidder.getId(), amount);
                    accepted.incrementAndGet();
                } catch (ApiException e) {
                    rejected.incrementAndGet(); // "bid too low" after losing a race is a valid outcome
                } catch (Throwable t) {
                    unexpected.incrementAndGet();
                    t.printStackTrace();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        Auction finalState = auctions.findById(created.id()).orElseThrow();
        List<Bid> history = bids.findByAuctionIdOrderByAmountDesc(created.id(), org.springframework.data.domain.Pageable.unpaged())
                .getContent();

        assertThat(unexpected.get()).as("no unexpected errors / 500s").isZero();
        assertThat(accepted.get() + rejected.get()).isEqualTo(BIDDERS);
        assertThat(accepted.get()).isGreaterThan(0);

        // Every accepted bid is persisted exactly once, and the counter matches the history (no lost updates).
        assertThat(history).hasSize(accepted.get());
        assertThat(finalState.getBidCount()).isEqualTo(accepted.get());

        // The auction's current price is the highest bid, held by that bid's bidder.
        Bid top = history.get(0);
        assertThat(finalState.getCurrentPrice()).isEqualByComparingTo(top.getAmount());
        assertThat(finalState.getHighestBidder().getId()).isEqualTo(top.getBidder().getId());

        // Accepted bids, in the order they were committed, form a strictly increasing price sequence
        // with every step at least one minimum increment above the previous one.
        List<Bid> chronological = history.stream().sorted(Comparator.comparing(Bid::getId)).toList();
        for (int i = 1; i < chronological.size(); i++) {
            BigDecimal prev = chronological.get(i - 1).getAmount();
            BigDecimal cur = chronological.get(i).getAmount();
            assertThat(cur).as("bid #%d must beat bid #%d by the increment", i, i - 1)
                    .isGreaterThanOrEqualTo(prev.add(finalState.getMinIncrement()));
        }
    }
}
