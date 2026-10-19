package com.auction.web;

import com.auction.dto.Dtos.AuctionDto;
import com.auction.dto.Dtos.BidDto;
import com.auction.dto.Dtos.PageDto;
import com.auction.dto.Dtos.StatsDto;
import com.auction.security.UserPrincipal;
import com.auction.service.AuctionService;
import com.auction.service.BidService;
import com.auction.service.StatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final AuctionService auctions;
    private final BidService bids;
    private final StatsService stats;

    public MeController(AuctionService auctions, BidService bids, StatsService stats) {
        this.auctions = auctions;
        this.bids = bids;
        this.stats = stats;
    }

    @GetMapping("/stats")
    public StatsDto stats() {
        return stats.stats();
    }

    @GetMapping("/users/me/auctions")
    public PageDto<AuctionDto> myAuctions(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auctions.mine(user.id(), page, size);
    }

    @GetMapping("/users/me/bids")
    public PageDto<BidDto> myBids(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return bids.historyForUser(user.id(), page, size);
    }
}
