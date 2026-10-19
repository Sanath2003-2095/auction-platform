package com.auction.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "auctions",
        indexes = {
            @Index(name = "idx_auction_status_end", columnList = "status, endTime"),
            @Index(name = "idx_auction_seller", columnList = "seller_id"),
            @Index(name = "idx_auction_category", columnList = "category")
        })
@Getter
@Setter
@NoArgsConstructor
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic lock: concurrent bids on the same row cannot silently overwrite each other. */
    @Version
    private Long version;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal startingPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minIncrement;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AuctionStatus status = AuctionStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "highest_bidder_id")
    private User highestBidder;

    @Column(nullable = false)
    private int bidCount = 0;

    public BigDecimal minNextBid() {
        return bidCount == 0 ? startingPrice : currentPrice.add(minIncrement);
    }
}
