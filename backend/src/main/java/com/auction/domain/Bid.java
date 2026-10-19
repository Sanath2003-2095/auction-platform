package com.auction.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "bids",
        indexes = {
            @Index(name = "idx_bid_auction_amount", columnList = "auction_id, amount"),
            @Index(name = "idx_bid_bidder_created", columnList = "bidder_id, createdAt")
        })
@Getter
@Setter
@NoArgsConstructor
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id")
    private Auction auction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id")
    private User bidder;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Bid(Auction auction, User bidder, BigDecimal amount) {
        this.auction = auction;
        this.bidder = bidder;
        this.amount = amount;
    }
}
