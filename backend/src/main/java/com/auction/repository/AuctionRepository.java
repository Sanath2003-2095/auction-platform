package com.auction.repository;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuctionRepository extends JpaRepository<Auction, Long>, JpaSpecificationExecutor<Auction> {

    // Entity graphs fetch seller/highestBidder in the same query, avoiding N+1 selects on list pages.
    @Override
    @EntityGraph(attributePaths = {"seller", "highestBidder"})
    Optional<Auction> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"seller", "highestBidder"})
    Page<Auction> findAll(Specification<Auction> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"seller", "highestBidder"})
    Page<Auction> findBySellerId(Long sellerId, Pageable pageable);

    @Query("select a.id from Auction a where a.status = :status and a.endTime <= :now")
    List<Long> findIdsByStatusAndEndTimeBefore(AuctionStatus status, Instant now);

    long countByStatus(AuctionStatus status);

    @Query("select distinct a.category from Auction a order by a.category")
    List<String> findDistinctCategories();
}
