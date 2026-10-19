package com.auction.dto;

import com.auction.domain.Auction;
import com.auction.domain.Bid;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.BidDto;
import com.auction.dto.Dtos.UserDto;

public final class Mappers {

    private Mappers() {}

    public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }

    public static AuctionDto toDto(Auction a) {
        User hb = a.getHighestBidder();
        return new AuctionDto(
                a.getId(),
                a.getTitle(),
                a.getDescription(),
                a.getImageUrl(),
                a.getCategory(),
                a.getStartingPrice(),
                a.getCurrentPrice(),
                a.getMinIncrement(),
                a.minNextBid(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getSeller().getId(),
                a.getSeller().getUsername(),
                hb == null ? null : hb.getId(),
                hb == null ? null : hb.getUsername(),
                a.getBidCount());
    }

    public static BidDto toDto(Bid b) {
        return new BidDto(
                b.getId(),
                b.getAuction().getId(),
                b.getAuction().getTitle(),
                b.getBidder().getId(),
                b.getBidder().getUsername(),
                b.getAmount(),
                b.getCreatedAt());
    }
}
