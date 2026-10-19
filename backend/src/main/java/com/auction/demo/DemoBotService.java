package com.auction.demo;

import com.auction.domain.AuctionStatus;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.repository.AuctionRepository;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps a demo deployment lively: bot users place bids every few seconds and new listings appear when
 * the number of active auctions runs low. Disable with APP_DEMO_BOTS_ENABLED=false for a real marketplace.
 */
@Component
@ConditionalOnProperty(name = "app.demo.bots-enabled", havingValue = "true")
public class DemoBotService {

    private static final Logger log = LoggerFactory.getLogger(DemoBotService.class);
    private static final int MIN_ACTIVE = 8;

    private final UserRepository users;
    private final AuctionRepository auctionRepo;
    private final AuctionService auctions;
    private final BidService bids;

    public DemoBotService(UserRepository users, AuctionRepository auctionRepo, AuctionService auctions, BidService bids) {
        this.users = users;
        this.auctionRepo = auctionRepo;
        this.auctions = auctions;
        this.bids = bids;
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 6000)
    public void tick() {
        try {
            List<User> bots = new ArrayList<>();
            for (String name : DemoDataSeeder.BOTS) {
                users.findByUsernameIgnoreCase(name).ifPresent(bots::add);
            }
            if (bots.size() < 2) {
                return;
            }
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            replenish(bots, rnd);
            bidOnRandomAuction(bots, rnd);
        } catch (RuntimeException e) {
            log.debug("Demo bot tick failed: {}", e.getMessage());
        }
    }

    private void replenish(List<User> bots, ThreadLocalRandom rnd) {
        long active = auctionRepo.countByStatus(AuctionStatus.ACTIVE);
        if (active < MIN_ACTIVE) {
            int duration = rnd.nextInt(5, 240);
            auctions.create(DemoCatalog.random(duration), bots.get(rnd.nextInt(bots.size())).getId());
        }
    }

    private void bidOnRandomAuction(List<User> bots, ThreadLocalRandom rnd) {
        if (rnd.nextInt(100) < 25) {
            return; // not every tick, so the activity feels organic
        }
        List<AuctionDto> active = auctions.list("ACTIVE", null, null, "ending", 0, 20).content();
        if (active.isEmpty()) {
            return;
        }
        AuctionDto target = active.get(rnd.nextInt(active.size()));
        List<User> eligible = new ArrayList<>(bots);
        eligible.removeIf(b -> b.getId().equals(target.sellerId()) || b.getId().equals(target.highestBidderId()));
        if (eligible.isEmpty()) {
            return;
        }
        User bidder = eligible.get(rnd.nextInt(eligible.size()));
        BigDecimal amount = target.minNextBid().add(target.minIncrement().multiply(BigDecimal.valueOf(rnd.nextInt(0, 3))));
        try {
            bids.placeBid(target.id(), bidder.getId(), amount);
        } catch (RuntimeException e) {
            log.debug("Bot bid rejected: {}", e.getMessage());
        }
    }
}
