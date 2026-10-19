package com.auction.service;

import com.auction.domain.Auction;
import com.auction.domain.AuctionStatus;
import org.springframework.data.jpa.domain.Specification;

final class AuctionSpecs {

    private AuctionSpecs() {}

    static Specification<Auction> status(AuctionStatus status) {
        return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    static Specification<Auction> category(String category) {
        return category == null || category.isBlank()
                ? null
                : (root, q, cb) -> cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase());
    }

    static Specification<Auction> search(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String like = "%" + text.trim().toLowerCase() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("description")), like));
    }
}
