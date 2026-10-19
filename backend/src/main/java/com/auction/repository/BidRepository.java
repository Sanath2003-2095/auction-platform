package com.auction.repository;

import com.auction.domain.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {

    @EntityGraph(attributePaths = {"bidder", "auction"})
    Page<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId, Pageable pageable);

    @EntityGraph(attributePaths = {"bidder", "auction"})
    Page<Bid> findByBidderIdOrderByCreatedAtDesc(Long bidderId, Pageable pageable);
}
