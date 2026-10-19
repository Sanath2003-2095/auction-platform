package com.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import com.auction.domain.Bid;
import com.auction.domain.Role;
import com.auction.domain.User;
import com.auction.exception.ApiException;
import com.auction.repository.AuctionRepository;
import com.auction.repository.BidRepository;
import com.auction.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidProcessorTest {

    @Mock AuctionRepository auctions;
    @Mock BidRepository bids;
    @Mock UserRepository users;
    @InjectMocks BidProcessor processor;

    User seller;
    User alice;
    User bob;
    Auction auction;

    @BeforeEach
    void setUp() {
        seller = user(1L, "seller");
        alice = user(2L, "alice");
        bob = user(3L, "bob");
        auction = new Auction();
        ReflectionTestUtils.setField(auction, "id", 10L);
        auction.setTitle("Lamp");
        auction.setStartingPrice(new BigDecimal("100.00"));
        auction.setCurrentPrice(new BigDecimal("100.00"));
        auction.setMinIncrement(new BigDecimal("5.00"));
        auction.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        auction.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setSeller(seller);
        when(auctions.findById(10L)).thenReturn(Optional.of(auction));
        lenient(alice);
        lenient(bob);
    }

    private void lenient(User u) {
        org.mockito.Mockito.lenient().when(users.findById(u.getId())).thenReturn(Optional.of(u));
    }

    @Test
    void firstBidMayEqualStartingPrice() {
        var result = processor.process(10L, 2L, new BigDecimal("100"));

        assertThat(result.auction().currentPrice()).isEqualByComparingTo("100.00");
        assertThat(result.auction().highestBidderName()).isEqualTo("alice");
        assertThat(result.auction().bidCount()).isEqualTo(1);
        assertThat(result.previousBidderId()).isNull();
    }

    @Test
    void laterBidMustBeAtLeastCurrentPlusIncrement() {
        processor.process(10L, 2L, new BigDecimal("100"));

        assertThatThrownBy(() -> processor.process(10L, 3L, new BigDecimal("104.99")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 105.00");

        var ok = processor.process(10L, 3L, new BigDecimal("105"));
        assertThat(ok.previousBidderId()).isEqualTo(2L);
        assertThat(ok.auction().highestBidderName()).isEqualTo("bob");
    }

    @Test
    void sellerCannotBidOnOwnAuction() {
        lenient(seller);
        assertThatThrownBy(() -> processor.process(10L, 1L, new BigDecimal("500")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own auction");
        verify(bids, never()).save(any(Bid.class));
    }

    @Test
    void highestBidderCannotOutbidThemselves() {
        processor.process(10L, 2L, new BigDecimal("100"));
        assertThatThrownBy(() -> processor.process(10L, 2L, new BigDecimal("200")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already the highest bidder");
    }

    @Test
    void cannotBidOnEndedOrCancelledAuction() {
        auction.setEndTime(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> processor.process(10L, 2L, new BigDecimal("200")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ended");

        auction.setEndTime(Instant.now().plus(1, ChronoUnit.HOURS));
        auction.setStatus(AuctionStatus.CANCELLED);
        assertThatThrownBy(() -> processor.process(10L, 2L, new BigDecimal("200")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ended");
    }

    @Test
    void bidInLastSecondsExtendsTheAuction() {
        Instant almostOver = Instant.now().plusSeconds(10);
        auction.setEndTime(almostOver);

        processor.process(10L, 2L, new BigDecimal("100"));

        assertThat(auction.getEndTime()).isEqualTo(almostOver.plusSeconds(30));
    }

    private static User user(Long id, String name) {
        User u = new User(name, name + "@x.com", "hash", Role.USER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
