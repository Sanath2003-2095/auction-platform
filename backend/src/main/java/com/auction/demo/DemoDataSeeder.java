package com.auction.demo;

import com.auction.domain.Role;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.repository.UserRepository;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** On an empty database, creates an admin, a demo user, a few bidders and a set of live auctions. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final List<String> BOTS = List.of("alice", "bob", "carol", "dave");
    private static final int[] DURATIONS_MIN = {3, 8, 20, 45, 90, 240, 720, 1440, 2880, 4320};

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuctionService auctions;
    private final BidService bids;
    private final String adminPassword;

    public DemoDataSeeder(
            UserRepository users,
            PasswordEncoder encoder,
            AuctionService auctions,
            BidService bids,
            @Value("${ADMIN_PASSWORD:admin12345}") String adminPassword) {
        this.users = users;
        this.encoder = encoder;
        this.auctions = auctions;
        this.bids = bids;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new User("admin", "admin@auction.local", encoder.encode(adminPassword), Role.ADMIN));
        User demo = users.save(new User("demo", "demo@auction.local", encoder.encode("demo1234"), Role.USER));
        List<User> bots = new ArrayList<>();
        for (String name : BOTS) {
            bots.add(users.save(new User(name, name + "@auction.local", encoder.encode("password123"), Role.USER)));
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < DURATIONS_MIN.length; i++) {
            User seller = i % 3 == 0 ? demo : bots.get(i % bots.size());
            AuctionDto a = auctions.create(DemoCatalog.request(i, DURATIONS_MIN[i]), seller.getId());
            // Give most listings a bidding history so the charts and "highest bidder" UI have data.
            if (i % 4 != 3) {
                BigDecimal price = a.minNextBid();
                int rounds = rnd.nextInt(2, 6);
                User last = null;
                for (int r = 0; r < rounds; r++) {
                    User bidder = pickBidder(bots, demo, seller, last, rnd, i);
                    try {
                        var res = bids.placeBid(a.id(), bidder.getId(), price);
                        last = bidder;
                        price = res.auction().minNextBid().add(a.minIncrement().multiply(BigDecimal.valueOf(rnd.nextInt(0, 3))));
                    } catch (RuntimeException e) {
                        log.debug("Seed bid skipped: {}", e.getMessage());
                    }
                }
            }
        }
        log.info("Seeded demo data. Logins: demo/demo1234, admin/<ADMIN_PASSWORD>, bots alice|bob|carol|dave / password123");
    }

    private static User pickBidder(List<User> bots, User demo, User seller, User last, ThreadLocalRandom rnd, int i) {
        List<User> pool = new ArrayList<>(bots);
        pool.removeIf(u -> u.getId().equals(seller.getId()) || (last != null && u.getId().equals(last.getId())));
        return pool.get(rnd.nextInt(pool.size()));
    }
}
